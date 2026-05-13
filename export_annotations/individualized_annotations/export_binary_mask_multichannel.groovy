// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName = null  // class name, null (all), or "" (unclassified)
double downsample   = 1.0
// ─────────────────────────────────────────────────────────────────────────────

def imageData = getCurrentImageData()

def outputDir = buildFilePath(PROJECT_BASE_DIR, 'export_stack')
mkdirs(outputDir)
def name = GeneralTools.getNameWithoutExtension(imageData.getServer().getMetadata().getName())
def path = buildFilePath(outputDir, name + "-labels.tif")

def builder = new LabeledImageServer.Builder(imageData)
    .backgroundLabel(0, ColorTools.WHITE)
    .downsample(downsample)
    .useAnnotations()
    .useInstanceLabels()
    .multichannelOutput(true)

if (targetClassName == "")
    builder.useFilter(p -> p.isAnnotation() && p.getPathClass() == null)
else if (targetClassName != null)
    builder.useFilter(p -> p.isAnnotation() && p.getPathClass() == getPathClass(targetClassName))

def labelServer = builder.build()
writeImage(labelServer, path)

println "Export complete: label stack saved to 'export_stack'."
