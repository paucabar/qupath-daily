import org.locationtech.jts.geom.*
import org.locationtech.jts.algorithm.construct.MaximumInscribedCircle

// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName = "Neuron"      // class of annotations to process
def centerMode      = "child_point" // "child_point" | "auto"
//   child_point: center is a point annotation that is a direct child of the
//                neuron annotation (select neuron → add point child annotation)
//   auto:        center is the pole of the largest inscribed circle (JTS
//                MaximumInscribedCircle); requires QuPath 0.4+ / JTS 1.17+
def startRadius     = 1             // first radius (pixels)
def endRadius       = null          // null = auto-compute per annotation
                                    //        (max vertex distance from center)
def stepSize        = 1             // radius increment (pixels)
// ─────────────────────────────────────────────────────────────────────────────

def annotations = getAnnotationObjects()
    .findAll { it.getPathClass()?.toString() == targetClassName }

if (annotations.isEmpty()) {
    print "No annotations of class '${targetClassName}' found."
    return
}
print "Found ${annotations.size()} annotations."

def gf = new GeometryFactory()

// Count contiguous arc segments of a geometry, ignoring tangential Point touches.
// Matches SNT's convention: 1 count per foreground segment crossing the circle,
// not 1 per entry/exit pair.
def countArcs = { Geometry g ->
    if (g.isEmpty()) return 0
    int count = 0
    for (int i = 0; i < g.getNumGeometries(); i++) {
        def sub = g.getGeometryN(i)
        if (!(sub instanceof Point) && !(sub instanceof MultiPoint)) count++
    }
    return count
}

int measured = 0, skippedCount = 0

annotations.eachWithIndex { annotation, idx ->
    def annotGeom = annotation.getROI().getGeometry()

    // ── Resolve center ────────────────────────────────────────────────────────
    double cx, cy
    if (centerMode == "child_point") {
        def pts = annotation.getChildObjects().findAll { it.getROI().isPoint() }
        if (pts.isEmpty()) {
            print "ROI ${idx}: no center point child annotation — skipped"
            skippedCount++
            return
        }
        if (pts.size() > 1) {
            print "ROI ${idx}: ${pts.size()} center point children found (expected 1) — skipped"
            skippedCount++
            return
        }
        cx = pts[0].getROI().getCentroidX()
        cy = pts[0].getROI().getCentroidY()
        if (!annotGeom.contains(gf.createPoint(new Coordinate(cx, cy))))
            print "ROI ${idx}: center point lies outside annotation boundary — proceeding"
    } else {
        def mic = new MaximumInscribedCircle(annotGeom, 1.0)
        def c   = mic.getCenter().getCoordinate()
        cx = c.x; cy = c.y
    }

    // ── Effective end radius ──────────────────────────────────────────────────
    double effectiveEnd = (endRadius != null) ? endRadius :
        annotGeom.getCoordinates()
            .collect { Math.hypot(it.x - cx, it.y - cy) }
            .max()

    if (effectiveEnd < startRadius) {
        print "ROI ${idx}: effective end radius (${String.format('%.1f', effectiveEnd)}) < startRadius — skipped"
        skippedCount++
        return
    }

    int effectiveEndInt = (int) Math.ceil(effectiveEnd)
    int digits = String.valueOf(effectiveEndInt).length()

    // ── Sholl profile ─────────────────────────────────────────────────────────
    def centerPt = gf.createPoint(new Coordinate(cx, cy))
    List<Integer> profile = []
    List<Integer> radii   = []

    for (int r = startRadius; r <= effectiveEndInt; r += stepSize) {
        // quadSegs controls circle approximation quality: ~1 sample per pixel
        // of circumference so thin processes are not missed at large radii.
        int quadSegs = Math.max(16, (int)(Math.PI * r))
        def ring     = centerPt.buffer(r, quadSegs).getBoundary()
        def result   = annotGeom.intersection(ring)
        profile << countArcs(result)
        radii   << r
    }

    if (profile.every { it == 0 })
        print "ROI ${idx}: all intersection counts are zero — check center placement and annotation class"

    // ── Summary stats ─────────────────────────────────────────────────────────
    int    maxCount    = profile.max()
    int    critRadius  = radii[profile.indexOf(maxCount)]
    double meanCount   = (profile.sum() as double) / profile.size()
    double sumCount    = profile.sum() as double

    // ── Write measurements ────────────────────────────────────────────────────
    def ml = annotation.getMeasurementList()
    profile.eachWithIndex { count, i ->
        ml.put(String.format("Sholl: r=%0${digits}d", radii[i]), count as double)
    }
    ml.put("Sholl: Max Intersections",  maxCount as double)
    ml.put("Sholl: Critical Radius",    critRadius as double)
    ml.put("Sholl: Mean Intersections", meanCount)
    ml.put("Sholl: Sum Intersections",  sumCount)
    ml.close()

    print "ROI ${idx}: max=${maxCount} at r=${critRadius}  sum=${(int)sumCount}  radii=${radii.size()}"
    measured++
}

fireHierarchyUpdate()
print "Done. Measured: ${measured}  Skipped: ${skippedCount}  Total: ${annotations.size()}"
