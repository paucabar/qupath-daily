/**
 * Remove detections that touch (or are within a set distance of) the image border.
 *
 * Detection-native counterpart to remove_annotations_at_image_boundaries.groovy: since the
 * objects are already detections, no annotation<->detection conversion or temporary full-image
 * annotation is needed - the image bounds are used directly as the boundary reference.
 *
 * Written for https://forum.image.sc/t/remove-detected-objects-touching-annotations-border/49053
 *
 * @author Pete Bankhead
 * @adapted by Pau Carrillo-Barberà
 */

import org.locationtech.jts.geom.util.LinearComponentExtracter
import qupath.lib.objects.PathDetectionObject
import qupath.lib.objects.PathObject
import qupath.lib.regions.ImagePlane
import qupath.lib.regions.ImageRegion
import qupath.lib.roi.ROIs

import java.util.stream.Collectors

import static qupath.lib.gui.scripting.QPEx.*

// ── Configuration ────────────────────────────────────────────────────────────
double distancePixels    = 1.0   // minimum distance in pixels from the image border (0 = touching)
boolean useHierarchyRule = true  // if true, only consider detections with centroids inside the image
// ─────────────────────────────────────────────────────────────────────────────

def hierarchy = getCurrentHierarchy()
def server = getCurrentServer()
def imageBoundsROI = ROIs.createRectangleROI(0, 0, server.getWidth(), server.getHeight(), ImagePlane.getDefaultPlane())

Collection<? extends PathObject> detections
if (useHierarchyRule)
    // Warning! This decides based upon centroids (the 'normal' hierarchy rule)
    detections = hierarchy.getObjectsForRegion(PathDetectionObject.class, ImageRegion.createInstance(imageBoundsROI), null)
else
    // This uses bounding boxes
    detections = hierarchy.getObjectsForROI(PathDetectionObject.class, imageBoundsROI)

// We need separate line strings for each edge of the rectangle (since otherwise we get distances of zero when inside)
def toRemove = new HashSet<PathObject>()
def geometry = imageBoundsROI.getGeometry()
for (def line in LinearComponentExtracter.getLines(geometry)) {
    toRemove.addAll(
            detections.parallelStream()
                    .filter(d -> line.isWithinDistance(d.getROI().getGeometry(), distancePixels))
                    .collect(Collectors.toList())
    )
}

println "Removing ${toRemove.size()} detections within ${distancePixels} pixels of the image border"
hierarchy.removeObjects(toRemove, true)
println "Done!"
