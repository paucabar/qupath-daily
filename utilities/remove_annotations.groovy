// Remove all detections classified as NSC

annotations = getCurrentImageData().getHierarchy().getAnnotationObjects()
removeObjects(annotations.findAll { it.getPathClass() == getPathClass("YOUR_CLASS_NAME") }, true)