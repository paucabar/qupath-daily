import qupath.lib.objects.PathDetectionObject
import qupath.lib.objects.PathAnnotationObject
import qupath.lib.roi.interfaces.ROI
import static qupath.lib.gui.scripting.QPEx.*

// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName = "YOUR_CLASS_NAME"  // class name, null (all), or "" (unclassified)
// ─────────────────────────────────────────────────────────────────────────────

def detectionsToConvert
if (targetClassName == null)
    detectionsToConvert = getDetectionObjects()
else if (targetClassName == "")
    detectionsToConvert = getDetectionObjects().findAll { it.getPathClass() == null }
else
    detectionsToConvert = getDetectionObjects().findAll { it.getPathClass() == getPathClass(targetClassName) }

removeObjects(detectionsToConvert, true)
def newAnnotations = detectionsToConvert.collect { detection ->
    PathObjects.createAnnotationObject(detection.getROI(), detection.getPathClass())
}
addObjects(newAnnotations)

fireHierarchyUpdate()
println "Converted ${detectionsToConvert.size()} detections to annotations."
