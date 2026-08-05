import qupath.lib.roi.ROIs

// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName    = "YOUR_CLASS_NAME"  // class name, null (all), or "" (unclassified)
double squareSizePixels = 512               // square side length, in image pixels (unaffected by calibration)
// ─────────────────────────────────────────────────────────────────────────────

def pointAnnotations
if (targetClassName == null)
    pointAnnotations = getAnnotationObjects().findAll { it.getROI().isPoint() }
else if (targetClassName == "")
    pointAnnotations = getAnnotationObjects().findAll { it.getROI().isPoint() && it.getPathClass() == null }
else
    pointAnnotations = getAnnotationObjects().findAll { it.getROI().isPoint() && it.getPathClass() == getPathClass(targetClassName) }

double half = squareSizePixels / 2

def squareAnnotations = pointAnnotations.collectMany { annotation ->
    def roi = annotation.getROI()
    def plane = roi.getImagePlane()
    def pathClass = annotation.getPathClass()
    roi.getAllPoints().collect { pt ->
        def squareRoi = ROIs.createRectangleROI(pt.getX() - half, pt.getY() - half, squareSizePixels, squareSizePixels, plane)
        PathObjects.createAnnotationObject(squareRoi, pathClass)
    }
}

removeObjects(pointAnnotations, true)
addObjects(squareAnnotations)

fireHierarchyUpdate()
println "Created ${squareAnnotations.size()} square annotation(s) (${squareSizePixels}x${squareSizePixels} px) from ${pointAnnotations.size()} point annotation(s)."
