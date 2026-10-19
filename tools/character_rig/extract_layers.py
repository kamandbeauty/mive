"""Builds the layered rig for the orchard girl character.

Input: the eight flat 512x768 RGBA renders in ``assets/game/characters``.
Output: tightly cropped PNG layers in ``assets/game/characters/rig`` plus
``manifest.json`` (rects, pivots, drawing order, face variants).

Every layer is cut from the *same* pixels as the source sheets, so the rest
pose is pixel-identical to the original art. Only areas that another part
covers are reconstructed, and only inside layers that sit behind that part.

Run: ``python3 tools/character_rig/extract_layers.py``
Needs: pillow, numpy, opencv-python-headless.
"""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(Path(__file__).parent))
import geometry as G  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / "assets/game/characters"
OUT = ART / "rig"
PREVIEW = ROOT / "build/character_rig_preview"
W, H = G.CANVAS_WIDTH, G.CANVAS_HEIGHT

SHEETS = {
    "idle": "orchard_girl_idle.png",
    "blink": "orchard_girl_blink.png",
    "happy": "orchard_girl_happy.png",
    "excited": "orchard_girl_excited.png",
    "encourage": "orchard_girl_encourage.png",
    "sad": "orchard_girl_sad.png",
    "celebrate": "orchard_girl_celebrate.png",
    "harvest": "orchard_girl_harvest.png",
}

# ---------------------------------------------------------------- helpers ---


def load(name: str) -> np.ndarray:
    return np.asarray(Image.open(ART / SHEETS[name]).convert("RGBA")).astype(np.float32)


def hsv_of(rgba: np.ndarray):
    hsv = np.asarray(Image.fromarray(rgba.astype(np.uint8)).convert("HSV")).astype(np.float32)
    return hsv[..., 0] * 360 / 255, hsv[..., 1] / 255, hsv[..., 2] / 255


def poly(points, blur=1.0) -> np.ndarray:
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).polygon(points, fill=255)
    if blur:
        mask = mask.filter(ImageFilter.GaussianBlur(blur))
    return np.asarray(mask).astype(np.float32) / 255


def ellipse(box, blur=2.0) -> np.ndarray:
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).ellipse(box, fill=255)
    if blur:
        mask = mask.filter(ImageFilter.GaussianBlur(blur))
    return np.asarray(mask).astype(np.float32) / 255


def smooth(mask: np.ndarray, radius=1.0, close=3) -> np.ndarray:
    binary = (np.clip(mask, 0, 1) * 255).astype(np.uint8)
    if close:
        binary = cv2.morphologyEx(binary, cv2.MORPH_CLOSE, np.ones((close, close), np.uint8))
    binary = cv2.GaussianBlur(binary, (0, 0), radius)
    return np.clip(binary.astype(np.float32) / 255, 0, 1)


def fill_holes(mask: np.ndarray) -> np.ndarray:
    binary = (np.clip(mask, 0, 1) > 0.5).astype(np.uint8)
    padded = np.zeros((binary.shape[0] + 2, binary.shape[1] + 2), np.uint8)
    flood = binary.copy()
    cv2.floodFill(flood, padded, (0, 0), 1)
    out = binary.copy()
    out[flood == 0] = 1
    return out.astype(np.float32)


def cut(rgba: np.ndarray, mask: np.ndarray) -> np.ndarray:
    out = rgba.copy()
    out[..., 3] = out[..., 3] * np.clip(mask, 0, 1)
    return out


def trim(rgba: np.ndarray):
    alpha = rgba[..., 3]
    ys, xs = np.nonzero(alpha > 3)
    if len(xs) == 0:
        raise ValueError("empty layer")
    x0, y0, x1, y1 = int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1
    return rgba[y0:y1, x0:x1].copy(), (x0, y0, x1 - x0, y1 - y0)


def nearest_fill(rgba: np.ndarray, inside: np.ndarray, known: np.ndarray) -> np.ndarray:
    """Paints unknown pixels inside ``inside`` with the nearest known colour."""
    out = rgba.copy()
    inside = np.clip(inside, 0, 1) > 0.02
    known = known & inside
    if not inside.any() or not known.any():
        return out
    _, labels = cv2.distanceTransformWithLabels(
        (~known).astype(np.uint8), cv2.DIST_L2, 5, labelType=cv2.DIST_LABEL_PIXEL
    )
    zero_y, zero_x = np.nonzero(known)
    order = np.lexsort((zero_x, zero_y))
    lookup = {rank + 1: (zero_y[index], zero_x[index]) for rank, index in enumerate(order)}
    ys, xs = np.nonzero(~known & inside)
    for y, x in zip(ys, xs):
        src = lookup.get(int(labels[y, x]))
        if src is None:
            continue
        sy, sx = src
        out[y, x, :3] = rgba[sy, sx, :3]
        out[y, x, 3] = 255.0
    return out


