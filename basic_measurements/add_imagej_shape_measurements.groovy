/**
 * Adds the ImageJ shape measurements that QuPath has no equivalent for: the Feret family
 * (Feret, MinFeret, FeretAngle, plus a Feret AR) and the fitted-ellipse family (Major, Minor,
 * Angle, AR, Round).
 *
 * Values come from ImageJ itself, via qupath.imagej.tools.IJTools, so they match the columns Fiji
 * produces under Analyze > Set Measurements and can be compared directly with published numbers.
 * Measurement names keep ImageJ's spelling and take QuPath's unit suffix, e.g. "Feret µm".
 *
 * This doubles as the template for pulling any ImageJ measurement into QuPath: convert the QuPath
 * ROI with IJTools.convertToIJRoi, ask ImageJ for the value, scale pixels to calibrated units, and
 * put it on the measurement list. Intensity measurements would follow the same shape, with
 * IJTools.convertToImagePlus to get the pixels as well as the outline.
 *
 * What is deliberately NOT duplicated, because QuPath already measures it (see
 * add_shape_measurements.groovy):
 *   - Area, Perim. (QuPath: "Area µm^2", "Length µm")
 *   - Circ. (QuPath: "Circularity" - same 4*pi*area/perimeter^2, computed on the JTS geometry
 *     rather than ImageJ's traced polygon, so the two agree to within polygonisation)
 *   - Solidity
 *
 * Two of QuPath's built-ins are worth knowing about before adding more:
 *   - "Min diameter µm" IS the minimum Feret diameter (JTS MinimumDiameter.getWidth()). This script
 *     recomputes it as MinFeret from ImageJ and reports the worst disagreement, which is a free
 *     correctness check on the whole conversion route.
 *   - "Max diameter µm" is NOT the maximum Feret diameter. QuPath computes it as the diameter of
 *     the minimum bounding circle (MinimumBoundingCircle.getRadius() * 2), which is always greater
 *     than or equal to Feret. On round objects the difference is under a percent; it grows with
 *     elongation and irregularity.
 *
 * Conventions: FeretAngle and Angle are ImageJ's, 0-180 degrees anticlockwise from the positive x
 * axis with y measured upwards. Round is 4*area/(pi*Major^2).
 *
 * Two aspect ratios are recorded, because they answer different questions and ImageJ only has one:
 *   - "AR" is ImageJ's, Major/Minor of the fitted ellipse. Reproduces Fiji's AR column exactly.
 *   - "Feret AR" is Feret/MinFeret, which ImageJ does not report. It is the elongation of the
 *     object's own extremes rather than of an ellipse fitted to it.
 * They agree closely on average and differ object by object; the ellipse Angle also becomes
 * unstable on near-round objects, where FeretAngle stays meaningful.
 *
 * Why EllipseFitter rather than asking ImageJ for AR: there is nothing to ask. ImageJ has no
 * Roi.getAR() - ImageStatistics.fitEllipse() runs EllipseFitter and stores major/minor/angle, and
 * Analyzer divides them when it fills the results table. Calling EllipseFitter directly is the same
 * computation without needing an ImagePlus and a ResultsTable.
 */

import ij.process.ByteProcessor
import ij.process.EllipseFitter
import qupath.imagej.tools.IJTools

import java.awt.Rectangle
import java.util.concurrent.atomic.AtomicInteger

// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName = null     // class name, null (all), or "" (unclassified)
def objectType = "auto"        // "annotations", "detections", or "auto" (detections, else annotations)
                               // note "auto" also picks up tile/region annotations such as Training
                               // squares when an image has no detections - filter with targetClassName
def measureFeret = true        // Feret, MinFeret, FeretAngle, Feret AR
def measureEllipse = true      // Major, Minor, Angle, AR, Round
def maxEllipsePixels = 25_000_000  // skip the ellipse fit for objects with a bigger bounding box
// ─────────────────────────────────────────────────────────────────────────────

def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()
def cal = imageData.getServer().getPixelCalibration()

double pixelWidth = cal.getPixelWidthMicrons()
double pixelHeight = cal.getPixelHeightMicrons()
boolean calibrated = !Double.isNaN(pixelWidth) && pixelWidth > 0
if (calibrated && Math.abs(pixelWidth - pixelHeight) > 1e-6 * pixelWidth) {
    println "ABORTED: pixels are not square (${pixelWidth} x ${pixelHeight} µm)."
    println "These measurements are made in pixel space, so they cannot be scaled by a single factor."
    return
}
double scale = calibrated ? pixelWidth : 1.0
def unit = calibrated ? "µm" : "px"

def nameFeret = "Feret ${unit}"
def nameMinFeret = "MinFeret ${unit}"
def nameFeretAngle = "FeretAngle deg"
def nameFeretAR = "Feret AR"    // not an ImageJ column - see the note on AR above
def nameMajor = "Major ${unit}"
def nameMinor = "Minor ${unit}"
def nameAngle = "Angle deg"
def nameAR = "AR"
def nameRound = "Round"
def nameQuPathMin = "Min diameter ${unit}"

