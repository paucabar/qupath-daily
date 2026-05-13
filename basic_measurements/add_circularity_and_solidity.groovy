import qupath.lib.roi.RoiTools

// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName = "YOUR_CLASS_NAME"  // class name, null (all), or "" (unclassified)
// ─────────────────────────────────────────────────────────────────────────────

def parentAnnotations
if (targetClassName == null)
    parentAnnotations = getAnnotationObjects()
else if (targetClassName == "")
    parentAnnotations = getAnnotationObjects().findAll { it.getPathClass() == null }
else
    parentAnnotations = getAnnotationObjects().findAll { it.getPathClass() == getPathClass(targetClassName) }

for (parent in parentAnnotations) {
    def roi = parent.getROI()
    if (roi != null) {
        def circularity = RoiTools.getCircularity(roi)
        def solidity = roi.getSolidity()

        def ml = parent.getMeasurementList()
        ml.put("Circularity", circularity as double)
        ml.put("Solidity", solidity as double)
        ml.close()
    }
}

fireHierarchyUpdate()
println "Added Circularity and Solidity measurements to ${parentAnnotations.size()} annotations."
