// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName = "YOUR_CLASS_NAME"  // class name, null (all), or "" (unclassified)
// ─────────────────────────────────────────────────────────────────────────────

def annotations
if (targetClassName == null)
    annotations = getAnnotationObjects()
else if (targetClassName == "")
    annotations = getAnnotationObjects().findAll { it.getPathClass() == null }
else
    annotations = getAnnotationObjects().findAll { it.getPathClass() == getPathClass(targetClassName) }

removeObjects(annotations, true)

println "Removed ${annotations.size()} annotations."
