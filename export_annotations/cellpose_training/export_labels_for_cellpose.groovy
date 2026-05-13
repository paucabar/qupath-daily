// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName = "YOUR_CLASS_NAME"  // class name, null (all), or "" (unclassified)
double downsample   = 4.0
// ─────────────────────────────────────────────────────────────────────────────

def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()

def outputDir = buildFilePath(PROJECT_BASE_DIR, 'export')
mkdirs(outputDir)

def instanceDir = buildFilePath(outputDir, 'labels')
mkdirs(instanceDir)
def imageDir = buildFilePath(outputDir, 'images')
mkdirs(imageDir)

def instanceBuilder = new LabeledImageServer.Builder(imageData)
    .backgroundLabel(0, ColorTools.BLACK)
    .downsample(downsample)
    .useAnnotations()
    .useInstanceLabels()
    .multichannelOutput(false)

if (targetClassName == "")
    instanceBuilder.useFilter(p -> p.isAnnotation() && p.getPathClass() == null)
else if (targetClassName != null)
    instanceBuilder.useFilter(p -> p.isAnnotation() && p.getPathClass() == getPathClass(targetClassName))

def instanceServer = instanceBuilder.build()

def server = imageData.getServer()
def region = RegionRequest.createInstance(server, downsample)

def name = GeneralTools.getNameWithoutExtension(imageData.getServer().getMetadata().getName())

def pathInstance = buildFilePath(instanceDir, name + "_mask.tif")
def pathImage    = buildFilePath(imageDir,    name + "_img.tif")

writeImage(instanceServer, pathInstance)
writeImageRegion(server, region, pathImage)

println "Export complete: labels and image saved to '${outputDir}'."
