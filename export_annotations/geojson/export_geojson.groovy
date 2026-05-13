import qupath.lib.regions.RegionRequest
import static qupath.lib.gui.scripting.QPEx.*

// ── Configuration ────────────────────────────────────────────────────────────
double downsample = 1.0
// ─────────────────────────────────────────────────────────────────────────────

def imageData = getCurrentImageData()
def server = imageData.getServer()

def outputDir = buildFilePath(PROJECT_BASE_DIR, "raw_export")
mkdirs(outputDir)

def name = GeneralTools.getNameWithoutExtension(server.getMetadata().getName())
def pathImage   = buildFilePath(outputDir, name + ".tif")
def pathGeoJSON = buildFilePath(outputDir, name + ".geojson")

def request = RegionRequest.createInstance(server, downsample)
writeImageRegion(server, request, pathImage)

exportAllObjectsToGeoJson(pathGeoJSON, "FEATURE_COLLECTION")

println "Export complete: raw image and annotations saved to 'raw_export'."
