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
    .cellExpansion(5)            // Expand nuclei to approximate cell boundaries
    .measureShape()              // Add shape measurements
    .measureIntensity()          // Add cell measurements (in all compartments)
    .createAnnotations()         // Generate annotation objects using StarDist, rather than detection objects
    .constrainToParent(true)     // Prevent nuclei/cells expanding beyond any parent annotations (default is true)
    //.classify("YOUR_CLASS_NAME")         // Automatically classify all created objects as 'Nucleus'
    .build()

// Get the image data
def imageData = getCurrentImageData()
def server = imageData.getServer()

// Get the number of zstacks (slices)
def numSlices = server.nZSlices()

// Loop through each frame
for (int z = 0; z < numSlices; z++) {
    print("Processing frame: " + z)

    // Create a full image annotation
    createFullImageAnnotation(true, z, 0) // Adjust z-slice (0) and timepoint (t) if necessary
    pathObjects = getAnnotationObjects() // Get newly created annotation

    // Run StarDist on the annotations
    stardist.detectObjects(imageData, pathObjects)
    stardist.close() // This can help clean up & regain memory
    
    // Delete full image annotation
    removeObjects(pathObjects, true)
    fireHierarchyUpdate()
    
    print("Finished processing frame: " + z)
}
println 'StarDist detection script done'