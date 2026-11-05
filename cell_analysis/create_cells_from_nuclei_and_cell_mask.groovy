/**
 * Create cell objects by growing nuclei inside a cell mask.
 *
 * Use case: nuclei were segmented separately (StarDist, Cellpose, CellProfiler...), and a
 * pixel classifier gives one object covering all cell area (cells + nuclei) but does not
 * separate the cells. Each nucleus grows until it meets a neighbouring cell or reaches the edge
 * of the mask (background). The result is one QuPath cell per nucleus, with intensity and shape
 * measurements, plus "Nucleus/Cytoplasm: <channel>: Mean ratio" for every channel
 * (QuPath only has an area ratio built in).
 *
 * How the growth works:
 *  - All nuclei grow at the same speed, pixel by pixel, and only through mask pixels. Every mask
 *    pixel goes to the nucleus it can be reached from first along a path inside the mask, so a
 *    cell never takes cytoplasm that is only close to it across background.
 *  - The nucleus is always kept whole, even where the mask misses part of it.
 *  - Mask areas with no nucleus are left unassigned.
 *
 * Nuclei and mask can be detections or annotations. The nuclei and the mask objects are replaced
 * by the cells. Cells go in the hierarchy where the nuclei were (or, if a nucleus was inside a
 * mask object, under that object's parent).
 *
 * Each connected part of the mask is processed as one full-resolution image, so a single mask
 * part spanning a whole slide needs a lot of memory (about 9 bytes per pixel of its bounding box).
 *
 * Tested in QuPath 0.7.0.
 */

import groovy.transform.CompileStatic
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import org.locationtech.jts.awt.ShapeWriter
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.index.strtree.STRtree
import qupath.lib.analysis.features.ObjectMeasurements
import qupath.lib.analysis.images.ContourTracing
import qupath.lib.analysis.images.SimpleImages
import qupath.lib.objects.PathCellObject
import qupath.lib.objects.PathObject
import qupath.lib.objects.PathObjects
import qupath.lib.regions.RegionRequest
import qupath.lib.roi.GeometryTools

// ── Configuration ────────────────────────────────────────────────────────────
def nucleusClassName    = "Nucleus"  // class of the nucleus objects
def cellMaskClassName   = "Cell"     // class of the pixel-classifier object(s) covering the cells
def maxExpansionMicrons = null       // optional cap on the growth from the nucleus edge; null = no cap
def cellClassName       = null       // class for the new cells; null = unclassified
def downsample          = 1          // downsample for intensity measurements
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Grow labels through mask pixels (Dijkstra with a bucket queue). Steps cost 5 (side) or 7
 * (diagonal), a close approximation of Euclidean distance. Ties go to the first label to arrive.
 */
@CompileStatic
static void growLabels(byte[] mask, int[] labels, int w, int h, int maxCost) {
    int n = w * h
    int[] dist = new int[n]
    Arrays.fill(dist, Integer.MAX_VALUE)
    List<ArrayDeque<Integer>> buckets = (0..<8).collect { new ArrayDeque<Integer>() }
    long pending = 0
    for (int i = 0; i < n; i++) {
        if (labels[i] > 0) { dist[i] = 0; buckets[0].add(i); pending++ }
    }
    int[] dx = [-1, 0, 1, -1, 1, -1, 0, 1] as int[]
    int[] dy = [-1, -1, -1, 0, 0, 1, 1, 1] as int[]
    int[] dc = [7, 5, 7, 5, 5, 7, 5, 7] as int[]
    int d = 0
    while (pending > 0) {
        ArrayDeque<Integer> q = buckets[d % 8]
        while (!q.isEmpty()) {
            int i = q.poll()
            pending--
            if (dist[i] != d) continue
            int x = i % w, y = i.intdiv(w)
            for (int k = 0; k < 8; k++) {
                int nx = x + dx[k], ny = y + dy[k]
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                int j = ny * w + nx
                if (mask[j] == 0) continue
                int cost = d + dc[k]
                if (cost > maxCost || cost >= dist[j]) continue
                dist[j] = cost
                labels[j] = labels[i]
                buckets[cost % 8].add(j)
                pending++
            }
        }
        d++
    }
}

