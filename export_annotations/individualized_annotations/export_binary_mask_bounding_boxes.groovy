import qupath.lib.images.servers.LabeledImageServer

def imageData = getCurrentImageData()

// Define output path (relative to project)
def name = GeneralTools.getNameWithoutExtension(imageData.getServer().getMetadata().getName())
def pathOutput = buildFilePath(PROJECT_BASE_DIR, 'export_bbs')
mkdirs(pathOutput)

double downsample = 1

// Create an ImageServer where the pixels are derived from annotations
def labelServer = new LabeledImageServer.Builder(imageData)
    .backgroundLabel(0, ColorTools.WHITE) // Specify background label (usually 0 or 255)
    .downsample(downsample)    // Choose server resolution; this should match the resolution at which tiles are exported
    .addUnclassifiedLabel(255)      // Choose objects with no class name
    .multichannelOutput(false) // If true, each label refers to the channel of a multichannel binary image (required for multiclass probability)
    .build()


// Export each region
int i = 0
for (annotation in getAnnotationObjects().findAll {it.getPathClass() == null}) {
    def region = RegionRequest.createInstance(
        labelServer.getPath(), downsample, annotation.getROI())
    i++
    def outputPath = buildFilePath(pathOutput, name + '_Region_' + i + '.tif')
    writeImageRegion(labelServer, region, outputPath)
}