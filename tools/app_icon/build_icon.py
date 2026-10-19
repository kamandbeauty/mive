"""Builds the launcher icon from the game's own artwork.

Everything is composed from shipped assets (the brand basket and the fruit
sprites), so the icon's palette and shapes stay identical to the game. No
character or fruit is redrawn.

    python3 tools/app_icon/build_icon.py                 # masters + preview
    python3 tools/app_icon/build_icon.py --write         # install into android/
    python3 tools/app_icon/build_icon.py --write --variant strawberry

Outputs:
    build/app_icon/<variant>_1024.png    master artwork
    build/app_icon/preview.png           real-size comparison (192/96/48 px)
    build/app_icon/launcher_masks.png    how it looks under launcher masks

Needs: pillow, numpy.
"""

from __future__ import annotations

import argparse
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / "assets"
OUT = ROOT / "build" / "app_icon"
MASTER = 1024

# palette shared with the game UI
CREAM = (255, 243, 221)
CREAM_DEEP = (240, 219, 188)
GREEN = (38, 96, 58)
GREEN_DEEP = (14, 52, 32)
SPARK = (255, 247, 218)


def load(path: str) -> Image.Image:
    return Image.open(ART / path).convert("RGBA")


def trimmed(path: str, alpha_threshold: int = 24) -> Image.Image:
    """Crops to the visible art, ignoring faint anti-aliased dust."""
    image = load(path)
    alpha = np.array(image.getchannel("A"))
    ys, xs = np.where(alpha > alpha_threshold)
    if len(xs) == 0:
        return image
    return image.crop(
        (int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1)
    )


def gradient(size: int, inner, outer, radius=0.9,
             center=(0.5, 0.45)) -> Image.Image:
    y, x = np.mgrid[0:size, 0:size].astype(np.float32)
    cx, cy = center[0] * size, center[1] * size
    d = np.clip(np.hypot(x - cx, y - cy) / (size * radius), 0, 1)[..., None]
    a, b = np.array(inner, np.float32), np.array(outer, np.float32)
    rgb = a + (b - a) * d
    out = np.dstack([rgb, np.full((size, size, 1), 255, np.float32)])
    return Image.fromarray(out.clip(0, 255).astype(np.uint8), "RGBA")


def glow(size: int, color, center=(0.5, 0.40), radius=0.40,
         strength=0.85, power=2.2) -> Image.Image:
    y, x = np.mgrid[0:size, 0:size].astype(np.float32)
    cx, cy = center[0] * size, center[1] * size
    d = np.hypot(x - cx, y - cy) / (size * radius)
    t = np.clip(1 - d, 0, 1) ** power * strength
    arr = np.zeros((size, size, 4), np.uint8)
    arr[..., 0], arr[..., 1], arr[..., 2] = color
    arr[..., 3] = (t * 255).astype(np.uint8)
    return Image.fromarray(arr, "RGBA")


def vignette(size: int, strength=0.30, color=(40, 28, 16)) -> Image.Image:
    """Darkens the corners so the centre reads as lit."""
    y, x = np.mgrid[0:size, 0:size].astype(np.float32)
    d = np.hypot(x / size - 0.5, y / size - 0.52) / 0.72
    t = np.clip((d - 0.45) / 0.55, 0, 1) ** 1.6 * strength
    arr = np.zeros((size, size, 4), np.uint8)
    arr[..., 0], arr[..., 1], arr[..., 2] = color
    arr[..., 3] = (t * 255).astype(np.uint8)
    return Image.fromarray(arr, "RGBA")


def drop_shadow(sprite: Image.Image, offset=(0, 26), blur=28, opacity=0.34,
                spread=0) -> Image.Image:
    alpha = sprite.getchannel("A")
    if spread:
        alpha = alpha.filter(ImageFilter.MaxFilter(spread))
    shadow = Image.new("RGBA", sprite.size, (12, 28, 16, 0))
    shadow.putalpha(alpha.point(lambda v: int(v * opacity)))
    shadow = shadow.filter(ImageFilter.GaussianBlur(blur))
    canvas = Image.new("RGBA", sprite.size, (0, 0, 0, 0))
    canvas.alpha_composite(shadow, offset)
    return canvas


