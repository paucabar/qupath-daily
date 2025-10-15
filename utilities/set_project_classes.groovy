// Platform.runLater to call on the correct (UI) thread
Platform.runLater {
    // Be really careful here!
    // Need to include null for unclassified objects, and make sure all names are unique
    getQuPath().getAvailablePathClasses().setAll(
        getPathClass(null), // Important to keep this!
        getPathClass('CLASS1_NAME', makeRGB(255, 0, 255)), 
        getPathClass('CLASS2_NAME', makeRGB(0, 0, 255)),
        getPathClass('CLASS3_NAME', makeRGB(255, 0, 0)),
    )
}