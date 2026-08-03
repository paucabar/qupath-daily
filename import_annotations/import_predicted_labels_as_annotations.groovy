import ij.IJ
import ij.ImagePlus
import ij.measure.Measurements
import ij.measure.ResultsTable
import ij.plugin.filter.ParticleAnalyzer
import ij.plugin.frame.RoiManager
import ij.process.ImageProcessor
import qupath.imagej.tools.IJTools
import qupath.lib.objects.PathObjects
import qupath.lib.objects.classes.PathClass
import qupath.lib.regions.ImagePlane

// ── Configuration ────────────────────────────────────────────────────────────
// Imports an external prediction/label image as annotations, as a starting
// point for manual curation, into whichever image is currently open. Safe to
// run via "Run for project" across the whole project in one go: images with
// no matching prediction file are skipped, and images that already have any
// annotation of a class listed in skipIfClassesExist are skipped too (never
// touches existing curated ground truth).

def predDir = "/path/to/predictions"

// Candidate filenames for a given class's prediction file, tried in order --
// the first one that exists is used (e.g. to prefer a filtered/post-processed
// variant when present, falling back to the raw prediction otherwise).
// {name}   = current image's name, without extension
// {suffix} = the class's key in classSpecs below
def filenameVariants = [
    "{name}_{suffix}_filtered.tif",
    "{name}_{suffix}.tif",
]

// suffix (matches {suffix} above) -> [className, lowThreshold, highThreshold].
// For border-aware predictions (0 = background, 1 = interior, 2 = border),
// use [1, 1] to import interior only -- this keeps touching instances split
// rather than re-fusing them via the border label, at the cost of a ~1px
// erosion per object. Plain binary predictions can use [1, 1] too.
def classSpecs = [
    "YOUR_SUFFIX": ["YOUR_CLASS_NAME", 1, 1],
]

double minDiameterPixels = 2   // drops single/few-pixel noise; tune per prediction quality

// Classes that, if already present as annotations on an image, mean it's
// already curated -- skip it rather than overwrite real ground truth.
def skipIfClassesExist = ["YOUR_CLASS_NAME"]
// ─────────────────────────────────────────────────────────────────────────────

def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()
def server    = imageData.getServer()
def imageName = GeneralTools.getNameWithoutExtension(server.getMetadata().getName())

def alreadyCurated = hierarchy.getAnnotationObjects().any {
    skipIfClassesExist.contains(it.getPathClass()?.toString())
}
if (alreadyCurated) {
    println "SKIP '${imageName}': already has annotation(s) of a class in skipIfClassesExist -- not touching existing curation"
    return
}

def allAnnotations = []

classSpecs.each { suffix, classSpec ->
    def (className, lowThresh, highThresh) = classSpec

    def predictionPath = filenameVariants
        .collect { it.replace('{name}', imageName).replace('{suffix}', suffix) }
        .collect { buildFilePath(predDir, it) }
        .find { new File(it).exists() }

    if (predictionPath == null) {
        println "SKIP ${className}: no prediction file found for '${imageName}' (suffix '${suffix}')"
        return
    }
    println "${className}: using ${new File(predictionPath).name}"

    def prediction = IJ.openImage(predictionPath)
    if (prediction == null) {
        println "SKIP ${className}: could not open ${predictionPath}"
        return
    }

    if (prediction.getNSlices() != server.nZSlices()) {
        println "SKIP ${className}: Z mismatch (prediction ${prediction.getNSlices()} vs image ${server.nZSlices()}) -- ${predictionPath}"
        return
    }
    // Computed, not assumed -- a prediction saved at a different XY resolution
    // than the raw image (e.g. a downsampled model output) is handled
    // correctly either way, rather than silently misplacing every ROI.
    double downsample = server.getWidth() / (double) prediction.getWidth()
    if (Math.abs(server.getWidth() / downsample - prediction.getWidth()) > 0.5 ||
        Math.abs(server.getHeight() / downsample - prediction.getHeight()) > 0.5) {
        println "SKIP ${className}: XY size ${prediction.getWidth()}x${prediction.getHeight()} doesn't evenly " +
                "match image ${server.getWidth()}x${server.getHeight()} at any consistent downsample -- ${predictionPath}"
        return
    }
    println "${className}: downsample=${downsample} (prediction ${prediction.getWidth()}x${prediction.getHeight()}, " +
            "image ${server.getWidth()}x${server.getHeight()})"

    def calibration = prediction.getCalibration()
    def pathClass = PathClass.getInstance(className)
    def nSlices = prediction.getNSlices()

    for (z in 0..<nSlices) {
        prediction.setSlice(z + 1)   // ImageJ slices are 1-indexed
        def plane = ImagePlane.getPlane(z, 0)

        def ip = prediction.getProcessor()
        ip.setThreshold(lowThresh, highThresh, ImageProcessor.NO_LUT_UPDATE)

        double minSize = Math.PI * Math.pow(minDiameterPixels / 2.0, 2)
        def binaryMask = new ImagePlus("mask_z${z}_${className}", ip.createMask())

        def rt = new ResultsTable()
        int options = ParticleAnalyzer.SHOW_MASKS + ParticleAnalyzer.ADD_TO_MANAGER + ParticleAnalyzer.COMPOSITE_ROIS
        def pa = new ParticleAnalyzer(options, Measurements.AREA, rt, minSize, Double.POSITIVE_INFINITY, 0, 1)
        def maskIp = binaryMask.getProcessor()
        maskIp.setBinaryThreshold()
        pa.setHideOutputImage(true)
        pa.analyze(binaryMask, maskIp)

        def rm = RoiManager.getInstance()
        if (rm == null || rm.getCount() == 0) {
            continue
        }
        rm.setVisible(false)
        def roiList = rm.getRoisAsArray()
        rm.close()

        roiList.each { roiIJ ->
            def roi = IJTools.convertToROI(roiIJ, calibration, downsample, plane)
            allAnnotations << PathObjects.createAnnotationObject(roi, pathClass)
        }
        println "  z=${z}: ${roiList.length} object(s)"
    }
    prediction.close()
}

hierarchy.addObjects(allAnnotations)
println "Added ${allAnnotations.size()} annotation(s) to '${imageName}'."
