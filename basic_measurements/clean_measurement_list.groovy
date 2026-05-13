// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName = "YOUR_CLASS_NAME"  // class name, null (all), or "" (unclassified)
// ─────────────────────────────────────────────────────────────────────────────

def targetAnnotations
if (targetClassName == null)
    targetAnnotations = getAnnotationObjects()
else if (targetClassName == "")
    targetAnnotations = getAnnotationObjects().findAll { it.getPathClass() == null }
else
    targetAnnotations = getAnnotationObjects().findAll { it.getPathClass() == getPathClass(targetClassName) }

targetAnnotations.each { a ->
    def ml = a.getMeasurementList()
    ml.clear()
    ml.close()
}

println 'Clean measurement list script done'
