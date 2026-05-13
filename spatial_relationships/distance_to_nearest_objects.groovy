import qupath.lib.objects.PathAnnotationObject
import qupath.lib.measurements.MeasurementList

// ── Configuration ────────────────────────────────────────────────────────────
def sourceClassName = "YOUR_SOURCE_CLASS"  // class to measure from
def targetClassName = "YOUR_TARGET_CLASS"  // class to measure to
// ─────────────────────────────────────────────────────────────────────────────

def annotations = getAnnotationObjects()

def sourceClass = getPathClass(sourceClassName)
def targetClass = getPathClass(targetClassName)

def sourceAnnotations = annotations.findAll { it.getPathClass() == sourceClass }
def targetAnnotations = annotations.findAll { it.getPathClass() == targetClass }

if (sourceAnnotations.isEmpty() || targetAnnotations.isEmpty()) {
    println "No ${sourceClassName} or ${targetClassName} annotations found!"
    return
}

def cal = getCurrentServer().getPixelCalibration()
def pixelSize = cal.getAveragedPixelSize()
def unit = cal.getPixelWidthUnit()

sourceAnnotations.each { source ->
    def sourceGeom = source.getROI().getGeometry()
    def sourcePlane = source.getROI().getImagePlane()

    def minDistance = Double.MAX_VALUE

    targetAnnotations.each { target ->
        if (target.getROI().getImagePlane() != sourcePlane)
            return  // skip targets on different planes

        def targetGeom = target.getROI().getGeometry()
        def distance = sourceGeom.distance(targetGeom)
        if (distance < minDistance)
            minDistance = distance
    }

    def distanceCalibrated = minDistance * pixelSize
    def ml = source.getMeasurementList()
    ml.put("Distance to nearest ${targetClassName} (${unit})", distanceCalibrated as double)
    ml.close()
}

println "Done! Distance measurements added to ${sourceAnnotations.size()} ${sourceClassName} annotations."
