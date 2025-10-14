import qupath.lib.roi.RoiTools

// Find all annotations classified as "Mitochondria"
parentAnnotations = getAnnotationObjects().findAll { it.getPathClass() == getPathClass("Mitochondria") }

// Process each annotation
for (parent in parentAnnotations) {
    def roi = parent.getROI()
    
    if (roi != null) {
        def circularity = RoiTools.getCircularity(roi) // Calculate circularity
        def solidity = roi.getSolidity() // Calculate solidity

        // Add measurements to the annotation
        parent.getMeasurementList().putMeasurement("Circularity", circularity)
        parent.getMeasurementList().putMeasurement("Solidity", solidity)
    }
}

// Refresh the display
fireHierarchyUpdate()
println("Added Circularity and Solidity measurements to 'Mitochondria' annotations.")
