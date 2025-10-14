import qupath.lib.objects.PathAnnotationObject
import qupath.lib.measurements.MeasurementList

// Get all annotation objects
def annotations = getAnnotationObjects()

// Filter annotations by class using getPathClass("ClassName")
def mitoClass = getPathClass("Mitochondria")
def cellbodyClass = getPathClass("CellBody")

def mitoAnnotations = annotations.findAll { it.getPathClass() == mitoClass }
def cellbodyAnnotations = annotations.findAll { it.getPathClass() == cellbodyClass }

if (mitoAnnotations.isEmpty() || cellbodyAnnotations.isEmpty()) {
    println "No Mitochondria or CellBody annotations found!"
    return
}

// Get pixel calibration info
def cal = getCurrentServer().getPixelCalibration()
def pixelSize = cal.getAveragedPixelSize()
def unit = cal.getPixelWidthUnit()
def isincellbody = 0

// Loop over each mitochondria and compute distance to closest nucleus
mitoAnnotations.each { mito ->
    def mitoGeom = mito.getROI().getGeometry()
    def mitoPlane = mito.getROI().getImagePlane()

    cellbodyAnnotations.each { cellbody ->
        if (cellbody.getROI().getImagePlane() != mitoPlane)
            return // Skip cellbody on different planes

        def cellbodyGeom = cellbody.getROI().getGeometry()
        def distance = mitoGeom.distance(cellbodyGeom)
        if (distance <= 0) {
            isincellbody = 1
        } else {
            isincellbody = 0
        }
    }

    // Add measurement to mitochondria annotation
    mito.getMeasurementList().putMeasurement("Cell Body (bool)", isincellbody)
}

println "Done! Cell Body parameter added to ${mitoAnnotations.size()} mitochondria annotations."
