#!/usr/bin/env python3
"""Package the delivered guide-rig art into a jointed, mobile-friendly rig.

Nothing here repaints or re-renders the character: every output pixel is a copy
of the delivered 1152x1152 layer art (crop only), plus small "joint socket"
images built from the same pixels so a rotating limb never opens a hole.

Outputs
  assets/characters/rig/layers/*.png   cropped source layers (lossless)
  assets/characters/rig/sockets/*.png  joint fill discs (parent ∪ child pixels)
  assets/characters/rig/manifest.json  rects, pivots, z-order, face sets
  assets/characters/rig/report.json    verification metrics

Run:  python3 tools/character_rig/build_rig_assets.py
"""
from __future__ import annotations

import glob
import hashlib
import json
import os
from dataclasses import dataclass, field

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "assets/characters/guide-rig")
EXPR = os.path.join(ROOT, "assets/characters/expressions")
OUT = os.path.join(ROOT, "assets/characters/rig")
CANVAS = 1152

PAD = 2  # transparent margin kept around every crop


def load_rgba(path: str) -> Image.Image:
    return Image.open(path).convert("RGBA")


def alpha_mask(img: Image.Image, threshold: int = 8) -> np.ndarray:
    return np.array(img)[..., 3] > threshold


def bbox_of(mask: np.ndarray) -> tuple[int, int, int, int]:
    ys, xs = np.nonzero(mask)
    return int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1


# --------------------------------------------------------------------------
# rig definition: source layer -> bone, pivot, draw order
# --------------------------------------------------------------------------


@dataclass
class LayerSpec:
    id: str
    source: str
    bone: str
    z: float
    pivot: str | None = None  # joints the bone rotates about
    note: str = ""


@dataclass
class BoneSpec:
    id: str
    parent: str | None
    pivot: tuple[float, float]
    label: str
    extra: dict = field(default_factory=dict)


JOINTS = {
    # joint id -> (x, y)
    "root": (566.0, 1088.0),
    "hip": (566.0, 745.0),
    "waist": (573.0, 660.0),
    "neck": (569.0, 509.0),
    "crown": (566.0, 150.0),
    "shoulderR": (672.0, 470.0),
    "elbowR": (756.0, 418.0),
    "wristR": (791.9, 355.1),
    "shoulderL": (441.0, 502.0),
    "elbowL": (392.5, 591.0),
    "wristL": (495.1, 653.8),
    "grip": (474.8, 656.1),
    "hipL": (500.0, 748.0),
    "hipR": (636.0, 748.0),
    "kneeL": (484.8, 908.0),
    "kneeR": (612.0, 907.9),
    "ankleL": (473.6, 995.8),
    "ankleR": (622.3, 990.8),
}

BONES = {
    "root": BoneSpec("root", None, JOINTS["root"], "feet"),
    "body": BoneSpec("body", "root", JOINTS["waist"], "waist / torso lean"),
    "skirt": BoneSpec("skirt", "root", JOINTS["waist"], "skirt follows the waist"),
    "bow": BoneSpec("bow", "root", JOINTS["waist"], "apron bow"),
    "legL": BoneSpec("legL", "root", JOINTS["hipL"], "left thigh"),
    "legR": BoneSpec("legR", "root", JOINTS["hipR"], "right thigh"),
    "calfL": BoneSpec("calfL", "legL", JOINTS["kneeL"], "left knee"),
    "calfR": BoneSpec("calfR", "legR", JOINTS["kneeR"], "right knee"),
    "shoeL": BoneSpec("shoeL", "calfL", JOINTS["ankleL"], "left ankle"),
    "shoeR": BoneSpec("shoeR", "calfR", JOINTS["ankleR"], "right ankle"),
    "neck": BoneSpec("neck", "body", JOINTS["neck"], "head + neck turn"),
    "hairBack": BoneSpec("hairBack", "neck", JOINTS["crown"], "back hair sway"),
    "hairFrontL": BoneSpec("hairFrontL", "neck", JOINTS["crown"], "front curl left"),
    "hairFrontR": BoneSpec("hairFrontR", "neck", JOINTS["crown"], "front curl right"),
    "clip": BoneSpec("clip", "hairFrontR", JOINTS["crown"], "strawberry clip"),
    "faceEyes": BoneSpec("faceEyes", "neck", JOINTS["neck"], "eye plate (blink swap)"),
    "faceBrows": BoneSpec("faceBrows", "neck", JOINTS["neck"], "brows"),
    "faceMouth": BoneSpec("faceMouth", "neck", JOINTS["neck"], "mouth"),
    "faceDetails": BoneSpec("faceDetails", "neck", JOINTS["neck"], "blush / tears"),
    "nose": BoneSpec("nose", "neck", JOINTS["neck"], "nose"),
    "armRUpper": BoneSpec("armRUpper", "body", JOINTS["shoulderR"], "right shoulder"),
    "armRFore": BoneSpec("armRFore", "armRUpper", JOINTS["elbowR"], "right elbow"),
    "armRHand": BoneSpec("armRHand", "armRFore", JOINTS["wristR"], "right wrist"),
    "armLUpper": BoneSpec("armLUpper", "body", JOINTS["shoulderL"], "left shoulder"),
    "armLFore": BoneSpec("armLFore", "armLUpper", JOINTS["elbowL"], "left elbow"),
    "armLHand": BoneSpec("armLHand", "armLFore", JOINTS["wristL"], "left wrist"),
    "basket": BoneSpec("basket", "armLHand", JOINTS["grip"], "basket pendulum"),
}

