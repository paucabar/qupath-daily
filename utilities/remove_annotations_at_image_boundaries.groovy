
/**
 * Remove detections that have ROIs that touch the border of any annotation ROI.
 *
 * Note that there are some non-obvious subtleties involved depending upon how ROIs are accessed -
 * see the 'useHierarchyRule' option for more info.
 *
 * Written for https://forum.image.sc/t/remove-detected-objects-touching-annotations-border/49053
 *
 * @author Pete Bankhead
 * @adapted by Pau Carrillo-Barberà
 */

import org.locationtech.jts.geom.util.LinearComponentExtracter
import qupath.lib.objects.PathDetectionObject
import qupath.lib.objects.PathObject
import qupath.lib.regions.ImageRegion

import java.util.stream.Collectors

import static qupath.lib.gui.scripting.QPEx.*

// Define the distance in pixels from an annotation boundary
// Zero is a valid option for 'touching'
double distancePixels = 1.0

// Toggle whether to use the 'hierarchy' rule, i.e. only consider detections with centroids inside an annotation
boolean useHierarchyRule = true

/**
 * Use this to exclude objects touching the image borders
 * Annotations of an specific class are converted into detections
 * A full image annotation is created
 * NOTE 1: make sure no other annotations have been created
 * NOTE 2: if your objects are already detections, skip the conversion step
 */

// Convert annotations to detections
def yourClass = getPathClass('YOUR_CLASS_NAME') // Get the path class
def annotationsAll = getAnnotationObjects() // Get all annotation objects
def annotationsByClass = annotationsAll.findAll { it.getPathClass() == yourClass } // Filter the annotations by class
def newDetections = annotationsByClass.collect {
    it.setPathClass(yourClass)
    return PathObjects.createDetectionObject(it.getROI(), it.getPathClass())
}

removeObjects(annotationsByClass, true)
addObjects(newDetections)

// create full image annotation
createFullImageAnnotation(true)

/**
 * From this point, it corresponds to Pete's regular script
 * to remove detections touching annotations edges
 * https://gist.github.com/petebankhead/aac937b112724ab1626b020b6cca87b4
 */

// Get parent annotations
def hierarchy = getCurrentHierarchy()
def annotations = hierarchy.getAnnotationObjects()

// Loop through detections
def toRemove = new HashSet<PathObject>()
for (def annotation in annotations) {
    def roi = annotation.getROI()
    if (roi == null)
        continue // Shouldn't actually happen...
    Collection<? extends PathObject> detections
    if (useHierarchyRule)
        // Warning! This decides based upon centroids (the 'normal' hierarchy rule)
        detections = hierarchy.getObjectsForRegion(PathDetectionObject.class, ImageRegion.createInstance(roi), null)
    else
        // This uses bounding boxes (the 'normal' hierarchy rule)
        detections = hierarchy.getObjectsForROI(PathDetectionObject.class, roi)
    // We need to get separate line strings for each polygon (since otherwise we get distances of zero when inside)
    def geometry = roi.getGeometry()
    for (def line in LinearComponentExtracter.getLines(geometry)) {
        toRemove.addAll(
                detections.parallelStream()
                        .filter(d -> line.isWithinDistance(d.getROI().getGeometry(), distancePixels))
                        .collect(Collectors.toList())
        )
    }
}
println "Removing ${toRemove.size()} detections without ${distancePixels} pixels of an annotation boundary"
hierarchy.removeObjects(toRemove, true)

/**
 * Now the full image annotation is removed
 * and the remaining detections are converted
 * back into annotations
 */
 
 // Clear full image annotation
def fullImageAnnotation = getAnnotationObjects().findAll{ it.getPathClass() == null }
removeObjects(fullImageAnnotation , true)

// convert detections to annotations
def detectionsFinal = getDetectionObjects()
def newAnnotations = detectionsFinal.collect {
    it.setPathClass(yourClass )
    return PathObjects.createAnnotationObject(it.getROI(), it.getPathClass())
}

removeObjects(detectionsFinal, true)
addObjects(newAnnotations)

println "Done!"