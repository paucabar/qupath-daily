import qupath.lib.regions.RegionRequest

def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()

// Create output path (relative to project)
def outputDir = buildFilePath(PROJECT_BASE_DIR, 'export_cellpose')
mkdirs(outputDir)

// Create output subfolders
def instanceDir = buildFilePath(outputDir, 'labels')
mkdirs(instanceDir)
def imageDir = buildFilePath(outputDir, 'images')
mkdirs(imageDir)

// Define how much to downsample during export
double downsample = 1

// Create an ImageServer where raw images are downsampled
def server = imageData.getServer()
def width = server.getWidth()
def height = server.getHeight()

// Get the current viewer
def viewer = getCurrentViewer()

// Get the currently displayed Z-slice and Timepoint
def zSlice = viewer.getImagePlane().getZ()
def timepoint = viewer.getImagePlane().getT()

// Define a RegionRequest for the current frame
def region = RegionRequest.createInstance(server.getPath(), downsample, 0, 0, width, height, zSlice, timepoint)

// Create an ImageServer for fibre instances (filtered by Z & T)
def instanceServer = new LabeledImageServer.Builder(imageData)
  .backgroundLabel(0, ColorTools.BLACK) // Specify background label (usually 0 or 255)
  .downsample(downsample) // Choose server resolution; this should match the resolution at which tiles are exported
  .useAnnotations()
  .useInstanceLabels()
  .useFilter(p -> 
      p.isAnnotation() && 
      p.getPathClass() == getPathClass('YOUR_CLASS') &&  // Filter by class
      p.getROI().getImagePlane().getZ() == zSlice &&  // Filter by Z-plane
      p.getROI().getImagePlane().getT() == timepoint  // Filter by timepoint
  )
  .multichannelOutput(false) // If true, each label refers to the channel of a multichannel binary image (required for multiclass probability)
  .build()

// Get image name to export annotations
def name = GeneralTools.getNameWithoutExtension(imageData.getServer().getMetadata().getName())

// Define export paths
def pathInstance = buildFilePath(instanceDir, name + "_frame${timepoint}_mask.tif") // Define instance output file paths
def pathImage = buildFilePath(imageDir, name + "_frame${timepoint}_img.tif") // Define image output file path

// Write the images (Ensures only the current frame is exported)
writeImageRegion(instanceServer, region, pathInstance) // Export annotations only for current frame
writeImageRegion(server, region, pathImage) // Export raw image only for current frame
