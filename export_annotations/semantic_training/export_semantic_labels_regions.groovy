import qupath.lib.regions.RegionRequest

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

def labelBuilder = new LabeledImageServer.Builder(imageData)
    .backgroundLabel(0, ColorTools.BLACK)
    .downsample(downsample)
    .useAnnotations()
    .multichannelOutput(false)

classNames.eachWithIndex { className, i ->
    labelBuilder.addLabel(className, i + 1)
}

def labelServer = labelBuilder.build()

def name = GeneralTools.getNameWithoutExtension(server.getMetadata().getName())

// Unclassified annotations define the export regions
def regions = getAnnotationObjects().findAll { it.getPathClass() == null }

if (regions.isEmpty()) {
    println "No unclassified annotations found — nothing to export."
    return
}

regions.eachWithIndex { annotation, i ->
    def region = RegionRequest.createInstance(server.getPath(), downsample, annotation.getROI())
    def fname  = "${name}_roi${i}.tif"

    writeImageRegion(labelServer, region, buildFilePath(labelDir, fname))
    writeImageRegion(server, region, buildFilePath(imageDir, fname))

    println "Exported: '${fname}'"
}

println "Export complete: ${regions.size()} region(s) saved to '${outputDir}'."
