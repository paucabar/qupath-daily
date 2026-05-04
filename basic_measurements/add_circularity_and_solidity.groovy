import qupath.lib.roi.RoiTools

// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName = "YOUR_CLASS_NAME"  // Change to the desired class
// ─────────────────────────────────────────────────────────────────────────────

def parentAnnotations = getAnnotationObjects().findAll { it.getPathClass() == getPathClass(targetClassName) }

for (parent in parentAnnotations) {
    def roi = parent.getROI()
    if (roi != null) {
        def circularity = RoiTools.getCircularity(roi)
        def solidity = roi.getSolidity()

        def ml = parent.getMeasurementList()
        ml.putMeasurement("Circularity", circularity)
        ml.putMeasurement("Solidity", solidity)
        ml.close()
    }
}

fireHierarchyUpdate()
println "Added Circularity and Solidity measurements to ${parentAnnotations.size()} annotations."
