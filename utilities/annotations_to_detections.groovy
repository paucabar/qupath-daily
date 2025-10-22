import qupath.lib.objects.PathDetectionObject
import qupath.lib.objects.PathAnnotationObject
import qupath.lib.roi.interfaces.ROI
import static qupath.lib.gui.scripting.QPEx.*

// Specify the class name to convert
def targetClass = "YOUR_TARGET_CLASS"  // Change this to the desired class


def annotationsToConvert = getAnnotationObjects().findAll {
    it.getPathClass()?.toString() == targetClass
}

removeObjects(annotationsToConvert, true)
def pathDetectionObjects = annotationsToConvert.collect { annotation ->
    ROI roi = annotation.getROI()
    def detection = PathObjects.createDetectionObject(roi, getPathClass(targetClass))
    return detection
}
addObjects(pathDetectionObjects)

fireHierarchyUpdate()
print("Conversion done!")