// Pick the objects to measure
def candidates
if (objectType == "annotations")
    candidates = hierarchy.getAnnotationObjects()
else if (objectType == "detections")
    candidates = hierarchy.getDetectionObjects()
else
    candidates = hierarchy.getDetectionObjects()
if (candidates.isEmpty() && objectType != "detections")
    candidates = hierarchy.getAnnotationObjects()

def targetObjects
if (targetClassName == null)
    targetObjects = candidates
else if (targetClassName == "")
    targetObjects = candidates.findAll { it.getPathClass() == null }
else
    targetObjects = candidates.findAll { it.getPathClass() == getPathClass(targetClassName) }

if (targetObjects.isEmpty()) {
    println "No objects to measure - check targetClassName and objectType."
    return
}

def skipped = new AtomicInteger(0)
def ellipseSkipped = new AtomicInteger(0)

targetObjects.parallelStream().forEach(pathObject -> {
    def roi = pathObject.getROI()
    if (roi == null || roi.isEmpty()) {
        skipped.incrementAndGet()
        return
    }
    def ijRoi = IJTools.convertToIJRoi(roi, 0, 0, 1.0)
    def measurements = pathObject.getMeasurementList()

    if (measureFeret) {
        // [0] Feret, [1] FeretAngle 0-180, [2] MinFeret, [3] FeretX, [4] FeretY - all in pixels
        double[] feret = ijRoi.getFeretValues()
        if (feret == null) {
            skipped.incrementAndGet()
            return
        }
        measurements.put(nameFeret, feret[0] * scale)
        measurements.put(nameMinFeret, feret[2] * scale)
        measurements.put(nameFeretAngle, feret[1])
        measurements.put(nameFeretAR, feret[2] > 0 ? feret[0] / feret[2] : Double.NaN)
    }

    if (measureEllipse) {
        def bounds = ijRoi.getBounds()
        if ((long) bounds.width * bounds.height > maxEllipsePixels) {
            ellipseSkipped.incrementAndGet()
        } else {
            // EllipseFitter works on an ImageProcessor's roi rectangle, restricted by its mask,
            // so give it a blank processor the size of the bounds and the ROI's own mask.
            // A null mask (a rectangular ROI) means the whole rectangle, which is correct.
            int width = bounds.width > 0 ? bounds.width : 1
            int height = bounds.height > 0 ? bounds.height : 1
            def processor = new ByteProcessor(width, height)
            processor.setRoi(new Rectangle(0, 0, width, height))
            processor.setMask(ijRoi.getMask())
            def fitter = new EllipseFitter()
            fitter.fit(processor, null)

            double major = fitter.major * scale
            double minor = fitter.minor * scale
            double area = roi.getArea() * scale * scale
            measurements.put(nameMajor, major)
            measurements.put(nameMinor, minor)
            measurements.put(nameAngle, fitter.angle)
            measurements.put(nameAR, minor > 0 ? major / minor : Double.NaN)
            measurements.put(nameRound, major > 0 ? 4.0 * area / (Math.PI * major * major) : Double.NaN)
        }
    }

    measurements.close()
})

fireHierarchyUpdate()

def measured = targetObjects.size() - skipped.get()
def added = []
if (measureFeret) added << "${nameFeret}, ${nameMinFeret}, ${nameFeretAngle}, ${nameFeretAR}"
if (measureEllipse) added << "${nameMajor}, ${nameMinor}, ${nameAngle}, ${nameAR}, ${nameRound}"
println "Added ${added.join(', ')} to ${measured} objects."
if (skipped.get() > 0)
    println "Skipped ${skipped.get()} object(s) with a missing or empty ROI."
if (ellipseSkipped.get() > 0)
    println "Skipped the ellipse fit for ${ellipseSkipped.get()} object(s) larger than ${maxEllipsePixels} pixels."

// Free correctness check: MinFeret should reproduce QuPath's own minimum diameter, wherever that
// has already been measured (run add_shape_measurements.groovy first to have something to compare).
if (measureFeret) {
    double worst = 0
    int compared = 0
    for (pathObject in targetObjects) {
        def measurements = pathObject.getMeasurementList()
        double quPathMin = measurements.get(nameQuPathMin)
        double ours = measurements.get(nameMinFeret)
        if (Double.isNaN(quPathMin) || quPathMin <= 0 || Double.isNaN(ours))
            continue
        compared++
        worst = Math.max(worst, Math.abs(ours - quPathMin) / quPathMin)
    }
    if (compared > 0) {
        def verdict = worst < 0.01 ? "agrees with" : "DISAGREES with"
        println "Cross-check on ${compared} objects: MinFeret ${verdict} QuPath's ${nameQuPathMin}, " +
                "worst difference ${String.format('%.3f', worst * 100)}%."
        if (worst >= 0.01)
            println "  Above 1% one of the two is wrong - do not use these values until it is explained."
    } else {
        println "Cross-check skipped: no ${nameQuPathMin} measurement found " +
                "(run add_shape_measurements.groovy first)."
    }
}
