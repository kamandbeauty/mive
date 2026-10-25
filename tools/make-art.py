"""Builds every raster asset the game ships in assets/art/.

Run:  python3 tools/make-art.py
Inputs : art-src/*.png   (AI generated characters + garden backdrop)
Outputs: game-android/app/src/main/assets/art/*.webp|png

The characters are generated on a flat magenta plate; the plate is removed here
with a tolerance flood fill from the borders, then despilled and cropped.
Every UI element is painted with Pillow so it keeps real gaussian shadows and
gradients, and is saved with alpha; the stretchable ones are cut as 9-slice
bitmaps (see ART_INSETS in Art.kt).
"""
import math
import os
import sys
from collections import deque

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "art-src")
OUT = os.path.join(ROOT, "game-android/app/src/main/assets/art")
os.makedirs(OUT, exist_ok=True)


# --------------------------------------------------------------- primitives
def rmask(size, radius, box=None):
    m = Image.new("L", size, 0)
    d = ImageDraw.Draw(m)
    b = box or (0, 0, size[0] - 1, size[1] - 1)
    d.rounded_rectangle(b, radius, fill=255)
    return m


def vgrad(size, stops):
    w, h = size
    img = Image.new("RGBA", size)
    d = ImageDraw.Draw(img)
    for y in range(h):
        k = y / max(1, h - 1)
        for i in range(len(stops) - 1):
            p0, c0 = stops[i]
            p1, c1 = stops[i + 1]
            if p0 <= k <= p1:
                t = 0 if p1 == p0 else (k - p0) / (p1 - p0)
                col = tuple(int(c0[j] + (c1[j] - c0[j]) * t) for j in range(4))
                d.line([(0, y), (w, y)], fill=col)
                break
    return img


def shape(img, box, radius, stops):
    layer = vgrad((img.width, img.height), stops)
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(box, radius, fill=255)
    img.paste(layer, (0, 0), mask)


def inner_shadow(img, box, radius, color, blur=10, offset=(0, 6), strength=120):
    outer = Image.new("L", img.size, 0)
    ImageDraw.Draw(outer).rounded_rectangle(box, radius, fill=255)
    inner = Image.new("L", img.size, 0)
    b = (box[0] + offset[0], box[1] + offset[1], box[2] + offset[0], box[3] + offset[1])
    ImageDraw.Draw(inner).rounded_rectangle(b, radius, fill=255)
    ring = outer.filter(ImageFilter.GaussianBlur(blur))
    ring = Image.fromarray(np.clip(np.asarray(ring).astype(int) - np.asarray(inner), 0, 255).astype("uint8"))
    tint = Image.new("RGBA", img.size, color + (255,))
    alpha = Image.fromarray((np.asarray(ring).astype(float) * strength / 255).astype("uint8"))
    tint.putalpha(alpha)
    img.alpha_composite(tint)


def drop_shadow(img, box, radius, dy=10, blur=12, strength=120, color=(6, 26, 16)):
    sh = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(sh)
    d.rounded_rectangle((box[0], box[1] + dy, box[2], box[3] + dy), radius, fill=color + (strength,))
    sh = sh.filter(ImageFilter.GaussianBlur(blur))
    img.alpha_composite(sh)


def gloss(img, box, radius, top=0.44, strength=120):
    band = (box[0], box[1], box[2], box[1] + (box[3] - box[1]) * top)
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    g = vgrad(img.size, [(0, (255, 255, 255, strength)), (0.55, (255, 255, 255, int(strength * 0.42))),
                         (1, (255, 255, 255, 0))])
    mask = Image.new("L", img.size, 0)
    md = ImageDraw.Draw(mask)
    md.rounded_rectangle(box, radius, fill=255)
    cut = Image.new("L", img.size, 0)
    ImageDraw.Draw(cut).rectangle((0, band[3], img.width, img.height), fill=255)
    mask = Image.fromarray(np.clip(np.asarray(mask).astype(int) - np.asarray(cut), 0, 255).astype("uint8"))
    layer.paste(g, (0, 0), mask)
    img.alpha_composite(layer)


