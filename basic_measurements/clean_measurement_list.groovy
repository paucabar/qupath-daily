def targetClass = getPathClass("YOUR_CLASS_NAME")
def targetAnnotations = getAnnotationObjects().findAll { it.getPathClass() == targetClass }

targetAnnotations.each { a ->
    def ml = a.getMeasurementList()
    ml.clear()
    ml.close()
}
