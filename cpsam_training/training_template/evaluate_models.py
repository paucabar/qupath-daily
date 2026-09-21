"""
Compare segmentation models on the held-out test set.

Usage:
    python evaluate_models.py --models cpsam models/my_model models/older_model
    python evaluate_models.py --models cpsam models/my_model --save_masks
    python evaluate_models.py --models cpsam models/my_model --reuse_masks --no_gpu

Runs each model over every *_img.tif in --test_dir, compares the predicted labels with the
matching *_mask.tif, and reports average precision (AP) at a range of IoU thresholds. AP here is
cellpose's definition, TP / (TP + FP + FN) at a given IoU threshold, not the COCO area-under-the-
curve. AP at 0.5 says whether objects are found at all; the higher thresholds say how well the
outlines agree.

The literal model name "cpsam" (or "base") evaluates the stock pretrained CellPose-SAM model, which
is the baseline a fine-tuned model has to beat.

If data/ holds one subfolder per dataset, results are also broken down by dataset, which shows which
dataset a model is weakest on, i.e. where annotating more would pay. The breakdown is read from the
layout of --data_root, because the split folders pool every dataset together.

Two things to keep in mind when reading the numbers. The split is per file, so if several files are
crops of the same image, one image can contribute to both sides and the scores are optimistic as an
estimate of generalisation to new images. And if the masks were made by curating some model's
predictions, that model is partly being scored against its own output.
"""

import argparse
import csv
import os
import sys
from pathlib import Path

import numpy as np
import tifffile

BASE_MODEL_NAMES = {"cpsam", "base"}
NO_DATASETS = "all"


def long_path(path: Path) -> Path:
    """
    Return a Windows extended-length ('\\\\?\\') path, exempt from the 260-character MAX_PATH limit.
    No-op on other platforms.

    Worth having because exported tile names can be long: QuPath names an .nd2 series
    '<file>.nd2 - <file>.nd2 (series 1)', which alone can run past 140 characters, and a mapped
    network drive expands to a much longer UNC path when resolved. Over the limit, Path.exists()
    returns False instead of raising, so files look missing rather than erroring.
    """
    if os.name != "nt":
        return path
    text = str(path)
    if text.startswith("\\\\?\\"):
        return path
    # The prefix is only valid on a fully qualified path, so absolutise relative ones first.
    text = os.path.abspath(text)
    if text.startswith("\\\\"):
        return Path("\\\\?\\UNC" + text[1:])
    return Path("\\\\?\\" + text)


def short(path: Path) -> str:
    """Strip the extended-length prefix again, so printed paths stay readable."""
    text = str(path)
    if text.startswith("\\\\?\\UNC"):
        return "\\" + text[len("\\\\?\\UNC"):]
    if text.startswith("\\\\?\\"):
        return text[len("\\\\?\\"):]
    return text


def dataset_lookup(data_root: Path) -> dict[str, str]:
    """
    Map each pair's stem (filename without _img.tif) to the dataset subfolder it came from.

    Returns an empty map for the flat layout, where there are no datasets to group by.
    """
    lookup = {}
    root = long_path(data_root)
    if not root.is_dir():
        return lookup
    for sub in sorted(root.iterdir()):
        if not sub.is_dir() or sub.name == "splits":
            continue
        for img in sub.glob("*_img.tif"):
            lookup[img.name[: -len("_img.tif")]] = sub.name
    return lookup


def find_pairs(test_dir: Path) -> list[tuple[str, Path, Path]]:
    """Return (stem, image path, mask path) for every complete pair in test_dir."""
    pairs = []
    for img in sorted(long_path(test_dir).glob("*_img.tif")):
        stem = img.name[: -len("_img.tif")]
        mask = img.parent / f"{stem}_mask.tif"
        if not mask.exists():
            print(f"SKIP {stem}: no matching _mask.tif")
            continue
        pairs.append((stem, img, mask))
    return pairs