def inpaint_fill(rgba: np.ndarray, inside: np.ndarray, known: np.ndarray,
                 radius: float = 10.0) -> np.ndarray:
    """Smoothly reconstructs occluded body pixels from their surroundings.

    A nearest-pixel fill copies the covering limb's outline into the hole, so
    the torso would keep a ghost arm. Diffusion (Telea) instead continues the
    garment colours across the gap, which is what a rotating arm reveals.
    """
    out = rgba.copy()
    inside = np.clip(inside, 0, 1) > 0.02
    known = known & inside
    hole = inside & ~known
    if not hole.any() or not known.any():
        return out
    source = np.clip(rgba[..., :3], 0, 255).astype(np.uint8)
    mask = (~known).astype(np.uint8) * 255
    filled = cv2.inpaint(source, mask, radius, cv2.INPAINT_TELEA)
    out[..., :3][hole] = filled[hole].astype(np.float32)
    out[..., 3][hole] = 255
    return out


def horizontal_fill(rgba: np.ndarray, inside: np.ndarray, known: np.ndarray,
                    center_x: float = 196.0) -> np.ndarray:
    """Paints unknown pixels from the nearest known pixel in the same row.

    Used for the body plate: the space under an arm must continue the clothes,
    not smear the arm's outline inwards. Each hole pixel copies from whichever
    side of the row is closer to the body's centre line.
    """
    out = rgba.copy()
    inside = np.clip(inside, 0, 1) > 0.02
    known = known & inside
    ys = np.unique(np.nonzero(inside & ~known)[0])
    for y in ys:
        row_known = np.nonzero(known[y])[0]
        if row_known.size == 0:
            continue
        for x in np.nonzero(inside[y] & ~known[y])[0]:
            left = row_known[row_known < x]
            right = row_known[row_known > x]
            candidates = []
            if left.size:
                candidates.append(int(left[-1]))
            if right.size:
                candidates.append(int(right[0]))
            if not candidates:
                continue
            src = min(candidates, key=lambda column: abs(column - center_x))
            out[y, x, :3] = rgba[y, src, :3]
            out[y, x, 3] = 255
    return out


def row_fill(rgba: np.ndarray, inside: np.ndarray, sample: np.ndarray) -> np.ndarray:
    """Fills unknown pixels with the per-row median colour of the samples.

    Used for skin under the hair: a soft, correctly toned gradient instead of
    a Voronoi patchwork of single pixels.
    """
    out = rgba.copy()
    known = inside & sample
    for y in range(H):
        row_known = known[y]
        if not row_known.any():
            continue
        row_fill_mask = inside[y] & ~known[y]
        if not row_fill_mask.any():
            continue
        colour = np.median(rgba[y, row_known, :3], axis=0)
        out[y, row_fill_mask, :3] = colour
        out[y, row_fill_mask, 3] = 255
    return out


def plate(source: np.ndarray, polygon: np.ndarray, known: np.ndarray,
          sample: np.ndarray | None = None, restrict: np.ndarray | None = None,
          fill=nearest_fill, close_size: int = 0,
          grow_under: np.ndarray | None = None) -> np.ndarray:
    """Layer with its real art kept as-is and reconstructed hidden pixels.

    ``known`` marks the real pixels of this layer; ``sample`` marks the known
    pixels whose colour may be reused for the reconstructed ones (for a face
    that is skin only, so hair never bleeds into the cheek).
    """
    poly_mask = np.clip(polygon, 0, 1)
    inside = (poly_mask > 0.02) & (source[..., 3] > 128)
    if restrict is not None:
        # parts that are drawn on top (hair over a collar, etc.) belong to
        # their own layer; a plate never invents pixels underneath them
        inside &= np.clip(restrict, 0, 1) > 0.5
    if close_size:
        # The polygon is a rough body outline. Closing the silhouette bridges
        # the pixels a limb covers without letting the plate grow into the
        # background where that limb sticks out of the body.
        solid = inside.astype(np.uint8)
        solid = cv2.morphologyEx(solid, cv2.MORPH_CLOSE,
                                 np.ones((close_size, close_size), np.uint8))
        grown = solid.astype(bool)
        if grow_under is not None:
            # only bridge gaps that a limb actually covers; never bulge into
            # open background
            grown &= np.clip(grow_under, 0, 1) > 0.02
            inside = inside | grown
        else:
            inside = grown
    layer = source.copy()
    layer[..., 3] = source[..., 3] * poly_mask
    filled = fill(layer, inside, (sample if sample is not None else known)
                  & (poly_mask > 0.02))
    out = layer.copy()
    fill_mask = inside & ~known
    out[fill_mask] = filled[fill_mask]
    out[..., 3] *= poly_mask
    return out


