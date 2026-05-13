import ij.ImagePlus
import ij.ImageStack
import ij.IJ
import ij.process.ByteProcessor
import ij.process.ColorProcessor
import ij.process.ImageProcessor

// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName = "YOUR_CLASS_NAME"
def minAreaPixels   = 50                // annotations smaller than this are skipped
// Indices of annotations to show as 2-slice ImageJ stacks (mask | skeleton).
// Set to an empty list [] to disable. Change to e.g. [0,1,6,11] to inspect those.
def debugIndices    = [] as Set
// ─────────────────────────────────────────────────────────────────────────────

def annotations = getAnnotationObjects().findAll { it.getPathClass()?.toString() == targetClassName }
if (annotations.isEmpty()) {
    print "No annotations of class '${targetClassName}' found."
    return
}
print "Found ${annotations.size()} annotations."

// 8-connected offsets and their Euclidean edge weights
final int[]    KDX = [-1, 0, 1, -1, 1, -1, 0, 1]
final int[]    KDY = [-1, -1, -1, 0, 0, 1, 1, 1]
final double[] KW  = [Math.sqrt(2), 1, Math.sqrt(2), 1, 1, Math.sqrt(2), 1, Math.sqrt(2)]

// Post-process a skeletonized ByteProcessor to remove residual thick regions left
// by Zhang-Suen thinning. Iteratively removes pixels that are topologically safe to
// delete: single arc of foreground neighbours (crossing number = 1), not an endpoint
// (≥ 2 neighbours), not an interior point (has at least one background 4-neighbour).
def postThin = { ByteProcessor proc ->
    final int W = proc.getWidth(), H = proc.getHeight()
    // Clockwise 8-neighbour offsets starting from N
    final int[] OX = [0, 1, 1, 1, 0, -1, -1, -1]
    final int[] OY = [-1, -1, 0, 1, 1, 1, 0, -1]
    boolean changed = true
    while (changed) {
        changed = false
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (proc.get(x, y) == 0) continue
                int[] n = new int[8]
                int n8 = 0
                for (int i = 0; i < 8; i++) {
                    int nx = x + OX[i], ny = y + OY[i]
                    if (nx >= 0 && nx < W && ny >= 0 && ny < H && proc.get(nx, ny) > 0) {
                        n[i] = 1; n8++
                    }
                }
                if (n8 < 2) continue  // endpoint or isolated — keep
                // Must have at least one background 4-neighbour (removing interior creates a hole)
                boolean hasBg4 =
                    (x == 0   || proc.get(x-1, y) == 0) ||
                    (x == W-1 || proc.get(x+1, y) == 0) ||
                    (y == 0   || proc.get(x, y-1) == 0) ||
                    (y == H-1 || proc.get(x, y+1) == 0)
                if (!hasBg4) continue
                // Count 0→1 transitions in cyclic neighbourhood = number of foreground arcs
                int transitions = 0
                for (int i = 0; i < 8; i++)
                    if (n[i] == 0 && n[(i+1) % 8] == 1) transitions++
                if (transitions == 1) { proc.set(x, y, 0); changed = true }
            }
        }
    }
}

int measured = 0, skippedCount = 0

