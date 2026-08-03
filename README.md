# QuPath Daily Scripts

A collection of [QuPath](https://qupath.github.io/) Groovy scripts used for daily image analysis tasks, including measurement extraction, cell detection, annotation export, and spatial analysis.

---

## Folder Overview

### **basic_measurements/**
Scripts for calculating or cleaning object measurements.
- `add_circularity_and_solidity.groovy`
- `add_intensity_measurements.groovy`
- `add_shape_measurements.groovy`
- `clean_measurement_list.groovy`

### **benchmarking/**
Scripts for comparing segmentation results or evaluating model performance.
- `segmentation_metrics.groovy`

### **cell_analysis/**
Scripts for generating and analyzing cell objects.
- `create_cells_from_annotation_hierarchy.groovy`

### **classification/**
Scripts for classifying objects based on morphology or other measurements.
- `classify_detections_shape_vs_round.groovy`

### **cpsam_training/**
Python toolkit for fine-tuning CellPose-SAM on custom annotated data. Unlike the Groovy scripts, this is used outside QuPath in a terminal or Jupyter notebook.

Copy the `training_template/` folder to your experiment project directory, then follow the workflow in the notebook or the script docstrings:

1. Export annotated pairs from QuPath using `export_annotations/cellpose_training/`
2. Place exported `_img.tif` / `_mask.tif` pairs in `data/` (flat or one subfolder per dataset)
3. Split the data using the **Split data** cell in the notebook, or run `python split_data.py data/` from a terminal
4. Train via notebook (`train_cpsam.ipynb`) or CLI (`python train_cpsam.py --model_name <name>`)
5. The trained model is saved to `models/<model_name>` inside your copied folder

Requires the `cellpose-sam` conda environment.

- `training_template/split_data.py` — creates reproducible train/test/eval splits; auto-detects flat or multi-dataset layout
- `training_template/train_cpsam.ipynb` — step-by-step notebook with inline loss plot
- `training_template/train_cpsam.py` — CLI equivalent; saves loss curve as PNG and CSV

### **detection_extensions/**
Scripts for cell detection using external models and extensions (e.g., StarDist, Cellpose, ImageJ).
- `cpsam_detection_live_cell_imaging.groovy` — **Requires QuPath 0.5.0 + qupath-extension-cellpose 0.9.3.** In QuPath 0.7.0, the extension does not correctly use the timepoint from the annotation ROI when exporting image tiles, causing all frames to be detected using frame 0 data. This is a known regression; do not run this script in QuPath 0.7.0 until a fix is confirmed.
- `imagej_threshold_detection_on_annotations.groovy` — creates threshold-based annotation objects using an ImageJ threshold method, keeping only regions overlapping with target annotations
- `stardist_fluorescence_cell_detection.groovy`
- `stardist_fluorescence_cell_detection_with_preprocessing.groovy`
- `stardist_fluorescence_cell_detection_zstack.groovy`
- `stardist_fluorescence_detection_on_annotations.groovy`

### **export_annotations/**
Scripts for exporting annotations, masks, or training labels.

- **cellpose_training/**
  - `export_labels_for_cellpose.groovy`
  - `export_labels_for_cellpose_all_zslices_and_timepoints.groovy`
  - `export_labels_for_cellpose_current_zslice_and_timepoint.groovy`
- **geojson/**
  - `export_geojson.groovy`
- **individualized_annotations/**
  - `export_binary_mask_bounding_boxes.groovy`
  - `export_binary_mask_multichannel.groovy`
- **semantic_training/**
  - `export_semantic_labels_regions.groovy` — multi-class label export per unclassified-annotation region, single plane
  - `export_semantic_labels_zstack.groovy` — single-class label export across the whole image (all z-slices/timepoints), with an optional border/boundary label to separate touching instances downstream

### **import_annotations/**
Scripts for importing external data (e.g. model predictions) as annotations.
- `import_predicted_labels_as_annotations.groovy` — imports a prediction/label image as annotations for curation; per-class threshold-based ROI extraction via ImageJ, auto-detects XY downsample mismatches, and skips images that already have curated annotations of the target class(es)

### **name_and_format_utilities/**
Scripts for renaming or reformatting project entries.
- `rename_project_images.groovy` — renames project entries by combining the parent folder name and filename, stripping the file extension and a configurable suffix

### **skeleton_analysis/**
Scripts for analyzing skeletonized structures via ImageJ.
- `analyze_skeleton.groovy`

### **spatial_relationships/**
Scripts for computing distances, relationships, or reference-based measurements.
- `add_reference_centroid_xy.groovy`
- `distance_to_nearest_objects.groovy`
- `is_in_object.groovy`

### **utilities/**
General-purpose helper scripts for managing channels, annotations, or calibration.
- `annotations_to_detections.groovy`
- `change_channel_names.groovy`
- `change_channel_names_nchannel_variable.groovy` — sets channel names for images with either 3 or 4 channels
- `detections_to_annotations.groovy`
- `fill_annotations.groovy`
- `prepend_folder_to_entry_name.groovy` — prepends the parent folder name to each project entry name
- `remove_annotations.groovy`
- `remove_annotations_at_image_boundaries.groovy` — removes objects whose ROIs touch or are within a set distance of an annotation boundary
- `remove_detections.groovy`
- `set_pixel_calibration.groovy`
- `set_project_classes.groovy` — sets the available classification classes for the project

---

## Usage

These scripts can be run directly within **QuPath's Script Editor**:

1. Open QuPath → `Automate → Show script editor`
2. Load the desired `.groovy` file.
3. Adjust the parameters in the configuration block at the top of the script.
4. Run (`Ctrl/Cmd + R`) or `Run for project`.

### Configuration convention

All scripts expose their parameters in a clearly marked block at the top:

```groovy
// ── Configuration ────────────────────────────────────────────────────────────
def targetClassName = "YOUR_CLASS_NAME"  // class name, null (all), or "" (unclassified)
// ─────────────────────────────────────────────────────────────────────────────
```

For scripts that filter by annotation class, `targetClassName` accepts three forms:

| Value | Behaviour |
|---|---|
| `"ClassName"` | objects of that class only |
| `null` | all objects, regardless of class |
| `""` | unclassified objects only |

Detection scripts (StarDist, Cellpose) follow their own parameter convention and are not covered by this pattern.

---

## Acknowledgments

These scripts build upon the QuPath scripting API and community examples.
