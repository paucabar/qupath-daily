/**
 * InstanSeg detection with a custom (or downloaded) model.
 *
 * Requires qupath-extension-instanseg (tested with 0.1.6 in QuPath 0.7.0).
 * The model is a folder with rdf.yaml + instanseg.pt, e.g. one exported with
 * instanseg_export_model.py after fine-tuning. The model's pixel size comes
 * from rdf.yaml, and QuPath rescales the image to it.
 *
 * Runs inside the annotations of regionClassName (or the selected annotations
 * if regionClassName is null). If there are none, it runs on the whole image.
 */

import qupath.ext.instanseg.core.InstanSeg
import qupath.lib.images.servers.ColorTransforms

// ── Configuration ────────────────────────────────────────────────────────────
def modelPath = 'C:/path/to/models/YOUR_MODEL'  // folder containing rdf.yaml and instanseg.pt
// Input channels, in the order the model was trained on: channel names, or
// 0-based indices for images with generic names (e.g. [1, 3, 4]).
def inputChannels = ['CHANNEL_1', 'CHANNEL_2']
// Output channels of a nuclei+cells model: 0 = nuclei, 1 = cells.
// [] = all outputs (nuclei and cells together become cell objects).
def outputChannels = [1]
def regionClassName = null        // class of the annotations to run in; null = selected annotations
def detectionClassName = 'YOUR_CLASS_NAME'  // class given to the new objects; null = unclassified
// 'detections', or 'annotations' to curate the objects and export them as
// training labels (export_labels_for_instanseg_training_regions.groovy)
def outputObjectType = 'detections'
// Post-processing thresholds; null = the model's defaults (seed 0.7, mask 0.53).
// Lower seedThreshold (e.g. 0.5) to pick up fainter/blurred cells.
def seedThreshold = null
def maskThreshold = null
// 'cpu', or 'gpu' if CUDA PyTorch is installed (Extensions > Deep Java Library > Manage engines)
def device = 'cpu'
// ─────────────────────────────────────────────────────────────────────────────

def imageData = getCurrentImageData()
def server = imageData.getServer()

def transforms = inputChannels.collect {
    it instanceof Number ? ColorTransforms.createChannelExtractor(it as int)
                         : ColorTransforms.createChannelExtractor(it as String)
}
def channelNames = server.getMetadata().getChannels()*.getName()
def missing = inputChannels.findAll { it instanceof String && !(it in channelNames) }
if (missing) {
    println "Channels ${missing} not in this image (it has ${channelNames}); use indices instead."
    return
}

def modelArgs = [:]
if (seedThreshold != null) modelArgs.seed_threshold = seedThreshold
if (maskThreshold != null) modelArgs.mask_threshold = maskThreshold

def builder = InstanSeg.builder()
        .modelPath(modelPath)
        .device(device)
        .inputChannels(transforms)
        .outputChannels(outputChannels as int[])
        .tileDims(512)
        .interTilePadding(32)
        .nThreads(4)
        .makeMeasurements(true)
        .randomColors(false)
        .args(modelArgs)
def instanseg = (outputObjectType == 'annotations' ? builder.outputAnnotations() : builder.outputDetections()).build()

def parents = regionClassName != null
        ? getAnnotationObjects().findAll { it.getPathClass() == getPathClass(regionClassName) }
        : getSelectedObjects().findAll { it.isAnnotation() }
def fullImage = parents.isEmpty()
if (fullImage) {
    println 'No region annotations found -- running on the whole image'
    parents = [createFullImageAnnotation(true)]
}

println "Running InstanSeg on ${parents.size()} region(s), channels ${inputChannels}"
instanseg.detectObjects(imageData, parents)

def detections = parents.collectMany { it.getChildObjects().findAll { outputObjectType == 'annotations' ? it.isAnnotation() : it.isDetection() } }
if (detectionClassName != null)
    detections.each { it.setPathClass(getPathClass(detectionClassName)) }
if (fullImage)
    removeObjects(parents, true)
fireHierarchyUpdate()

println "InstanSeg detection done: ${detections.size()} ${outputObjectType}"
