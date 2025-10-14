import qupath.lib.gui.scripting.QPEx
import qupath.lib.objects.PathDetectionObject
import qupath.lib.roi.interfaces.ROI

// Define target class and thresholds
def targetClass = "YOUR_TARGET_CLASS"

// Get all detections
def detections = QPEx.getDetectionObjects()

// Store new detections
def newDetections = []

// Iterate through detections and classify
detections.each { detection ->
    if (detection.getPathClass()?.toString() == targetClass) {
        def circularity = detection.getMeasurementList().getMeasurementValue("Circularity")
        
        if (circularity != null) {
            def newClass = circularity < 0.5 ? "Positive" : "Negative"
            def pathClass = getPathClass(newClass)
            def newDetection = new PathDetectionObject(detection.getROI(), pathClass, detection.getMeasurementList())
            newDetections.add(newDetection)
        }
    }
}

// Remove old detections
QPEx.getCurrentHierarchy().removeObjects(detections, true)

// Add new detections
QPEx.getCurrentHierarchy().addObjects(newDetections)

println "Reclassification complete!"