def renumber(mask: np.ndarray) -> np.ndarray:
    """
    Relabel a mask so its objects are numbered 1..n with no gaps.

    Worth doing before scoring, because label values are not always a count. QuPath's
    LabeledImageServer, for one, numbers instances per image rather than per exported region, so a
    region's mask holds a sparse slice of the image's label range and its largest label can be
    several times its object count.
    """
    values, flat = np.unique(mask, return_inverse=True)
    relabelled = flat.reshape(mask.shape).astype(np.uint32)
    if values[0] != 0:  # no background present, so shift labels up to keep 0 free
        relabelled += 1
    return relabelled


def model_label(spec: str) -> str:
    return "cpsam (base)" if spec.lower() in BASE_MODEL_NAMES else Path(spec).name


def load_model(spec: str, gpu: bool):
    from cellpose import models

    if spec.lower() in BASE_MODEL_NAMES:
        return models.CellposeModel(gpu=gpu)
    path = long_path(Path(spec))
    if not path.exists():
        sys.exit(f"Model not found: {short(path)}")
    return models.CellposeModel(gpu=gpu, pretrained_model=str(path))


def mask_dir_for(out_dir: Path, label: str) -> Path:
    return out_dir / "masks" / label.replace(" ", "_").replace("(", "").replace(")", "")


