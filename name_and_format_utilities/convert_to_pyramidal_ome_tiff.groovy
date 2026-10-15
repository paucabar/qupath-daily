import qupath.lib.images.writers.ome.OMEPyramidWriter

/**
 * Convert the current image to a pyramidal, tiled, lossless OME-TIFF, optionally setting channel names first.
 * Same settings as File > Export images > OME TIFF (ZLIB, downsample 4, tile 256, parallel), so non-pyramidal
 * exports (e.g. Leica LAS X *_Merged.ome.tif) open fast in QuPath, Fiji or napari.
 *
 * Existing outputs are skipped, so the script is safe to re-run.
 *
 * Single image:      Run in the Script editor
 * Batch in the GUI:  Script editor > "..." menu > Run for project
 * Batch headless:    "QuPath-0.7.0 (console).exe" script -p path/to/project.qpproj convert_to_pyramidal_ome_tiff.groovy
 * Single file:       "QuPath-0.7.0 (console).exe" script -i path/to/image.ome.tif convert_to_pyramidal_ome_tiff.groovy
 */

// ── Configuration ────────────────────────────────────────────────────────────
def channelNames   = []       // [] = keep the existing names; else one name per channel, e.g. ['DAPI', 'CD3', 'CD20']
def outputNameFrom = "file"   // "file" (image file name) or "folder" (parent folder name, for generic file names like R1_Merged)
def outputDir      = null     // null = "pyramidal" folder next to each original; or an absolute path
int tileSize       = 256
int pyramidScaling = 4        // downsample factor between pyramid levels
def compression    = OMEPyramidWriter.CompressionType.ZLIB   // lossless
// ─────────────────────────────────────────────────────────────────────────────

def imageData = getCurrentImageData()
def inputFile = new File(imageData.getServer().getURIs()[0])

if (channelNames) {
    def nChannels = imageData.getServer().nChannels()
    if (channelNames.size() != nChannels) {
        println "${inputFile.getName()}: image has ${nChannels} channels but channelNames lists ${channelNames.size()} -- skipped."
        return
    }
    setChannelNames(imageData, *channelNames)
}
def server = imageData.getServer()
println "Channels: ${server.getMetadata().getChannels()*.getName()}"

def outFolder = outputDir ? new File(outputDir) : new File(inputFile.getParentFile(), 'pyramidal')
outFolder.mkdirs()
def baseName = outputNameFrom == "folder" ?
        inputFile.getParentFile().getName() :
        inputFile.getName().replaceAll(/(?i)(\.ome)?\.tiff?$/, '')
def outFile = new File(outFolder, baseName.replaceAll(/\s+/, '_') + '_pyramid.ome.tif')

if (outFile.exists()) {
    println "Skipping (already exists): ${outFile}"
    return
}

println "Writing ${outFile}"
long start = System.currentTimeMillis()
new OMEPyramidWriter.Builder(server)
        .compression(compression)
        .parallelize()
        .tileSize(tileSize)
        .scaledDownsampling(1, pyramidScaling)
        .build()
        .writePyramid(outFile.getAbsolutePath())
println "Done in ${(System.currentTimeMillis() - start) / 1000} s"
