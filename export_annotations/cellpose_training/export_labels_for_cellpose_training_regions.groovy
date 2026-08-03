import qupath.lib.images.servers.TransformedServerBuilder
import qupath.lib.regions.RegionRequest

// ── Configuration ────────────────────────────────────────────────────────────
// Exports one image/mask pair per "training region" annotation -- draw an
// annotation of regionClassName around each area you want to include in the
// training set; only the objects inside that area are exported as instance
// labels, everything else in the image is skipped. Useful for carving out
// fully-annotated training patches out of an otherwise partially annotated
// image. Assumes a single 2D plane (no z-stack/timepoint support).
def regionClassName = "Training"  // class of the annotations that define each training region
def targetClassName = ""          // class name, null (all), or "" (unclassified) -- objects to instance-label within each region
double downsample   = 1.0
def channelsToKeep  = []          // 0-based channel indices to keep in the exported raw image; [] = keep all channels
// ─────────────────────────────────────────────────────────────────────────────

def imageData = getCurrentImageData()
def server    = imageData.getServer()
def exportServer = channelsToKeep ? new TransformedServerBuilder(server).extractChannels(*channelsToKeep).build() : server

def outputDir = buildFilePath(PROJECT_BASE_DIR, 'export_cellpose_training_regions')
mkdirs(outputDir)

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

def name = GeneralTools.getNameWithoutExtension(server.getMetadata().getName())

def trainingRegions = getAnnotationObjects().findAll { it.getPathClass() == getPathClass(regionClassName) }

if (trainingRegions.isEmpty()) {
    println "No '${regionClassName}' annotations found -- nothing to export."
    return
}

trainingRegions.eachWithIndex { region, i ->
    def request = RegionRequest.createInstance(server.getPath(), downsample, region.getROI())

    def pathInstance = buildFilePath(outputDir, "${name}_roi${i}_mask.tif")
    def pathImage    = buildFilePath(outputDir, "${name}_roi${i}_img.tif")

    writeImageRegion(instanceServer, request, pathInstance)
    writeImageRegion(exportServer, request, pathImage)

    println "Exported: roi${i}"
}

println "Export complete: ${trainingRegions.size()} training region(s) saved to '${outputDir}'."