FACE_LAYERS = [
    LayerSpec("brow-left", "15_eyebrow-left", "faceBrows", 30.2),
    LayerSpec("brow-right", "16_eyebrow-right", "faceBrows", 30.3),
    LayerSpec("eye-left", "17_eye-left", "faceEyes", 30.4),
    LayerSpec("eye-right", "18_eye-right", "faceEyes", 30.45),
    LayerSpec("lid-upper-left", "19_upper-eyelid-left", "faceEyes", 30.5),
    LayerSpec("lid-upper-right", "20_upper-eyelid-right", "faceEyes", 30.55),
    LayerSpec("lid-lower-left", "21_lower-eyelid-left", "faceEyes", 30.6),
    LayerSpec("lid-lower-right", "22_lower-eyelid-right", "faceEyes", 30.65),
    LayerSpec("mouth", "23_mouth", "faceMouth", 30.7),
]

# Draw order is the delivered numbering order (verified pixel-exact against
# expressions/happy/character.png). Sockets sit between parent and child.
LAYERS = [
    LayerSpec("leg-left-thigh", "01_left-thigh", "legL", 10),
    LayerSpec("leg-right-thigh", "02_right-thigh", "legR", 11),
    LayerSpec("leg-left-calf", "03_left-calf", "calfL", 12),
    LayerSpec("leg-right-calf", "04_right-calf", "calfR", 13),
    LayerSpec("shoe-left", "05_left-shoe", "shoeL", 14),
    LayerSpec("shoe-right", "06_right-shoe", "shoeR", 15),
    LayerSpec("hair-back", "07_back-hair", "hairBack", 20),
    LayerSpec("arm-right-upper", "08_upper-arm-wave", "armRUpper", 21),
    LayerSpec("arm-right-fore", "09_forearm-wave", "armRFore", 22),
    LayerSpec("arm-right-hand", "10_hand-wave", "armRHand", 23),
    LayerSpec("arm-left-upper", "11_upper-arm-carry", "armLUpper", 24),
    LayerSpec("head", "12_head-and-neck", "neck", 26),
    LayerSpec("body", "13_body-and-overalls", "body", 28),
    LayerSpec("skirt", "14_skirt", "skirt", 29),
    *FACE_LAYERS,
    LayerSpec("hair-front-left", "24_front-hair-left", "hairFrontL", 36),
    LayerSpec("hair-front-right", "25_front-hair-right", "hairFrontR", 37),
    LayerSpec("bow", "26_waist-bow", "bow", 38),
    LayerSpec("basket-bowl", "27_basket-bowl", "basket", 39),
    LayerSpec("arm-left-fore", "28_forearm-carry", "armLFore", 40),
    LayerSpec("basket-handle", "29_basket-handle", "basket", 41),
    LayerSpec("arm-left-hand", "30_hand-carry", "armLHand", 42),
    LayerSpec("clip", "31_strawberry-hairclip", "clip", 43),
]

# Canonical face set, split exactly as delivered (used for the rest pose).
# Inserted after the skirt and before the front hair / clip, matching the
# delivered stack order so the bangs still overlap the brows.