annotations.eachWithIndex { annotation, idx ->
    def qpRoi = annotation.getROI()

    if (qpRoi.getArea() < minAreaPixels) {
        print "ROI ${idx}: skipped (area=${(int)qpRoi.getArea()} px²)"
        skippedCount++
        return
    }

    // Build a bounding-box-sized binary mask using QuPath's own containment test —
    // correct for all ROI types (polygon, ellipse, rectangle, area ROIs with holes).
    def x0 = (int) qpRoi.getBoundsX()
    def y0 = (int) qpRoi.getBoundsY()
    def w  = (int) Math.ceil(qpRoi.getBoundsWidth())  + 2
    def h  = (int) Math.ceil(qpRoi.getBoundsHeight()) + 2

    def bp = new ByteProcessor(w, h)
    for (int py = 0; py < h; py++)
        for (int px = 0; px < w; px++)
            if (qpRoi.contains(px + x0, py + y0)) bp.set(px, py, 255)

    // Preserve original mask for debug display before it is modified in-place.
    def origBp = debugIndices.contains(idx) ? bp.duplicate() : null

    // Skeletonize checks isBinary() before running; isBinary() returns false on a
    // freshly-built ByteProcessor because no threshold is set. setThreshold() flags
    // the image as binary without touching pixel values — avoiding Make Binary, which
    // auto-thresholds and can invert the image when background pixels outnumber
    // foreground pixels in the bounding box.
    def maskImp = new ImagePlus("skel_${idx}", bp)
    maskImp.getProcessor().setThreshold(128, 255, ImageProcessor.NO_LUT_UPDATE)
    IJ.run(maskImp, "Skeletonize", "")
    def skelBp = (ByteProcessor) maskImp.getProcessor()
    postThin(skelBp)

    if (skelBp.getStatistics().mean == 0) {
        maskImp.close()
        print "ROI ${idx}: empty skeleton"
        skippedCount++
        return
    }

    // ── Classify skeleton pixels ──────────────────────────────────────────────
    // Endpoint = 1 skeleton neighbor; junction = 3+ skeleton neighbors; slab = 2.
    boolean[] isNode     = new boolean[w * h]
    boolean[] isJunction = new boolean[w * h]
    int nEndpoints = 0, nJunctions = 0, nSlabs = 0

    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            if (skelBp.get(x, y) == 0) continue
            int n = 0
            for (int i = 0; i < 8; i++) {
                int nx = x + KDX[i], ny = y + KDY[i]
                if (nx >= 0 && nx < w && ny >= 0 && ny < h && skelBp.get(nx, ny) > 0) n++
            }
            if      (n == 1) { nEndpoints++; isNode[y * w + x] = true }
            else if (n == 2) { nSlabs++ }
            else if (n >= 3) { nJunctions++; isNode[y * w + x] = true; isJunction[y * w + x] = true }
        }
    }

    // Flood-fill connected junction pixels into clusters so intra-cluster
    // pixel pairs are not counted as branches during traversal.
    int[] jCluster = new int[w * h]
    java.util.Arrays.fill(jCluster, -1)
    int[] bfsQ = new int[w * h * 2]
    int nJClusters = 0
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            if (!isJunction[y * w + x] || jCluster[y * w + x] >= 0) continue
            int head = 0, tail = 0
            jCluster[y * w + x] = nJClusters
            bfsQ[tail++] = x; bfsQ[tail++] = y
            while (head < tail) {
                int qx = bfsQ[head++], qy = bfsQ[head++]
                for (int i = 0; i < 8; i++) {
                    int nx = qx + KDX[i], ny = qy + KDY[i]
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                    if (!isJunction[ny * w + nx] || jCluster[ny * w + nx] >= 0) continue
                    jCluster[ny * w + nx] = nJClusters
                    bfsQ[tail++] = nx; bfsQ[tail++] = ny
                }
            }
            nJClusters++
        }
    }

    // ── Traverse branches ─────────────────────────────────────────────────────
    // Walk from each node along slab pixels until reaching another node.
    // Slab pixels are marked used as they are visited, preventing double-counting.
    boolean[]    used     = new boolean[w * h]
    List<Double> bLengths = []

    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            if (!isNode[y * w + x]) continue
            used[y * w + x] = true

            for (int i = 0; i < 8; i++) {
                int ax = x + KDX[i], ay = y + KDY[i]
                if (ax < 0 || ax >= w || ay < 0 || ay >= h) continue
                if (skelBp.get(ax, ay) == 0 || used[ay * w + ax]) continue

                if (isNode[ay * w + ax]) {
                    // Skip edges between junction pixels in the same cluster
                    if (isJunction[y * w + x] && isJunction[ay * w + ax] && jCluster[y * w + x] == jCluster[ay * w + ax]) continue
                    bLengths << KW[i]
                    // Prevent same-cluster siblings from also counting this endpoint
                    if (isJunction[y * w + x] && !isJunction[ay * w + ax]) used[ay * w + ax] = true
                    continue
                }

                // Walk the slab path until a node is reached
                double len = KW[i]
                int cx = ax, cy = ay, px = x, py = y

                while (!isNode[cy * w + cx]) {
                    used[cy * w + cx] = true
                    boolean stepped = false
                    for (int j = 0; j < 8; j++) {
                        int tx = cx + KDX[j], ty = cy + KDY[j]
                        if (tx == px && ty == py) continue
                        if (tx < 0 || tx >= w || ty < 0 || ty >= h) continue
                        if (skelBp.get(tx, ty) == 0) continue
                        // Skip already-visited slab pixels, but always enter node pixels
                        if (used[ty * w + tx] && !isNode[ty * w + tx]) continue
                        len += KW[j]
                        px = cx; py = cy
                        cx = tx; cy = ty
                        stepped = true
                        break
                    }
                    if (!stepped) break
                }
                if (isNode[cy * w + cx]) bLengths << len
            }
        }
    }

    // Unvisited slab pixels after node traversal are isolated loops (cycles with no
    // endpoints or junctions). Walk each loop and record its total circumference.
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            if (skelBp.get(x, y) == 0 || used[y * w + x]) continue
            double len = 0.0
            int cx = x, cy = y, px = -1, py = -1
            while (skelBp.get(cx, cy) > 0 && !used[cy * w + cx]) {
                used[cy * w + cx] = true
                for (int i = 0; i < 8; i++) {
                    int tx = cx + KDX[i], ty = cy + KDY[i]
                    if (tx == px && ty == py) continue
                    if (tx < 0 || tx >= w || ty < 0 || ty >= h) continue
                    if (skelBp.get(tx, ty) == 0 || (used[ty * w + tx] && !(tx == x && ty == y))) continue
                    len += KW[i]
                    px = cx; py = cy
                    cx = tx; cy = ty
                    break
                }
            }
            if (len > 0) bLengths << len
        }
    }

    int    nBranches = bLengths.size()
    double avgLen    = nBranches > 0 ? bLengths.sum() / nBranches : 0.0
    double maxLen    = nBranches > 0 ? bLengths.max()             : 0.0

    // ── Debug display ─────────────────────────────────────────────────────────
    if (debugIndices.contains(idx)) {
        def labelCp = new ColorProcessor(w, h)
        for (int ly = 0; ly < h; ly++) {
            for (int lx = 0; lx < w; lx++) {
                if (skelBp.get(lx, ly) == 0) continue
                int ln = 0
                for (int i = 0; i < 8; i++) {
                    int nx = lx + KDX[i], ny = ly + KDY[i]
                    if (nx >= 0 && nx < w && ny >= 0 && ny < h && skelBp.get(nx, ny) > 0) ln++
                }
                if      (ln == 1) labelCp.set(lx, ly, 0xFF0000)  // endpoint: red
                else if (ln >= 3) labelCp.set(lx, ly, 0xFFFF00)  // junction: yellow
                else              labelCp.set(lx, ly, 0xFFFFFF)  // slab: white
            }
        }
        def stack = new ImageStack(w, h)
        stack.addSlice("mask",                          origBp.convertToColorProcessor())
        stack.addSlice("skeleton",                      skelBp.duplicate().convertToColorProcessor())
        stack.addSlice("labels  e=red j=yellow slab=white", labelCp)
        def title = "ROI${idx}  b=${nBranches} j=${nJClusters} e=${nEndpoints}"
        new ImagePlus(title, stack).show()
    }

    maskImp.close()

    // TODO: triple points (clusters with degree 3) and quadruple points (degree ≥ 4)
    //       require per-cluster degree tracking during branch traversal.
    def ml = annotation.getMeasurementList()
    ml.put("Skeleton: Branches",          nBranches as double)
    ml.put("Skeleton: Junctions",         nJClusters as double)
    ml.put("Skeleton: Junction Pixels",   nJunctions as double)
    ml.put("Skeleton: Endpoint Pixels",   nEndpoints as double)
    ml.put("Skeleton: Slab Pixels",       nSlabs as double)
    ml.put("Skeleton: Avg Branch Length", avgLen)
    ml.put("Skeleton: Max Branch Length", maxLen)
    ml.close()
    print "ROI ${idx}: branches=${nBranches} junctions=${nJClusters} endpoints=${nEndpoints}"
    measured++
}

fireHierarchyUpdate()
print "Done. Measured: ${measured}  Skipped: ${skippedCount}  Total: ${annotations.size()}"
