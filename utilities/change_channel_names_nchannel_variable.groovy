/**
 * Set the channel names for the specified ImageData.
 * It is not essential to pass names for all channels:
 * by passing n values, the first n channel names will be set.
 * Any name that is null will be left unchanged (not recommended).
 */

// ── Configuration ────────────────────────────────────────────────────────────
def channelNames3 = ["DAPI", "Nestin", "NeuN"]           // Change to match your channel names (3-channel images)
def channelNames4 = ["DAPI", "Cre", "Nestin", "NeuN"]    // Change to match your channel names (4-channel images)
// ─────────────────────────────────────────────────────────────────────────────

def imageData = getCurrentImageData()
def server    = imageData.getServer()
int nChannels = server.nChannels()

if (nChannels == 3) {
    setChannelNames(imageData, *channelNames3)
} else if (nChannels == 4) {
    setChannelNames(imageData, *channelNames4)
}
