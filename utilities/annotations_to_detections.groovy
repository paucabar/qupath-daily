import qupath.lib.objects.PathDetectionObject
import qupath.lib.objects.PathAnnotationObject
import qupath.lib.roi.interfaces.ROI
import static qupath.lib.gui.scripting.QPEx.*

// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName = "YOUR_TARGET_CLASS"  // class name, null (all), or "" (unclassified)
// ─────────────────────────────────────────────────────────────────────────────

def annotationsToConvert
if (targetClassName == null)
    annotationsToConvert = getAnnotationObjects()
else if (targetClassName == "")
    annotationsToConvert = getAnnotationObjects().findAll { it.getPathClass() == null }
else
    annotationsToConvert = getAnnotationObjects().findAll { it.getPathClass() == getPathClass(targetClassName) }

removeObjects(annotationsToConvert, true)
def newDetections = annotationsToConvert.collect { annotation ->
    PathObjects.createDetectionObject(annotation.getROI(), annotation.getPathClass())
}
addObjects(newDetections)

fireHierarchyUpdate()
println "Converted ${annotationsToConvert.size()} annotations to detections."
