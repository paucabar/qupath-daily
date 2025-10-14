import qupath.imagej.gui.ImageJMacroRunner
import ij.measure.ResultsTable
import ij.IJ

def targetClass = "YOUR_CLASS_NAME"

// Initialize Fiji Macro Runner parameters
def params = new ImageJMacroRunner(getQuPath()).getParameterList()
params.getParameters().get('downsampleFactor').setValue(1)
params.getParameters().get('sendROI').setValue(true)
params.getParameters().get('sendOverlay').setValue(false)
params.getParameters().get('getOverlay').setValue(false)
params.getParameters().get('getROI').setValue(false)
params.getParameters().get('clearObjects').setValue(false)

// Define ImageJ macro to process a single annotation and calculate skeleton metrics
def macro = """
// Close any existing Results window to avoid mixing
if (isOpen("Results")) {
    selectWindow("Results");
    run("Close");
}
print("Running Fiji macro for annotation...");

// Ensure ROI Manager is clean, add the ROI, make mask, skeletonize and analyze
run("ROI Manager...");
roiManager("Reset");
roiManager("Add");
run("Create Mask");
selectWindow("Mask");
run("Skeletonize (2D/3D)");
run("Analyze Skeleton (2D/3D)", "prune=none calculate");

// Close Mask window (clean up)
if (isOpen("Mask")) {
    selectWindow("Mask");
    run("Close");
}
"""

// Get current image & annotations
def imageData = getCurrentImageData()
def annotations = getAnnotationObjects().findAll { it.getPathClass()?.toString() == targetClass }

if (annotations.isEmpty()) {
    print "No annotations found for class '${targetClass}'!"
    return
}

print "Processing ${annotations.size()} annotations..."

int count = 0
for (annotation in annotations) {
    count++
    print "Processing annotation ${count} of ${annotations.size()}..."

    // Run the ImageJ macro for this annotation
    ImageJMacroRunner.runMacro(params, imageData, null, annotation, macro)

    // Access Fiji ResultsTable
    def rt = ResultsTable.getResultsTable()
    if (rt == null || rt.getCounter() == 0) {
        print "No results returned for annotation ${count}"
        // Make extra sure it's cleared
        if (rt != null) rt.reset()
        continue
    }

    // Iterate through all columns returned by Analyze Skeleton and store them
    def ml = annotation.getMeasurementList()
    int nCols = rt.getLastColumn() + 1 // getLastColumn returns index of last, +1 -> count; fallback handled
    if (nCols <= 0) {
        // fallback: try columnCount via getColumnCount if available
        try {
            nCols = rt.getColumnCount()
        } catch (Exception e) {
            nCols = 0
        }
    }

    // If column count not available via getLastColumn, iterate using getColumnHeading until exception
    if (nCols <= 0) {
        // safer fallback: attempt to gather headings by trying indexes until failure (rare)
        def headings = []
        try {
            int idx = 0
            while (true) {
                def h = rt.getColumnHeading(idx)
                if (h == null) break
                headings << h
                idx++
            }
            nCols = headings.size()
            for (int i = 0; i < headings.size(); i++) {
                def heading = headings[i]
                def value = null
                try {
                    value = rt.getValue(heading, 0)       // numeric
                } catch (Exception ex) {
                    try { value = rt.getStringValue(heading, 0) } catch (Exception ex2) { value = null }
                }
                if (value != null && !(value instanceof String && value == "")) {
                    // store numeric or string representation
                    try {
                        ml.putMeasurement(heading, (double) value)
                        print "Stored ${heading}: ${value}"
                    } catch (Exception ex3) {
                        // not numeric: store as string by key + "_str"
                        ml.putMeasurement(heading + " (str)", value.toString())
                        print "Stored ${heading} (string): ${value}"
                    }
                }
            }
        } catch (Exception e) {
            print "Failed to enumerate ResultsTable columns: ${e}"
        }
    } else {
        // Preferred path: use getColumnHeading over the known column indices
        for (int col = 0; col < nCols; col++) {
            String heading = null
            try {
                heading = rt.getColumnHeading(col)
            } catch (Exception e) {
                // skip if no heading
                continue
            }
            
            // Clean column names: remove leading "# " if present
            if (heading != null)
                heading_clean = heading.replaceFirst(/^#\s*/, "")

            if (heading == null || heading.trim().length() == 0) continue

            // Attempt numeric retrieval first, then string
            def value = null
            boolean stored = false
            try {
                // try numeric
                value = rt.getValue(heading, 0)      // returns double if numeric
                if (!Double.isNaN((double)value)) {
                    // store as numeric measurement
                    ml.putMeasurement(heading_clean, (double)value)
                    print "Stored ${heading_clean}: ${value}"
                    stored = true
                }
            } catch (Exception e) {
                // not numeric or lookup by name failed — try string
            }

            if (!stored) {
                try {
                    value = rt.getStringValue(heading, 0)
                    if (value != null && value != "") {
                        // store string as measurement by appending " (str)" to avoid type issues
                        try {
                            // try to parse to double
                            double d = Double.parseDouble(value.toString())
                            ml.putMeasurement(heading, d)
                            print "Parsed & stored ${heading}: ${d}"
                        } catch (Exception parseEx) {
                            ml.putMeasurement(heading + " (str)", value.toString())
                            print "Stored ${heading} (string): ${value}"
                        }
                    }
                } catch (Exception e2) {
                    // nothing more we can do for this column
                }
            }
        }
    }

    // Clear ResultsTable after each annotation to prevent data mixing
    try { rt.reset() } catch (Exception e) { print "Failed to reset ResultsTable: ${e}" }
}

// Update QuPath
fireHierarchyUpdate()
print "Finished processing all annotations."

// Close ImageJ
try { IJ.run("Quit") } catch (Exception e) { print "Could not quit ImageJ: ${e}" }
