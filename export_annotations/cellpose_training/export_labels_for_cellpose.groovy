def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()

// Create output path (relative to project)
def outputDir = buildFilePath(PROJECT_BASE_DIR, 'export_nsc-em')
mkdirs(outputDir)

// Create output subfolders
def instanceDir = buildFilePath(outputDir, 'labels')
mkdirs(instanceDir)
def imageDir = buildFilePath(outputDir, 'images')
mkdirs(imageDir)

// Define how much to downsample during export
double downsample = 4

// Create an ImageServer for fibre instances
def instanceServer = new LabeledImageServer.Builder(imageData)
  .backgroundLabel(0, ColorTools.BLACK) // Specify background label (usually 0 or 255)
  .downsample(downsample)    // Choose server resolution; this should match the resolution at which tiles are exported
  .useAnnotations()
  .useInstanceLabels()
  .useFilter(p -> p.isAnnotation() && p.getPathClass() == getPathClass('YOUR_CLASS'))
  .multichannelOutput(false) // If true, each label refers to the channel of a multichannel binary image (required for multiclass probability)
  .build()
  
// Create an ImageServer where raw images are downsampled
def server = imageData.getServer()
def region = RegionRequest.createInstance(server, downsample)


// Get image name to export annotations
def name = GeneralTools.getNameWithoutExtension(imageData.getServer().getMetadata().getName())


// Define export paths
def pathInstance = buildFilePath(instanceDir, name + "_mask.tif") // Define instance output file paths
def pathImage = buildFilePath(imageDir, name + "_img.tif") // Define image output file path

// Write the images
writeImage(instanceServer, pathInstance)
writeImageRegion(server, region, pathImage)