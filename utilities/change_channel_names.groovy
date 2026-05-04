/**
 * Set the channel names for the specified ImageData.
 * It is not essential to pass names for all channels:
 * by passing n values, the first n channel names will be set.
 * Any name that is null will be left unchanged (not recommended).
 */

// ── Configuration ────────────────────────────────────────────────────────────
def channelNames = ["DAPI", "488", "Cy3", "Cy5"]  // Change to match your channel names
// ─────────────────────────────────────────────────────────────────────────────

def imageData = getCurrentImageData()
setChannelNames(imageData, *channelNames)
