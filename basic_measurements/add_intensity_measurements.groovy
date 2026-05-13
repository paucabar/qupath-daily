import qupath.lib.analysis.features.ObjectMeasurements
import qupath.lib.analysis.features.ObjectMeasurements.Measurements

// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName = "YOUR_CLASS_NAME"  // class name, null (all), or "" (unclassified)
def downsample = 1.0
def measurements = [
    Measurements.MEAN,
    Measurements.MIN,
    Measurements.MAX,
    Measurements.STD_DEV
] as Set
// ─────────────────────────────────────────────────────────────────────────────

def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()
def server = imageData.getServer()

def targetObjects
if (targetClassName == null)
    targetObjects = hierarchy.getAnnotationObjects()
else if (targetClassName == "")
    targetObjects = hierarchy.getAnnotationObjects().findAll { it.getPathClass() == null }
else
    targetObjects = hierarchy.getAnnotationObjects().findAll { it.getPathClass() == getPathClass(targetClassName) }

targetObjects.parallelStream().forEach { annotation ->
    ObjectMeasurements.addIntensityMeasurements(server, annotation, downsample, measurements, null)
}

println 'Add intensity measurements script done'
