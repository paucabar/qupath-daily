import qupath.lib.regions.RegionRequest

// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName = "YOUR_CLASS_NAME"  // class name, or "" (unclassified) -- no "null/all" option here,
                                          // since the border label below only makes sense for one class at a time
double downsample   = 1.0

// Optional border/boundary label: separates touching/adjacent instances via
// connected-components after removing border pixels downstream. Leave false
// for plain binary semantic export.
boolean includeBorder    = false
int     borderThickness  = 2   // pixels at this export's own (downsampled) resolution
int     borderLabelValue = 2   // interior = 1, border = borderLabelValue
// ─────────────────────────────────────────────────────────────────────────────

def imageData = getCurrentImageData()
def server    = imageData.getServer()

def outputDir = buildFilePath(PROJECT_BASE_DIR, 'export_semantic_zstack')
mkdirs(outputDir)

def labelDir = buildFilePath(outputDir, 'labels')
mkdirs(labelDir)
def imageDir = buildFilePath(outputDir, 'images')
mkdirs(imageDir)

def width       = server.getWidth()
def height      = server.getHeight()
def nTimepoints = server.nTimepoints()
def nSlices     = server.nZSlices()

def name = GeneralTools.getNameWithoutExtension(server.getMetadata().getName())

for (timepoint = 0; timepoint < nTimepoints; timepoint++) {
    for (zSlice = 0; zSlice < nSlices; zSlice++) {
        println "Timepoint: $timepoint, Z-slice: $zSlice"

        def region = RegionRequest.createInstance(server.getPath(), downsample, 0, 0, width, height, zSlice, timepoint)

        def labelBuilder = new LabeledImageServer.Builder(imageData)
            .backgroundLabel(0, ColorTools.BLACK)
            .downsample(downsample)
            .useAnnotations()
            .multichannelOutput(false)

        if (targetClassName == "")
            labelBuilder.addLabel((PathClass) null, 1)
        else
            labelBuilder.addLabel(targetClassName, 1)

        if (includeBorder) {
            labelBuilder
                .lineThickness(borderThickness)
                .setBoundaryLabel(targetClassName + ' border', borderLabelValue)
        }

        def pathClassFilter = targetClassName == "" ? null : getPathClass(targetClassName)
        labelBuilder.useFilter(p ->
            p.isAnnotation() &&
            p.getPathClass() == pathClassFilter &&
            p.getROI().getImagePlane().getZ() == zSlice &&
            p.getROI().getImagePlane().getT() == timepoint
        )

        def labelServer = labelBuilder.build()

        def pathLabel = buildFilePath(labelDir, "${name}_slice${zSlice}_frame${timepoint}.tif")
        def pathImage = buildFilePath(imageDir, "${name}_slice${zSlice}_frame${timepoint}.tif")

        // Paired raw-image export at the SAME downsample, so image/label pairs
        // are pixel-aligned without needing to replicate QuPath's resampling downstream.
        writeImageRegion(labelServer, region, pathLabel)
        writeImageRegion(server, region, pathImage)
    }
}

println "Export complete: ${nTimepoints * nSlices} slice(s) saved to '${outputDir}'."
