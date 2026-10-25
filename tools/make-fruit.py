#!/usr/bin/env python3
"""
Turn the generated fruit photographs into game-ready transparent PNGs.

Input : ref-art/raw/f0..f6.png   (photoreal, flat magenta backdrop)
Output: game-android/app/src/main/assets/fruit/0..6.png  (RGBA, square, padded)

The keying is by chroma, not by exact colour: the backdrop is pink/magenta with a
soft gradient, so we score every pixel on "how magenta is this" and then clean up
with morphological open/close, edge feathering and a despill pass that removes the
pink rim light left on the subject's outline.
"""

import os
import sys

from PIL import Image, ImageFilter

SRC = "ref-art/raw"
OUT = "game-android/app/src/main/assets/fruit"
SIZE = 512          # output canvas
INSET = 0.04        # margin around the subject inside the canvas


def magenta_score(im):
    """0..255: how much a pixel looks like the magenta backdrop."""
    px = im.load()
    w, h = im.size
    out = Image.new("L", (w, h))
    op = out.load()
    for y in range(h):
        for x in range(w):
            r, g, b = px[x, y]
            # backdrop: strong red+blue, weak green
            strength = min(r, b) - g
            # also require the pink hue: r and b are both high
            hue = 1.0 if (r > 90 and b > 90) else 0.0
            v = int(max(0, min(255, (strength - 40) * 2.4)) * hue)
            op[x, y] = v
    return out


def clean_mask(mask, size):
    # PIL only accepts odd kernel sizes
    size = size if size % 2 == 1 else size + 1
    size = max(3, min(size, 9))
    # close small holes, then open leftover speckle
    m = mask.filter(ImageFilter.MaxFilter(size)).filter(ImageFilter.MinFilter(size))
    m = m.filter(ImageFilter.MinFilter(size)).filter(ImageFilter.MaxFilter(size))
    return m


def flood_key(im):
    """Backdrop connected to the border only, so magenta-ish fruit stays intact."""
    w, h = im.size
    score = magenta_score(im)
    sp = score.load()
    seen = bytearray(w * h)
    out = Image.new("L", (w, h), 255)   # 255 = keep (subject)
    op = out.load()
    stack = []
    for x in range(w):
        stack.append((x, 0))
        stack.append((x, h - 1))
    for y in range(h):
        stack.append((0, y))
        stack.append((w - 1, y))
    while stack:
        x, y = stack.pop()
        if x < 0 or y < 0 or x >= w or y >= h:
            continue
        i = y * w + x
        if seen[i]:
            continue
        seen[i] = 1
        if sp[x, y] < 96:
            continue
        op[x, y] = 0
        stack.append((x + 1, y))
        stack.append((x - 1, y))
        stack.append((x, y + 1))
        stack.append((x, y - 1))
    return out


def despill(im, alpha):
    """Subtract the pink rim where the subject is semi-transparent."""
    im = im.convert("RGBA")
    px = im.load()
    ap = alpha.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, _ = px[x, y]
            a = ap[x, y]
            if a > 240 or a == 0:
                continue
            # where the backdrop bleeds in, green is the loser
            if min(r, b) - g > 12:
                g2 = min(255, int(g + (min(r, b) - g) * 0.85))
                px[x, y] = (r, g2, b, a)
    return im


def build(idx):
    src = os.path.join(SRC, f"f{idx}.png")
    im = Image.open(src).convert("RGB")
    w, h = im.size
    alpha = flood_key(im)
    alpha = clean_mask(alpha, max(3, w // 220))
    # feather the outline a touch so it does not look cut out
    alpha = alpha.filter(ImageFilter.GaussianBlur(max(0.8, w / 700.0)))

    cut = despill(im, alpha)
    cut.putalpha(alpha)

    bbox = alpha.point(lambda v: 255 if v > 8 else 0).getbbox()
    if bbox:
        cut = cut.crop(bbox)

    # fit into a square canvas with a uniform margin
    side = int(SIZE * (1 - INSET * 2))
    cw, ch = cut.size
    k = min(side / cw, side / ch)
    cut = cut.resize((max(1, int(cw * k)), max(1, int(ch * k))), Image.LANCZOS)

    canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    canvas.alpha_composite(cut, ((SIZE - cut.width) // 2, (SIZE - cut.height) // 2))
    return canvas


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.chdir(root)
    os.makedirs(OUT, exist_ok=True)
    for i in range(7):
        if not os.path.exists(os.path.join(SRC, f"f{i}.png")):
            print(f"skip {i}: missing source", file=sys.stderr)
            continue
        out = build(i)
        path = os.path.join(OUT, f"{i}.png")
        out.save(path, optimize=True)
        print(f"fruit/{i}.png  {out.size}  {os.path.getsize(path) / 1024:.1f} KB")


if __name__ == "__main__":
    main()
