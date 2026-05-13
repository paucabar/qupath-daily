import qupath.lib.objects.PathObjects
import qupath.lib.analysis.features.ObjectMeasurements

// ── Configuration ────────────────────────────────────────────────────────────
def nucleusClassName   = "Nucleus"          // class name for nucleus annotations
def cytoplasmClassName = "YOUR_CELL_CLASS"  // class name for cytoplasm annotations
def downsample         = 1
// ─────────────────────────────────────────────────────────────────────────────

// convert annotations to detections to resolve hierarchy
// for annotations, the entire edge is checked
// for detections, only the centroid is checked
def annotations = getAnnotationObjects().findAll { it.getPathClass() == getPathClass(nucleusClassName) }
def newDetections = annotations.collect {
    PathObjects.createDetectionObject(it.getROI(), it.getPathClass())
}

removeObjects(annotations, true)
addObjects(newDetections)

// resolve hierarchy
resolveHierarchy()

def cytoplasms = getAnnotationObjects().findAll { it.getPathClass() == getPathClass(cytoplasmClassName) }
def nuclei     = getDetectionObjects().findAll  { it.getPathClass() == getPathClass(nucleusClassName) }
def cells      = []

cytoplasms.each { it ->
    def roiCytoplasm = it.getROI()
    def children = it.getDescendantObjects()
    if (children.size() == 1) {
        def roiNucleus = children.get(0).getROI()
        cells.add(PathObjects.createCellObject(roiCytoplasm, roiNucleus, it.getPathClass()))
    }
}

removeObjects(nuclei, true)
removeObjects(cytoplasms, true)
addObjects(cells)

// Add cell measurements
def server = getCurrentServer()
var cal = server.getPixelCalibration()

def measurements = ObjectMeasurements.Measurements.values() as List
def compartments = ObjectMeasurements.Compartments.values() as List
def shape        = ObjectMeasurements.ShapeFeatures.values() as List

cells = getCellObjects()
for (cell in cells) {
    ObjectMeasurements.addIntensityMeasurements(server, cell, downsample, measurements, compartments)
    ObjectMeasurements.addCellShapeMeasurements(cell, cal, shape)
}
