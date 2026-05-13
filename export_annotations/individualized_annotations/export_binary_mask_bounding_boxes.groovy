import qupath.lib.images.servers.LabeledImageServer

// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName = ""    // class name, null (all), or "" (unclassified)
double downsample   = 1.0
// ─────────────────────────────────────────────────────────────────────────────

def imageData = getCurrentImageData()
def name = GeneralTools.getNameWithoutExtension(imageData.getServer().getMetadata().getName())

def pathOutput = buildFilePath(PROJECT_BASE_DIR, 'export_bbs')
mkdirs(pathOutput)

def selectedAnnotations
if (targetClassName == null)
    selectedAnnotations = getAnnotationObjects()
else if (targetClassName == "")
    selectedAnnotations = getAnnotationObjects().findAll { it.getPathClass() == null }
else
    selectedAnnotations = getAnnotationObjects().findAll { it.getPathClass() == getPathClass(targetClassName) }

def builder = new LabeledImageServer.Builder(imageData)
    .backgroundLabel(0, ColorTools.WHITE)
    .downsample(downsample)
    .multichannelOutput(false)

if (targetClassName == null) {
    selectedAnnotations.collect { it.getPathClass() }.unique().each { pc ->
        if (pc == null) builder.addUnclassifiedLabel(255)
        else builder.addLabel(pc, 255)
    }
} else if (targetClassName == "") {
    builder.addUnclassifiedLabel(255)
} else {
    builder.addLabel(getPathClass(targetClassName), 255)
}

def labelServer = builder.build()

selectedAnnotations.eachWithIndex { annotation, i ->
    def region = RegionRequest.createInstance(labelServer.getPath(), downsample, annotation.getROI())
    def outputPath = buildFilePath(pathOutput, name + '_Region_' + (i + 1) + '.tif')
    writeImageRegion(labelServer, region, outputPath)
}

println "Exported ${selectedAnnotations.size()} annotation masks to 'export_bbs'."
