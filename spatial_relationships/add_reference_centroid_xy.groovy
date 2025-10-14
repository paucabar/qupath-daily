/**
 * For a target annotation class adds the coordinates to a reference object
 * Only works if n reference object == 1
 */

def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()
def cal = imageData.getServer().getPixelCalibration()

// Find mitochondria and nucleus annotations
def mitochondriaObjects = hierarchy.getAnnotationObjects().findAll { it.getPathClass() == getPathClass("TARGET_CLASS_NAME") }
def nucleusObjects = hierarchy.getAnnotationObjects().findAll { it.getPathClass() == getPathClass("REFERENCE_CLASS_NAME") }

if (nucleusObjects.size() == 1) {
    def nucleus = nucleusObjects[0]
    def roi = nucleus.getROI()

    // Get centroid in pixels
    def centroidX_px = roi.getCentroidX()
    def centroidY_px = roi.getCentroidY()

    // Convert to microns using calibration
    def nucleusX = centroidX_px * cal.getPixelWidthMicrons()
    def nucleusY = centroidY_px * cal.getPixelHeightMicrons()

    // Add nucleus centroid to each mitochondria annotation
    mitochondriaObjects.each { mito ->
        mito.getMeasurementList().putMeasurement("Centroid X µm (RefObject)", nucleusX)
        mito.getMeasurementList().putMeasurement("Centroid Y µm (RefObject)", nucleusY)
    }

    println 'Added calibrated nucleus centroid coordinates to mitochondria objects.'

} else if (nucleusObjects.size() > 1) {
    println 'Multiple nucleus annotations found — this script only supports one.'
} else {
    println 'No nucleus annotation found.'
}
