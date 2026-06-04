// ── Configuration ────────────────────────────────────────────────────────────
double downsample = 1.0

// List your annotation classes in order — index is assigned automatically (1, 2, 3...)
// Background is always 0
def classNames = ['YOUR_CLASS_1', 'YOUR_CLASS_2', 'YOUR_CLASS_3']
// ─────────────────────────────────────────────────────────────────────────────

def imageData = getCurrentImageData()
def server    = imageData.getServer()

def outputDir = buildFilePath(PROJECT_BASE_DIR, 'export_semantic')
mkdirs(outputDir)

def labelDir = buildFilePath(outputDir, 'labels')
mkdirs(labelDir)
def imageDir = buildFilePath(outputDir, 'images')
mkdirs(imageDir)

def name = GeneralTools.getNameWithoutExtension(server.getMetadata().getName())

def labelBuilder = new LabeledImageServer.Builder(imageData)
    .backgroundLabel(0, ColorTools.BLACK)
    .downsample(downsample)
    .useAnnotations()
    .multichannelOutput(false)

classNames.eachWithIndex { className, i ->
    labelBuilder.addLabel(className, i + 1)
}

def labelServer = labelBuilder.build()

def region = RegionRequest.createInstance(server, downsample)

writeImage(labelServer, buildFilePath(labelDir, name + ".tif"))
writeImageRegion(server, region, buildFilePath(imageDir, name + ".tif"))

println "Export complete: '${name}' saved to '${outputDir}'."
