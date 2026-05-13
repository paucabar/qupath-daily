// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName = "YOUR_CLASS_NAME"  // class name, null (all), or "" (unclassified)
// ─────────────────────────────────────────────────────────────────────────────

def detections
if (targetClassName == null)
    detections = getDetectionObjects()
else if (targetClassName == "")
    detections = getDetectionObjects().findAll { it.getPathClass() == null }
else
    detections = getDetectionObjects().findAll { it.getPathClass() == getPathClass(targetClassName) }

removeObjects(detections, true)

println "Removed ${detections.size()} detections."
