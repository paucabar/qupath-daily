"""
Pack image/mask pairs exported with
export_annotations/instanseg_training/export_labels_for_instanseg_training_regions.groovy
into InstanSeg's dataset file, <data_dir>/<name>_dataset.pth =
{"Train": [...], "Validation": [...], "Test": [...]}.

The channel names and pixel size are read from each OME-TIFF. Masks are
relabelled 1..N. Splits are made by source image (the part of the file name
before "__"), so tiles of one image never end up on both sides; pass
--split-csv (columns image,split) to fix them yourself, e.g. by patient.

Needs torch, numpy and tifffile (any InstanSeg training environment):
  python pack_instanseg_dataset.py <export folder> --name my_cells
Then train with:  -d_p <export folder> -data my_cells -source "[my_cells]"
"""
import argparse
import csv
import random
from pathlib import Path

import numpy as np
import tifffile
import torch


def ome_meta(path):
    with tifffile.TiffFile(path) as t:
        img = t.series[0].asarray()
        names, pixel_size = [], None
        if t.ome_metadata:
            from xml.etree import ElementTree
            root = ElementTree.fromstring(t.ome_metadata)
            pixels = next(e for e in root.iter() if e.tag.endswith("Pixels"))
            names = [c.get("Name") or f"Channel {i + 1}"
                     for i, c in enumerate(e for e in pixels if e.tag.endswith("Channel"))]
            size_x, unit = pixels.get("PhysicalSizeX"), pixels.get("PhysicalSizeXUnit", "µm")
            if size_x is not None:
                pixel_size = float(size_x) * {"nm": 1e-3, "mm": 1e3}.get(unit, 1.0)
    if img.ndim == 2:
        img = img[None]
    return img, names, pixel_size


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("data_dir", type=Path, help="folder with *_img.ome.tif / *_mask.tif pairs (searched recursively)")
    ap.add_argument("--name", required=True, help="dataset name: writes <name>_dataset.pth")
    ap.add_argument("--val-frac", type=float, default=0.15, help="fraction of source images for Validation")
    ap.add_argument("--test-frac", type=float, default=0.15, help="fraction of source images for Test")
    ap.add_argument("--split-csv", type=Path, default=None, help="columns image,split (Train/Validation/Test)")
    ap.add_argument("--modality", default="Fluorescence", choices=["Fluorescence", "Brightfield"])
    ap.add_argument("--pixel-size", type=float, default=None, help="override the pixel size (µm) in the files")
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()

    pairs = []
    for img_path in sorted(args.data_dir.rglob("*_img.ome.tif")):
        mask_path = img_path.with_name(img_path.name.replace("_img.ome.tif", "_mask.tif"))
        if not mask_path.exists():
            print(f"WARNING: no mask for {img_path.name}, skipped")
            continue
        pairs.append((img_path, mask_path, img_path.name.split("__")[0]))
    if not pairs:
        raise SystemExit(f"no *_img.ome.tif / *_mask.tif pairs in {args.data_dir}")

    images = sorted({p[2] for p in pairs})
    if args.split_csv:
        split = {r["image"]: r["split"] for r in csv.DictReader(open(args.split_csv, encoding="utf-8"))}
        unknown = [i for i in images if i not in split]
        if unknown:
            raise SystemExit(f"images missing from {args.split_csv}: {unknown}")
    else:
        rng = random.Random(args.seed)
        shuffled = images[:]
        rng.shuffle(shuffled)
        n_test = round(len(images) * args.test_frac)
        n_val = max(1, round(len(images) * args.val_frac)) if len(images) > 1 else 0
        split = {i: "Test" for i in shuffled[:n_test]}
        split.update({i: "Validation" for i in shuffled[n_test:n_test + n_val]})
        split.update({i: "Train" for i in shuffled[n_test + n_val:]})

    dataset = {"Train": [], "Validation": [], "Test": []}
    rows = []
    for img_path, mask_path, image in pairs:
        img, channels, pixel_size = ome_meta(img_path)
        pixel_size = args.pixel_size or pixel_size
        if pixel_size is None:
            raise SystemExit(f"{img_path.name}: no pixel size in the file, pass --pixel-size")
        masks = tifffile.imread(mask_path)
        _, inverse = np.unique(masks, return_inverse=True)  # 0 stays 0 (background is the smallest)
        masks = inverse.reshape(masks.shape).astype(np.int32)
        if masks.shape != img.shape[-2:]:
            raise SystemExit(f"{img_path.name}: image {img.shape} and mask {masks.shape} differ")
        dataset[split[image]].append({
            "image": img,
            "cell_masks": masks,
            "parent_dataset": args.name,
            "image_modality": args.modality,
            "pixel_size": float(pixel_size),
            "channel_names": channels,
            "file_name": img_path.relative_to(args.data_dir).as_posix(),
            "source_image": image,
        })
        rows.append([img_path.name, image, split[image], ",".join(channels), pixel_size, int(masks.max())])

    out = args.data_dir / f"{args.name}_dataset.pth"
    torch.save(dataset, out)
    with open(args.data_dir / f"{args.name}_contents.csv", "w", newline="", encoding="utf-8") as fh:
        wr = csv.writer(fh)
        wr.writerow(["file", "image", "split", "channels", "pixel_size_um", "n_objects"])
        wr.writerows(rows)
    print(f"{out}: " + ", ".join(f"{k} {len(v)} pairs" for k, v in dataset.items())
          + f" from {len(images)} images; channels {sorted({r[3] for r in rows})}")
    for k, v in dataset.items():
        if not v and k != "Test":
            print(f"WARNING: {k} is empty (InstanSeg's train.py needs it)")


if __name__ == "__main__":
    main()
