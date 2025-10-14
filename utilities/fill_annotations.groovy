import qupath.lib.objects.PathAnnotationObject
import qupath.lib.roi.interfaces.ROI
import qupath.lib.roi.RoiTools
import static qupath.lib.gui.scripting.QPEx.*

RoiTools rt = new RoiTools()

def annotationsToFill = getAnnotationObjects()
removeObjects(annotationsToFill, true)

def pathFilledAnnotations = annotationsToFill.collect { annotation ->
    ROI roi = annotation.getROI()
    if (roi.getRoiName() != "Rectangle") {
        filled_roi = rt.fillHoles(roi)
    } else {
        filled_roi = roi
    }
    def filledAnnotation = PathObjects.createAnnotationObject(filled_roi, annotation.getPathClass())
    return filledAnnotation
}
addObjects(pathFilledAnnotations)

fireHierarchyUpdate()
print("Conversion done!")