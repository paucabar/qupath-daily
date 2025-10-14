import qupath.lib.regions.RegionRequest
import static qupath.lib.gui.scripting.QPEx.*

def imageData = getCurrentImageData()
def server = imageData.getServer()

// Set output resolution
double downsample = 1

// Set up export directory
def outputDir = buildFilePath(PROJECT_BASE_DIR, "raw_export")
mkdirs(outputDir)

// Get base filename
def name = GeneralTools.getNameWithoutExtension(server.getMetadata().getName())
def pathImage = buildFilePath(outputDir, name + ".tif")
def pathGeoJSON = buildFilePath(outputDir, name + ".geojson")

// Export raw image at full resolution
def request = RegionRequest.createInstance(server, downsample)
writeImageRegion(server, request, pathImage)

// Export all annotations as GeoJSON
exportAllObjectsToGeoJson(pathGeoJSON, "FEATURE_COLLECTION")

print "Export complete: raw image and annotations saved to 'basic_export'."
