import qupath.lib.roi.RoiTools
import qupath.lib.roi.interfaces.ROI

// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName     = "YOUR_CLASS_NAME"  // class name, null (all), or "" (unclassified)
double expansionPixels  = 3.0                // positive to dilate, negative to erode
// ─────────────────────────────────────────────────────────────────────────────

RoiTools rt = new RoiTools()

def annotationsToResize
if (targetClassName == null)
    annotationsToResize = getAnnotationObjects()
else if (targetClassName == "")
    annotationsToResize = getAnnotationObjects().findAll { it.getPathClass() == null }
else
    annotationsToResize = getAnnotationObjects().findAll { it.getPathClass() == getPathClass(targetClassName) }

removeObjects(annotationsToResize, true)

def emptied = 0
def resizedAnnotations = annotationsToResize.findResults { annotation ->
    ROI roi = annotation.getROI()
    ROI resizedRoi = rt.buffer(roi, expansionPixels)
    if (resizedRoi == null || resizedRoi.isEmpty()) {
        emptied++
        return null
    }
    PathObjects.createAnnotationObject(resizedRoi, annotation.getPathClass())
}
addObjects(resizedAnnotations)

fireHierarchyUpdate()
println "Resized ${resizedAnnotations.size()} annotation(s) by ${expansionPixels} px" +
        (emptied > 0 ? " (${emptied} eroded to nothing and dropped)." : ".")
