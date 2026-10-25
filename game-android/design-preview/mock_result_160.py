"""Faithful preview of the 1.6.0 result screen.

Every number below is copied from VictoryGeometry / Screens.drawResult at 412x915,
and every shading call mirrors the Art.* helper that runs on device:
frame -> three gold bands + cream body + bevel ring + gloss band,
woodBody -> aura + wood gradient + grain + bevel + gloss + studs,
socketPlate -> cream plate + bevel + gloss + gold rim + leaf pair,
plus the lime/royal buttons and Laleh standing in the hedge.
"""
import math
from PIL import Image, ImageDraw

from premium_victory import (
    W, H, fa, shape, inner_shadow, drop_shadow, gloss, glow,
    leaf, flower, text, strawberry, lemon, blueberries, F_BLACK,
)

GOLD = (255, 201, 77, 255)

# ------------------------------------------------------------------ geometry
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


def laleh(img, cx, feet, height, mood="cheer"):
    """Laleh — straw hat, brown bob, green dress, cream apron."""
    u = height / 100.0
    d = ImageDraw.Draw(img)
    skin, skin_sh = (255, 221, 187, 255), (232, 185, 143, 255)
    hair, hair_hi = (107, 62, 30, 255), (166, 105, 60, 255)
    dress, dress_d = (79, 166, 74, 255), (44, 117, 52, 255)
    apron = (255, 246, 229, 255)
    straw, straw_d = (242, 194, 91, 255), (192, 139, 30, 255)

    head_r = 15 * u
    head_cy = feet - 58 * u

    # legs + boots
    d.rounded_rectangle((cx - 7.2 * u, feet - 22 * u, cx - 1.6 * u, feet - 2 * u), 3 * u, fill=skin_sh)
    d.rounded_rectangle((cx + 1.6 * u, feet - 22 * u, cx + 7.2 * u, feet - 2 * u), 3 * u, fill=skin_sh)
    d.rounded_rectangle((cx - 8.6 * u, feet - 6 * u, cx - 0.8 * u, feet), 2.6 * u, fill=straw_d)
    d.rounded_rectangle((cx + 0.8 * u, feet - 6 * u, cx + 8.6 * u, feet), 2.6 * u, fill=straw_d)

    # dress + apron
    d.polygon([(cx - 11 * u, feet - 34 * u), (cx + 11 * u, feet - 34 * u),
               (cx + 19 * u, feet - 17 * u), (cx - 19 * u, feet - 17 * u)], fill=dress)
    d.rounded_rectangle((cx - 11.4 * u, feet - 40 * u, cx + 11.4 * u, feet - 26 * u), 6 * u, fill=dress_d)
    d.polygon([(cx - 7 * u, feet - 33 * u), (cx + 7 * u, feet - 33 * u),
               (cx + 10 * u, feet - 15 * u), (cx - 10 * u, feet - 15 * u)], fill=apron)

    # arms
    lift = -34 if mood == "cheer" else 8
    for sgn in (-1, 1):
        d.line([(cx + sgn * 10 * u, feet - 38 * u), (cx + sgn * 17 * u, feet - (20 + lift) * u)],
               fill=skin, width=max(2, int(5.4 * u)))
        d.ellipse((cx + sgn * 17 * u - 3.1 * u, feet - (19.4 + lift) * u - 3.1 * u,
                   cx + sgn * 17 * u + 3.1 * u, feet - (19.4 + lift) * u + 3.1 * u), fill=skin)

    # head
    d.rounded_rectangle((cx - 3.4 * u, head_cy + 10 * u, cx + 3.4 * u, feet - 38 * u), 2 * u, fill=skin_sh)
    d.ellipse((cx - head_r * 1.06, head_cy - head_r * 1.06, cx + head_r * 1.06, head_cy + head_r * 1.06), fill=hair)
    d.rounded_rectangle((cx - head_r * 0.92, head_cy, cx + head_r * 0.92, head_cy + head_r * 1.15),
                        head_r * 0.7, fill=hair)
    d.ellipse((cx - head_r * 0.92, head_cy + 1.2 * u - head_r * 0.92,
               cx + head_r * 0.92, head_cy + 1.2 * u + head_r * 0.92), fill=skin)
    # fringe
    d.chord((cx - head_r * 0.98, head_cy - head_r * 1.0, cx + head_r * 0.98, head_cy + head_r * 0.30),
            180, 360, fill=hair)
    d.ellipse((cx - head_r * 0.55, head_cy - head_r * 0.95, cx - head_r * 0.05, head_cy - head_r * 0.15), fill=hair)
    d.ellipse((cx + head_r * 0.10, head_cy - head_r * 0.98, cx + head_r * 0.62, head_cy - head_r * 0.20), fill=hair)

    eye_y = head_cy + 1.6 * u
    eye_dx = head_r * 0.40
    if mood == "cheer":
        for sgn in (-1, 1):
            d.arc((cx + sgn * eye_dx - 2.6 * u, eye_y - 2.4 * u,
                   cx + sgn * eye_dx + 2.6 * u, eye_y + 2.0 * u), 200, 340,
                  fill=(58, 36, 21, 255), width=max(2, int(2.4 * u)))
        d.arc((cx - 3.8 * u, eye_y + 3.2 * u, cx + 3.8 * u, eye_y + 8.4 * u), 10, 170,
              fill=(138, 51, 37, 255), width=max(2, int(1.9 * u)))
    for sgn in (-1, 1):
        d.ellipse((cx + sgn * head_r * 0.62 - 2.5 * u, eye_y + 0.7 * u,
                   cx + sgn * head_r * 0.62 + 2.5 * u, eye_y + 5.7 * u), fill=(255, 123, 150, 90))

    # hat
    hat_y = head_cy - head_r * 0.72
    d.ellipse((cx - head_r * 1.55, hat_y - 3.4 * u, cx + head_r * 1.55, hat_y + 4.6 * u), fill=straw)
    d.ellipse((cx - head_r * 1.55, hat_y + 1.2 * u, cx + head_r * 1.55, hat_y + 4.6 * u), fill=straw_d)
    d.rounded_rectangle((cx - head_r * 0.80, hat_y - head_r * 0.72, cx + head_r * 0.80, hat_y + 1.6 * u),
                        head_r * 0.46, fill=straw)
    d.rounded_rectangle((cx - head_r * 0.82, hat_y - head_r * 0.20, cx + head_r * 0.82, hat_y + 1.2 * u),
                        head_r * 0.18, fill=(232, 84, 107, 255))
    leaf(img, cx + head_r * 0.62, hat_y - head_r * 0.30, head_r * 0.52, 32)

    if mood == "cheer":
        for i in range(3):
            a = i * 2.09
            glow(img, cx + math.cos(a) * height * 0.30, head_cy + math.sin(a) * height * 0.18,
                 height * 0.10, GOLD, 120, blur=8)


