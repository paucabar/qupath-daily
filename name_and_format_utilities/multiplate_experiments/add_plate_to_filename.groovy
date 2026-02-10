import qupath.lib.images.servers.ServerTools

// Get project & current image
def project = getProject()
def imageData = getCurrentImageData()
def server = imageData.getServer()

// Get the URI of the image file
def uri = server.getURIs()[0]
def fileImage = new File(uri)

// Get parent folder name
def parentFolderName = fileImage.getParentFile().getName()

// Get image name without extension & trailing extra text
def imageName = fileImage.getName()
imageName = imageName.replaceFirst(/\.tif.*$/, "")  // Remove ".tif" and anything after

// Build new name
def newName = "${parentFolderName}_${imageName}"

// Update the server metadata (optional, so the name shows in the viewer)
ServerTools.setImageName(server, newName)

// Update the project entry name
def entry = project.getEntry(imageData)
entry.setImageName(newName)

// Refresh project
getQuPath().refreshProject()

print "Renamed project entry to: ${newName}"
