import org.locationtech.jts.geom.Geometry
import qupath.lib.gui.scripting.QPEx.*

// Method to compute results

// Method to compute the segmentation metrics and the split and merge events.
// The method fills and assesses 3 arrays to generate the results:
    // - IoU matrix (intersection over union) to compute the F1 score
    // - IoP matrix (intersection over prediction) to comput split events
    // - IoT matrix (intersection over target) to compute merge events
def assessSegmentation (target_objects, predicted_objects) {

    // create matrix filled with zeros
    // y = targets
    // x = predictions
    int rows = target_objects.size()
    int cols = predicted_objects.size()
    def iou_matrix = new float[rows][cols]
    def iop_matrix = new float[rows][cols]
    def iot_matrix = new float[rows][cols]
    
    // fill intersection over union matrix
    target_objects.eachWithIndex { target, index_y ->
        Geometry g1 = target.getROI().getGeometry()
        predicted_objects.eachWithIndex { prediction, index_x ->
            Geometry g2 = prediction.getROI().getGeometry()
            float intersection = g1.intersection(g2).getArea()
            if (intersection > 0) {
                def target_area = g1.getArea()
                def prediction_area = g2.getArea()
                float union = target_area + prediction_area - intersection
                float iou = intersection / union
                float iop = intersection / prediction_area
                float iot = intersection / target_area
                iou_matrix[index_y][index_x] = iou
                iop_matrix[index_y][index_x] = iop
                iot_matrix[index_y][index_x] = iot
            }
        }
    }
    results = assess_all_iou (iou_matrix)
    splits = get_splits (0.5, iop_matrix)
    merges = get_merges (0.5, iot_matrix)
    return new Result(
        resultTable: results,
        splitEvents: splits,
        mergeEvents: merges
    )
}

// Auxiliary methods

// Method to compute the segmentation metrics at a specific iou threshold
def getMetrics (iou_threshold, iou_matrix) {
    def rows = iou_matrix.length // gets the number of rows
    def cols = iou_matrix[0].length // gets the number of columns, as the length of the first row
    
    iou_matrix = iou_matrix.collect { row ->
        row.collect { element ->
            element >= iou_threshold
        }
    }
        
    // Counting the number of true values in the matrix
    int tp = iou_matrix.sum { row ->
        row.count { element ->
            element == true
        }
    }
    
    // Calculating metrics
    int fp = cols - tp
    int fn = rows - tp
    float precision = tp / (tp + fp + Math.pow(10, -9))
    float recall = tp / (tp + fn + Math.pow(10, -9))
    float f1 = 2 * ((precision * recall) / (precision + recall + Math.pow(10, -9)))
    
    return [IoU_Threshold: iou_threshold, TP: tp, FP: fp, FN: fn, Precision: precision, Recall: recall, F1: f1]
}

// Method to assess all iou threshlds in range 0.5 - 09
def assess_all_iou (iou_matrix) {
    //Create an empty list
    def collectionOfResults = []
    
    // Create list of IoU thresholds
    def thresholdList = []
    def iThreshold = 0.5
    def endThreshold = 0.9
    def step = 0.05
    while (iThreshold <= endThreshold) {
        thresholdList.add(iThreshold)
        iThreshold += step
    }
    
    // get results
    for (threshold in thresholdList) {
        results = getMetrics(threshold, iou_matrix)
        //println results
        // TODO?: fill and return QuPath table
        collectionOfResults << results
    }
    
    return collectionOfResults
}

// Method to get split events
def get_splits (iop_threshold, iop_matrix) {
    int rows = iop_matrix.length // gets the number of rows
    int cols = iop_matrix[0].length // gets the number of columns, as the length of the first row
    
    iop_matrix = iop_matrix.collect { row ->
        row.collect { element ->
            element >= iop_threshold
        }
    }
    
    int splits = 0
    iop_matrix.each { row ->
        int trueCount = row.count { it } // Count number of true values in the row
        if (trueCount > 1) {
            splits++
        }
    }
    
    return splits
}

// Method to get merge events
def get_merges (iot_threshold, iot_matrix) {
    int rows = iot_matrix.length // gets the number of rows
    int cols = iot_matrix[0].length // gets the number of columns, as the length of the first row
    
    iot_matrix = iot_matrix.collect { row ->
        row.collect { element ->
            element >= iot_threshold
        }
    }
    
    int merges = 0
    for (col in 0..<cols) {
        def trueCount = 0
        for (row in 0..<rows) {
            if (iot_matrix[row][col]) {
                trueCount++
            }
        }

        if (trueCount > 1) {
            merges++
        }
    }

    return merges
}


// Define your class for results

@groovy.transform.RecordType
class Result {
    def resultTable
    int splitEvents
    int mergeEvents
}


// Show in action
 
def target_objects = getAnnotationObjects().findAll{(it.getPathClass() == getPathClass("YOUR_TARGET_CLASS")) }
def predicted_objects = getDetectionObjects().findAll {it.getPathClass() == getPathClass("YOUR_PREDICTION_CLASS")}

println "Comparing ${target_objects.size()} targets vs ${predicted_objects.size()} predictions"

def result = assessSegmentation (target_objects, predicted_objects)

// print the results
// Print the collection of dictionaries
result.resultTable.each { dict ->
    println dict
}
println "Splits: $result.splitEvents"
println "Merges: $result.mergeEvents"