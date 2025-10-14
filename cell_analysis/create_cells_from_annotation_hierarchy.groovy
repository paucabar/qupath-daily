import qupath.lib.objects.PathObjects
import qupath.lib.analysis.features.ObjectMeasurements

// convert annotations to detections to resolve hierarchy
// for annotations, the entire edge is checked
// for detections, only the centroid is checked
def annotations = getAnnotationObjects().findAll{ it.getPathClass() == getPathClass("Nucleus") }
def newDetections = annotations.collect {
    return PathObjects.createDetectionObject(it.getROI(), it.getPathClass())
}

removeObjects(annotations, true)
addObjects(newDetections)

// resolve hierarchy
resolveHierarchy()


def cytoplasms = getAnnotationObjects().findAll{ it.getPathClass() == getPathClass("YOUR_CELL_CLASS") }
def nuclei = getDetectionObjects().findAll{ it.getPathClass() == getPathClass("Nucleus") }
def cells = []

cytoplasms.each { it ->
    def roiCytoplasm = it.getROI()
    def children = it.getDescendantObjects()
    if (children.size() == 1) {
        def roiNucleus = children.get(0).getROI()
        def pathClass = it.getPathClass()
        cells.add(PathObjects.createCellObject(roiCytoplasm, roiNucleus, pathClass))
    }
}

removeObjects(nuclei, true)
removeObjects(cytoplasms, true)
addObjects(cells)

// Add cell measurements
def downsample = 1
def server = getCurrentServer()
var cal = server.getPixelCalibration()

def measurements = ObjectMeasurements.Measurements.values() as List
def compartments = ObjectMeasurements.Compartments.values() as List
def shape = ObjectMeasurements.ShapeFeatures.values() as List
cells = getCellObjects()
for (cell in cells) {
    ObjectMeasurements.addIntensityMeasurements(server, cell, downsample, measurements, compartments)
    ObjectMeasurements.addCellShapeMeasurements(cell, cal, shape)
}