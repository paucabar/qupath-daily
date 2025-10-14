import qupath.lib.objects.PathDetectionObject
import qupath.lib.objects.PathAnnotationObject
import qupath.lib.roi.interfaces.ROI
import static qupath.lib.gui.scripting.QPEx.*

// Specify the class name to convert
def targetClass = "YOUR_CLASS_NAME"  // Change this to the desired class

def detectionsToConvert = getDetectionObjects().findAll {
    it.getPathClass()?.toString() == targetClass
}

removeObjects(detectionsToConvert, true)
def pathAnnotationObjects = detectionsToConvert.collect { detection ->
    ROI roi = detection.getROI()
    def annotation = PathObjects.createAnnotationObject(roi, getPathClass(targetClass))
    return annotation
}
addObjects(pathAnnotationObjects)

fireHierarchyUpdate()
print("Conversion done!")