// labels drawn as RGB colours: strip the alpha byte
@CompileStatic
static int[] rgbToLabels(int[] rgb) {
    int[] labels = new int[rgb.length]
    for (int i = 0; i < rgb.length; i++) labels[i] = rgb[i] & 0xFFFFFF
    return labels
}

@CompileStatic
static float[] toFloat(int[] a) {
    float[] f = new float[a.length]
    for (int i = 0; i < a.length; i++) f[i] = (float) a[i]
    return f
}

def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()
def server    = imageData.getServer()
def cal       = server.getPixelCalibration()

def nucleusClass = getPathClass(nucleusClassName)
def maskClass    = getPathClass(cellMaskClassName)
def cellClass    = cellClassName ? getPathClass(cellClassName) : null

def isTarget = { PathObject p, pc -> (p.isDetection() || p.isAnnotation()) && p.getPathClass() == pc }
def nuclei = hierarchy.getAllObjects(false).findAll { isTarget(it, nucleusClass) }
def masks  = hierarchy.getAllObjects(false).findAll { isTarget(it, maskClass) }

if (nuclei.isEmpty()) { println "No '${nucleusClassName}' objects found, nothing to do"; return }
if (masks.isEmpty())  { println "No '${cellMaskClassName}' objects found, nothing to do"; return }

// growth cap in step-cost units (5 per pixel)
int maxCost = maxExpansionMicrons == null ? Integer.MAX_VALUE
            : Math.round(5 * (maxExpansionMicrons as double) / cal.getAveragedPixelSize().doubleValue()) as int

// the nucleus ROI (for StarDist cell objects, the nucleus part)
def nucleusRoi = { PathObject p -> p instanceof PathCellObject && p.getNucleusROI() != null ? p.getNucleusROI() : p.getROI() }

def polygonsOf = { Geometry g ->
    (0..<g.getNumGeometries()).collect { g.getGeometryN(it) }.findAll { it instanceof Polygon && it.getArea() > 0 }
}

def shapeWriter = new ShapeWriter()
def newCells = []          // [cell, parent]
int nNoCytoplasm = 0

