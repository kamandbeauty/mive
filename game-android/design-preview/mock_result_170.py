"""Composite preview of the 1.7.0 result screen using the *shipped* assets.

Unlike mock_result_160.py (which repainted everything with Pillow primitives),
this script pastes the real files from assets/art at the real VictoryGeometry
positions, so it shows how the painted skin behaves at 412x915: nine-slice
stretching, sprite scale, contrast against the painted backdrop.

Run:  python3 design-preview/mock_result_170.py
"""
import math
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.normpath(os.path.join(HERE, "..", "app/src/main/assets/art"))
sys.path.insert(0, HERE)
from premium_victory import text  # noqa: E402  (Persian shaping lives there)

W, H = 412, 915
PW = W * 0.914
RIM = W * 0.013
PANEL = (W * 0.043, H * 0.072, W * 0.957, H * 0.920)
INNER = (PANEL[0] + RIM, PANEL[1] + RIM, PANEL[2] - RIM, PANEL[3] - RIM)
INNER_W = INNER[2] - INNER[0]
INNER_H = INNER[3] - INNER[1]
GARDEN_H = INNER_H * 0.075
GARDEN_TOP = INNER[3] - GARDEN_H
AVAIL = INNER_H - GARDEN_H

PLAQUE = (INNER[0] + PW * 0.185, 52.2, INNER[0] + PW * 0.815, 158.0)
CHIP = (113.4, 179.3, 309.3, 213.2)
STAR_CY = 288.3
STAR_R = 25.5
SOCKET_R = STAR_R * 1.36
SCORE = (50.2, 363.3, 361.8, 500.9)
CAPSULE = (INNER[0] + INNER_W * 0.5 - PW * 0.330, 528.7,
           INNER[0] + INNER_W * 0.5 + PW * 0.330, 572.6)
PRIMARY = (54.0, 613.8, 358.0, 681.0)
SECONDARY = (54.0, 702.0, 358.0, 760.0)


def load(name):
    for ext in (".webp", ".png"):
        p = os.path.join(ART, name + ext)
        if os.path.exists(p):
            return Image.open(p).convert("RGBA")
    raise FileNotFoundError(name)


def nine(img, src, rect, inset):
    """Same nine-slice split as Art.nineSlice."""
    bw, bh = src.size
    i = min(inset, bw * 0.45, bh * 0.45)
    x0, y0, x1, y1 = rect
    iw = min(i, (x1 - x0) / 2.05)
    ih = min(i, (y1 - y0) / 2.05)
    xs = [0, i, bw - i, bw]
    ys = [0, i, bh - i, bh]
    dxs = [x0, x0 + iw, x1 - iw, x1]
    dys = [y0, y0 + ih, y1 - ih, y1]
    for r in range(3):
        for col in range(3):
            sw, sh = xs[col + 1] - xs[col], ys[r + 1] - ys[r]
            dw, dh = dxs[col + 1] - dxs[col], dys[r + 1] - dys[r]
            if sw < 1 or sh < 1 or dw < 1 or dh < 1:
                continue
            piece = src.crop((int(xs[col]), int(ys[r]), int(xs[col + 1]), int(ys[r + 1])))
            piece = piece.resize((max(1, int(round(dw))), max(1, int(round(dh)))), Image.LANCZOS)
            img.alpha_composite(piece, (int(round(dxs[col])), int(round(dys[r]))))


def sprite(img, name, cx, cy, size, rot=0.0):
    s = load(name)
    w = size * s.width / s.height
    s = s.resize((max(1, int(w)), max(1, int(size))), Image.LANCZOS)
    if rot:
        s = s.rotate(-rot, resample=Image.BICUBIC, expand=True)
    img.alpha_composite(s, (int(cx - s.width / 2), int(cy - s.height / 2)))


def star(img, cx, cy, r, fill=(255, 216, 86, 255)):
    d = ImageDraw.Draw(img)
    pts = []
    for i in range(10):
        rr = r if i % 2 == 0 else r * 0.46
        a = math.radians(-90 + i * 36)
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    d.polygon(pts, fill=fill, outline=(178, 118, 12, 255))
    d.polygon([(cx + (x - cx) * 0.60, cy + (y - cy) * 0.60) for x, y in pts], fill=(255, 240, 170, 255))


def coin(img, cx, cy, r):
    img.alpha_composite(load("coin_star").resize((int(r * 2), int(r * 2)), Image.LANCZOS),
                        (int(cx - r), int(cy - r)))