def clip_sum(*masks) -> np.ndarray:
    total = np.zeros((H, W), np.float32)
    for mask in masks:
        total = np.clip(total + np.asarray(mask, np.float32), 0, 1)
    return total


def split_at_elbow(rgba: np.ndarray, elbow, wrist):
    """Splits an arm at the elbow; the forearm keeps a disc on the joint."""
    ex, ey = elbow
    wx, wy = wrist
    angle = math.atan2(wy - ey, wx - ex)
    ys, xs = np.mgrid[0:H, 0:W]
    along = (xs - ex) * math.cos(angle) + (ys - ey) * math.sin(angle)
    radial = np.hypot(xs - ex, ys - ey)
    fore_cut = ((along > 0) | (radial < 26)).astype(np.float32)
    upper = cut(rgba, 1.0 - fore_cut)
    fore = cut(rgba, fore_cut)
    return upper, fore


class Builder:
    def __init__(self):
        self.layers = {}
        self.manifest = {
            "canvas": [W, H],
            "joints": G.JOINTS,
            "layers": self.layers,
            "order": G.LAYER_ORDER,
            "moodFace": G.MOOD_FACE,
            "variants": G.FACE_VARIANTS,
            "poseLimits": G.POSE_LIMITS,
        }

    def add(self, name, rgba, group):
        cropped, rect = trim(rgba)
        OUT.mkdir(parents=True, exist_ok=True)
        Image.fromarray(np.clip(cropped, 0, 255).astype(np.uint8), "RGBA").save(
            OUT / f"{name}.png", optimize=True
        )
        self.layers[name] = {"rect": list(rect), "group": group}


# ----------------------------------------------------------------- build ---


