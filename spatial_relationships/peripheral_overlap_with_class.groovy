/**
 * For each target detection, measures how much of its periphery is covered by
 * objects of an overlap class (e.g. objects created from a thresholder).
 * The periphery is a band along the detection boundary: `innerWidth` inwards
 * and, optionally, `outerWidth` outwards. The detection's core is excluded.
 * Detections too small to have a core use the whole detection (plus outer band).
 */

import org.locationtech.jts.operation.overlayng.OverlayNG
import org.locationtech.jts.operation.overlayng.OverlayNGRobust
import qupath.lib.roi.GeometryTools

// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName  = ""       // detections to measure: class name, null (all), or "" (unclassified)
def overlapClassName = "Other"  // class of the objects (annotations or detections) to measure overlap with
def innerWidth       = 0.5      // band width inside the boundary, in calibrated units (e.g. µm)
def outerWidth       = 0.0      // band width outside the boundary, in calibrated units (0 = none)
// ─────────────────────────────────────────────────────────────────────────────

def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()
def cal = imageData.getServer().getPixelCalibration()
def unit = cal.getPixelWidthUnit()
def pixelSize = cal.getAveragedPixelSize()
def pixelArea = cal.getPixelWidth() * cal.getPixelHeight()
def innerPx = innerWidth / pixelSize
def outerPx = outerWidth / pixelSize

def overlapClass = getPathClass(overlapClassName)
def overlapObjects = hierarchy.getAllObjects(false).findAll { it.hasROI() && it.getPathClass() == overlapClass }
if (overlapObjects.isEmpty()) {
    println "No ${overlapClassName} objects found!"
    return
}

def targets
if (targetClassName == null)
    targets = hierarchy.getDetectionObjects().findAll { it.getPathClass() != overlapClass }
else if (targetClassName == "")
    targets = hierarchy.getDetectionObjects().findAll { it.getPathClass() == null }
else
    targets = hierarchy.getDetectionObjects().findAll { it.getPathClass() == getPathClass(targetClassName) }

// Merge the overlap objects into one geometry per image plane
def overlapByPlane = overlapObjects
        .groupBy { it.getROI().getImagePlane() }
        .collectEntries { plane, objs -> [plane, GeometryTools.union(objs.collect { it.getROI().getGeometry() })] }

targets.each { target ->
    def roi = target.getROI()
    def geom = roi.getGeometry()

    def outer = outerPx > 0 ? geom.buffer(outerPx) : geom
    def core = geom.buffer(-innerPx)
    def band = core.isEmpty() ? outer : outer.difference(core)

    def overlapArea = 0.0
    def overlapGeom = overlapByPlane[roi.getImagePlane()]
    if (overlapGeom != null && overlapGeom.getEnvelopeInternal().intersects(band.getEnvelopeInternal()))
        overlapArea = OverlayNGRobust.overlay(band, overlapGeom, OverlayNG.INTERSECTION).getArea()

    def bandArea = band.getArea()
    def ml = target.getMeasurementList()
    ml.put("Periphery area ${unit}^2", bandArea * pixelArea as double)
    ml.put("Periphery ${overlapClassName} area ${unit}^2", overlapArea * pixelArea as double)
    ml.put("Periphery ${overlapClassName} %", (bandArea > 0 ? 100 * overlapArea / bandArea : Double.NaN) as double)
    ml.close()
}

fireHierarchyUpdate()
println "Done! Periphery overlap with ${overlapClassName} added to ${targets.size()} detections."