def star(img, cx, cy, r, filled=True):
    d = ImageDraw.Draw(img)
    pts = []
    for i in range(10):
        rr = r if i % 2 == 0 else r * 0.46
        a = math.radians(-90 + i * 36)
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    d.polygon(pts, fill=(255, 216, 86, 255) if filled else (176, 164, 140, 255),
              outline=(178, 118, 12, 255) if filled else (140, 130, 110, 255))
    if filled:
        d.polygon([(cx + (x - cx) * 0.62, cy + (y - cy) * 0.62) for x, y in pts],
                  fill=(255, 240, 170, 255))


def bevel_ring(img, box, radius, t, top=90, bottom=130):
    """Mirror of Art.bevel: a clipped stroke shaded dark at the top, lit at the bottom."""
    x0, y0, x1, y1 = box
    ring = Image.new("RGBA", img.size, (0, 0, 0, 0))
    rd = ImageDraw.Draw(ring)
    steps = 40
    for i in range(steps):
        k = i / (steps - 1)
        if k < 0.24:
            col = (42, 18, 0, int(top * (1 - k / 0.24)))
        elif k > 0.76:
            col = (255, 255, 255, int(bottom * ((k - 0.76) / 0.24)))
        else:
            col = None
        if col is None or col[3] <= 0:
            continue
        yy = y0 + (y1 - y0) * k
        rd.line([(x0 + radius * 0.5, yy), (x1 - radius * 0.5, yy)], fill=col, width=int(t))
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(box, radius, fill=255)
    inner = Image.new("L", img.size, 0)
    ImageDraw.Draw(inner).rounded_rectangle((x0 + t, y0 + t, x1 - t, y1 - t), max(1, radius - t), fill=255)
    from PIL import ImageChops
    ring.putalpha(ImageChops.multiply(ring.getchannel("A"), ImageChops.subtract(mask, inner)))
    img.alpha_composite(ring)


