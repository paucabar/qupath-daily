import qupath.lib.regions.RegionRequest

def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()

// Create output path (relative to project)
def outputDir = buildFilePath(PROJECT_BASE_DIR, 'export')
mkdirs(outputDir)

// Create output subfolders
def semanticDir = buildFilePath(outputDir, 'masks')
mkdirs(semanticDir)
def instanceDir = buildFilePath(outputDir, 'labels')
mkdirs(instanceDir)
def imageDir = buildFilePath(outputDir, 'images')
mkdirs(imageDir)

// Define how much to downsample during export
double downsample = 1

// Create an ImageServer where the pixels are derived from annotations
def semanticServer = new LabeledImageServer.Builder(imageData)
  .backgroundLabel(0, ColorTools.BLACK)
  .downsample(downsample)
  .addLabel('Fibre', 1)
  .addLabel('Inner Tongue', 2)
  .addLabel('Axon', 3)
  .multichannelOutput(false)
  .build()

// Create an ImageServer for fibre instances
def instanceServer = new LabeledImageServer.Builder(imageData)
  .backgroundLabel(0, ColorTools.BLACK)
  .downsample(downsample)
  .useAnnotations()
  .useInstanceLabels()
  .useFilter(p -> p.isAnnotation() && p.getPathClass() == getPathClass('Fibre'))
  .multichannelOutput(false)
  .build()

// Create an ImageServer where raw images are downsampled
def server = imageData.getServer()
def region = RegionRequest.createInstance(server, downsample)

// Get all annotation objects with no class (these are your rois)
def roiObject = hierarchy.getAnnotationObjects().findAll { it.getPathClass() == null }

print("ROIs found: " + roiObject.size())

// Get image name to export annotations
def name = GeneralTools.getNameWithoutExtension(imageData.getServer().getMetadata().getName())

// Loop through rois to write image regions
if (roiObject.size() > 0) {
    roiObject.eachWithIndex { it, index ->
        def roi = it.getROI()
        def pathSemantic = buildFilePath(semanticDir, "${name}_roi${index}.tif")
        def pathInstance = buildFilePath(instanceDir, "${name}_roi${index}.tif")
        def pathImage = buildFilePath(imageDir, "${name}_roi${index}.tif")

        def requestROISemantic = RegionRequest.createInstance(semanticServer.getPath(), downsample, roi)
        def requestROIInstance = RegionRequest.createInstance(instanceServer.getPath(), downsample, roi)
        def requestROIImage = RegionRequest.createInstance(server.getPath(), downsample, roi)

        writeImageRegion(semanticServer, requestROISemantic, pathSemantic)
        writeImageRegion(instanceServer, requestROIInstance, pathInstance)
        writeImageRegion(server, requestROIImage, pathImage)
    }
} else {
    def pathSemantic = buildFilePath(semanticDir, "${name}.tif")
    def pathInstance = buildFilePath(instanceDir, "${name}.tif")
    def pathImage = buildFilePath(imageDir, "${name}.tif")

    writeImage(semanticServer, pathSemantic)
    writeImage(instanceServer, pathInstance)
    writeImageRegion(server, region, pathImage)
}

print "Export complete."
