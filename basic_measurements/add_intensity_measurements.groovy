import qupath.lib.analysis.features.ObjectMeasurements
import qupath.lib.analysis.features.ObjectMeasurements.Measurements

def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()
def server = imageData.getServer()

// Get all annotation objects
def annotationObjects = hierarchy.getAnnotationObjects()

// Define which intensity measurements to calculate
def measurements = [
    Measurements.MEAN,
    Measurements.MIN,
    Measurements.MAX,
    Measurements.STD_DEV
] as Set

// Apply intensity measurements to each annotation (no compartments needed)
annotationObjects.parallelStream().forEach { annotation ->
    ObjectMeasurements.addIntensityMeasurements(server, annotation, 1.0, measurements, null)
}

println 'Add intensity measurements script done'