def build_layers() -> Builder:
    idle = load("idle")
    A = idle[..., 3] / 255.0
    Hh, Ss, Vv = hsv_of(idle)
    red, green, blue = idle[..., 0], idle[..., 1], idle[..., 2]
    opaque = A > 0.5
    builder = Builder()
    add = builder.add

    # colour classes (used only to pick good sample pixels)
    hair_cls = (Vv < 0.62) & (Ss > 0.30) & (Hh < 45) & opaque
    skin_cls = (Hh >= 16) & (Hh <= 30) & (Ss > 0.35) & (Ss < 0.62) & (Vv > 0.72) & opaque
    yellow_cls = (Hh >= 28) & (Hh <= 46) & (Ss > 0.6) & (Vv > 0.6) & opaque
    salmon_cls = (Hh >= 6) & (Hh < 18) & (Ss > 0.45) & (Vv > 0.55) & opaque
    pants_cls = (Hh > 100) & (Hh < 180) & (Ss > 0.15) & (Vv < 0.7) & opaque

    head_zone = poly(G.HEAD_ZONE, 0.5)
    # (no hole filling here: the face is enclosed by the hair ring and must
    # stay outside the hair mask)
    hair = smooth(hair_cls.astype(np.float32), 0.8, 5) * head_zone

    # --- head -------------------------------------------------------------
    hair_back = cut(idle, head_zone * A)

    # The face is everything inside the head zone that is neither hair nor
    # clothing. Hole filling then includes the eyes/brows/mouth, which are
    # redrawn from their own layers, so the plate edge sits exactly on the
    # original anti-aliased face/hair boundary and stays pixel-identical.
    teal_cls = (Hh > 100) & (Hh < 180) & (Ss > 0.12) & opaque
    yellow_cls = (Hh >= 26) & (Hh <= 50) & (Ss > 0.5) & (Vv > 0.55) & opaque
    face_mask = head_zone * np.clip(opaque.astype(np.float32) - hair, 0, 1)
    face_mask *= np.clip(1.0 - teal_cls.astype(np.float32) - yellow_cls.astype(np.float32), 0, 1)
    face_mask *= np.clip(poly(G.FACE_ZONE, 0) + poly(G.NECK, 0), 0, 1)
    face_mask = fill_holes((face_mask > 0.5).astype(np.float32))
    features = np.clip(
        ellipse(G.EYE_LEFT, 1.0) + ellipse(G.EYE_RIGHT, 1.0)
        + ellipse(G.BROW_LEFT, 0.8) + ellipse(G.BROW_RIGHT, 0.8)
        + ellipse(G.MOUTH, 1.2), 0, 1)
    face_known = (face_mask > 0.5) & (features < 0.05)
    face_base = plate(idle, face_mask, face_known, sample=skin_cls & face_known)

    hair_front = cut(idle, np.clip(hair * poly(G.FRONT_HAIR, 0.8), 0, 1))
    lock_left = cut(idle, np.clip(hair * poly(G.LOCK_LEFT, 0.8), 0, 1))
    lock_right = cut(idle, np.clip(hair * poly(G.LOCK_RIGHT, 0.8), 0, 1))

    # --- arms -------------------------------------------------------------
    basket_mask = clip_sum(poly(G.BASKET_BOWL, 0.6), poly(G.BASKET_HANDLE_POLY, 0.4))
    # The teal bib/straps are drawn over the sleeves and the basket is drawn
    # over the forearm, so both are punched out of the arm layer. The basket is
    # parented rigidly to the arm, so its own layers always cover that cut.
    teal = (Hh > 100) & (Hh < 180) & (Ss > 0.12) & opaque
    arm_left_mask = (poly(G.ARM_LEFT, 0.4)
                     * np.clip(1.0 - teal.astype(np.float32), 0, 1)
                     * np.clip(1.0 - basket_mask, 0, 1))
    arm_right_mask = (poly(G.ARM_RIGHT, 0.4)
                      * np.clip(1.0 - teal.astype(np.float32), 0, 1))
    arm_left = cut(idle, arm_left_mask * A)
    arm_right = cut(idle, arm_right_mask * A)
    arm_right_up, arm_right_fore = split_at_elbow(arm_right, G.RIGHT_ELBOW, G.RIGHT_WRIST)

    # --- body plates ------------------------------------------------------
    basket_cover = clip_sum(poly(G.BASKET_BOWL, 1.0), poly(G.BASKET_HANDLE_POLY, 0.8))

    # Both arms hang in front of the torso and the apron. Their pixels may not
    # be baked into those plates, and the dilated mask keeps the anti-aliased
    # skin rim from being sampled as fabric.
    arm_pixels = (arm_left[..., 3] > 0) | (arm_right[..., 3] > 0)
    arm_cover = cv2.dilate(
        arm_pixels.astype(np.uint8), np.ones((5, 5), np.uint8)
    ).astype(bool)

    torso_poly = poly(G.TORSO, 1.0)
    torso_known = opaque & (arm_left[..., 3] < 10) & (arm_right[..., 3] < 10) & (hair < 0.15)
    torso = plate(idle, torso_poly, torso_known, restrict=1.0 - hair,
                  fill=inpaint_fill, close_size=41, grow_under=arm_pixels)

    apron_poly = poly(G.APRON_TAIL, 1.2)
    apron_known = (opaque & ~arm_cover & (basket_cover < 0.05)
                   & (apron_poly > 0.4))
    apron = plate(idle, apron_poly, apron_known, fill=nearest_fill,
                  close_size=41,
                  grow_under=arm_cover | (basket_cover > 0.5))

    legs_poly = poly(G.LEGS, 1.0)
    legs_known = (opaque & (apron_poly < 0.3) & (legs_poly > 0.4)
                  & ~arm_cover)
    legs = plate(idle, legs_poly, legs_known)

    # --- basket -----------------------------------------------------------
    basket_bowl = cut(idle, poly(G.BASKET_BOWL, 0.9) * A)
    basket_handle = cut(idle, poly(G.BASKET_HANDLE_POLY, 0.7) * A)

    add("hair_back", hair_back, "hair")
    add("face_base", face_base, "face")
    add("hair_front", hair_front, "hair")
    add("lock_left", lock_left, "hair")
    add("lock_right", lock_right, "hair")
    add("torso", torso, "body")
    add("apron_tail", apron, "body")
    add("legs", legs, "body")
    add("basket_bowl", basket_bowl, "body")
    add("basket_handle", basket_handle, "body")
    add("arm_left", arm_left, "body")
    add("arm_right_up", arm_right_up, "body")
    add("arm_right_fore", arm_right_fore, "body")

    # --- face variants ----------------------------------------------------
    for variant, sheet in G.FACE_VARIANTS["eyes"].items():
        rgba = load(sheet)
        for side, box in (("left", G.EYE_LEFT), ("right", G.EYE_RIGHT)):
            add(f"eyes_{variant}_{side}", cut(rgba, ellipse(box, 2.2)), "face:eyes")
    for variant, sheet in G.FACE_VARIANTS["brows"].items():
        rgba = load(sheet)
        for side, box in (("left", G.BROW_LEFT), ("right", G.BROW_RIGHT)):
            add(f"brows_{variant}_{side}", cut(rgba, ellipse(box, 1.8)), "face:brows")
    for variant, sheet in G.FACE_VARIANTS["mouth"].items():
        add(f"mouth_{variant}", cut(load(sheet), ellipse(G.MOUTH, 2.6)), "face:mouth")

    return builder