// work plane by plane (z / t)
def planes = nuclei.collect { it.getROI().getImagePlane() }.unique()
for (plane in planes) {
    def planeNuclei = nuclei.findAll { it.getROI().getImagePlane() == plane }
    def planeMasks  = masks.findAll  { it.getROI().getImagePlane() == plane }
    def nucGeoms    = planeNuclei.collect { nucleusRoi(it).getGeometry() }
    def nucTree     = new STRtree()
    nucGeoms.eachWithIndex { g, i -> nucTree.insert(g.getEnvelopeInternal(), i) }
    def grownParts  = planeNuclei.collect { [] }

    // 1. grow the nuclei inside each connected part of the mask
    def maskGeom = planeMasks.isEmpty() ? null : GeometryTools.union(planeMasks.collect { it.getROI().getGeometry() })
    def components = maskGeom == null ? [] : polygonsOf(maskGeom)
    for (comp in components) {
        def env = comp.getEnvelopeInternal()
        int x0 = Math.floor(env.getMinX()) - 1, y0 = Math.floor(env.getMinY()) - 1
        int w  = (Math.ceil(env.getMaxX()) as int) + 1 - x0, h = (Math.ceil(env.getMaxY()) as int) + 1 - y0
        def region = comp.getFactory().toGeometry(new org.locationtech.jts.geom.Envelope(x0, x0 + w, y0, y0 + h))
        def near = nucTree.query(region.getEnvelopeInternal()).findAll { nucGeoms[it].intersects(region) }
        if (near.isEmpty()) continue
        if ((long) w * h > 100_000_000L) println "Large mask part (${w} x ${h} px), this may need a lot of memory"

        // rasterise mask and nuclei (pixel centres, as for QuPath's measurements)
        def maskImg = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY)
        def g2 = maskImg.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g2.translate(-x0, -y0); g2.setColor(Color.WHITE); g2.fill(shapeWriter.toShape(comp)); g2.dispose()
        def labelImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        g2 = labelImg.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g2.translate(-x0, -y0)
        near.eachWithIndex { idx, k -> g2.setColor(new Color(k + 1)); g2.fill(shapeWriter.toShape(nucGeoms[idx])) }
        g2.dispose()

        byte[] maskPx = maskImg.getRaster().getDataBuffer().getData()
        int[] labels  = rgbToLabels(labelImg.getRaster().getDataBuffer().getData())
        growLabels(maskPx, labels, w, h, maxCost)

        def request = RegionRequest.createInstance(server.getPath(), 1.0, x0, y0, w, h, plane.getZ(), plane.getT())
        def traced  = ContourTracing.createGeometries(SimpleImages.createFloatImage(toFloat(labels), w, h), request, 1, -1)   // -1 = all labels (an explicit max returns nothing in 0.7.0)
        traced.each { label, g -> grownParts[near[(label as int) - 1]] << g }
    }

    // 2. build the cells: grown area + whole nucleus, minus any neighbouring nucleus
    planeNuclei.eachWithIndex { nucleusObject, i ->
        def nucGeom = nucGeoms[i]
        def geom = GeometryTools.union(grownParts[i] + [nucGeom])
        def others = nucTree.query(geom.getEnvelopeInternal()).findAll { it != i && nucGeoms[it].intersects(geom) }
        if (others) geom = GeometryTools.union([geom.difference(GeometryTools.union(others.collect { nucGeoms[it] })), nucGeom])
        def parts = polygonsOf(geom).findAll { it.getArea() >= 1 || it.intersects(nucGeom) }
        geom = parts.size() == 1 ? parts[0] : GeometryTools.union(parts)
        if (Math.abs(geom.getArea() - nucGeom.getArea()) < 1e-6) nNoCytoplasm++

        def cell = PathObjects.createCellObject(GeometryTools.geometryToROI(geom, plane), nucleusRoi(nucleusObject), cellClass)
        def parent = nucleusObject.getParent()
        while (parent != null && parent in masks) parent = parent.getParent()
        newCells << [cell, parent]
    }
}

// replace nuclei and mask objects with the cells
hierarchy.removeObjects(nuclei + masks, true)
newCells.each { cell, parent ->
    if (parent == null || parent.isRootObject()) hierarchy.addObject(cell, false)
    else hierarchy.addObjectBelowParent(parent, cell, false)
}
hierarchy.fireHierarchyChangedEvent(this)

// measurements
def cells        = newCells.collect { it[0] }
def measurements = ObjectMeasurements.Measurements.values() as List
def compartments = ObjectMeasurements.Compartments.values() as List
def shape        = ObjectMeasurements.ShapeFeatures.values()
def channels     = server.getMetadata().getChannels().collect { it.getName() }

cells.parallelStream().forEach { cell ->
    ObjectMeasurements.addIntensityMeasurements(server, cell, downsample, measurements, compartments)
    ObjectMeasurements.addShapeMeasurements(cell, cal, shape)
    def ml = cell.getMeasurementList()
    channels.each { ch ->
        double nuc  = ml.get("Nucleus: ${ch}: Mean".toString())
        double cyto = ml.get("Cytoplasm: ${ch}: Mean".toString())
        ml.put("Nucleus/Cytoplasm: ${ch}: Mean ratio".toString(), cyto > 0 ? nuc / cyto : Double.NaN)
    }
    ml.close()
}
hierarchy.fireObjectMeasurementsChangedEvent(this, cells)

println "Created ${cells.size()} cells from ${nuclei.size()} nuclei; removed ${masks.size()} mask object(s)"
if (nNoCytoplasm > 0) println "  ${nNoCytoplasm} cell(s) have no cytoplasm (nucleus outside the mask); their ratio is NaN"
