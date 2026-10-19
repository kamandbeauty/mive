"""Renders test poses from the extracted layers and the manifest joints.

Mirrors the Dart rig (parent rotation around shared pivots) so joint placement,
overlap and the reconstructed areas can be judged before the Flutter code is
written. Development tool only; not shipped in the app.

Run: ``python3 tools/character_rig/pose_preview.py``
"""

from __future__ import annotations

import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
LAYERS = ROOT / "assets/game/characters/rig"
PREVIEW = ROOT / "build/character_rig_preview"

# layer -> (parent layer or "root", joint used as this layer's pivot)
PARENT = {
    "hair_back": ("head", "head"),
    "face_base": ("head", "head"),
    "hair_front": ("head", "bangs"),
    "lock_left": ("head", "lockLeft"),
    "lock_right": ("head", "lockRight"),
    "torso": ("root", "hip"),
    "apron_tail": ("torso", "apron"),
    "legs": ("root", "hip"),
    "arm_left": ("torso", "shoulderLeft"),
    "basket_handle": ("arm_left", "basket"),
    "basket_bowl": ("arm_left", "basket"),
    "arm_right_up": ("torso", "shoulderRight"),
    "arm_right_fore": ("arm_right_up", "elbowRight"),
}

DRAW = [
    "hair_back", "legs", "torso", "apron_tail",
    "arm_left", "basket_handle", "basket_bowl",
    "arm_right_up", "arm_right_fore",
    "face_base", "eyes", "brows", "mouth",
    "lock_left", "lock_right", "hair_front",
]


class Node:
    def __init__(self, pivot, angle=0.0, translate=(0.0, 0.0), scale=1.0):
        self.pivot = np.array(pivot, dtype=float)
        self.angle = angle
        self.translate = np.array(translate, dtype=float)
        self.scale = scale

    def matrix(self):
        c, s = math.cos(math.radians(self.angle)), math.sin(math.radians(self.angle))
        a = np.array([[c * self.scale, -s * self.scale], [s * self.scale, c * self.scale]])
        b = self.pivot - a @ self.pivot + self.translate
        return np.vstack([np.hstack([a, b[:, None]]), [0, 0, 1]])


def load_layers(manifest):
    canvas_size = tuple(manifest["canvas"])
    layers = {}
    for key, meta in manifest["layers"].items():
        image = Image.open(LAYERS / f"{key}.png").convert("RGBA")
        x, y, _, _ = meta["rect"]
        canvas = Image.new("RGBA", canvas_size, (0, 0, 0, 0))
        canvas.alpha_composite(image, (x, y))
        layers[key] = canvas
    return layers


def warp(image, matrix):
    inverse = np.linalg.inv(matrix)
    coeffs = tuple(inverse[0:2].reshape(-1))
    return image.transform(image.size, Image.AFFINE, coeffs, resample=Image.BICUBIC)


def compose_pose(manifest, layers, nodes, face="open", blink=0.0):
    canvas = Image.new("RGBA", tuple(manifest["canvas"]), (0, 0, 0, 0))
    world = {}

    def matrix_of(name):
        if name in world:
            return world[name]
        if name not in PARENT:
            world[name] = np.eye(3)
            return world[name]
        parent, _ = PARENT[name]
        world[name] = matrix_of(parent) @ nodes.get(name, Node((0, 0))).matrix()
        return world[name]

    for name in DRAW:
        if name == "eyes":
            variant = "closed" if blink > 0.5 else face
            for side in ("left", "right"):
                canvas.alpha_composite(layers[f"eyes_{variant}_{side}"])
        elif name == "brows":
            for side in ("left", "right"):
                canvas.alpha_composite(layers[f"brows_neutral_{side}"])
        elif name == "mouth":
            canvas.alpha_composite(layers["mouth_smile"])
        elif name in layers:
            image = layers[name]
            matrix = matrix_of(name)
            if not np.allclose(matrix, np.eye(3)):
                image = warp(image, matrix)
            canvas.alpha_composite(image)
    return canvas


def main():
    PREVIEW.mkdir(parents=True, exist_ok=True)
    manifest = json.loads((LAYERS / "manifest.json").read_text())
    layers = load_layers(manifest)
    joints = manifest["joints"]

    poses = {
        # Keep this list inside manifest poseLimits and inside the visually
        # safe envelope: the basket arm never rotates (the basket is rigidly
        # parented to it), the free arm stays under ~24 degrees.
        "a_idle": {},
        "b_head_tilt": {"head": Node(joints["head"], angle=-6)},
        "c_breathe": {"torso": Node(joints["hip"], scale=1.015, translate=(0, -3)),
                      "head": Node(joints["head"], translate=(0, -2))},
        "d_happy_arm": {"arm_right_up": Node(joints["shoulderRight"], angle=-11),
                        "arm_right_fore": Node(joints["elbowRight"], angle=-6),
                        "head": Node(joints["head"], angle=-2.6)},
        "e_excited": {"arm_right_up": Node(joints["shoulderRight"], angle=-17),
                      "arm_right_fore": Node(joints["elbowRight"], angle=-10),
                      "torso": Node(joints["hip"], translate=(0, -9)),
                      "head": Node(joints["head"], angle=-4.5)},
        "f_celebrate": {"arm_right_up": Node(joints["shoulderRight"], angle=-22),
                        "arm_right_fore": Node(joints["elbowRight"], angle=-14),
                        "torso": Node(joints["hip"], translate=(0, -15)),
                        "head": Node(joints["head"], angle=-5.5)},
        "g_sad": {"head": Node(joints["head"], angle=7, translate=(0, 4)),
                  "torso": Node(joints["hip"], scale=0.985)},
        "h_harvest": {"arm_right_up": Node(joints["shoulderRight"], angle=-8),
                      "arm_right_fore": Node(joints["elbowRight"], angle=-6),
                      "head": Node(joints["head"], angle=-4)},
        "i_blink": {"head": Node(joints["head"], angle=-2)},
    }

    base_nodes = {name: Node(joints[joint]) for name, (_, joint) in PARENT.items()}
    tiles = []
    for label, overrides in poses.items():
        local = {k: Node(v.pivot, v.angle, v.translate, v.scale) for k, v in base_nodes.items()}
        local.update(overrides)
        blink = 1.0 if label == "i_blink" else 0.0
        pose = compose_pose(manifest, layers, local, blink=blink)
        plate = Image.new("RGBA", pose.size, (240, 236, 228, 255))
        plate.alpha_composite(pose)
        ImageDraw.Draw(plate).text((8, 8), label, fill=(30, 30, 30, 255))
        tiles.append(plate.convert("RGB").resize((256, 384), Image.LANCZOS))

    columns = 5
    rows = (len(tiles) + columns - 1) // columns
    sheet = Image.new("RGB", (256 * columns, 384 * rows), (250, 250, 250))
    for index, tile in enumerate(tiles):
        sheet.paste(tile, ((index % columns) * 256, (index // columns) * 384))
    sheet.save(PREVIEW / "poses.png")
    print("poses written:", len(tiles))


if __name__ == "__main__":
    main()