def bevel(img, box, radius, t, top=90, bottom=120):
    ring = Image.new("RGBA", img.size, (0, 0, 0, 0))
    rd = ImageDraw.Draw(ring)
    steps = max(8, int((box[3] - box[1])))
    for i in range(steps):
        k = i / (steps - 1)
        if k < 0.22:
            col = (42, 18, 0, int(top * (1 - k / 0.22)))
        elif k > 0.78:
            col = (255, 255, 255, int(bottom * ((k - 0.78) / 0.22)))
        else:
            continue
        y = box[1] + (box[3] - box[1]) * k
        rd.line([(box[0] + radius * 0.6, y), (box[2] - radius * 0.6, y)], fill=col, width=int(t))
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(box, radius, fill=255)
    inner = Image.new("L", img.size, 0)
    ImageDraw.Draw(inner).rounded_rectangle((box[0] + t, box[1] + t, box[2] - t, box[3] - t),
                                            max(1, radius - t), fill=255)
    alpha = Image.fromarray(np.clip(np.asarray(mask).astype(int) - np.asarray(inner), 0, 255).astype("uint8"))
    ring.putalpha(Image.fromarray((np.asarray(ring.getchannel("A")).astype(int) * np.asarray(alpha) // 255
                                   ).astype("uint8")))
    img.alpha_composite(ring)


def star(img, cx, cy, r, fill=(255, 216, 86, 255), edge=(178, 118, 12, 255)):
    d = ImageDraw.Draw(img)
    pts = []
    for i in range(10):
        rr = r if i % 2 == 0 else r * 0.46
        a = math.radians(-90 + i * 36)
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    d.polygon(pts, fill=fill, outline=edge, width=max(2, int(r * 0.10)))
    d.polygon([(cx + (x - cx) * 0.60, cy + (y - cy) * 0.60) for x, y in pts], fill=(255, 240, 170, 255))


def leaf(img, cx, cy, size, rot, dark=(44, 120, 52), light=(150, 222, 110)):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    w = size * 0.42
    pts = [(cx, cy - size * 0.5)]
    for i in range(1, 19):
        k = i / 18
        y = cy - size * 0.5 + size * k
        pts.append((cx + math.sin(k * math.pi) * w, y))
    for i in range(18, 0, -1):
        k = i / 18
        y = cy - size * 0.5 + size * k
        pts.append((cx - math.sin(k * math.pi) * w, y))
    ImageDraw.Draw(layer).polygon(pts, fill=dark)
    hi = [(cx + (x - cx) * 0.55, cy + (y - cy) * 0.7) for x, y in pts]
    ImageDraw.Draw(layer).polygon(hi, fill=light)
    layer = layer.rotate(rot, resample=Image.BICUBIC, center=(cx, cy))
    img.alpha_composite(layer)


def flower(img, cx, cy, r, petal=(255, 150, 180), core=(255, 214, 92)):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for i in range(5):
        a = i * 2 * math.pi / 5
        px, py = cx + math.cos(a) * r * 0.62, cy + math.sin(a) * r * 0.62
        d.ellipse((px - r * 0.62, py - r * 0.62, px + r * 0.62, py + r * 0.62), fill=petal + (255,))
    d.ellipse((cx - r * 0.5, cy - r * 0.5, cx + r * 0.5, cy + r * 0.5), fill=core + (255,))
    d.ellipse((cx - r * 0.22, cy - r * 0.26, cx + r * 0.02, cy - r * 0.02), fill=(255, 255, 255, 140))
    img.alpha_composite(layer)


