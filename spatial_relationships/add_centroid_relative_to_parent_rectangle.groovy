/**
 * Adds the centroid of each detection relative to its parent's top-left corner,
 * in calibrated units. X increases to the right and Y increases downwards, as in
 * the image. Only rectangle parents are used: detections whose direct parent is
 * not a rectangle (or that have no parent) are skipped.
 */

import qupath.lib.roi.RectangleROI

// ── Configuration ────────────────────────────────────────────────────────────
def detectionClassName = null  // detection class: class name, null (all), or "" (unclassified)
def parentClassName    = null  // parent class: class name, null (all), or "" (unclassified)
// ─────────────────────────────────────────────────────────────────────────────

def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()
def cal = imageData.getServer().getPixelCalibration()
def unit = cal.getPixelWidthUnit()

def matchesClass = { obj, className ->
    if (className == null)
        return true
    if (className == "")
        return obj.getPathClass() == null
    return obj.getPathClass() == getPathClass(className)
}

def detections = hierarchy.getDetectionObjects().findAll { matchesClass(it, detectionClassName) }

def nAdded = 0
def nNoParent = 0
def nNotRectangle = 0
def nOtherClass = 0

detections.each { detection ->
    def parent = detection.getParent()
    if (parent == null || parent.isRootObject()) {
        nNoParent++
        return
    }
    if (!(parent.getROI() instanceof RectangleROI)) {
        nNotRectangle++
        return
    }
    if (!matchesClass(parent, parentClassName)) {
        nOtherClass++
        return
    }

    def parentRoi = parent.getROI()
    def roi = detection.getROI()
    def x = (roi.getCentroidX() - parentRoi.getBoundsX()) * cal.getPixelWidth()
    def y = (roi.getCentroidY() - parentRoi.getBoundsY()) * cal.getPixelHeight()

    def ml = detection.getMeasurementList()
    ml.put("Centroid X ${unit} (Parent)", x as double)
    ml.put("Centroid Y ${unit} (Parent)", y as double)
    ml.close()
    nAdded++
}

fireHierarchyUpdate()
println "Added parent-relative centroids to ${nAdded} of ${detections.size()} detections."
if (nNoParent > 0)
    println "Skipped ${nNoParent} detections without a parent (try Objects > Annotations... > Resolve hierarchy)."
if (nNotRectangle > 0)
    println "Skipped ${nNotRectangle} detections whose parent is not a rectangle."
if (nOtherClass > 0)
    println "Skipped ${nOtherClass} detections whose parent is not of class '${parentClassName}'."
