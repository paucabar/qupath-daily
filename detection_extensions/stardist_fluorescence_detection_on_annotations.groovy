import qupath.ext.stardist.StarDist2D
import qupath.lib.scripting.QP

// IMPORTANT! Replace this with the path to your StarDist model
// that takes a single channel as input (e.g. dsb2018_heavy_augment.pb)
// You can find some at https://github.com/qupath/models
// (Check credit & reuse info before downloading)
def modelPath = "YOUR_MODEL_PATH"

// Customize how the StarDist detection should be applied
// Here some reasonable default options are specified
def stardist = StarDist2D
    .builder(modelPath)
    .channels('DAPI')            // Extract channel called 'DAPI'
    .normalizePercentiles(1, 99) // Percentile normalization
    .threshold(0.5)              // Probability (detection) threshold
    .pixelSize(0.5)              // Resolution for detection
    //.cellExpansion(5)            // Expand nuclei to approximate cell boundaries
    .measureShape()              // Add shape measurements
    .measureIntensity()          // Add cell measurements (in all compartments)
    .createAnnotations()         // Generate annotation objects using StarDist, rather than detection objects
    .constrainToParent(true)     // Prevent nuclei/cells expanding beyond any parent annotations (default is true)
    .classify("Nucleus")         // Automatically classify all created objects as 'Nucleus'
    .build()
	
// Define which objects will be used as the 'parents' for detection
def pathObjects = getAnnotationObjects().findAll{(it.getPathClass() == getPathClass("YOUR_TARGET_CLASS")) }

// Run detection for the selected objects
def imageData = QP.getCurrentImageData()
if (pathObjects.isEmpty()) {
    QP.getLogger().error("No parent objects are selected!")
    return
}
stardist.detectObjects(imageData, pathObjects)
stardist.close() // This can help clean up & regain memory
println('Done!')