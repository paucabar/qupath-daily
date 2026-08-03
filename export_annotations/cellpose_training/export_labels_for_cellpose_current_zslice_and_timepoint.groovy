import qupath.lib.regions.RegionRequest

// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName = "YOUR_CLASS_NAME"  // class name, null (all), or "" (unclassified)
double downsample   = 1.0
// ─────────────────────────────────────────────────────────────────────────────

def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()

def outputDir = buildFilePath(PROJECT_BASE_DIR, 'export_cellpose')
mkdirs(outputDir)

def server = imageData.getServer()
def width  = server.getWidth()
def height = server.getHeight()

def viewer    = getCurrentViewer()
def zSlice    = viewer.getImagePlane().getZ()
def timepoint = viewer.getImagePlane().getT()

def region = RegionRequest.createInstance(server.getPath(), downsample, 0, 0, width, height, zSlice, timepoint)

def instanceBuilder = new LabeledImageServer.Builder(imageData)
    .backgroundLabel(0, ColorTools.BLACK)
    .downsample(downsample)
    .useAnnotations()
    .useInstanceLabels()
    .multichannelOutput(false)

if (targetClassName == "")
    instanceBuilder.useFilter(p ->
        p.isAnnotation() &&
        p.getPathClass() == null &&
        p.getROI().getImagePlane().getZ() == zSlice &&
        p.getROI().getImagePlane().getT() == timepoint
    )
else if (targetClassName != null)
    instanceBuilder.useFilter(p ->
        p.isAnnotation() &&
        p.getPathClass() == getPathClass(targetClassName) &&
        p.getROI().getImagePlane().getZ() == zSlice &&
        p.getROI().getImagePlane().getT() == timepoint
    )
else
    instanceBuilder.useFilter(p ->
        p.isAnnotation() &&
        p.getROI().getImagePlane().getZ() == zSlice &&
        p.getROI().getImagePlane().getT() == timepoint
    )

def instanceServer = instanceBuilder.build()

def name = GeneralTools.getNameWithoutExtension(imageData.getServer().getMetadata().getName())

def pathInstance = buildFilePath(outputDir, name + "_slice${zSlice}_frame${timepoint}_mask.tif")
def pathImage    = buildFilePath(outputDir, name + "_slice${zSlice}_frame${timepoint}_img.tif")

writeImageRegion(instanceServer, region, pathInstance)
writeImageRegion(server, region, pathImage)

println "Export complete: slice ${zSlice}, frame ${timepoint} saved to '${outputDir}'."