# ------------------------------------------------------------------ keying
def key_character(src, dst, target_h=560):
    im = Image.open(src).convert("RGB")
    w, h = im.size
    a = np.asarray(im).astype(np.int32)
    flat = Image.fromarray(a.astype("uint8"))
    for xy in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
        ImageDraw.floodfill(flat, xy, (1, 2, 3), thresh=60)
    bg = np.all(np.asarray(flat).astype(np.int32) == np.array([1, 2, 3]), axis=-1)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    bg |= ((r - g) > 55) & ((b - g) > 20) & (g < 55)
    # drop small islands (sparkles) left on the plate
    lab = np.zeros(bg.shape, dtype=bool)
    hh, ww = bg.shape
    keep = np.zeros(bg.shape, dtype=bool)
    seen = np.zeros(bg.shape, dtype=bool)
    for y in range(0, hh, 3):
        for x in range(0, ww, 3):
            if bg[y, x] or seen[y, x]:
                continue
            q = deque([(y, x)])
            seen[y, x] = True
            comp = [(y, x)]
            while q:
                cy, cx = q.popleft()
                for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    ny, nx = cy + dy, cx + dx
                    if 0 <= ny < hh and 0 <= nx < ww and not seen[ny, nx] and not bg[ny, nx]:
                        seen[ny, nx] = True
                        q.append((ny, nx))
                        comp.append((ny, nx))
            big = len(comp) > 3000
            for yy, xx in comp:
                if big:
                    keep[yy, xx] = True
    bg |= ~keep
    band = np.asarray(Image.fromarray((bg * 255).astype("uint8")).filter(ImageFilter.MaxFilter(5))) > 0
    band &= (~bg) & ((b - g) > 8)
    o = a.copy()
    o[..., 0] = np.where(band, np.minimum(r, g + 55), r)
    o[..., 2] = np.where(band, np.minimum(b, g + 30), b)
    alpha = np.where(bg, 0, 255).astype("uint8")
    out = Image.fromarray(np.dstack([np.clip(o, 0, 255).astype("uint8"), alpha]), "RGBA")
    out.putalpha(out.getchannel("A").filter(ImageFilter.GaussianBlur(0.6)))
    out = out.crop(out.getbbox())
    scale = target_h / out.height
    out = out.resize((max(1, int(out.width * scale)), target_h), Image.LANCZOS)
    out.save(dst, "WEBP", quality=90, method=6)
    return out


# ----------------------------------------------------------------- objects
W = H = 340          # canvas for the stretchable chrome, drawn at 2x of a 170dp card


def make_frame():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    box = (6, 6, W - 6, H - 6)
    rad = 46
    drop_shadow(img, box, rad, dy=10, blur=16, strength=120)
    shape(img, box, rad, [(0, (255, 240, 190, 255)), (0.45, (234, 186, 76, 255)), (1, (140, 88, 12, 255))])
    rim = 16
    shape(img, (box[0] + rim * 0.42, box[1] + rim * 0.42, box[2] - rim * 0.42, box[3] - rim * 0.42),
          int(rad * 0.94), [(0, (255, 252, 224, 255)), (0.5, (255, 226, 138, 255)), (1, (206, 146, 34, 255))])
    body = (box[0] + rim, box[1] + rim, box[2] - rim, box[3] - rim)
    shape(img, body, int(rad * 0.90),
          [(0, (255, 250, 233, 255)), (0.22, (253, 238, 205, 255)), (0.60, (249, 227, 186, 255)),
           (1, (238, 208, 158, 255))])
    inner_shadow(img, body, int(rad * 0.90), (120, 76, 18), blur=12, offset=(0, 9), strength=80)
    bevel(img, body, int(rad * 0.90), rim * 0.75, 60, 110)
    gloss(img, body, int(rad * 0.90), top=0.20, strength=45)
    img.save(os.path.join(OUT, "frame.png"))


def make_plaque():
    img = Image.new("RGBA", (420, 170), (0, 0, 0, 0))
    box = (6, 6, 414, 164)
    rad = 44
    # gold aura
    shape(img, (box[0] - 10, box[1] - 10, box[2] + 10, box[3] + 10), int(rad * 1.16),
          [(0, (255, 244, 196, 255)), (0.5, (232, 178, 82, 255)), (1, (150, 96, 14, 255))])
    drop_shadow(img, box, rad, dy=10, blur=14, strength=140)
    shape(img, box, rad, [(0, (150, 96, 58, 255)), (0.5, (116, 66, 31, 255)), (1, (70, 34, 12, 255))])
    d = ImageDraw.Draw(img)
    y = box[1] + 22
    while y < box[3]:
        d.line([(box[0] + 8, y), (box[2] - 8, y)], fill=(0, 0, 0, 55), width=4)
        y += 26
    bevel(img, box, rad, 8, 90, 120)
    gloss(img, box, rad, top=0.40, strength=90)
    for sx in (box[0] + 34, box[2] - 34):
        cy = (box[1] + box[3]) // 2
        d.ellipse((sx - 13, cy - 13, sx + 13, cy + 13), fill=(154, 106, 12, 255))
        d.ellipse((sx - 9, cy - 10, sx + 9, cy + 8), fill=(255, 201, 77, 255))
    img.save(os.path.join(OUT, "plaque.png"))


