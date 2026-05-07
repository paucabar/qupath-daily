# QuPath Daily Scripts

A collection of [QuPath](https://qupath.github.io/) Groovy scripts used for daily image analysis tasks, including measurement extraction, cell detection, annotation export, and spatial analysis.

---

## Folder Overview

### **basic_measurements/**
Scripts for calculating or cleaning object measurements.
- `add_circularity_and_solidity.groovy`
- `add_intensity_measurements.groovy`
- `add_shape_measurements_script.groovy`
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
3. Run `python split_data.py data/` to generate `data/splits/train/` and `data/splits/test/`
4. Train via notebook (`train_cpsam.ipynb`) or CLI (`python train_cpsam.py --model_name <name>`)
5. The trained model is saved to `models/<model_name>` inside your copied folder

Requires the `cpsam` conda environment.

- `training_template/split_data.py` — creates reproducible train/test/eval splits; auto-detects flat or multi-dataset layout
- `training_template/train_cpsam.ipynb` — step-by-step notebook with inline loss plot
- `training_template/train_cpsam.py` — CLI equivalent; saves loss curve as PNG and CSV

### **detection_extensions/**
Scripts for cell detection using external models and extensions (e.g., StarDist, Cellpose).
- `cpsam_detection_live_cell_imaging.groovy` — **Requires QuPath 0.5.0 + qupath-extension-cellpose 0.9.3.** In QuPath 0.7.0, the extension does not correctly use the timepoint from the annotation ROI when exporting image tiles, causing all frames to be detected using frame 0 data. This is a known regression; do not run this script in QuPath 0.7.0 until a fix is confirmed.
- `stardist_fluorescence_cell_detection.groovy`
- `stardist_fluorescence_cell_detection_with_preprocessing.groovy`
- `stardist_fluorescence_detection_on_annotations.groovy`

### **export_annotations/**
Scripts for exporting annotations, masks, or training labels.

- **axonwrap_training/**
  - `export_labels_for_axonwrap.groovy`
- **cellpose_training/**
  - `export_labels_for_cellpose.groovy`
  - `export_labels_for_cellpose_current_zslice_and_timepoint.groovy`
- **geojson/**
  - `export_geojson.groovy`
- **individualized_annotations/**
  - `export_binary_mask_bounding_boxes.groovy`
  - `export_binary_mask_multichannel.groovy`

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
- `change_channel_names.groovy`
- `detections_to_annotations.groovy`
- `fill_annotations.groovy`
- `remove_annotations.groovy`
- `remove_detections.groovy`
- `set_pixel_calibration.groovy`

---

## Usage

These scripts can be run directly within **QuPath’s Script Editor**:

1. Open QuPath → `Automate → Show script editor`
2. Load the desired `.groovy` file.
3. Adjust any parameters.
4. Run (`Ctrl/Cmd + R`) or Run for project.

---


## Acknowledgments

These scripts build upon the QuPath scripting API and community examples.  