def coin(img, cx, cy, r):
    d = ImageDraw.Draw(img)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(255, 214, 70, 255), outline=(176, 116, 12, 255), width=2)
    d.ellipse((cx - r * 0.72, cy - r * 0.72, cx + r * 0.72, cy + r * 0.72), fill=(255, 238, 150, 255))
    star(img, cx, cy, r * 0.54)
    d.ellipse((cx - r * 0.52, cy - r * 0.62, cx - r * 0.14, cy - r * 0.24), fill=(255, 255, 255, 120))


def crown(img, cx, cy, wd, ht):
    d = ImageDraw.Draw(img)
    body = [(cx - wd * 0.5, cy + ht * 0.34), (cx - wd * 0.48, cy - ht * 0.14),
            (cx - wd * 0.24, cy + ht * 0.12), (cx, cy - ht * 0.46),
            (cx + wd * 0.24, cy + ht * 0.12), (cx + wd * 0.48, cy - ht * 0.14),
            (cx + wd * 0.5, cy + ht * 0.34)]
    d.polygon(body, fill=(255, 226, 130, 255), outline=(122, 74, 5, 255))
    d.rounded_rectangle((cx - wd * 0.50, cy + ht * 0.16, cx + wd * 0.50, cy + ht * 0.36), ht * 0.08,
                        fill=(176, 110, 6, 255))
    d.ellipse((cx - wd * 0.055, cy + ht * 0.005, cx + wd * 0.055, cy + ht * 0.115), fill=(229, 69, 95, 255))


