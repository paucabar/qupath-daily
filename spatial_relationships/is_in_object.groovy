import qupath.lib.objects.PathAnnotationObject
import qupath.lib.measurements.MeasurementList

// ── Configuration ────────────────────────────────────────────────────────────
def sourceClassName    = "YOUR_SOURCE_CLASS"     // class to test for containment
def containerClassName = "YOUR_CONTAINER_CLASS"  // class acting as the container
// ─────────────────────────────────────────────────────────────────────────────

def annotations = getAnnotationObjects()

def sourceClass    = getPathClass(sourceClassName)
def containerClass = getPathClass(containerClassName)

def sourceAnnotations    = annotations.findAll { it.getPathClass() == sourceClass }
def containerAnnotations = annotations.findAll { it.getPathClass() == containerClass }

if (sourceAnnotations.isEmpty() || containerAnnotations.isEmpty()) {
    println "No ${sourceClassName} or ${containerClassName} annotations found!"
    return
}

def cal = getCurrentServer().getPixelCalibration()

sourceAnnotations.each { source ->
    def sourceGeom  = source.getROI().getGeometry()
    def sourcePlane = source.getROI().getImagePlane()
    def isInContainer = 0

    containerAnnotations.each { container ->
        if (container.getROI().getImagePlane() != sourcePlane)
            return  // skip containers on different planes

        def containerGeom = container.getROI().getGeometry()
        if (sourceGeom.distance(containerGeom) <= 0)
            isInContainer = 1
    }

    source.getMeasurementList().putMeasurement("In ${containerClassName} (bool)", isInContainer)
}

println "Done! Containment parameter added to ${sourceAnnotations.size()} ${sourceClassName} annotations."
