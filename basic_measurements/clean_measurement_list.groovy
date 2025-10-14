def annotations = getAnnotationObjects()
def targetClass = getPathClass("YOUR_CLASS_NAME")
def targetAnnotations = annotations.findAll { it.getPathClass() == targetClass }

targetAnnotationstations.each { a ->
    a.getMeasurementList().clear()
}