def place(canvas: Image.Image, sprite: Image.Image, center=(0.5, 0.5),
          width=0.6, angle=0.0, shadow=True, shadow_opacity=0.34) -> None:
    target = max(1, int(canvas.width * width))
    scale = target / sprite.width
    resized = sprite.resize(
        (max(1, int(sprite.width * scale)), max(1, int(sprite.height * scale))),
        Image.LANCZOS,
    )
    if angle:
        resized = resized.rotate(angle, resample=Image.BICUBIC, expand=True)
    x = int(canvas.width * center[0] - resized.width / 2)
    y = int(canvas.height * center[1] - resized.height / 2)
    if shadow:
        pad = max(16, resized.width // 3)
        layer = Image.new(
            "RGBA", (resized.width + 2 * pad, resized.height + 2 * pad),
            (0, 0, 0, 0),
        )
        layer.alpha_composite(resized, (pad, pad))
        canvas.alpha_composite(
            drop_shadow(layer, offset=(0, max(6, resized.height // 26)),
                        blur=max(10, resized.width // 22),
                        opacity=shadow_opacity),
            (x - pad, y - pad),
        )
    canvas.alpha_composite(resized, (x, y))


def sparkle(size: int, color=SPARK) -> Image.Image:
    s = size
    layer = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    c = s / 2
    draw.polygon(
        [(c, 1), (c + s * 0.085, c - s * 0.085), (s - 1, c),
         (c + s * 0.085, c + s * 0.085), (c, s - 1),
         (c - s * 0.085, c + s * 0.085), (1, c),
         (c - s * 0.085, c - s * 0.085)],
        fill=color + (255,),
    )
    return layer.filter(ImageFilter.GaussianBlur(s * 0.01))


def confetti(size: int, color=CREAM, count=22, seed=11, opacity=0.18,
             avoid=0.30) -> Image.Image:
    """Sparse dots pushed to the edges so the centre stays clean."""
    rng = np.random.default_rng(seed)
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    for _ in range(count):
        x, y = rng.uniform(0, size), rng.uniform(0, size)
        if math.hypot(x / size - 0.5, y / size - 0.5) < avoid:
            continue
        r = rng.uniform(size * 0.005, size * 0.014)
        draw.ellipse([x - r, y - r, x + r, y + r],
                     fill=color + (int(255 * opacity),))
    return layer


def squircle_mask(size: int, radius_ratio=0.225, supersample=4) -> Image.Image:
    big = size * supersample
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, big - 1, big - 1], radius=int(big * radius_ratio), fill=255
    )
    return mask.resize((size, size), Image.LANCZOS)


def masked(image: Image.Image) -> Image.Image:
    out = Image.new("RGBA", image.size, (0, 0, 0, 0))
    out.paste(image, (0, 0), squircle_mask(image.width))
    return out


# ---------------------------------------------------------------- variants --
# Each variant returns ``(background, hero)`` at the requested size. Keeping the
# layers separate is what makes a correct Android adaptive icon possible: the
# background is a full-bleed gradient, the hero is the transparent foreground.


def variant_orchard(size: int):
    """Brand basket on a warm cream field. The default icon."""
    bg = gradient(size, (255, 250, 239), CREAM_DEEP, radius=0.86,
                  center=(0.5, 0.40))
    bg.alpha_composite(glow(size, (255, 236, 198), center=(0.5, 0.42),
                            radius=0.44, strength=0.75))
    # soft green pool under the basket separates it from the warm field
    bg.alpha_composite(glow(size, (34, 92, 56), center=(0.5, 0.60),
                            radius=0.34, strength=0.26, power=1.6))
    bg.alpha_composite(vignette(size, strength=0.16, color=(120, 86, 40)))
    bg.alpha_composite(confetti(size, color=(198, 138, 66), opacity=0.16))

    hero = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    place(hero, trimmed("images/mivehchin_brand_mark.png"),
          center=(0.5, 0.53), width=0.84, shadow_opacity=0.30)
    for pos, s in (((0.14, 0.17), 0.052), ((0.86, 0.21), 0.038),
                   ((0.11, 0.82), 0.034), ((0.89, 0.80), 0.046)):
        place(hero, sparkle(size), center=pos, width=s, shadow=False)

    # Themed icon: the hero fruit. The basket silhouette collapses into a
    # featureless blob at 48 dp, the strawberry stays instantly readable. It
    # must stay inside the 66/108 dp safe circle of an adaptive icon.
    glyph = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    place(glyph, trimmed("game/fruits/strawberry.png"),
          center=(0.5, 0.50), width=0.50, shadow=False)
    return bg, hero, glyph


def variant_strawberry(size: int):
    """Deep orchard green with the hero strawberry: the strongest small size."""
    bg = gradient(size, (46, 108, 66), GREEN_DEEP, radius=0.95,
                  center=(0.5, 0.36))
    bg.alpha_composite(glow(size, (255, 214, 150), center=(0.5, 0.42),
                            radius=0.34, strength=0.40))
    bg.alpha_composite(vignette(size, strength=0.34, color=(6, 26, 16)))
    bg.alpha_composite(confetti(size, color=(214, 240, 208), opacity=0.13))

    hero = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    place(hero, trimmed("game/fruits/strawberry.png"),
          center=(0.5, 0.52), width=0.70, angle=-5, shadow_opacity=0.40)
    for pos, s in (((0.16, 0.20), 0.066), ((0.85, 0.27), 0.044),
                   ((0.83, 0.80), 0.058), ((0.19, 0.81), 0.038)):
        place(hero, sparkle(size), center=pos, width=s, shadow=False)

    glyph = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    place(glyph, trimmed("game/fruits/strawberry.png"),
          center=(0.5, 0.50), width=0.50, angle=-5, shadow=False)
    return bg, hero, glyph


def variant_harvest(size: int):
    """Sunrise over the orchard: three picked fruits in a balanced cluster."""
    top = gradient(size, (255, 247, 228), (249, 212, 156), radius=1.15,
                   center=(0.5, 0.12))
    bottom = gradient(size, GREEN, GREEN_DEEP, radius=1.25, center=(0.5, 0.92))
    horizon = Image.new("L", (size, size), 0)
    ImageDraw.Draw(horizon).ellipse(
        [-size * 0.5, size * 0.60, size * 1.5, size * 2.0], fill=255
    )
    horizon = horizon.filter(ImageFilter.GaussianBlur(size * 0.015))
    bg = top.copy()
    bg.paste(bottom, (0, 0), horizon)
    bg.alpha_composite(glow(size, (255, 236, 190), center=(0.5, 0.34),
                            radius=0.40, strength=0.55))
    bg.alpha_composite(confetti(size, color=CREAM, opacity=0.20))

    hero = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    place(hero, trimmed("game/fruits/orange.png"),
          center=(0.60, 0.44), width=0.40, shadow_opacity=0.26)
    place(hero, trimmed("game/fruits/watermelon.png"),
          center=(0.31, 0.56), width=0.38, angle=-12, shadow_opacity=0.26)
    place(hero, trimmed("game/fruits/strawberry.png"),
          center=(0.66, 0.66), width=0.34, angle=8, shadow_opacity=0.26)
    place(hero, trimmed("game/fruits/blueberry.png"),
          center=(0.40, 0.71), width=0.32, angle=-4, shadow_opacity=0.24)
    for pos, s in (((0.16, 0.19), 0.060), ((0.85, 0.22), 0.044),
                   ((0.80, 0.45), 0.032)):
        place(hero, sparkle(size), center=pos, width=s, shadow=False)

    # Solid silhouette: the fruit cluster, no sparkles or shadow.
    glyph = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    place(glyph, trimmed("game/fruits/orange.png"), center=(0.60, 0.43),
          width=0.30, shadow=False)
    place(glyph, trimmed("game/fruits/watermelon.png"), center=(0.32, 0.55),
          width=0.28, angle=-12, shadow=False)
    place(glyph, trimmed("game/fruits/strawberry.png"), center=(0.64, 0.65),
          width=0.25, angle=8, shadow=False)
    place(glyph, trimmed("game/fruits/blueberry.png"), center=(0.41, 0.70),
          width=0.24, angle=-4, shadow=False)
    return bg, hero, glyph


VARIANTS = {
    "orchard": variant_orchard,
    "strawberry": variant_strawberry,
    "harvest": variant_harvest,
}


def compose(size: int, variant: str) -> Image.Image:
    bg, hero, _ = VARIANTS[variant](size)
    icon = bg.copy()
    icon.alpha_composite(hero)
    return icon


# ------------------------------------------------------------------ outputs --

def preview_sheet(masters: dict[str, Image.Image]) -> Image.Image:
    sizes = (192, 96, 48)
    pad, gap = 24, 12
    width = pad * 2 + sum(192 + 96 + 48 + gap * 2 + 18 for _ in masters)
    height = pad * 2 + 192 + 24
    sheet = Image.new("RGB", (width, height), (246, 243, 237))
    draw = ImageDraw.Draw(sheet)
    x = pad
    for name, master in masters.items():
        y = pad
        for s in sizes:
            tile = masked(master.resize((s, s), Image.LANCZOS))
            sheet.paste(tile, (x, y), tile)
            x += s + gap
        draw.text((x - 192 - 96 - 48 - gap * 2, height - 20), name,
                  fill=(60, 60, 60))
        x += 18
    return sheet


def mask_preview(master: Image.Image) -> Image.Image:
    """How the icon looks under the launcher shapes that actually exist."""
    shapes = {
        "circle": lambda s: _circle_mask(s),
        "squircle": lambda s: squircle_mask(s, 0.30),
        "rounded": lambda s: squircle_mask(s, 0.16),
    }
    size = 160
    sheet = Image.new("RGB", (size * len(shapes) + 24 * (len(shapes) + 1), 210),
                      (246, 243, 237))
    draw = ImageDraw.Draw(sheet)
    for index, (name, mask_of) in enumerate(shapes.items()):
        x = 24 + index * (size + 24)
        tile = master.resize((size, size), Image.LANCZOS)
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        out.paste(tile, (0, 0), mask_of(size))
        sheet.paste(out, (x, 24), out)
        draw.text((x, 190), name, fill=(60, 60, 60))
    return sheet


def _circle_mask(size: int, supersample=4) -> Image.Image:
    big = size * supersample
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, big - 1, big - 1], fill=255)
    return mask.resize((size, size), Image.LANCZOS)


