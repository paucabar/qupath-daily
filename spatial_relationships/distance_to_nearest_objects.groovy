import qupath.lib.objects.PathAnnotationObject
import qupath.lib.measurements.MeasurementList

// Get all annotation objects
def annotations = getAnnotationObjects()

// Filter annotations by class using getPathClass("ClassName")
def mitoClass = getPathClass("Mitochondria")
def nucleusClass = getPathClass("Nucleus")

def mitoAnnotations = annotations.findAll { it.getPathClass() == mitoClass }
def nucleusAnnotations = annotations.findAll { it.getPathClass() == nucleusClass }

if (mitoAnnotations.isEmpty() || nucleusAnnotations.isEmpty()) {
    println "No Mitochondria or Nucleus annotations found!"
    return
}

// Get pixel calibration info
def cal = getCurrentServer().getPixelCalibration()
def pixelSize = cal.getAveragedPixelSize()
def unit = cal.getPixelWidthUnit()

// Loop over each mitochondria and compute distance to closest nucleus
mitoAnnotations.each { mito ->
    def mitoGeom = mito.getROI().getGeometry()
    def mitoPlane = mito.getROI().getImagePlane()

    def minDistance = Double.MAX_VALUE

    nucleusAnnotations.each { nucleus ->
        if (nucleus.getROI().getImagePlane() != mitoPlane)
            return // Skip nuclei on different planes

        def nucleusGeom = nucleus.getROI().getGeometry()
        def distance = mitoGeom.distance(nucleusGeom)
        if (distance < minDistance) {
            minDistance = distance
        }
    }

    // Add measurement to mitochondria annotation
    def distanceCalibrated = minDistance * pixelSize
    mito.getMeasurementList().putMeasurement("Distance to nearest nucleus (${unit})", distanceCalibrated)
}

println "Done! Distance measurements added to ${mitoAnnotations.size()} mitochondria annotations."
