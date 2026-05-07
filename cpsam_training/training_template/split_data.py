"""
Split annotated image/mask pairs into train, test, and (optionally) eval sets.

Usage:
    python split_data.py data/
    python split_data.py data/ --test_fraction 0.15 --eval_fraction 0.05 --seed 42

Expects pairs of files named <stem>_img.tif and <stem>_mask.tif. Supports two layouts:
  - Flat:  data_dir contains _img.tif files directly
  - Multi: data_dir contains subdirectories, each holding _img.tif files (one per dataset)

Splits are created in data_dir/splits/train/, /test/, and (if requested) /eval/.
Files are hard-linked where possible to avoid duplicating disk space; copied as fallback.
"""

import argparse
import os
import random
import shutil
import sys
from pathlib import Path


def find_pairs(directory: Path) -> list[tuple[Path, Path]]:
    """Return validated (img, mask) pairs from a single flat directory."""
    pairs = []
    for img_path in sorted(directory.glob("*_img.tif")):
        stem = img_path.name[: -len("_img.tif")]
        mask_path = directory / f"{stem}_mask.tif"
        if not mask_path.exists():
            print(f"  WARNING: no matching mask for {img_path.name} — skipped")
            continue
        pairs.append((img_path, mask_path))
    return pairs


def detect_layout(data_dir: Path) -> list[tuple[Path, Path]]:
    """
    Auto-detect flat vs. multi-dataset layout and return all valid (img, mask) pairs.

    Raises SystemExit with a descriptive message on ambiguous or empty input.
    """
    subdirs = [d for d in data_dir.iterdir() if d.is_dir() and d.name != "splits"]
    top_imgs = list(data_dir.glob("*_img.tif"))

    if top_imgs and subdirs:
        sys.exit(
            f"ERROR: '{data_dir}' contains both image files and subdirectories.\n"
            "Organise data as either:\n"
            "  - All images directly in the directory (flat), or\n"
            "  - All images inside subdirectories, one per dataset\n"
            "not a mix of both."
        )

    if top_imgs:
        print("Layout: flat (single dataset)")
        pairs = find_pairs(data_dir)
        if not pairs:
            sys.exit(f"ERROR: no valid _img.tif/_mask.tif pairs found in '{data_dir}'")
        return pairs

    if subdirs:
        print("Layout: multi-dataset (subdirectories)")
        all_pairs: list[tuple[Path, Path]] = []
        stems_seen: dict[str, str] = {}
        collisions: list[str] = []

        for subdir in sorted(subdirs):
            pairs = find_pairs(subdir)
            if not pairs:
                print(f"  WARNING: '{subdir.name}' has no valid pairs — skipped")
                continue
            for img, mask in pairs:
                stem = img.name
                if stem in stems_seen:
                    collisions.append(
                        f"  '{stem}'  found in '{subdir.name}' and '{stems_seen[stem]}'"
                    )
                else:
                    stems_seen[stem] = subdir.name
                    all_pairs.append((img, mask))

        if collisions:
            sys.exit(
                "ERROR: duplicate filenames detected across datasets:\n"
                + "\n".join(collisions)
                + "\nRename the conflicting files before splitting."
            )
        if not all_pairs:
            sys.exit(
                f"ERROR: no valid image/mask pairs found in any subdirectory of '{data_dir}'"
            )
        return all_pairs

    sys.exit(f"ERROR: no _img.tif files or subdirectories found in '{data_dir}'")


def link_or_copy(src: Path, dst: Path) -> None:
    try:
        os.link(src, dst)
    except OSError:
        shutil.copy2(src, dst)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Split CPSAM training data into train/test/eval sets.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("data_dir", type=Path, help="Directory containing image/mask pairs")
    parser.add_argument(
        "--test_fraction", type=float, default=0.1,
        help="Fraction of data reserved for the test set",
    )
    parser.add_argument(
        "--eval_fraction", type=float, default=0.0,
        help="Fraction of data reserved for an optional eval set (0 = disabled)",
    )
    parser.add_argument("--seed", type=int, default=42, help="Random seed")
    parser.add_argument(
        "--overwrite", action="store_true",
        help="Remove an existing splits/ directory before running",
    )
    args = parser.parse_args()

    data_dir = args.data_dir.resolve()
    if not data_dir.is_dir():
        sys.exit(f"ERROR: '{data_dir}' is not a directory")

    splits_dir = data_dir / "splits"
    if splits_dir.exists():
        if args.overwrite:
            shutil.rmtree(splits_dir)
            print(f"Removed existing '{splits_dir}'")
        else:
            sys.exit(
                f"ERROR: '{splits_dir}' already exists.\nUse --overwrite to replace it."
            )

    print(f"Scanning '{data_dir}' ...")
    pairs = detect_layout(data_dir)
    print(f"Found {len(pairs)} valid pairs")

    if len(pairs) < 2:
        sys.exit("ERROR: need at least 2 pairs to create a split")

    random.seed(args.seed)
    random.shuffle(pairs)

    n_total = len(pairs)
    n_eval = round(n_total * args.eval_fraction) if args.eval_fraction > 0 else 0
    n_remaining = n_total - n_eval
    n_test = max(1, round(n_remaining * args.test_fraction))
    n_train = n_remaining - n_test

    if n_train < 1:
        sys.exit("ERROR: not enough pairs for the requested split fractions")

    eval_pairs  = pairs[:n_eval]
    test_pairs  = pairs[n_eval : n_eval + n_test]
    train_pairs = pairs[n_eval + n_test :]

    split_sets: list[tuple[str, list]] = [("train", train_pairs), ("test", test_pairs)]
    if n_eval > 0:
        split_sets.append(("eval", eval_pairs))

    for split_name, split_pairs in split_sets:
        split_dir = splits_dir / split_name
        split_dir.mkdir(parents=True)
        for img, mask in split_pairs:
            link_or_copy(img, split_dir / img.name)
            link_or_copy(mask, split_dir / mask.name)

    summary = f"Train: {n_train} | Test: {n_test}"
    if n_eval > 0:
        summary += f" | Eval: {n_eval}"
    print(f"{summary}  →  {splits_dir}")


if __name__ == "__main__":
    main()
