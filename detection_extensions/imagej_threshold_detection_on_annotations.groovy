/**
 * QuPath script: Create threshold-based annotation objects overlapping with target objects.
 *
 * Description:
 * This script uses an ImageJ thresholding method to create new annotation objects based on pixel intensity.
 * It then keeps only those thresholded regions that overlap with preexisting "target" objects (annotations).
 *
 * Usage:
 * - The script will apply the specified ImageJ thresholding method on the chosen channel,
 *   and create new annotation objects with class "THRESHOLDED_CLASS" that overlap with the target class.
 * - Edit "TARGET_CLASS", "IMAGEJ_THRESHOLD_METHOD" and "channel" as needed.
 * - Channel start by 1.
 * - Assumes a 2D image.
 */

import qupath.imagej.tools.IJTools
import qupath.lib.roi.RoiTools
import qupath.lib.roi.GeometryTools
import qupath.lib.roi.interfaces.ROI
import qupath.lib.regions.ImagePlane
import qupath.lib.objects.PathObject

import static qupath.lib.gui.scripting.QPEx.*
import org.locationtech.jts.geom.Geometry
import ij.ImagePlus
import ij.process.ImageProcessor
import qupath.imagej.processing.SimpleThresholding


def getIntersectedObjects(cellObjects, pffRoi, className) {
    Geometry g1 = pffRoi.getGeometry()
    List<ROI> intersectionList = []
    ImagePlane plane = ImagePlane.getDefaultPlane()
    cellObjects.each { cell ->
        Geometry gCell = cell.getROI().getGeometry()
        Geometry intersection = g1.intersection(gCell)
        intersectionList << new GeometryTools().geometryToROI(intersection, plane)
    }
    def pathObjects = intersectionList.collect { roi ->
        return PathObjects.createAnnotationObject(roi, getPathClass(className))
    }
    addObjects(pathObjects)
    return
}

// Get the current image
def imageData = getCurrentImageData()
def server = imageData.getServer()

// Create a region request for the entire image & request the pixels from the server
// (This assumes the image is 2D)
double downsample = 1
def request = RegionRequest.createInstance(server, downsample)
ImagePlus imp = IJTools.convertToImagePlus(server, request).getImage()

// Get objects of interest
def objectsOfInterest = getAnnotationObjects().findAll { it.getPathClass() == getPathClass("TARGET_CLASS") }

// Create semantic ROIs applying a threshold method
int channel = 1 // Set the channel index (1-based)
imp.setC(channel)
ImageProcessor ip = imp.getProcessor()
ip.setAutoThreshold("IMAGEJ_THRESHOLD_METHOD", true, ImageProcessor.NO_LUT_UPDATE)
int lowerThreshold = ip.getMinThreshold()  // Gets the calculated lower threshold value
int upperThreshold = ip.getMaxThreshold()  // Gets the calculated upper threshold value

// Apply the threshold to generate an ROI
ip.setThreshold(lowerThreshold, upperThreshold, ImageProcessor.NO_LUT_UPDATE)
def multipartRoi = SimpleThresholding.thresholdToROI(ip, request) // Generates a multi-part ROI including all positive pixels

// Create the intersected objects
getIntersectedObjects(objectsOfInterest, multipartRoi, "THRESHOLDED_CLASS")

// Resolve hierarchy
resolveHierarchy()