# ---------------------------------------------------------------- preview ---


def place(manifest, key):
    layer = Image.open(OUT / f"{key}.png").convert("RGBA")
    x, y, _, _ = manifest["layers"][key]["rect"]
    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    canvas.alpha_composite(layer, (x, y))
    return canvas


def compose(manifest, face=None, blink=0.0):
    """Mirrors the Dart renderer: the lid is clipped to the eye socket and
    slides up out of it as ``blink`` goes 0 -> 1."""
    face = face or G.MOOD_FACE["idle"]
    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    for name in G.LAYER_ORDER:
        if name == "eyes":
            for side in ("left", "right"):
                canvas.alpha_composite(place(manifest, f"eyes_{face['eyes']}_{side}"))
        elif name == "brows":
            for side in ("left", "right"):
                canvas.alpha_composite(place(manifest, f"brows_{face['brows']}_{side}"))
        elif name == "mouth":
            canvas.alpha_composite(place(manifest, f"mouth_{face['mouth']}"))
        else:
            canvas.alpha_composite(place(manifest, name))
    return canvas


def preview(manifest):
    PREVIEW.mkdir(parents=True, exist_ok=True)
    for label, color in (("light", (247, 243, 235, 255)), ("dark", (34, 30, 26, 255)),
                         ("green", (110, 150, 96, 255))):
        plate_bg = Image.new("RGBA", (W, H), color)
        plate_bg.alpha_composite(compose(manifest))
        plate_bg.convert("RGB").save(PREVIEW / f"composite_{label}.png")

    composed = compose(manifest)
    composed.save(PREVIEW / "composite_idle.png")

    face = dict(G.MOOD_FACE["idle"])
    face["eyes"] = "closed"
    compose(manifest, face=face).save(PREVIEW / "composite_blink.png")
    for mood, face in G.MOOD_FACE.items():
        compose(manifest, face=face).save(PREVIEW / f"face_{mood}.png")

    keys = list(manifest["layers"])
    tiles = []
    for key in keys:
        tile = Image.new("RGBA", (150, 150), (52, 52, 60, 255))
        draw = ImageDraw.Draw(tile)
        for cy in range(0, 150, 15):
            for cx in range(0, 150, 15):
                if (cx // 15 + cy // 15) % 2:
                    draw.rectangle([cx, cy, cx + 15, cy + 15], fill=(84, 84, 94, 255))
        art = Image.open(OUT / f"{key}.png").convert("RGBA")
        art.thumbnail((136, 136), Image.LANCZOS)
        tile.alpha_composite(art, ((150 - art.width) // 2, (150 - art.height) // 2 + 8))
        ImageDraw.Draw(tile).text((4, 4), key[:26], fill=(255, 255, 140, 255))
        tiles.append(tile)
    columns = 8
    rows = (len(tiles) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * 150, rows * 150), (18, 18, 22))
    for index, tile in enumerate(tiles):
        sheet.paste(tile.convert("RGB"), ((index % columns) * 150, (index // columns) * 150))
    sheet.save(PREVIEW / "layers_sheet.png")


def main():
    builder = build_layers()
    (OUT / "manifest.json").write_text(json.dumps(builder.manifest, ensure_ascii=False, indent=1))
    preview(builder.manifest)
    print(f"wrote {len(builder.layers)} layers")


if __name__ == "__main__":
    main()
