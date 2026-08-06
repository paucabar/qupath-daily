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

// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName      = "YOUR_CLASS_NAME"  // class name, null (all), or "" (unclassified)
double distancePixels    = 1.0                // minimum distance in pixels from annotation boundary (0 = touching)
boolean useHierarchyRule = true               // if true, only consider detections with centroids inside an annotation
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Use this to exclude objects touching the image borders.
 * Annotations of a specific class are converted into detections,
 * a full image annotation is created, boundary detections are removed,
 * then the remaining detections are converted back to annotations.
 * NOTE 1: make sure no other annotations have been created
 * NOTE 2: if your objects are already detections, skip the conversion step
 */

// Convert annotations to detections
def annotationsByClass
if (targetClassName == null)
    annotationsByClass = getAnnotationObjects()
else if (targetClassName == "")
    annotationsByClass = getAnnotationObjects().findAll { it.getPathClass() == null }
else
    annotationsByClass = getAnnotationObjects().findAll { it.getPathClass() == getPathClass(targetClassName) }

def newDetections = annotationsByClass.collect {
    PathObjects.createDetectionObject(it.getROI(), it.getPathClass())
}

removeObjects(annotationsByClass, true)
addObjects(newDetections)

// Create full image annotation
createFullImageAnnotation(true)

/**
 * From this point, it corresponds to Pete's regular script
 * to remove detections touching annotations edges:
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
        continue
    Collection<? extends PathObject> detections
    if (useHierarchyRule)
        // Warning! This decides based upon centroids (the 'normal' hierarchy rule)
        detections = hierarchy.getObjectsForRegion(PathDetectionObject.class, ImageRegion.createInstance(roi), null)
    else
        // This uses bounding boxes
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
println "Removing ${toRemove.size()} detections within ${distancePixels} pixels of an annotation boundary"
hierarchy.removeObjects(toRemove, true)

/**
 * Now the full image annotation is removed
 * and the remaining detections are converted back into annotations.
 */

// Clear full image annotation
def fullImageAnnotation = getAnnotationObjects().findAll { it.getPathClass() == null }
removeObjects(fullImageAnnotation, true)

// Convert detections back to annotations
def detectionsFinal = getDetectionObjects()
def newAnnotations = detectionsFinal.collect {
    PathObjects.createAnnotationObject(it.getROI(), it.getPathClass())
}

removeObjects(detectionsFinal, true)
addObjects(newAnnotations)

println "Done!"
