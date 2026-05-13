// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName       = "YOUR_TARGET_CLASS"  // class of detections to reclassify
def circularityThreshold  = 0.5                  // detections below this threshold → "Positive"
// ─────────────────────────────────────────────────────────────────────────────

def detections = getDetectionObjects().findAll { it.getPathClass()?.toString() == targetClassName }

if (detections.isEmpty()) {
    println "No detections found for class '${targetClassName}'"
    return
}

detections.each { detection ->
    def circularity = detection.getMeasurementList().get("Circularity")
    if (!Double.isNaN(circularity)) {
        def newClass = circularity < circularityThreshold ? "Positive" : "Negative"
        detection.setPathClass(getPathClass(newClass))
    }
}

fireHierarchyUpdate()
println "Reclassification complete!"
