import ij.ImagePlus
import ij.ImageStack
import ij.IJ
import ij.process.ByteProcessor
import ij.process.ColorProcessor
import ij.process.ImageProcessor

// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName  = "YOUR_CLASS_NAME"
def minAreaPixels    = 50                // annotations smaller than this are skipped
def minBranchLength  = 3.0              // leaf branches shorter than this (pixels) are pruned
// Indices of annotations to show as 2-slice ImageJ stacks (mask | skeleton).
// Set to an empty list [] to disable. Change to e.g. [0,1,6,11] to inspect those.
def debugIndices     = [] as Set
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

    // Second pass: merge junction clusters connected by a slab-pixel bridge.
    // Any non-junction skeleton pixel adjacent to junction pixels of two different
    // clusters is a bridge; collapse both clusters into one so the slab path
    // between them is not recorded as a branch.
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            if (skelBp.get(x, y) == 0 || isJunction[y * w + x]) continue
            int clusterA = -1
            for (int i = 0; i < 8; i++) {
                int nx = x + KDX[i], ny = y + KDY[i]
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                int c = jCluster[ny * w + nx]
                if (c < 0) continue
                if (clusterA < 0) { clusterA = c; continue }
                if (c != clusterA) {
                    int cOld = c, cNew = clusterA
                    for (int k = 0; k < w * h; k++)
                        if (jCluster[k] == cOld) jCluster[k] = cNew
                }
            }
        }
    }

    // ── Traverse branches ─────────────────────────────────────────────────────
    // Walk from each node along slab pixels until reaching another node.
    // Node IDs: endpoint pixel at (x,y) → y*w+x  (< w*h)
    //           junction cluster c       → w*h+c  (≥ w*h)
    boolean[]    used        = new boolean[w * h]
    List<int[]>  brEdges     = []   // [nodeId1, nodeId2] per branch
    List<Double> bLengths    = []   // branch length (parallel to brEdges)
    List<Double> loopLengths = []   // isolated loop circumferences (no node endpoints)

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
                    int nid1 = isJunction[y * w + x]   ? w * h + jCluster[y * w + x]   : y * w + x
                    int nid2 = isJunction[ay * w + ax] ? w * h + jCluster[ay * w + ax] : ay * w + ax
                    brEdges << ([nid1, nid2] as int[])
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
                if (isNode[cy * w + cx]) {
                    int nid1 = isJunction[y * w + x]    ? w * h + jCluster[y * w + x]    : y * w + x
                    int nid2 = isJunction[cy * w + cx] ? w * h + jCluster[cy * w + cx] : cy * w + cx
                    if (nid1 != nid2) {   // skip slab paths within a merged cluster
                        brEdges << ([nid1, nid2] as int[])
                        bLengths << len
                    }
                }
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
            if (len > 0) loopLengths << len
        }
    }

    // ── Prune short leaf branches ─────────────────────────────────────────────
    // Build a degree map. Iteratively remove branches shorter than minBranchLength
    // that terminate at an original endpoint pixel (node ID < w*h). Junction clusters
    // are never removed, even if their degree falls to 1 after pruning.
    Map<Integer, Integer> degree = [:]
    brEdges.each { e ->
        degree[e[0]] = (degree[e[0]] ?: 0) + 1
        degree[e[1]] = (degree[e[1]] ?: 0) + 1
    }
    boolean[] prunedEdge = new boolean[brEdges.size()]
    if (minBranchLength > 0) {
        boolean changed = true
        while (changed) {
            changed = false
            brEdges.eachWithIndex { e, i ->
                if (prunedEdge[i] || bLengths[i] >= minBranchLength) return
                boolean leaf1 = degree[e[0]] == 1
                boolean leaf2 = degree[e[1]] == 1
                if (leaf1 || leaf2) {
                    prunedEdge[i] = true; degree[e[0]]--; degree[e[1]]--; changed = true
                }
            }
        }
    }

    // ── Collapse degree-2 junction clusters ───────────────────────────────────
    // Build mutable adjacency from non-pruned edges, then collapse junction clusters
    // whose degree dropped to 2 (one arm was pruned). A degree-2 junction is just a
    // pass-through node: merge its two branches and remove it — equivalent to
    // reclassifying those junction pixels as slabs.
    Map<Integer, List> adj = [:]
    brEdges.eachWithIndex { e, i ->
        if (prunedEdge[i]) return
        int id1 = e[0], id2 = e[1]; double ew = bLengths[i]
        if (!adj.containsKey(id1)) adj[id1] = []
        if (!adj.containsKey(id2)) adj[id2] = []
        adj[id1] << [id2, ew]
        adj[id2] << [id1, ew]
    }
    while (true) {
        def jEntry = adj.find { k, v -> k >= w * h && v.size() == 2 }
        if (!jEntry) break
        int jNode = jEntry.key as int
        int    a    = jEntry.value[0][0] as int;  double lenA = jEntry.value[0][1] as double
        int    b    = jEntry.value[1][0] as int;  double lenB = jEntry.value[1][1] as double
        adj.remove(jNode)
        adj[a] = adj[a]?.findAll { it[0] != jNode } ?: []
        adj[b] = adj[b]?.findAll { it[0] != jNode } ?: []
        if (a != b) {
            adj[a] << [b, lenA + lenB]
            adj[b] << [a, lenA + lenB]
        } else {
            loopLengths << (lenA + lenB)   // cycle through the collapsed junction
        }
    }

    // ── Post-collapse metrics ─────────────────────────────────────────────────
    // Rebuild degree from final adjacency; collect each undirected edge once (nodeId < neighborId).
    degree = [:]
    adj.each { nodeId, neighbors -> if (neighbors) degree[nodeId] = neighbors.size() }

    List<Double> keptLengths = []
    adj.each { nodeId, neighbors ->
        neighbors.each { nb ->
            if ((nodeId as int) < (nb[0] as int)) keptLengths << (nb[1] as double)
        }
    }
    keptLengths.addAll(loopLengths)

    int    nBranches = keptLengths.size()
    double totalLen  = nBranches > 0 ? (keptLengths.sum() as double) : 0.0
    double avgLen    = nBranches > 0 ? totalLen / nBranches          : 0.0
    double maxLen    = nBranches > 0 ? keptLengths.max()             : 0.0
    int    nJPruned  = degree.count { k, v -> k >= w * h && v >= 2 }
    int    nEPruned  = degree.count { k, v -> v == 1 }

    // ── Longest shortest path ─────────────────────────────────────────────────
    // Dijkstra from every degree-1 node; record the longest pairwise shortest path.
    // Graphs are small (< ~50 nodes), so O(V²) Dijkstra is fast enough.
    Set<Integer>  allNodes  = degree.findAll { k, v -> v > 0 }.keySet() as Set<Integer>
    List<Integer> leafNodes = degree.findAll { k, v -> v == 1 }.collect { k, v -> k as Integer }

    double longestShortestPath = 0.0
    leafNodes.each { startId ->
        Map<Integer, Double> dist = [:]
        allNodes.each { dist[it] = Double.MAX_VALUE }
        dist[startId] = 0.0
        Set<Integer> visited = [] as Set
        while (true) {
            Integer u = null; double minD = Double.MAX_VALUE
            dist.each { k, v -> if (!visited.contains(k) && v < minD) { u = k; minD = v } }
            if (u == null || minD == Double.MAX_VALUE) break
            visited << u
            adj[u]?.each { edge ->
                int nbr = edge[0] as int; double ew = edge[1] as double
                double nd = dist[u] + ew
                if (nd < dist[nbr]) dist[nbr] = nd
            }
        }
        leafNodes.each { endId ->
            double d = dist[endId] ?: Double.MAX_VALUE
            if (endId != startId && d < Double.MAX_VALUE)
                longestShortestPath = Math.max(longestShortestPath, d)
        }
    }

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
        stack.addSlice("mask",                              origBp.convertToColorProcessor())
        stack.addSlice("skeleton",                          skelBp.duplicate().convertToColorProcessor())
        stack.addSlice("labels  e=red j=yellow slab=white", labelCp)
        def title = "ROI${idx}  b=${nBranches} j=${nJPruned} e=${nEPruned}"
        new ImagePlus(title, stack).show()
    }

    maskImp.close()

    // TODO: triple points (clusters with degree 3) and quadruple points (degree ≥ 4)
    //       require per-cluster degree tracking during branch traversal.
    def ml = annotation.getMeasurementList()
    ml.put("Skeleton: Branches",              nBranches as double)
    ml.put("Skeleton: Junctions",             nJPruned as double)
    ml.put("Skeleton: Junction Pixels",       nJunctions as double)
    ml.put("Skeleton: Endpoint Pixels",       nEndpoints as double)
    ml.put("Skeleton: Slab Pixels",           nSlabs as double)
    ml.put("Skeleton: Total Branch Length",   totalLen)
    ml.put("Skeleton: Avg Branch Length",     avgLen)
    ml.put("Skeleton: Max Branch Length",     maxLen)
    ml.put("Skeleton: Longest Shortest Path", longestShortestPath)
    ml.close()
    print "ROI ${idx}: branches=${nBranches} junctions=${nJPruned} endpoints=${nEPruned} lsp=${String.format('%.1f', longestShortestPath)}"
    measured++
}

fireHierarchyUpdate()
print "Done. Measured: ${measured}  Skipped: ${skippedCount}  Total: ${annotations.size()}"
