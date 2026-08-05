import qupath.lib.roi.ROIs
import qupath.lib.roi.RoiTools

// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName           = "YOUR_CLASS_NAME"  // class name, null (all), or "" (unclassified)
double expansionPixels        = 3.0                 // positive to dilate, negative to erode
boolean respectParentBoundary = true                // treat the parent annotation's edge (or the image bounds, if no parent) as a
                                                     // fixed boundary rather than free space: erosion won't recede away from it,
                                                     // dilation won't cross it. Set false to buffer uniformly in all directions,
                                                     // ignoring parents (previous script behaviour).
                                                     // Requires correct parent/child nesting - if annotations aren't already nested,
                                                     // run getCurrentHierarchy().resolveHierarchy() first, otherwise getParent()
                                                     // falls back silently to the image bounds instead of the intended parent.
// ─────────────────────────────────────────────────────────────────────────────

def server = getCurrentServer()

def annotationsToResize
if (targetClassName == null)
    annotationsToResize = getAnnotationObjects()
else if (targetClassName == "")
    annotationsToResize = getAnnotationObjects().findAll { it.getPathClass() == null }
else
    annotationsToResize = getAnnotationObjects().findAll { it.getPathClass() == getPathClass(targetClassName) }

// Capture each annotation's boundary (parent ROI, or full image bounds if it has no parent) before the
// annotations are removed from the hierarchy below.
def boundaryByAnnotation = annotationsToResize.collectEntries { annotation ->
    def parentRoi = annotation.getParent()?.getROI()
    def boundaryRoi = parentRoi ?: ROIs.createRectangleROI(0, 0, server.getWidth(), server.getHeight(), annotation.getROI().getImagePlane())
    [(annotation): boundaryRoi]
}

removeObjects(annotationsToResize, true)

def emptied = 0
def resizedAnnotations = annotationsToResize.findResults { annotation ->
    def roi = annotation.getROI()

    def resizedRoi
    if (!respectParentBoundary || expansionPixels == 0) {
        resizedRoi = RoiTools.buffer(roi, expansionPixels)
    } else if (expansionPixels < 0) {
        // Erosion: glue a synthetic "phantom" strip beyond the boundary, adjacent to this annotation,
        // so the uniform erosion consumes that strip first instead of receding the true shared edge.
        def boundaryRoi = boundaryByAnnotation[annotation]
        double d = -expansionPixels
        def outerRing = RoiTools.difference(RoiTools.buffer(boundaryRoi, d), boundaryRoi)
        def paddingNearRoi = RoiTools.intersection(outerRing, RoiTools.buffer(roi, d))
        def paddedRoi = RoiTools.union(roi, paddingNearRoi)
        resizedRoi = RoiTools.buffer(paddedRoi, expansionPixels)
    } else {
        // Dilation: grow normally, then clip back to the boundary - growth simply can't cross it.
        def boundaryRoi = boundaryByAnnotation[annotation]
        resizedRoi = RoiTools.intersection(RoiTools.buffer(roi, expansionPixels), boundaryRoi)
    }

    if (resizedRoi == null || resizedRoi.isEmpty()) {
        emptied++
        return null
    }
    PathObjects.createAnnotationObject(resizedRoi, annotation.getPathClass())
}
addObjects(resizedAnnotations)

// Re-nest everything by containment in one pass, rather than manually re-attaching each result to its
// old parent - addObjects() alone only adds flat, and re-parenting object-by-object turned out unreliable
// (only some children of a shared parent were ending up nested; likely a stale-cache issue from calling
// addObjectBelowParent() repeatedly with fireUpdate=false).
getCurrentHierarchy().resolveHierarchy()

fireHierarchyUpdate()
println "Resized ${resizedAnnotations.size()} annotation(s) by ${expansionPixels} px" +
        (emptied > 0 ? " (${emptied} eroded to nothing and dropped)." : ".")