def make_button(name, top, body, bottom, rim):
    img = Image.new("RGBA", (320, 132), (0, 0, 0, 0))
    box = (8, 6, 312, 112)
    rad = (box[3] - box[1]) // 2 - 8
    drop_shadow(img, box, rad, dy=12, blur=10, strength=110)
    shape(img, box, rad, [(0, tuple(min(255, c + 26) for c in top) + (255,)), (0.34, top + (255,)),
                          (0.74, body + (255,)), (1, tuple(int(c * 0.78) for c in bottom) + (255,))])
    bevel(img, box, rad, 7, 70, 110)
    gloss(img, box, rad, top=0.46, strength=120)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle(box, rad, outline=rim + (200,), width=4)
    img.save(os.path.join(OUT, name))


def make_capsule():
    img = Image.new("RGBA", (320, 104), (0, 0, 0, 0))
    box = (6, 8, 314, 88)
    rad = (box[3] - box[1]) // 2
    drop_shadow(img, box, rad, dy=8, blur=8, strength=80)
    shape(img, box, rad, [(0, (255, 252, 240, 255)), (0.55, (255, 242, 214, 255)), (1, (242, 220, 168, 255))])
    gloss(img, box, rad, top=0.50, strength=120)
    inner_shadow(img, box, rad, (150, 100, 20), blur=6, offset=(0, 5), strength=60)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle(box, rad, outline=(216, 162, 56, 255), width=3)
    img.save(os.path.join(OUT, "capsule.png"))


def make_socket():
    img = Image.new("RGBA", (200, 200), (0, 0, 0, 0))
    box = (12, 12, 188, 188)
    rad = 44
    drop_shadow(img, box, rad, dy=8, blur=10, strength=110)
    shape(img, box, rad, [(0, (255, 251, 228, 255)), (1, (239, 203, 128, 255))])
    bevel(img, box, rad, 9, 70, 100)
    gloss(img, box, rad, top=0.44, strength=96)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle(box, rad, outline=(255, 216, 120, 255), width=6)
    d.rounded_rectangle((box[0] + 14, box[1] + 14, box[2] - 14, box[3] - 14), rad - 12, fill=(184, 116, 12, 45))
    leaf(img, box[0] + 22, box[3] - 18, 56, 42)
    leaf(img, box[2] - 22, box[3] - 18, 56, -42)
    img.save(os.path.join(OUT, "socket.png"))