def _largest_blob(alpha: np.ndarray, threshold=110) -> np.ndarray:
    """Keeps only the biggest connected shape, so sparkle dust disappears."""
    solid = alpha > threshold
    seen = np.zeros_like(solid, dtype=bool)
    best = np.zeros_like(solid)
    best_area = 0
    height, width = solid.shape
    for y in range(height):
        for x in range(width):
            if not solid[y, x] or seen[y, x]:
                continue
            stack = [(y, x)]
            seen[y, x] = True
            blob = []
            while stack:
                cy, cx = stack.pop()
                blob.append((cy, cx))
                for ny, nx in ((cy - 1, cx), (cy + 1, cx),
                               (cy, cx - 1), (cy, cx + 1)):
                    if (0 <= ny < height and 0 <= nx < width
                            and solid[ny, nx] and not seen[ny, nx]):
                        seen[ny, nx] = True
                        stack.append((ny, nx))
            if len(blob) > best_area:
                best_area = len(blob)
                best = np.zeros_like(solid)
                for by, bx in blob:
                    best[by, bx] = True
    return best


def _fill_holes(solid: np.ndarray) -> np.ndarray:
    """Fills interior gaps (basket weave, fruit highlights) for a clean glyph."""
    height, width = solid.shape
    outside = np.zeros_like(solid)
    stack = [(0, 0)]
    outside[0, 0] = True
    while stack:
        cy, cx = stack.pop()
        for ny, nx in ((cy - 1, cx), (cy + 1, cx), (cy, cx - 1), (cy, cx + 1)):
            if (0 <= ny < height and 0 <= nx < width
                    and not solid[ny, nx] and not outside[ny, nx]):
                outside[ny, nx] = True
                stack.append((ny, nx))
    return ~outside | solid


