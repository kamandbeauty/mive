#!/usr/bin/env python3
"""Rebuild the reference pose out of the extracted parts.

This is a *validation* tool, not a runtime rig: it proves that the parts
delivered on the doll-maker sheet are a complete decomposition of the
character and that they can be composited back into the shipped idle pose.
The transforms live in `parts_manifest.json`.

Usage
-----
    python3 tools/character_parts/extract_parts.py --out build/character_parts
    python3 tools/character_parts/assemble_parts.py \
        --parts build/character_parts --out build/character_parts_assembly.png
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

MANIFEST = Path(__file__).with_name("parts_manifest.json")
CLEAN_FACE = {"04", "05", "06", "07", "11", "12"}


def load_manifest() -> dict:
    return json.loads(MANIFEST.read_text(encoding="utf-8"))


def load_part(parts_dir: Path, key: str) -> tuple[np.ndarray, np.ndarray]:
    rgba = np.array(Image.open(parts_dir / f"part_{key}.png").convert("RGBA"),
                    dtype=np.float32)
    rgb, alpha = rgba[..., :3], rgba[..., 3] / 255.0
    if key in CLEAN_FACE:
        # The sheet's flat green background leaves a one-pixel fringe on the
        # small face parts; drop it and keep the largest blob only.
        import cv2
        r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
        green = (g > r + 8) & (g > b + 8)
        alpha = np.where(green, 0.0, alpha)
        mask = (alpha > 0.35).astype(np.uint8)
        count, labels, stats, _ = cv2.connectedComponentsWithStats(mask, 8)
        if count > 1:
            keep = 1 + int(np.argmax(stats[1:, 4]))
            alpha = np.where(labels == keep, alpha, 0.0)
    return rgb, alpha[..., None]


def render(parts_dir: Path, manifest: dict) -> Image.Image:
    width, height = manifest["canvas"]
    canvas = np.zeros((height, width, 3), np.float32)
    alpha = np.zeros((height, width, 1), np.float32)

    for key in manifest["stacking_order"]:
        scale, dx, dy = manifest["transforms"][key]
        rgb, a = load_part(parts_dir, key)
        w, h = max(2, round(a.shape[1] * scale)), max(2, round(a.shape[0] * scale))
        rgb = np.array(Image.fromarray(rgb.astype(np.uint8)).resize((w, h), Image.LANCZOS),
                       dtype=np.float32)
        a = np.array(Image.fromarray(a[..., 0]).resize((w, h), Image.LANCZOS),
                     dtype=np.float32)[..., None]

        x0, y0 = int(round(dx)), int(round(dy))
        x1, y1 = x0 + w, y0 + h
        sx0, sy0 = max(0, -x0), max(0, -y0)
        x0c, y0c = max(0, x0), max(0, y0)
        x1c, y1c = min(width, x1), min(height, y1)
        if x1c <= x0c or y1c <= y0c:
            continue
        src_rgb = rgb[sy0:sy0 + (y1c - y0c), sx0:sx0 + (x1c - x0c)]
        src_a = a[sy0:sy0 + (y1c - y0c), sx0:sx0 + (x1c - x0c)]
        dst = canvas[y0c:y1c, x0c:x1c]
        dst[:] = src_rgb * src_a + dst * (1 - src_a)
        dst_a = alpha[y0c:y1c, x0c:x1c]
        dst_a[:] = src_a + dst_a * (1 - src_a)

    rgba = np.dstack([np.clip(canvas, 0, 255), np.clip(alpha * 255, 0, 255)])
    return Image.fromarray(rgba.astype(np.uint8), "RGBA")


def comparison_sheet(parts_dir: Path, manifest: dict, assembly: Image.Image) -> Image.Image:
    width, height = manifest["canvas"]
    reference = Image.open(parts_dir / "part_00.png").convert("RGBA")
    left = Image.new("RGBA", (width, height), (255, 255, 255, 255))
    left.alpha_composite(reference)
    right = Image.new("RGBA", (width, height), (255, 255, 255, 255))
    right.alpha_composite(assembly)
    sheet = Image.new("RGB", (width * 2 + 24, height), (235, 235, 230))
    sheet.paste(left.convert("RGB"), (0, 0))
    sheet.paste(right.convert("RGB"), (width + 24, 0))
    draw = ImageDraw.Draw(sheet)
    draw.text((6, 6), "reference", fill=(150, 20, 20))
    draw.text((width + 30, 6), "assembled from parts", fill=(20, 110, 40))
    return sheet


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--parts", type=Path, default=Path("build/character_parts"))
    parser.add_argument("--out", type=Path, default=Path("build/character_parts_assembly.png"))
    args = parser.parse_args()

    manifest = load_manifest()
    assembly = render(args.parts, manifest)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    assembly.save(args.out.with_name(args.out.stem + "_alpha.png"))
    comparison_sheet(args.parts, manifest, assembly).save(args.out)
    print(f"wrote {args.out} and {args.out.with_name(args.out.stem + '_alpha.png')}")


if __name__ == "__main__":
    main()
