/**
 * Remove detections whose value for a given measurement falls outside a threshold,
 * e.g. keep only detections with Circularity >= 0.5.
 */

// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName  = "YOUR_CLASS_NAME"  // class name, null (all), or "" (unclassified)
def measurementName  = "Circularity"      // measurement/feature name to threshold on
double threshold      = 0.5               // threshold value
boolean keepAbove     = true              // true = keep values >= threshold (remove below); false = keep values <= threshold (remove above)
// ─────────────────────────────────────────────────────────────────────────────

def detections
if (targetClassName == null)
    detections = getDetectionObjects()
else if (targetClassName == "")
    detections = getDetectionObjects().findAll { it.getPathClass() == null }
else
    detections = getDetectionObjects().findAll { it.getPathClass() == getPathClass(targetClassName) }

def toRemove = []
def skippedNaN = 0

detections.each { detection ->
    def value = detection.getMeasurementList().get(measurementName)
    if (Double.isNaN(value)) {
        skippedNaN++
        return
    }
    def keep = keepAbove ? value >= threshold : value <= threshold
    if (!keep)
        toRemove << detection
}

removeObjects(toRemove, true)

if (skippedNaN > 0)
    println "Skipped ${skippedNaN} detections with no '${measurementName}' measurement."
println "Removed ${toRemove.size()} of ${detections.size()} detections (kept ${detections.size() - toRemove.size() - skippedNaN})."
