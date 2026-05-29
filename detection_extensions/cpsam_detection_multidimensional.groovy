/**
 * Adapted from
 * Olivier Burri's Cellpose Detection Template script
 */


import qupath.ext.biop.cellpose.Cellpose2D

// Specify the model name (cyto, nuclei, cyto2, ... or a path to your custom model as a string)
// Other models for Cellpose https://cellpose.readthedocs.io/en/latest/models.html
// And for Omnipose: https://omnipose.readthedocs.io/models.html
def pathModel = 'cpsam'
def cellpose = Cellpose2D.builder( pathModel )
        .pixelSize( 1 )                  // Resolution for detection in um
        .channels( 0,1 )	               // Select detection channel(s)
//        .tempDirectory( new File( '/tmp' ) ) // Temporary directory to export images to. defaults to 'cellpose-temp' inside the QuPath Project
//        .preprocess( ImageOps.Filters.median(1) )                // List of preprocessing ImageOps to run on the images before exporting them
//        .normalizePercentilesGlobal(0.1, 99.8, 10) // Convenience global percentile normalization. arguments are percentileMin, percentileMax, dowsample.
//        .tileSize(1024)                  // If your GPU can take it, make larger tiles to process fewer of them. Useful for Omnipose
//        .cellposeChannels(1,2)           // Overwrites the logic of this plugin with these two values. These will be sent directly to --chan and --chan2
//        .cellprobThreshold(0.0)          // Threshold for the mask detection, defaults to 0.0
//        .flowThreshold(0.4)              // Threshold for the flows, defaults to 0.4
//        .diameter(15)                    // Median object diameter. Set to 0.0 for the `bact_omni` model or for automatic computation
//        .useOmnipose()                   // Use omnipose instead
//        .addParameter("cluster")         // Any parameter from cellpose or omnipose not available in the builder.
//        .addParameter("save_flows")      // Any parameter from cellpose or omnipose not available in the builder.
//        .addParameter("anisotropy", "3") // Any parameter from cellpose or omnipose not available in the builder.
//        .cellExpansion(5.0)              // Approximate cells based upon nucleus expansion
//        .cellConstrainScale(1.5)         // Constrain cell expansion using nucleus size
        .classify("YOUR_CLASS_NAME")       // PathClass to give newly created objects
        .measureShape()                  // Add shape measurements
        .measureIntensity()              // Add cell measurements (in all compartments)
//        .createAnnotations()             // Make annotations instead of detections. This ignores cellExpansion
//        .simplify(0)                     // Simplification 1.6 by default, set to 0 to get the cellpose masks as precisely as possible
        .build()

// Get the image data
def imageData = getCurrentImageData()
def server = imageData.getServer()

// Create full-image annotations for all z-slices and timepoints
def zStep = 1      // 1 = every slice, 2 = every other slice, etc.
def frameStep = 1  // 1 = every frame, 2 = every other frame, etc.
def annotations = []
for (int z = 0; z < server.nZSlices(); z += zStep)
    for (int t = 0; t < server.nTimepoints(); t += frameStep)
        annotations << createFullImageAnnotation(true, z, t)
fireHierarchyUpdate()

// Run Cellpose once across all annotations
print("Running Cellpose on ${annotations.size()} plane(s)")
cellpose.detectObjects(imageData, annotations)

// Remove annotations, keeping detections
removeObjects(annotations, true)
fireHierarchyUpdate()

println 'Cellpose detection script done'
