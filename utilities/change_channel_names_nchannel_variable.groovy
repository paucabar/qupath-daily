/**
 * Set the channel names for the specified ImageData.
 * It is not essential to pass names for all channels:
 * by passing n values, the first n channel names will be set.
 * Any name that is null will be left unchanged (not recommended).
 */

def imageData = getCurrentImageData()
def server = imageData.getServer()
int nChannels = server.nChannels()

if (nChannels == 3) {
    setChannelNames(imageData, "DAPI", "Nestin", "NeuN") // change the string list to rename your channels
} else if (nChannels == 4) {
    setChannelNames(imageData, "DAPI", "Cre", "Nestin", "NeuN") // change the string list to rename your channels
}
