/**
 * Adapted from the Stardist fluorescent cell detection template
 */

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
    .preprocess(        // Extra preprocessing steps, applied sequentially
        ImageOps.Core.sqrt()
        )
    .threshold(0.5)              // Probability (detection) threshold
    .pixelSize(0.5)              // Resolution for detection
    .cellExpansion(2)            // Expand nuclei to approximate cell boundaries
    .measureShape()              // Add shape measurements
    .measureIntensity()          // Add cell measurements (in all compartments)
    .classify("YOUR_CLASS_NAME")       // PathClass to give newly created objects
//  .createAnnotations()             // Make annotations instead of detections. This ignores cellExpansion
//  .simplify(0)                     // Simplification 1.6 by default, set to 0 to get the cellpose masks as precisely as possible
    .build()


// Run detection for the selected objects
createFullImageAnnotation(true)
def imageData = getCurrentImageData()
def pathObjects = getSelectedObjects() // To process only selected annotations, useful while testing
//def pathObjects = getAnnotationObjects() // To process all annotations. For working in batch mode
if (pathObjects.isEmpty()) {
    QP.getLogger().error("No parent objects are selected!")
    return
}

stardist.detectObjects(imageData, pathObjects)
stardist.close() // This can help clean up & regain memory

// Clear full image annotation
def fullImageAnnotation = getAnnotationObjects().findAll{ it.getPathClass() == null }
removeObjects(fullImageAnnotation , true)

println('StarDist detection script done')