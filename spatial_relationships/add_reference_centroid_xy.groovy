/**
 * Adds centroid coordinates of a reference object to each target annotation.
 * Requires exactly one reference object to be present.
 */

// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName    = "YOUR_TARGET_CLASS"     // class to add coordinates to
def referenceClassName = "YOUR_REFERENCE_CLASS"  // class acting as reference (must have exactly 1 instance)
// ─────────────────────────────────────────────────────────────────────────────

def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()
def cal = imageData.getServer().getPixelCalibration()

def targetObjects    = hierarchy.getAnnotationObjects().findAll { it.getPathClass() == getPathClass(targetClassName) }
def referenceObjects = hierarchy.getAnnotationObjects().findAll { it.getPathClass() == getPathClass(referenceClassName) }

if (referenceObjects.size() == 1) {
    def roi = referenceObjects[0].getROI()
    def referenceX = roi.getCentroidX() * cal.getPixelWidthMicrons()
    def referenceY = roi.getCentroidY() * cal.getPixelHeightMicrons()

    targetObjects.each { target ->
        def ml = target.getMeasurementList()
        ml.putMeasurement("Centroid X µm (RefObject)", referenceX)
        ml.putMeasurement("Centroid Y µm (RefObject)", referenceY)
        ml.close()
    }

    println "Added reference centroid coordinates to ${targetObjects.size()} ${targetClassName} objects."

} else if (referenceObjects.size() > 1) {
    println "Multiple ${referenceClassName} annotations found — this script only supports one."
} else {
    println "No ${referenceClassName} annotation found."
}