def vgrad(img, box, radius, stops):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    y0, y1 = box[1], box[3]
    for y in range(int(y0), int(y1) + 1):
        k = (y - y0) / max(1.0, y1 - y0)
        for i in range(len(stops) - 1):
            p0, c0 = stops[i]
            p1, c1 = stops[i + 1]
            if p0 <= k <= p1:
                t = 0 if p1 == p0 else (k - p0) / (p1 - p0)
                d.line([(box[0], y), (box[2], y)],
                       fill=tuple(int(c0[j] + (c1[j] - c0[j]) * t) for j in range(4)))
                break
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(box, radius, fill=255)
    img.paste(layer, (0, 0), mask)


def main():
    bg = load("bg_garden")
    scale = max(W / bg.width, H / bg.height)
    bg = bg.resize((int(bg.width * scale), int(bg.height * scale)), Image.LANCZOS)
    img = Image.new("RGBA", (W, H))
    img.alpha_composite(bg, (int((W - bg.width) / 2), int((H - bg.height) / 2)))

    img.alpha_composite(Image.new("RGBA", (W, H), (4, 21, 13, 186)))

    # ------------------------------------------------------------- window
    nine(img, load("frame"), PANEL, 58)

    # corner ornaments
    sprite(img, "sprig", INNER[0] + PW * 0.048, INNER[1] + PW * 0.062, PW * 0.062 * 1.9, -24)
    sprite(img, "sprig", INNER[2] - PW * 0.048, INNER[1] + PW * 0.062, PW * 0.062 * 1.9, 24)
    sprite(img, "flower_pink", INNER[0] + PW * 0.052, INNER[1] + PW * 0.086, PW * 0.060)
    sprite(img, "flower_violet", INNER[2] - PW * 0.052, INNER[1] + PW * 0.086, PW * 0.060)
    sprite(img, "strawberry", INNER[0] + PW * 0.086, INNER[1] + PW * 0.080, PW * 0.052)
    sprite(img, "berries", INNER[2] - PW * 0.085, INNER[1] + PW * 0.078, PW * 0.045)

    # hedge
    d = ImageDraw.Draw(img)
    for hx, hw in ((INNER[0] + PW * 0.14, PW * 0.38), (INNER[2] - PW * 0.14, PW * 0.38),
                   (INNER[0] + INNER_W / 2, PW * 0.46)):
        d.ellipse((hx - hw / 2, GARDEN_TOP + GARDEN_H * 0.20, hx + hw / 2, GARDEN_TOP + GARDEN_H * 1.25),
                  fill=(25, 82, 48, 255))
        d.ellipse((hx - hw * 0.42, GARDEN_TOP + GARDEN_H * 0.16, hx + hw * 0.42, GARDEN_TOP + GARDEN_H * 0.86),
                  fill=(38, 112, 62, 255))
    for i in range(9):
        fx = INNER[0] + PW * (0.09 + 0.82 * i / 8)
        fy = GARDEN_TOP + GARDEN_H * (0.34 + 0.22 * (i % 3))
        sprite(img, "flower_pink" if i % 2 == 0 else "flower_violet", fx, fy, PW * 0.042)
    sprite(img, "lemon", INNER[2] - PW * 0.105, GARDEN_TOP + GARDEN_H * 0.46, PW * 0.098)

    # ---------------------------------------------------------------- plaque
    nine(img, load("plaque"), PLAQUE, 58)
    ph = PLAQUE[3] - PLAQUE[1]
    sprite(img, "crown", (PLAQUE[0] + PLAQUE[2]) / 2, PLAQUE[1] - ph * 0.02, ph * 0.46)
    sprite(img, "flower_pink", PLAQUE[0] + ph * 0.35, (PLAQUE[1] + PLAQUE[3]) / 2, ph * 0.20)
    sprite(img, "flower_violet", PLAQUE[2] - ph * 0.35, (PLAQUE[1] + PLAQUE[3]) / 2, ph * 0.20)
    tsize = ph * 0.50
    text(img, ((PLAQUE[0] + PLAQUE[2]) / 2 + ph * 0.012, (PLAQUE[1] + PLAQUE[3]) / 2 + ph * 0.40),
         "آفرین!", tsize, (255, 250, 226, 255), stroke=int(tsize * 0.15), stroke_fill=(74, 38, 10, 255))
    text(img, ((PLAQUE[0] + PLAQUE[2]) / 2, (PLAQUE[1] + PLAQUE[3]) / 2 + ph * 0.36),
         "آفرین!", tsize, (255, 212, 74, 255), stroke=int(tsize * 0.09), stroke_fill=(78, 42, 8, 255))

    # ------------------------------------------------------------------ chip
    cx0, cy0, cx1, cy1 = CHIP
    ch = cy1 - cy0
    vgrad(img, CHIP, ch / 2, [(0, (156, 98, 52, 255)), (0.55, (107, 63, 28, 255)), (1, (62, 33, 16, 255))])
    d.rounded_rectangle(CHIP, ch / 2, outline=(255, 201, 77, 235), width=max(1, int(ch * 0.05)))
    text(img, ((cx0 + cx1) / 2, (cy0 + cy1) / 2 + ch * 0.14), "سطح ۱۲ • باغ بهار", ch * 0.38,
         (255, 246, 226, 255), stroke=1, stroke_fill=(52, 26, 6, 255))

    # --------------------------------------------------------------- sockets
    for i in range(3):
        sx = (INNER[0] + INNER_W / 2) + (i - 1) * PW * 0.205
        sprite(img, "socket", sx, STAR_CY, SOCKET_R * 2.10)
        star(img, sx, STAR_CY, STAR_R * 1.02)
        star(img, sx, STAR_CY - STAR_R * 0.05, STAR_R * 0.86)

    # ----------------------------------------------------------- score plate
    sh = SCORE[3] - SCORE[1]
    vgrad(img, SCORE, sh * 0.30, [(0, (46, 156, 99, 255)), (0.35, (23, 116, 79, 255)),
                                  (1, (3, 32, 15, 255))])
    d.rounded_rectangle(SCORE, sh * 0.30, outline=(255, 217, 138, 230), width=3)
    text(img, ((SCORE[0] + SCORE[2]) / 2, SCORE[1] + sh * 0.28), "امتیاز", sh * 0.19, (207, 238, 220, 255))
    text(img, ((SCORE[0] + SCORE[2]) / 2, SCORE[3] - sh * 0.16), "۱۴۵۷", sh * 0.42,
         (255, 252, 242, 255), stroke=int(sh * 0.05), stroke_fill=(3, 32, 15, 255))

    # ------------------------------------------------------------- capsule
    caph = CAPSULE[3] - CAPSULE[1]
    nine(img, load("capsule"), (CAPSULE[0], CAPSULE[1] - caph * 0.10, CAPSULE[2], CAPSULE[3] + caph * 0.10), 46)
    coin(img, CAPSULE[2] - caph * 0.66, (CAPSULE[1] + CAPSULE[3]) / 2, caph * 0.36)
    text(img, ((CAPSULE[0] + CAPSULE[2]) / 2 - caph * 0.26, (CAPSULE[1] + CAPSULE[3]) / 2 + caph * 0.14),
         "+۸۶ سکه", caph * 0.40, (138, 90, 5, 255))

    # ------------------------------------------------------------- buttons
    for box, asset, label, ink, glyph in (
        (PRIMARY, "btn_lime", "سطح بعد", (20, 51, 11, 255), "chevron"),
        (SECONDARY, "btn_royal", "نقشه", (255, 253, 245, 255), "home"),
    ):
        nine(img, load(asset), box, 52)
        bh = box[3] - box[1]
        text(img, ((box[0] + box[2]) / 2 - bh * 0.14, (box[1] + box[3]) / 2 + bh * 0.14), label,
             bh * 0.36, ink, stroke=int(bh * 0.05),
             stroke_fill=(255, 255, 255, 110) if glyph == "chevron" else (14, 30, 96, 255))
        ix, iy, ir = box[2] - bh * 0.60, (box[1] + box[3]) / 2, bh * 0.24
        if glyph == "chevron":
            d.ellipse((ix - ir, iy - ir, ix + ir, iy + ir), fill=(96, 186, 52, 190),
                      outline=(230, 255, 190, 230), width=2)
            d.line([(ix - ir * 0.22, iy - ir * 0.44), (ix + ir * 0.30, iy), (ix - ir * 0.22, iy + ir * 0.44)],
                   fill=(255, 255, 255, 250), width=max(3, int(bh * 0.07)), joint="curve")
        else:
            d.polygon([(ix - ir, iy - ir * 0.10), (ix, iy - ir * 0.85), (ix + ir, iy - ir * 0.10),
                       (ix + ir * 0.60, iy - ir * 0.10), (ix + ir * 0.60, iy + ir * 0.70),
                       (ix - ir * 0.60, iy + ir * 0.70), (ix - ir * 0.60, iy - ir * 0.10)],
                      fill=(255, 255, 255, 245))

    # ---------------------------------------------------------------- Laleh
    girl_h = AVAIL * 0.225
    feet = INNER[3] - GARDEN_H * 0.12
    gx = INNER[0] + girl_h * 0.30
    d.ellipse((gx - girl_h * 0.26, feet - girl_h * 0.03, gx + girl_h * 0.26, feet + girl_h * 0.03),
              fill=(4, 21, 13, 150))
    sprite(img, "char_cheer", gx, feet - girl_h / 2, girl_h)
    return img


if __name__ == "__main__":
    out = main()
    out.convert("RGB").save(os.path.join(HERE, "premium_result_170.png"))
    print("ok")
