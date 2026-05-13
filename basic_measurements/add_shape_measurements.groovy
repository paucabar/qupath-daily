import qupath.lib.analysis.features.ObjectMeasurements

// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName = "YOUR_CLASS_NAME"  // class name, null (all), or "" (unclassified)
// ─────────────────────────────────────────────────────────────────────────────

def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()
def server = imageData.getServer()
def cal = server.getPixelCalibration()

def targetObjects
if (targetClassName == null)
    targetObjects = hierarchy.getAnnotationObjects()
else if (targetClassName == "")
    targetObjects = hierarchy.getAnnotationObjects().findAll { it.getPathClass() == null }
else
    targetObjects = hierarchy.getAnnotationObjects().findAll { it.getPathClass() == getPathClass(targetClassName) }

targetObjects.parallelStream().forEach(c -> ObjectMeasurements.addShapeMeasurements(c, cal))

println 'Add shape measurements script done'
