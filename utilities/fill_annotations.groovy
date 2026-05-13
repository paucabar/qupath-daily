import qupath.lib.objects.PathAnnotationObject
import qupath.lib.roi.interfaces.ROI
import qupath.lib.roi.RoiTools
import static qupath.lib.gui.scripting.QPEx.*

// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName = null  // class name, null (all), or "" (unclassified)
// ─────────────────────────────────────────────────────────────────────────────

RoiTools rt = new RoiTools()

def annotationsToFill
if (targetClassName == null)
    annotationsToFill = getAnnotationObjects()
else if (targetClassName == "")
    annotationsToFill = getAnnotationObjects().findAll { it.getPathClass() == null }
else
    annotationsToFill = getAnnotationObjects().findAll { it.getPathClass() == getPathClass(targetClassName) }

removeObjects(annotationsToFill, true)

def filledAnnotations = annotationsToFill.collect { annotation ->
    ROI roi = annotation.getROI()
    def filledRoi = (roi.getRoiName() != "Rectangle") ? rt.fillHoles(roi) : roi
    PathObjects.createAnnotationObject(filledRoi, annotation.getPathClass())
}
addObjects(filledAnnotations)

fireHierarchyUpdate()
println "Filled holes in ${filledAnnotations.size()} annotations."