# joint id, parent bone, child bone, socket draw order, parent layer, child layer
# Sockets are drawn *between* their parent and child layer, so at rest they
# repaint identical pixels and only become visible once the joint rotates.
# The shoulders and hips need none: the delivered stack already tucks both
# upper arms and both thighs behind the torso/skirt, which hides those roots.
SOCKETS = [
    ("neck", "body", "neck", 27.0, "13_body-and-overalls", "12_head-and-neck"),
    ("elbowR", "armRUpper", "armRFore", 21.5, "08_upper-arm-wave", "09_forearm-wave"),
    ("wristR", "armRFore", "armRHand", 22.5, "09_forearm-wave", "10_hand-wave"),
    ("elbowL", "armLUpper", "armLFore", 39.5, "11_upper-arm-carry", "28_forearm-carry"),
    ("wristL", "armLFore", "armLHand", 40.5, "28_forearm-carry", "30_hand-carry"),
    ("kneeL", "legL", "calfL", 10.5, "01_left-thigh", "03_left-calf"),
    ("kneeR", "legR", "calfR", 11.5, "02_right-thigh", "04_right-calf"),
    ("ankleL", "calfL", "shoeL", 12.5, "03_left-calf", "05_left-shoe"),
    ("ankleR", "calfR", "shoeR", 13.5, "04_right-calf", "06_right-shoe"),
]

HAPPY_FACE = {
    "eye-left": ["eye-left", "lid-upper-left", "lid-lower-left"],
    "eye-right": ["eye-right", "lid-upper-right", "lid-lower-right"],
    "eyebrow-left": ["brow-left"],
    "eyebrow-right": ["brow-right"],
    "mouth": ["mouth"],
}

FACE_MOODS = {
    "happy": ["eye-left", "eye-right", "eyebrow-left", "eyebrow-right", "mouth"],
    "excited": ["eye-left", "eye-right", "eyebrow-left", "eyebrow-right", "mouth", "details"],
    "laughing": ["eye-left", "eye-right", "eyebrow-left", "eyebrow-right", "mouth"],
    "surprised": ["eye-left", "eye-right", "eyebrow-left", "eyebrow-right", "mouth"],
    "winking": ["eye-left", "eye-right", "eyebrow-left", "eyebrow-right", "mouth"],
    "sad": ["eye-left", "eye-right", "eyebrow-left", "eyebrow-right", "mouth", "details"],
    "crying": ["eye-left", "eye-right", "eyebrow-left", "eyebrow-right", "mouth", "details"],
}

FACE_BONE = {
    "eye-left": "faceEyes",
    "eye-right": "faceEyes",
    "eyebrow-left": "faceBrows",
    "eyebrow-right": "faceBrows",
    "mouth": "faceMouth",
    "details": "faceDetails",
}

FACE_Z = {
    "eyebrow-left": 30.2,
    "eyebrow-right": 30.3,
    "eye-left": 30.4,
    "eye-right": 30.5,
    "mouth": 30.6,
    "details": 30.7,
}

# blink art: only the laughing/winking sets ship closed eyes
BLINK = {
    "full": ("laughing/eye-left", "laughing/eye-right"),
    "right": (None, "laughing/eye-right"),
    "left": ("winking/eye-left", None),
}


def crop_save(img: Image.Image, mask: np.ndarray, path: str) -> list[int]:
    x0, y0, x1, y1 = bbox_of(mask)
    x0 = max(0, x0 - PAD)
    y0 = max(0, y0 - PAD)
    x1 = min(img.width, x1 + PAD)
    y1 = min(img.height, y1 + PAD)
    img.crop((x0, y0, x1, y1)).save(path, optimize=True)
    return [x0, y0, x1 - x0, y1 - y0]