def pooled_f1(tp: int, fp: int, fn: int) -> float:
    denominator = 2 * tp + fp + fn
    return 2 * tp / denominator if denominator else float("nan")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Compare segmentation models on the held-out test set.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "--models", nargs="+", default=["cpsam"],
        help='Model paths to compare; "cpsam" means the stock pretrained model',
    )
    parser.add_argument("--test_dir", type=Path, default=Path("data/splits/test"))
    parser.add_argument(
        "--data_root", type=Path, default=Path("data"),
        help="Directory whose subfolders name the datasets (ignored for a flat layout)",
    )
    parser.add_argument("--out_dir", type=Path, default=Path("evaluation"))
    parser.add_argument(
        "--thresholds", type=float, nargs="+",
        default=[0.5, 0.55, 0.6, 0.65, 0.7, 0.75, 0.8, 0.85, 0.9, 0.95],
        help="IoU thresholds for average precision",
    )
    parser.add_argument("--flow_threshold", type=float, default=0.4)
    parser.add_argument("--cellprob_threshold", type=float, default=0.0)
    parser.add_argument("--batch_size", type=int, default=8)
    parser.add_argument(
        "--save_masks", action="store_true",
        help="Also write each model's predicted labels as TIFFs, for visual checks",
    )
    parser.add_argument(
        "--reuse_masks", action="store_true",
        help="Score the predicted labels already in --out_dir instead of running inference again",
    )
    parser.add_argument("--no_gpu", action="store_true", help="Use CPU instead of GPU")
    args = parser.parse_args()

    from cellpose import metrics

    pairs = find_pairs(args.test_dir)
    if not pairs:
        sys.exit(f"No image/mask pairs found in {short(long_path(args.test_dir))}")
    datasets = dataset_lookup(args.data_root)

    images, truths, stems, pair_datasets = [], [], [], []
    for stem, img_path, mask_path in pairs:
        images.append(tifffile.imread(img_path))
        truths.append(renumber(tifffile.imread(mask_path)))
        stems.append(stem)
        pair_datasets.append(datasets.get(stem, NO_DATASETS) if datasets else NO_DATASETS)

    unknown = sum(1 for d in pair_datasets if d == NO_DATASETS)
    if datasets and unknown:
        print(f"WARNING: {unknown} pair(s) not found under {short(long_path(args.data_root))} "
              f"- grouped as '{NO_DATASETS}'")

    print(f"{len(pairs)} test pairs, datasets: "
          + ", ".join(f"{d} {pair_datasets.count(d)}" for d in sorted(set(pair_datasets))))

    thresholds = list(args.thresholds)
    out_dir = long_path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    rows = []
    for spec in args.models:
        label = model_label(spec)
        print(f"\n=== {label} ===")
        mask_dir = mask_dir_for(out_dir, label)
        model = None
        predictions = []
        for stem, image in zip(stems, images):
            cached = mask_dir / f"{stem}_pred.tif"
            if args.reuse_masks and cached.exists():
                prediction = renumber(tifffile.imread(cached))
                source = "cached"
            else:
                if model is None:
                    model = load_model(spec, gpu=not args.no_gpu)
                masks, _, _ = model.eval(
                    image,
                    batch_size=args.batch_size,
                    flow_threshold=args.flow_threshold,
                    cellprob_threshold=args.cellprob_threshold,
                )
                prediction = renumber(masks)
                source = "predicted"
            predictions.append(prediction)
            print(f"  {stem[:60]:<60} {int(prediction.max()):>5} objects ({source})")

        ap, tp, fp, fn = metrics.average_precision(truths, predictions, threshold=thresholds)

        if args.save_masks:
            mask_dir.mkdir(parents=True, exist_ok=True)
            for stem, prediction in zip(stems, predictions):
                tifffile.imwrite(mask_dir / f"{stem}_pred.tif", prediction.astype(np.uint16))
            print(f"  Predicted labels -> {short(mask_dir)}")

        for i, stem in enumerate(stems):
            row = {
                "model": label,
                "dataset": pair_datasets[i],
                "image": stem,
                "n_true": int(truths[i].max()),
                "n_pred": int(predictions[i].max()),
                "tp@0.5": int(tp[i, 0]),
                "fp@0.5": int(fp[i, 0]),
                "fn@0.5": int(fn[i, 0]),
                "mean_ap": float(ap[i].mean()),
            }
            for j, threshold in enumerate(thresholds):
                row[f"ap@{threshold:g}"] = float(ap[i, j])
            rows.append(row)

        del model
        try:
            import torch

            torch.cuda.empty_cache()
        except ImportError:
            pass

    fieldnames = (["model", "dataset", "image", "n_true", "n_pred", "tp@0.5", "fp@0.5", "fn@0.5",
                   "mean_ap"] + [f"ap@{t:g}" for t in thresholds])
    per_image_path = out_dir / "per_image_ap.csv"
    with open(per_image_path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    groups = sorted(set(pair_datasets))
    if len(groups) > 1:
        groups = groups + ["ALL"]

    summary = []
    for spec in args.models:
        label = model_label(spec)
        model_rows = [r for r in rows if r["model"] == label]
        for group_name in groups:
            group = (model_rows if group_name in ("ALL", NO_DATASETS)
                     else [r for r in model_rows if r["dataset"] == group_name])
            if not group:
                continue
            entry = {
                "model": label,
                "dataset": group_name,
                "n_images": len(group),
                "n_true": sum(r["n_true"] for r in group),
                "n_pred": sum(r["n_pred"] for r in group),
                "f1@0.5": pooled_f1(sum(r["tp@0.5"] for r in group),
                                    sum(r["fp@0.5"] for r in group),
                                    sum(r["fn@0.5"] for r in group)),
                "mean_ap": float(np.mean([r["mean_ap"] for r in group])),
            }
            for threshold in thresholds:
                key = f"ap@{threshold:g}"
                entry[key] = float(np.mean([r[key] for r in group]))
            summary.append(entry)

    summary_path = out_dir / "summary.csv"
    with open(summary_path, "w", newline="") as f:
        writer = csv.DictWriter(
            f,
            fieldnames=["model", "dataset", "n_images", "n_true", "n_pred", "f1@0.5", "mean_ap"]
            + [f"ap@{t:g}" for t in thresholds],
        )
        writer.writeheader()
        writer.writerows(summary)

    shown = [t for t in (0.5, 0.75, 0.9) if t in thresholds]
    header = (f"{'model':<16} {'dataset':<12} {'imgs':>5} {'true':>6} {'pred':>6} "
              + " ".join(f"{'AP@' + format(t, 'g'):>7}" for t in shown)
              + f" {'meanAP':>7} {'F1@0.5':>7}")
    print("\n" + header)
    print("-" * len(header))
    for entry in summary:
        print(f"{entry['model']:<16} {entry['dataset']:<12} {entry['n_images']:>5} "
              f"{entry['n_true']:>6} {entry['n_pred']:>6} "
              + " ".join(f"{entry['ap@' + format(t, 'g')]:>7.3f}" for t in shown)
              + f" {entry['mean_ap']:>7.3f} {entry['f1@0.5']:>7.3f}")

    print(f"\nPer image -> {short(per_image_path)}")
    print(f"Summary   -> {short(summary_path)}")


if __name__ == "__main__":
    main()
