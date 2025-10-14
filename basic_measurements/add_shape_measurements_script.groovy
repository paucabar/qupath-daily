import qupath.lib.analysis.features.ObjectMeasurements

def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()
def server = imageData.getServer()
def cal = server.getPixelCalibration()

// Use to include all the annotations
//def pathObjects = hierarchy.getAnnotationObjects()

// Find all annotation objects classified as 'Neurosphere'
def neurosphereObjects = hierarchy.getAnnotationObjects().findAll { it.getPathClass() == getPathClass("Neurosphere") }

// Add shape measurements
neurosphereObjects.parallelStream().forEach(c -> ObjectMeasurements.addShapeMeasurements(c, cal))

println 'Add shape measurements script done'