def monochrome(glyph: Image.Image, canvas: int) -> Image.Image:
    """Themed-icon glyph: the basket's own silhouette, holes filled.

    Themed icons are monochrome by definition, and the launcher shows them at
    ~48 dp. A filled silhouette of the basket stays readable there, while line
    tracing the artwork turns into noise. The filled shape is dilated slightly
    and the interior is closed so the weave does not punch holes in it.
    """
    offset = (canvas - glyph.width) // 2
    layer = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    layer.alpha_composite(glyph, (offset, offset))

    alpha = np.array(layer.getchannel("A"))
    solid = _fill_holes(_largest_blob(alpha, threshold=140))
    mask = Image.fromarray((solid * 255).astype(np.uint8), "L")
    # close the anti-aliased rim, then soften so the OS mask keeps it crisp
    mask = mask.filter(ImageFilter.MaxFilter(3))
    mask = mask.filter(ImageFilter.MinFilter(3))
    mask = mask.filter(ImageFilter.GaussianBlur(1.0))
    mask = mask.point(lambda v: 255 if v > 100 else 0)

    out = Image.new("RGBA", (canvas, canvas), (255, 255, 255, 0))
    out.putalpha(mask)
    return out


def write_android(variant: str, master: Image.Image) -> None:
    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144,
                 "xxxhdpi": 192}
    res = ROOT / "android" / "app" / "src" / "main" / "res"

    # legacy icon: full artwork, squircle silhouette
    legacy = masked(master)
    # Adaptive icon: the background is the full-bleed field, the foreground is
    # the artwork alone, scaled so it stays inside the 66/108 dp safe circle
    # (the launcher masks and parallaxes the two layers independently).
    adaptive = 432
    bg_src, hero_src, glyph_src = VARIANTS[variant](adaptive)
    bg = bg_src.copy()
    bg.alpha_composite(vignette(adaptive, strength=0.10))
    # The OS may crop the outer ~18% of the adaptive canvas, so the hero is
    # kept inside the central 64/108 dp. The glyph artwork is already drawn on
    # that same canvas, so the monochrome layer is used 1:1.
    safe = int(round(adaptive * 0.64))
    fg = Image.new("RGBA", (adaptive, adaptive), (0, 0, 0, 0))
    hero = hero_src.resize((safe, safe), Image.LANCZOS)
    fg.alpha_composite(hero, ((adaptive - safe) // 2, (adaptive - safe) // 2))
    mono = monochrome(glyph_src, adaptive)

    round_icon = Image.new("RGBA", master.size, (0, 0, 0, 0))
    round_icon.paste(master, (0, 0), _circle_mask(master.width))

    for folder, px in densities.items():
        target = res / f"mipmap-{folder}"
        target.mkdir(parents=True, exist_ok=True)
        legacy.resize((px, px), Image.LANCZOS).save(
            target / "ic_launcher.png", optimize=True
        )
        round_icon.resize((px, px), Image.LANCZOS).save(
            target / "ic_launcher_round.png", optimize=True
        )
        adaptive_px = int(round(px * 108 / 48))
        for name, image in (
            ("ic_launcher_background", bg.convert("RGB").convert("RGBA")),
            ("ic_launcher_foreground", fg),
            ("ic_launcher_monochrome", mono),
        ):
            image.resize((adaptive_px, adaptive_px), Image.LANCZOS).save(
                target / f"{name}.png", optimize=True
            )
    print(f"installed '{variant}' launcher art in {res}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true",
                        help="install the chosen variant into android/")
    parser.add_argument("--variant", default="orchard", choices=sorted(VARIANTS))
    args = parser.parse_args()

    OUT.mkdir(parents=True, exist_ok=True)
    masters = {}
    for name in VARIANTS:
        master = compose(MASTER, name)
        master.save(OUT / f"{name}_1024.png", optimize=True)
        masters[name] = master
        print("built", name)
    preview_sheet(masters).save(OUT / "preview.png", optimize=True)
    mask_preview(masters[args.variant]).save(OUT / "launcher_masks.png",
                                             optimize=True)

    if args.write:
        write_android(args.variant, masters[args.variant])


if __name__ == "__main__":
    main()
