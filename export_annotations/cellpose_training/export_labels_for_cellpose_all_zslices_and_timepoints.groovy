import qupath.lib.regions.RegionRequest

// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName = "YOUR_CLASS_NAME"  // class name, null (all), or "" (unclassified)
double downsample   = 1.0
// ─────────────────────────────────────────────────────────────────────────────

def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()

def outputDir = buildFilePath(PROJECT_BASE_DIR, 'export_cellpose')
mkdirs(outputDir)

def instanceDir = buildFilePath(outputDir, 'labels')
mkdirs(instanceDir)
def imageDir = buildFilePath(outputDir, 'images')
mkdirs(imageDir)

def server = imageData.getServer()
def width       = server.getWidth()
def height      = server.getHeight()
def nTimepoints = server.nTimepoints()
def nSlices     = server.nZSlices()

def name = GeneralTools.getNameWithoutExtension(imageData.getServer().getMetadata().getName())

for (timepoint = 0; timepoint < nTimepoints; timepoint++) {
    for (zSlice = 0; zSlice < nSlices; zSlice++) {
        println "Timepoint: $timepoint, Z-slice: $zSlice"

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

        def pathInstance = buildFilePath(instanceDir, name + "_slice${zSlice}_frame${timepoint}_mask.tif")
        def pathImage    = buildFilePath(imageDir,    name + "_slice${zSlice}_frame${timepoint}_img.tif")

        writeImageRegion(instanceServer, region, pathInstance)
        writeImageRegion(server, region, pathImage)
    }
}

println "Export complete."
