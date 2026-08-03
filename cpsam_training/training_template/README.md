# Training CellPose-SAM from the command line

Step-by-step guide to fine-tuning a custom CellPose-SAM model using only the
terminal — no Jupyter notebook required. If you'd rather work in a notebook
(e.g. to see the loss plot inline as it happens), use `train_cpsam.ipynb`
instead; both use the same underlying scripts.

## Prerequisites

- The `cellpose-sam` conda environment, activated:
  ```
  conda activate cellpose-sam
  ```
- All commands below are run from inside this folder (`training_template/`,
  or your own copy of it).

## Step 1 — Get your annotated data in

Export annotated image/mask pairs from QuPath using one of the scripts in
`export_annotations/cellpose_training/`. Each exported image produces a pair
of files named `<name>_img.tif` and `<name>_mask.tif`.

Place the pairs in the `data/` folder next to this README, either flat or
split into one subfolder per dataset:

```
data/
├── frame001_img.tif        ← flat: all images directly in data/
└── frame001_mask.tif
```

```
data/
├── experiment_A/           ← multi-dataset: one subfolder per dataset
│   ├── frame001_img.tif
│   └── frame001_mask.tif
└── experiment_B/
    ├── frame001_img.tif
    └── frame001_mask.tif
```

Don't mix the two layouts (loose files and subfolders) in the same `data/`
folder — `split_data.py` will refuse to run if it finds both.

## Step 2 — Split into train/test sets

```
python split_data.py data/
```

This creates `data/splits/train/` and `data/splits/test/` (90 / 10 % by
default). Files are hard-linked where possible, so this doesn't duplicate
disk space.

Examples for other cases:

```
# Reserve more data for testing
python split_data.py data/ --test_fraction 0.2

# Also hold out a separate eval set (untouched until final model checkpoint)
python split_data.py data/ --test_fraction 0.15 --eval_fraction 0.05

# Re-run the split after adding more annotated data
python split_data.py data/ --overwrite

# Reproduce the exact same split later (e.g. to compare two training runs)
python split_data.py data/ --seed 42
```

## Step 3 — Train

```
python train_cpsam.py --model_name my_model
```

The trained model is saved to `models/my_model`. Default settings
(200 epochs, learning rate `1e-5`) are aimed at **fine-tuning with a small
dataset** — since CellPose-SAM already has strong pretrained features, a
small custom dataset usually needs adaptation rather than training from
scratch, and fewer epochs reduces the risk of overfitting to a handful of
images.

Typical cases:

```
# Small dataset, default fine-tuning settings
python train_cpsam.py --model_name my_model

# No GPU available on this machine
python train_cpsam.py --model_name my_model --no_gpu

# Larger dataset — train for longer
python train_cpsam.py --model_name my_model --n_epochs 600

# Using a custom split location (e.g. --eval_fraction was used in Step 2)
python train_cpsam.py --model_name my_model \
    --train_dir data/splits/train --test_dir data/splits/test
```

Run `python train_cpsam.py --help` for the full list of options
(`--learning_rate`, `--weight_decay`, `--save_every`, ...).

## Step 4 — Check the outputs

Everything is saved under `models/`:

- `<model_name>` — the trained model weights, ready to load with
  `models.CellposeModel(pretrained_model="models/<model_name>")`
- `<model_name>_losses.csv` — train/test loss per epoch
- `<model_name>_loss.png` — loss curve (only if `matplotlib` is installed —
  see note below)

Look at the loss curve before trusting the model: if train loss keeps
dropping while test loss flattens or rises, the model is overfitting —
consider fewer epochs, more training data, or a lower `--learning_rate`.

## Troubleshooting

- **`matplotlib` not installed** — training still completes and the CSV is
  still saved; only the PNG/PDF loss plot is skipped. Install with
  `pip install matplotlib` (don't use `conda install` — it tends to trigger
  a full environment solve/reshuffle in this environment).
- **`ERROR: no valid _img.tif/_mask.tif pairs found`** — check that both
  files of each pair share the same `<name>` prefix and are directly inside
  `data/` (or inside one of its dataset subfolders, not nested further).