def build_socket(parent: Image.Image, child: Image.Image, pivot: tuple[float, float],
                 radius: float) -> tuple[Image.Image, tuple[int, int]]:
    """Disc of the rest-pose pixels around a joint, masked by parent ∪ child.

    Returns the cropped disc plus its canvas-space origin, so a socket can be
    drawn at the same place the joint sits in the rest pose.
    """
    pa = alpha_mask(parent)
    ca = alpha_mask(child)
    union = pa | ca
    comp = Image.alpha_composite(parent, child)
    h = w = int(radius * 2 + 6)
    cx, cy = pivot
    disc = Image.new("L", (w, h), 0)
    from PIL import ImageDraw

    ImageDraw.Draw(disc).ellipse(
        (w / 2 - radius, h / 2 - radius, w / 2 + radius, h / 2 + radius), fill=255
    )
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    x0 = int(round(cx - w / 2))
    y0 = int(round(cy - h / 2))
    out.paste(comp.crop((x0, y0, x0 + w, y0 + h)), (0, 0))
    mask = Image.new("L", (w, h), 0)
    mask.paste(
        Image.fromarray((union * 255).astype(np.uint8)).crop((x0, y0, x0 + w, y0 + h)),
        (0, 0),
    )
    mask = Image.composite(mask, Image.new("L", (w, h), 0), disc)
    out.putalpha(Image.composite(out.getchannel("A"), Image.new("L", (w, h), 0), mask))
    bb = out.getbbox()
    if bb is not None:
        out = out.crop(bb)
        return out, (x0 + bb[0], y0 + bb[1])
    return out, (x0, y0)


def radius_for(parent: Image.Image, child: Image.Image) -> float:
    both = alpha_mask(parent) & alpha_mask(child)
    area = int(both.sum())
    return float(max(18.0, min(66.0, (area / np.pi) ** 0.5 * 1.1)))


def sha(path: str) -> str:
    with open(path, "rb") as fh:
        return hashlib.sha256(fh.read()).hexdigest()[:16]


