"""
Fine-tune CellPose-SAM on custom annotated data.

Usage:
    python train_cpsam.py --model_name my_model
    python train_cpsam.py --model_name my_model --n_epochs 600 --no_gpu

Run from the training_template directory (or any copy of it). The trained model is
saved to models/<model_name> relative to the current working directory.

Input data must be pairs of *_img.tif and *_mask.tif files. Run split_data.py first
to generate the train/test split from your annotated images.

Both 2D and 3D (multi-page TIFF stack) inputs are supported — cellpose auto-detects
dimensionality from the arrays. For inference on 3D data, use do_3D=True in
CellposeModel.eval().
"""

import argparse
import csv
from pathlib import Path

try:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    _HAS_MPL = True
except ImportError:
    _HAS_MPL = False

from cellpose import io, models, train


def save_outputs(model_name: str, train_losses: list[float], test_losses: list[float]) -> None:
    out_dir = Path("models")
    out_dir.mkdir(exist_ok=True)

    csv_path = out_dir / f"{model_name}_losses.csv"
    with open(csv_path, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["epoch", "train_loss", "test_loss"])
        for i, (tl, vl) in enumerate(zip(train_losses, test_losses), 1):
            writer.writerow([i, tl, vl])
    print(f"Losses  → {csv_path}")

    if _HAS_MPL:
        fig, ax = plt.subplots()
        ax.plot(train_losses, label="train")
        ax.plot(test_losses, label="test")
        ax.set_xlabel("Epoch")
        ax.set_ylabel("Loss")
        ax.legend()
        fig.tight_layout()
        png_path = out_dir / f"{model_name}_loss.png"
        fig.savefig(png_path, dpi=150)
        plt.close(fig)
        print(f"Loss plot → {png_path}")
    else:
        print("Loss plot skipped (matplotlib not available — install it to enable)")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Fine-tune CellPose-SAM on custom annotated data.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("--model_name", required=True, help="Name for the saved model")
    parser.add_argument(
        "--train_dir", default="data/splits/train",
        help="Directory of training images/masks",
    )
    parser.add_argument(
        "--test_dir", default="data/splits/test",
        help="Directory of test images/masks",
    )
    parser.add_argument("--n_epochs", type=int, default=200)
    parser.add_argument("--learning_rate", type=float, default=1e-5)
    parser.add_argument("--weight_decay", type=float, default=0.1)
    parser.add_argument(
        "--save_every", type=int, default=100,
        help="Save a model checkpoint every N epochs",
    )
    parser.add_argument("--no_gpu", action="store_true", help="Use CPU instead of GPU")
    args = parser.parse_args()

    io.logger_setup()

    output = io.load_train_test_data(
        args.train_dir, args.test_dir,
        image_filter="_img", mask_filter="_mask",
    )
    images, labels, _, test_images, test_labels, _ = output

    model = models.CellposeModel(gpu=not args.no_gpu)

    model_path, train_losses, test_losses = train.train_seg(
        model.net,
        train_data=images,
        train_labels=labels,
        test_data=test_images,
        test_labels=test_labels,
        weight_decay=args.weight_decay,
        learning_rate=args.learning_rate,
        n_epochs=args.n_epochs,
        save_every=args.save_every,
        model_name=args.model_name,
    )

    print(f"Model   → {model_path}")
    save_outputs(args.model_name, train_losses, test_losses)


if __name__ == "__main__":
    main()