def main():
    from premium_victory import background
    img = background()
    scrim = Image.new("RGBA", img.size, (4, 21, 13, 178))
    img.alpha_composite(scrim)
    glow(img, W * 0.5, H * 0.36, W * 0.95, (255, 217, 138, 255), 36, blur=60)

    # ---------------------------------------------------------------- frame
    drop_shadow(img, PANEL, W * 0.060, dy=13, blur=18, strength=150)
    shape(img, PANEL, W * 0.060, [(0, (255, 240, 190, 255)), (0.45, (234, 186, 76, 255)), (1, (140, 88, 12, 255))])
    shape(img, (PANEL[0] + RIM * 0.42, PANEL[1] + RIM * 0.42, PANEL[2] - RIM * 0.42, PANEL[3] - RIM * 0.42),
          W * 0.056, [(0, (255, 252, 224, 255)), (0.5, (255, 226, 138, 255)), (1, (206, 146, 34, 255))])
    shape(img, INNER, W * 0.052,
          [(0, (255, 250, 233, 255)), (0.22, (253, 238, 205, 255)), (0.60, (249, 227, 186, 255)), (1, (238, 208, 158, 255))])
    inner_shadow(img, INNER, W * 0.052, (120, 76, 18), blur=16, offset=(0, 10), strength=95)
    bevel_ring(img, INNER, W * 0.052, RIM * 0.75, 60, 110)
    gloss(img, INNER, W * 0.052, top=0.20, strength=42)

    # ------------------------------------------------------------ ornaments
    for (mx, my, rot) in ((INNER[0] + PW * 0.034, INNER[1] + PW * 0.046, 148),
                          (INNER[2] - PW * 0.034, INNER[1] + PW * 0.046, 32),
                          (INNER[0] + PW * 0.034, INNER[3] - PW * 0.030, 212),
                          (INNER[2] - PW * 0.034, INNER[3] - PW * 0.030, 328)):
        for k in (-1, 0, 1):
            a = math.radians(rot + k * 34)
            leaf(img, mx + math.cos(a) * PW * 0.026, my + math.sin(a) * PW * 0.026, PW * 0.058, rot + k * 34 - 90)
        flower(img, mx, my, PW * 0.020)
    strawberry(img, INNER[0] + PW * 0.086, INNER[1] + PW * 0.080, PW * 0.034)
    blueberries(img, INNER[2] - PW * 0.085, INNER[1] + PW * 0.078, PW * 0.010)

    # hedge along the base
    for (hx, hw) in ((INNER[0] + PW * 0.14, PW * 0.38), (INNER[2] - PW * 0.14, PW * 0.38),
                     (INNER[0] + INNER_W / 2, PW * 0.46)):
        d = ImageDraw.Draw(img)
        d.ellipse((hx - hw / 2, GARDEN_TOP + GARDEN_H * 0.20, hx + hw / 2, GARDEN_TOP + GARDEN_H * 1.25),
                  fill=(25, 82, 48, 255))
        d.ellipse((hx - hw * 0.42, GARDEN_TOP + GARDEN_H * 0.16, hx + hw * 0.42, GARDEN_TOP + GARDEN_H * 0.86),
                  fill=(38, 112, 62, 255))
    cols = [(255, 143, 163), (255, 209, 102), (185, 166, 255)]
    for i in range(9):
        fx = INNER[0] + PW * (0.09 + 0.82 * i / 8)
        fy = GARDEN_TOP + GARDEN_H * (0.34 + 0.22 * (i % 3))
        flower(img, fx, fy, PW * 0.016, petal=cols[i % 3], core=(255, 243, 196))
    for (fx, fy, fr) in ((INNER[0] + PW * 0.105, GARDEN_TOP + GARDEN_H * 0.46, PW * 0.135),
                         (INNER[2] - PW * 0.105, GARDEN_TOP + GARDEN_H * 0.46, PW * 0.120),
                         (INNER[2] - PW * 0.235, GARDEN_TOP + GARDEN_H * 0.76, PW * 0.095)):
        glow(img, fx, fy, fr, (198, 243, 107, 255), 70, blur=12)
        lemon(img, fx, fy, fr * 0.62) if int(fx) % 2 else strawberry(img, fx, fy, fr * 0.95)

    # --------------------------------------------------------------- plaque
    px0, py0, px1, py1 = PLAQUE
    ph = py1 - py0
    rad = ph * 0.34
    halo = (px0 - ph * 0.07, py0 - ph * 0.07, px1 + ph * 0.07, py1 + ph * 0.07)
    shape(img, halo, rad * 1.16, [(0, (255, 244, 196, 255)), (0.5, (232, 178, 82, 255)), (1, (150, 96, 14, 255))])
    drop_shadow(img, (px0, py0, px1, py1), rad, dy=ph * 0.16, blur=12, strength=150)
    shape(img, PLAQUE, rad, [(0, (150, 96, 58, 255)), (0.5, (116, 66, 31, 255)), (1, (70, 34, 12, 255))])
    d = ImageDraw.Draw(img)
    y = py0 + ph * 0.16
    while y < py1:
        d.line([(px0 + 2, y), (px1 - 2, y)], fill=(0, 0, 0, 60), width=max(1, int(ph * 0.022)))
        y += ph * 0.16
    bevel_ring(img, PLAQUE, rad, ph * 0.05, 90, 120)
    gloss(img, PLAQUE, rad, top=0.40, strength=90)
    for sx in (px0 + ph * 0.26, px1 - ph * 0.26):
        d.ellipse((sx - ph * 0.075, (py0 + py1) / 2 - ph * 0.075,
                   sx + ph * 0.075, (py0 + py1) / 2 + ph * 0.075), fill=(154, 106, 12, 255))
        d.ellipse((sx - ph * 0.050, (py0 + py1) / 2 - ph * 0.055,
                   sx + ph * 0.050, (py0 + py1) / 2 + ph * 0.045), fill=(255, 201, 77, 255))
    flower(img, px0 + ph * 0.35, (py0 + py1) / 2, ph * 0.090, petal=(255, 143, 163))
    flower(img, px1 - ph * 0.35, (py0 + py1) / 2, ph * 0.090, petal=(185, 166, 255))
    tsize = ph * 0.50
    text(img, ((px0 + px1) / 2 + ph * 0.012, (py0 + py1) / 2 + ph * 0.40), "آفرین!", tsize,
         (255, 250, 226, 255), stroke=int(tsize * 0.15), stroke_fill=(74, 38, 10, 255))
    text(img, ((px0 + px1) / 2, (py0 + py1) / 2 + ph * 0.36), "آفرین!", tsize,
         (255, 212, 74, 255), stroke=int(tsize * 0.09), stroke_fill=(78, 42, 8, 255))
    crown(img, (px0 + px1) / 2, py0 - ph * 0.02, ph * 0.62, ph * 0.34)

    # ----------------------------------------------------------------- chip
    cx0, cy0, cx1, cy1 = CHIP
    ch = cy1 - cy0
    drop_shadow(img, CHIP, ch / 2, dy=ch * 0.14, blur=8, strength=90)
    shape(img, CHIP, ch / 2, [(0, (156, 98, 52, 255)), (0.55, (107, 63, 28, 255)), (1, (62, 33, 16, 255))])
    bevel_ring(img, CHIP, ch / 2, ch * 0.07, 80, 110)
    gloss(img, CHIP, ch / 2, top=0.48, strength=70)
    d.rounded_rectangle(CHIP, ch / 2, outline=(255, 201, 77, 235), width=max(1, int(ch * 0.05)))
    text(img, ((cx0 + cx1) / 2, (cy0 + cy1) / 2 + ch * 0.14), "سطح ۱۲ • باغ بهار", ch * 0.38,
         (255, 246, 226, 255), stroke=1, stroke_fill=(52, 26, 6, 255))

    # -------------------------------------------------------------- sockets
    for i in range(3):
        sx = (INNER[0] + INNER_W / 2) + (i - 1) * PW * 0.205
        box = (sx - SOCKET_R, STAR_CY - SOCKET_R, sx + SOCKET_R, STAR_CY + SOCKET_R)
        filled = True
        glow(img, sx, STAR_CY, SOCKET_R * 1.9, GOLD, 55, blur=16)
        shape(img, box, SOCKET_R * 0.50,
              [(0, (255, 251, 228, 255)), (1, (239, 203, 128, 255))] if filled
              else [(0, (237, 228, 210, 255)), (1, (198, 184, 153, 255))])
        bevel_ring(img, box, SOCKET_R * 0.50, SOCKET_R * 0.12, 70, 100)
        gloss(img, box, SOCKET_R * 0.50, top=0.44, strength=96 if filled else 60)
        d.rounded_rectangle(box, SOCKET_R * 0.50, outline=(255, 216, 120, 255) if filled else (246, 232, 204, 255),
                            width=max(2, int(SOCKET_R * 0.11)))
        leaf(img, box[0] + SOCKET_R * 0.06, box[3] - SOCKET_R * 0.04, SOCKET_R * 0.62, 42)
        leaf(img, box[2] - SOCKET_R * 0.06, box[3] - SOCKET_R * 0.04, SOCKET_R * 0.62, -42)
        star(img, sx, STAR_CY, STAR_R * 1.02, True)
        star(img, sx, STAR_CY - STAR_R * 0.05, STAR_R * 0.86, True)
        d.ellipse((sx - STAR_R * 0.46, STAR_CY - STAR_R * 0.52,
                   sx - STAR_R * 0.14, STAR_CY - STAR_R * 0.20), fill=(255, 255, 255, 140))

    # ---------------------------------------------------------- score plate
    drop_shadow(img, SCORE, (SCORE[3] - SCORE[1]) * 0.30, dy=8, blur=10, strength=90)
    shape(img, SCORE, (SCORE[3] - SCORE[1]) * 0.30,
          [(0, (46, 156, 99, 255)), (0.35, (23, 116, 79, 255)), (1, (3, 32, 15, 255))])
    bevel_ring(img, SCORE, (SCORE[3] - SCORE[1]) * 0.30, (SCORE[3] - SCORE[1]) * 0.05, 90, 70)
    gloss(img, SCORE, (SCORE[3] - SCORE[1]) * 0.30, top=0.42, strength=52)
    d.rounded_rectangle(SCORE, (SCORE[3] - SCORE[1]) * 0.30, outline=(255, 217, 138, 230),
                        width=max(2, int((SCORE[3] - SCORE[1]) * 0.022)))
    sh = SCORE[3] - SCORE[1]
    text(img, ((SCORE[0] + SCORE[2]) / 2, SCORE[1] + sh * 0.28), "امتیاز", sh * 0.19,
         (207, 238, 220, 255), stroke=0)
    leaf(img, (SCORE[0] + SCORE[2]) / 2 - PW * 0.115, SCORE[1] + sh * 0.22, PW * 0.040, -60)
    leaf(img, (SCORE[0] + SCORE[2]) / 2 + PW * 0.115, SCORE[1] + sh * 0.22, PW * 0.040, 60)
    text(img, ((SCORE[0] + SCORE[2]) / 2, SCORE[3] - sh * 0.16), "۱۴۵۷", sh * 0.42,
         (255, 252, 242, 255), stroke=int(sh * 0.05), stroke_fill=(3, 32, 15, 255))

    # ------------------------------------------------------------- capsule
    caph = CAPSULE[3] - CAPSULE[1]
    drop_shadow(img, CAPSULE, caph / 2, dy=caph * 0.14, blur=8, strength=80)
    shape(img, CAPSULE, caph / 2, [(0, (255, 252, 240, 255)), (0.55, (255, 242, 214, 255)), (1, (242, 220, 168, 255))])
    gloss(img, CAPSULE, caph / 2, top=0.50, strength=110)
    bevel_ring(img, CAPSULE, caph / 2, caph * 0.06, 40, 90)
    d.rounded_rectangle(CAPSULE, caph / 2, outline=(216, 162, 56, 255), width=max(1, int(caph * 0.045)))
    coin(img, CAPSULE[2] - caph * 0.66, (CAPSULE[1] + CAPSULE[3]) / 2, caph * 0.36)
    text(img, ((CAPSULE[0] + CAPSULE[2]) / 2 - caph * 0.26, (CAPSULE[1] + CAPSULE[3]) / 2 + caph * 0.14),
         "+۸۶ سکه", caph * 0.40, (138, 90, 5, 255))

    # ------------------------------------------------------------- buttons
    for (box, top, bot, label, icon) in (
        (PRIMARY, (217, 251, 144, 255), (60, 132, 30, 255), "سطح بعد", "chevron"),
        (SECONDARY, (127, 192, 255, 255), (42, 86, 204, 255), "نقشه", "home"),
    ):
        bh = box[3] - box[1]
        rad = bh * 0.40
        d.rounded_rectangle((box[0], box[1] + bh * 0.12, box[2], box[3] + bh * 0.14), rad, fill=(0, 0, 0, 110))
        shape(img, box, rad, [(0, tuple(min(255, c + 26) for c in top[:3]) + (255,)),
                              (0.35, top), (0.75, bot), (1, tuple(int(c * 0.78) for c in bot[:3]) + (255,))])
        bevel_ring(img, box, rad, bh * 0.055, 60, 110)
        gloss(img, box, rad, top=0.46, strength=130)
        d.rounded_rectangle(box, rad, outline=(255, 255, 255, 190), width=max(1, int(bh * 0.035)))
        text(img, ((box[0] + box[2]) / 2 - bh * 0.14, (box[1] + box[3]) / 2 + bh * 0.14), fa(label),
             bh * 0.36, (20, 51, 11, 255) if icon == "chevron" else (255, 253, 245, 255),
             stroke=int(bh * 0.05), stroke_fill=(255, 255, 255, 120) if icon == "chevron" else (14, 30, 96, 255))
        ix, iy, ir = box[2] - bh * 0.60, (box[1] + box[3]) / 2, bh * 0.24
        if icon == "chevron":
            d.ellipse((ix - ir, iy - ir, ix + ir, iy + ir), fill=(96, 186, 52, 190), outline=(230, 255, 190, 230), width=2)
            d.line([(ix - ir * 0.22, iy - ir * 0.44), (ix + ir * 0.30, iy), (ix - ir * 0.22, iy + ir * 0.44)],
                   fill=(255, 255, 255, 250), width=max(3, int(bh * 0.07)), joint="curve")
        else:
            hy = iy - ir * 0.1
            d.polygon([(ix - ir, hy + ir * 0.10), (ix, hy - ir * 0.75), (ix + ir, hy + ir * 0.10),
                       (ix + ir * 0.62, hy + ir * 0.10), (ix + ir * 0.62, hy + ir * 0.80),
                       (ix - ir * 0.62, hy + ir * 0.80), (ix - ir * 0.62, hy + ir * 0.10)],
                      fill=(255, 255, 255, 245))

    # ---------------------------------------------------------------- Laleh
    girl_h = AVAIL * 0.225
    feet = INNER[3] - GARDEN_H * 0.12
    gx = INNER[0] + girl_h * 0.30
    d.ellipse((gx - girl_h * 0.26, feet - girl_h * 0.03, gx + girl_h * 0.26, feet + girl_h * 0.03),
              fill=(4, 21, 13, 150))
    laleh(img, gx, feet, girl_h, "cheer")

    return img


if __name__ == "__main__":
    out = main()
    out.convert("RGB").save("/home/user/ref-art/mock_result_160.png")
    print("ok")