def main() -> None:
    layers_dir = os.path.join(OUT, "layers")
    sockets_dir = os.path.join(OUT, "sockets")
    os.makedirs(layers_dir, exist_ok=True)
    os.makedirs(sockets_dir, exist_ok=True)
    for old in glob.glob(os.path.join(layers_dir, "*.png")):
        os.remove(old)
    for old in glob.glob(os.path.join(sockets_dir, "*.png")):
        os.remove(old)

    source_images: dict[str, Image.Image] = {}
    for spec in LAYERS:
        source_images[spec.source] = load_rgba(os.path.join(SRC, spec.source + ".png"))

    manifest_layers: dict[str, dict] = {}
    for spec in LAYERS:
        img = source_images[spec.source]
        rect = crop_save(img, alpha_mask(img), os.path.join(layers_dir, spec.id + ".png"))
        bone = BONES[spec.bone]
        manifest_layers[spec.id] = {
            "file": "layers/%s.png" % spec.id,
            "rect": rect,
            "bone": spec.bone,
            "z": spec.z,
            "pivot": [bone.pivot[0], bone.pivot[1]],
            "source": "assets/characters/guide-rig/%s.png" % spec.source,
            "sourceHash": sha(os.path.join(SRC, spec.source + ".png")),
        }

    sockets_meta = []
    for joint, parent_bone, child_bone, z, parent_src, child_src in SOCKETS:
        parent = source_images[parent_src]
        child = source_images[child_src]
        radius = radius_for(parent, child)
        socket, (sx, sy) = build_socket(parent, child, JOINTS[joint], radius)
        path = os.path.join(sockets_dir, joint + ".png")
        socket.save(path, optimize=True)
        sockets_meta.append(
            {
                "id": "socket-" + joint,
                "file": "sockets/%s.png" % joint,
                "rect": [sx, sy, socket.width, socket.height],
                "bone": parent_bone,
                "z": z,
                "radius": round(radius, 2),
                "pixels": int((np.array(socket)[..., 3] > 0).sum()),
            }
        )

    # ---- faces -----------------------------------------------------------
    face_meta: dict[str, dict] = {}
    used_files: dict[str, str] = {}

    def face_id(mood: str, part: str) -> str:
        return "face/%s/%s" % (mood, part)

    for mood, parts in FACE_MOODS.items():
        if mood == "happy":
            face_meta[mood] = {k: list(v) for k, v in HAPPY_FACE.items()}
            continue
        meta = {}
        for part in parts:
            path = os.path.join(EXPR, mood, part + ".png")
            if not os.path.exists(path):
                continue
            img = load_rgba(path)
            key = hashlib.sha1(np.array(img).tobytes()).hexdigest()[:12]
            name = used_files.get(key)
            if name is None:
                name = "%s-%s" % (mood, part)
                h = hashlib.sha1(np.array(img).tobytes()).hexdigest()[:10]
                name = "face-%s-%s-%s" % (part, mood, h[:6])
                used_files[key] = name
                rect = crop_save(img, alpha_mask(img), os.path.join(layers_dir, name + ".png"))
                manifest_layers[name] = {
                    "file": "layers/%s.png" % name,
                    "rect": rect,
                    "bone": FACE_BONE[part],
                    "z": FACE_Z[part],
                    "pivot": [JOINTS["neck"][0], JOINTS["neck"][1]],
                    "source": "assets/characters/expressions/%s/%s.png" % (mood, part),
                    "sourceHash": hashlib.sha1(np.array(img).tobytes()).hexdigest()[:16],
                }
            meta[part] = name
        face_meta[mood] = {k: [v] for k, v in meta.items()}

    blink_meta = {}
    for name, (left, right) in BLINK.items():
        entry = {}
        for side, ref in (("left", left), ("right", right)):
            if ref is None:
                continue
            mood, part = ref.split("/")
            entry[side] = list(face_meta[mood][part])
        blink_meta[name] = entry

    # ---- verification ----------------------------------------------------
    canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    for spec in LAYERS:
        entry = manifest_layers[spec.id]
        x, y, w, h = entry["rect"]
        canvas.alpha_composite(load_rgba(os.path.join(OUT, entry["file"])), (x, y))
    ref = np.array(load_rgba(os.path.join(EXPR, "happy/character.png"))).astype(int)
    got = np.array(canvas).astype(int)
    diff = np.abs(got - ref)
    rest_report = {
        "meanAbs": round(float(diff.mean()), 5),
        "maxAbs": int(diff.max()),
        "pixelsOver30": int((diff.max(2) > 30).sum()),
        "pixelsOver60": int((diff.max(2) > 60).sum()),
    }

    mood_report = {}
    canonical_face = {"brow-left", "brow-right", "eye-left", "eye-right",
                      "lid-upper-left", "lid-upper-right", "lid-lower-left", "lid-lower-right", "mouth"}
    base_ids = [spec.id for spec in LAYERS if spec.id not in canonical_face]
    base_canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    for layer_id in base_ids:
        entry = manifest_layers[layer_id]
        x, y, w, h = entry["rect"]
        base_canvas.alpha_composite(load_rgba(os.path.join(OUT, entry["file"])), (x, y))
    for mood, parts in face_meta.items():
        c = base_canvas.copy()
        ids = sorted(
            {layer_id for group in parts.values() for layer_id in group},
            key=lambda layer_id: manifest_layers[layer_id]["z"],
        )
        for part_id in ids:
            e = manifest_layers[part_id]
            c.alpha_composite(load_rgba(os.path.join(OUT, e["file"])), (e["rect"][0], e["rect"][1]))
        a = np.array(c).astype(int)
        b = np.array(load_rgba(os.path.join(EXPR, mood, "character.png"))).astype(int)
        d = np.abs(a - b)
        mood_report[mood] = {
            "meanAbs": round(float(d.mean()), 5),
            "pixelsOver30": int((d.max(2) > 30).sum()),
            "pixelsOver60": int((d.max(2) > 60).sum()),
        }

    manifest = {
        "version": 2,
        "canvas": CANVAS,
        "joints": {k: [round(v[0], 2), round(v[1], 2)] for k, v in JOINTS.items()},
        "bones": {
            k: {
                "parent": v.parent,
                "pivot": [round(v.pivot[0], 2), round(v.pivot[1], 2)],
                "label": v.label,
            }
            for k, v in BONES.items()
        },
        "layers": manifest_layers,
        "sockets": sockets_meta,
        "faces": face_meta,
        "blink": blink_meta,
        "restReference": "assets/characters/expressions/happy/character.png",
        "source": "assets/characters/guide-rig",
    }
    with open(os.path.join(OUT, "manifest.json"), "w") as fh:
        json.dump(manifest, fh, indent=1, sort_keys=True)

    report = {
        "layers": len(LAYERS),
        "sockets": len(sockets_meta),
        "facePlates": len(used_files),
        "rest": rest_report,
        "moods": mood_report,
        "totalBytes": sum(
            os.path.getsize(os.path.join(dp, f))
            for dp, _, fs in os.walk(OUT)
            for f in fs
        ),
    }
    with open(os.path.join(OUT, "report.json"), "w") as fh:
        json.dump(report, fh, indent=1, sort_keys=True)
    print(json.dumps(report, indent=1))


if __name__ == "__main__":
    main()
