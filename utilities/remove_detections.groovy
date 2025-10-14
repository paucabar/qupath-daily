// Remove all detections classified as NSC

detections = getCurrentImageData().getHierarchy().getDetectionObjects()
removeObjects(detections.findAll { it.getPathClass() == getPathClass("YOUR_CLASS_NAME") }, true)