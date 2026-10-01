import qupath.lib.images.servers.TransformedServerBuilder
import qupath.lib.regions.RegionRequest

// ── Configuration ────────────────────────────────────────────────────────────
// InstanSeg version of export_labels_for_cellpose_training_regions.groovy.
// Exports image/mask pairs from "training region" annotations: draw an
// annotation of regionClassName around each fully annotated area; only the
// objects of targetClassNames inside it are instance-labelled, everything
// else is background. Differences from the Cellpose export:
//  - the image is written as OME-TIFF, so the channel names and pixel size
//    travel with it (InstanSeg is channel-invariant and rescales to its own
//    pixel size, so pack_instanseg_dataset.py reads both from the file);
//  - channels are chosen by name;
//  - objects can be annotations or detections, and several classes;
//  - a large region can be cut into tiles lying fully inside it.
// Assumes a single 2D plane (no z-stack/timepoint support).
def regionClassName  = "Training"   // class of the annotations that define each training region
def targetClassNames = []           // classes to instance-label, e.g. ["Immune Cells"]; [] = all; [""] = unclassified
def objectType       = "annotations" // "annotations" or "detections" (cells count as detections)
def channelNames     = []           // channels to keep, by name, in this order; [] = keep all channels
double downsample    = 1.0          // keep 1.0 unless the objects are very large; the pixel size is saved either way
int tileSize         = 0            // 0 = one pair per region; > 0 = tiles of this size (px, after downsampling) fully inside each region
// ─────────────────────────────────────────────────────────────────────────────

def imageData = getCurrentImageData()
def server    = imageData.getServer()
def name      = GeneralTools.stripInvalidFilenameChars(GeneralTools.getNameWithoutExtension(server.getMetadata().getName()))

def available = server.getMetadata().getChannels()*.getName()
def missing = channelNames.findAll { !(it in available) }
if (missing) {
    println "${name}: channels ${missing} not found (image has ${available}) -- skipped."
    return
}
def channelIndices = channelNames.collect { available.indexOf(it) }
def exportServer = channelIndices ? new TransformedServerBuilder(server).extractChannels(*channelIndices).build() : server

def outputDir = buildFilePath(PROJECT_BASE_DIR, 'export_instanseg_training_regions')
mkdirs(outputDir)

def targetClasses = targetClassNames.collect { it == "" ? null : getPathClass(it) }
def isTarget = { p ->
    (objectType == "annotations" ? p.isAnnotation() : p.isDetection()) &&
        p.getPathClass() != getPathClass(regionClassName) &&
        (targetClassNames.isEmpty() || p.getPathClass() in targetClasses)
}

def instanceBuilder = new LabeledImageServer.Builder(imageData)
    .backgroundLabel(0, ColorTools.BLACK)
    .downsample(downsample)
    .useInstanceLabels()
    .multichannelOutput(false)
    .useFilter(isTarget)
def instanceServer = instanceBuilder.build()

def trainingRegions = getAnnotationObjects().findAll { it.getPathClass() == getPathClass(regionClassName) }
if (trainingRegions.isEmpty()) {
    println "${name}: no '${regionClassName}' annotations found -- nothing to export."
    return
}

int nPairs = 0
trainingRegions.eachWithIndex { region, i ->
    def roi = region.getROI()
    def requests = []
    if (tileSize <= 0) {
        requests << [RegionRequest.createInstance(server.getPath(), downsample, roi), "r${i}"]
    } else {
        int step = (int) Math.round(tileSize * downsample)
        def shape = roi.getGeometry()
        for (int y = (int) roi.getBoundsY(); y + step <= roi.getBoundsY() + roi.getBoundsHeight(); y += step) {
            for (int x = (int) roi.getBoundsX(); x + step <= roi.getBoundsX() + roi.getBoundsWidth(); x += step) {
                def tile = ROIs.createRectangleROI(x, y, step, step, roi.getImagePlane())
                if (shape.covers(tile.getGeometry()))
                    requests << [RegionRequest.createInstance(server.getPath(), downsample, tile), "r${i}_x${x}_y${y}"]
            }
        }
    }
    requests.each { request, suffix ->
        // "__" separates the image name, so the packing script can split by source image
        writeImageRegion(instanceServer, request, buildFilePath(outputDir, "${name}__${suffix}_mask.tif"))
        writeImageRegion(exportServer, request, buildFilePath(outputDir, "${name}__${suffix}_img.ome.tif"))
        nPairs++
    }
    println "${name}: region ${i} -> ${requests.size()} pair(s)"
}

println "Export complete: ${nPairs} image/mask pair(s) from ${trainingRegions.size()} region(s) saved to '${outputDir}'."