def make_ornaments():
    cw, chh = 240, 170
    img = Image.new("RGBA", (cw, chh), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    body = [(30, 120), (34, 52), (86, 92), (120, 24), (154, 92), (206, 52), (210, 120)]
    d.polygon(body, fill=(255, 226, 130, 255), outline=(122, 74, 5, 255), width=4)
    d.rounded_rectangle((30, 104, 210, 132), 8, fill=(176, 110, 6, 255))
    for cx, col in ((120, (229, 69, 95, 255)), (86, (89, 195, 255, 255)), (154, (89, 195, 255, 255))):
        d.ellipse((cx - 7, 62 - 7, cx + 7, 62 + 7), fill=col)
    d.ellipse((110, 40, 130, 60), fill=(255, 255, 255, 150))
    img.save(os.path.join(OUT, "crown.png"))

    img = Image.new("RGBA", (260, 220), (0, 0, 0, 0))
    for rot, dx, dy, s in ((-58, -34, 18, 108), (-18, -6, 0, 128), (22, 26, 12, 104)):
        leaf(img, 130 + dx, 110 + dy, s, rot)
    flower(img, 196, 58, 26)
    img.save(os.path.join(OUT, "sprig.png"))

    img = Image.new("RGBA", (120, 120), (0, 0, 0, 0))
    flower(img, 60, 60, 40, petal=(255, 143, 163))
    img.save(os.path.join(OUT, "flower_pink.png"))

    img = Image.new("RGBA", (120, 120), (0, 0, 0, 0))
    flower(img, 60, 60, 40, petal=(185, 166, 255))
    img.save(os.path.join(OUT, "flower_violet.png"))

    img = Image.new("RGBA", (140, 160), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon([(70 - 42, 40), (70 - 26, 132), (70, 152), (70 + 26, 132), (70 + 42, 40)], fill=(226, 42, 62, 255))
    d.ellipse((28, 8, 112, 74), fill=(238, 58, 76, 255))
    d.ellipse((44, 20, 76, 46), fill=(255, 140, 150, 170))
    for gx, gy in ((54, 76), (86, 88), (68, 112), (92, 62)):
        d.ellipse((gx - 4, gy - 5, gx + 4, gy + 5), fill=(255, 232, 170, 235))
    for i in range(5):
        a = math.radians(i * 72 - 90)
        leaf(img, 70 + math.cos(a) * 26, 26 + math.sin(a) * 10, 40, i * 72, (28, 92, 40), (104, 190, 74))
    img.save(os.path.join(OUT, "strawberry.png"))

    img = Image.new("RGBA", (160, 130), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((16, 30, 144, 106), fill=(246, 200, 30, 255))
    d.ellipse((30, 38, 120, 82), fill=(255, 232, 96, 255))
    d.ellipse((40, 44, 82, 64), fill=(255, 250, 200, 190))
    leaf(img, 126, 26, 58, 34)
    img.save(os.path.join(OUT, "lemon.png"))

    img = Image.new("RGBA", (140, 120), (0, 0, 0, 0))
    for dx, dy, r in ((-22, 10, 22), (2, -10, 24), (24, 14, 20), (-6, 30, 19), (16, 38, 17)):
        d = ImageDraw.Draw(img)
        d.ellipse((70 + dx - r, 60 + dy - r, 70 + dx + r, 60 + dy + r), fill=(96, 74, 190, 255))
        d.ellipse((70 + dx - r * 0.5, 60 + dy - r * 0.6, 70 + dx + r * 0.2, 60 + dy - r * 0.05),
                  fill=(158, 140, 226, 200))
    leaf(img, 104, 18, 54, 40, (28, 92, 40), (104, 190, 74))
    img.save(os.path.join(OUT, "berries.png"))

    img = Image.new("RGBA", (150, 150), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((10, 10, 140, 140), fill=(255, 214, 70, 255), outline=(176, 116, 12, 255), width=5)
    d.ellipse((26, 26, 124, 124), fill=(255, 238, 150, 255))
    star(img, 75, 75, 34)
    d.ellipse((44, 40, 70, 64), fill=(255, 255, 255, 130))
    img.save(os.path.join(OUT, "coin_star.png"))


def main():
    made = []
    for name, src in (("char_idle", "character_base.png"),
                      ("char_cheer", "character_cheer.png"),
                      ("char_sad", "character_sad.png")):
        p = os.path.join(SRC, src)
        if not os.path.exists(p):
            print("missing", p)
            continue
        out = os.path.join(OUT, name + ".webp")
        im = key_character(p, out)
        made.append((name + ".webp", im.size, os.path.getsize(out)))

    bgp = os.path.join(SRC, "bg_garden.png")
    if os.path.exists(bgp):
        bg = Image.open(bgp).convert("RGB")
        bg = bg.resize((900, int(900 * bg.height / bg.width)), Image.LANCZOS)
        bg.save(os.path.join(OUT, "bg_garden.webp"), "WEBP", quality=84, method=6)
        made.append(("bg_garden.webp", bg.size, os.path.getsize(os.path.join(OUT, "bg_garden.webp"))))

    make_frame()
    make_plaque()
    make_button("btn_lime.png", (217, 251, 144), (150, 226, 76), (60, 132, 30), (255, 255, 255))
    make_button("btn_royal.png", (140, 176, 255), (58, 106, 226), (30, 52, 150), (200, 226, 255))
    make_button("btn_gold.png", (255, 232, 160), (233, 176, 52), (168, 110, 12), (255, 243, 200))
    make_button("btn_danger.png", (255, 172, 184), (214, 62, 86), (140, 22, 40), (255, 214, 220))
    make_capsule()
    make_socket()
    make_ornaments()

    for f in sorted(os.listdir(OUT)):
        p = os.path.join(OUT, f)
        made.append((f, Image.open(p).size, os.path.getsize(p)))
    for f, size, n in made:
        print(f"{f:24s} {str(size):14s} {n/1024:8.1f} kB")
    print("total", round(sum(n for _, _, n in made) / 1024), "kB in", OUT)


if __name__ == "__main__":
    main()
