#!/usr/bin/env python3
"""Split the delivered doll-maker sheet into individual, transparent parts.

The source sheet (`assets/art/character_parts_sheet_green.png`) shows the
assembled character on the top left and every separated part laid out around
it, all drawn on a flat green background.

The parts are separated by background distance, not by a fixed grid, because
several pieces touch each other on the sheet.

Usage
-----
    python3 tools/character_parts/extract_parts.py \
        --sheet assets/art/character_parts_sheet_green.png \
        --out build/character_parts

Requires Pillow, numpy and OpenCV (`pip install pillow numpy opencv-python-headless`).
The output folder holds one RGBA PNG per part plus `inventory.json`.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import cv2
import numpy as np
from PIL import Image

# Flat background colour of the sheet (sampled from its corners).
BACKGROUND = (60.0, 121.0, 50.0)

# (distance below which a pixel is background, width of the alpha ramp).
ALPHA_FLOOR = 12.0
ALPHA_RAMP = 22.0

# Components smaller than this are compression noise / specks.
MIN_AREA = 220


def load_rgb(path: Path) -> np.ndarray:
    return np.array(Image.open(path).convert("RGB"))


def background_alpha(rgb: np.ndarray) -> np.ndarray:
    """Alpha from the colour distance to the flat background."""
    smooth = cv2.bilateralFilter(rgb, 5, 25, 25).astype(np.float32)
    dist = np.linalg.norm(smooth - np.array(BACKGROUND, np.float32), axis=2)
    return np.clip((dist - ALPHA_FLOOR) / ALPHA_RAMP, 0.0, 1.0)


def despill(rgb: np.ndarray, alpha: np.ndarray) -> np.ndarray:
    """Remove the green fringe the background bleeds onto the edges."""
    out = rgb.astype(np.float32).copy()
    r, g, b = out[..., 0], out[..., 1], out[..., 2]
    out[..., 1] = g - 0.9 * np.maximum(0.0, g - np.maximum(r, b) * 1.02)
    return np.clip(out, 0, 255) * alpha[..., None]


def extract(sheet: Path, out_dir: Path) -> list[dict]:
    rgb = load_rgb(sheet)
    alpha = background_alpha(rgb)
    premultiplied = despill(rgb, alpha)

    mask = (alpha > 0.5).astype(np.uint8)
    kernel = np.ones((3, 3), np.uint8)
    mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, kernel)
    mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, kernel)

    count, labels, stats, _ = cv2.connectedComponentsWithStats(mask, 8)

    out_dir.mkdir(parents=True, exist_ok=True)
    inventory: list[dict] = []
    index = 0
    for label in range(1, count):
        x, y, w, h, area = (int(v) for v in stats[label])
        if area < MIN_AREA:
            continue
        part_alpha = np.where(labels == label, alpha, 0.0)
        part_rgb = np.where(part_alpha[..., None] > 0, rgb, 0).astype(np.float32)
        # Re-apply the de-spill inside the part only.
        part_rgb = despill(part_rgb, part_alpha) / np.maximum(part_alpha[..., None], 1e-6)

        rgba = np.dstack([
            np.clip(part_rgb[y:y + h, x:x + w], 0, 255),
            np.clip(part_alpha[y:y + h, x:x + w] * 255, 0, 255),
        ]).astype(np.uint8)
        Image.fromarray(rgba, "RGBA").save(out_dir / f"part_{index:02d}.png")

        inventory.append({"index": index, "sheet_bbox": [x, y, w, h], "area": area,
                          "width": w, "height": h})
        index += 1

    (out_dir / "inventory.json").write_text(
        json.dumps(inventory, indent=1), encoding="utf-8")
    return inventory


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sheet", type=Path,
                        default=Path("assets/art/character_parts_sheet_green.png"))
    parser.add_argument("--out", type=Path, default=Path("build/character_parts"))
    args = parser.parse_args()

    inventory = extract(args.sheet, args.out)
    print(f"{len(inventory)} parts written to {args.out}")
    for item in inventory:
        print(f"  part_{item['index']:02d}  {item['width']:4d}x{item['height']:<4d} "
              f"at {tuple(item['sheet_bbox'][:2])}")


if __name__ == "__main__":
    main()
