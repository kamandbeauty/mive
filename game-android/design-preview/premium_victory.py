"""Premium garden style prototype — mirrors VictoryGeometry so the Kotlin port
can copy exact shading ratios. Renders 412x915.

The point of this script is the *shading recipe*, not photo-realism: multi-stop
gradients, inner shadows, specular gloss bands, ornamental leaf clusters and a
bevelled gold frame. Everything here maps 1:1 onto Canvas primitives.
"""
import math
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageChops

W, H = 412, 915
FONTS = "/home/user/mive/game-android/app/src/main/assets/fonts/"
F_BLACK = FONTS + "Vazirmatn-FD-Black.ttf"
F_BOLD = FONTS + "Vazirmatn-FD-Bold.ttf"

try:
    import arabic_reshaper
    from bidi.algorithm import get_display
    def fa(s):
        return get_display(arabic_reshaper.reshape(s))
except Exception:
    def fa(s):
        return s


# ---------------------------------------------------------------- primitives
def L(size):
    return Image.new("RGBA", size, (0, 0, 0, 0))


def vgrad(size, stops):
    w, h = size
    img = Image.new("RGBA", size)
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(1, h - 1)
        col = stops[-1][1]
        for i in range(len(stops) - 1):
            p0, c0 = stops[i]
            p1, c1 = stops[i + 1]
            if p0 <= t <= p1:
                k = (t - p0) / max(1e-6, p1 - p0)
                col = tuple(int(c0[j] + (c1[j] - c0[j]) * k) for j in range(4))
                break
        d.line([(0, y), (w, y)], fill=col)
    return img


def rmask(size, radius):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=max(0, int(radius)), fill=255)
    return m


def multiply_alpha(layer, mask):
    a = layer.split()[3]
    layer.putalpha(ImageChops.multiply(a, mask))
    return layer


def shape(img, box, radius, stops, outline=None, ow=2):
    x0, y0, x1, y1 = [int(round(v)) for v in box]
    w, h = x1 - x0, y1 - y0
    if w <= 0 or h <= 0:
        return
    layer = vgrad((w, h), stops)
    img.alpha_composite(multiply_alpha(layer, rmask((w, h), radius)), (x0, y0))
    if outline:
        ImageDraw.Draw(img).rounded_rectangle((x0, y0, x1 - 1, y1 - 1), radius=int(radius), outline=outline, width=ow)


def inner_shadow(img, box, radius, color, blur=8, offset=(0, 6), strength=150):
    x0, y0, x1, y1 = [int(round(v)) for v in box]
    w, h = x1 - x0, y1 - y0
    inset = max(1, int(min(w, h) * 0.06))
    outer = rmask((w, h), radius)
    inner_w, inner_h = max(1, w - inset * 2), max(1, h - inset * 2)
    inner = Image.new("L", (w, h), 0)
    inner.paste(rmask((inner_w, inner_h), max(0, radius - inset)),
                (inset + int(offset[0]), inset + int(offset[1])))
    ring = ImageChops.subtract(outer, inner).filter(ImageFilter.GaussianBlur(blur))
    sh = Image.new("RGBA", (w, h), color[:3] + (0,))
    sh.putalpha(ImageChops.multiply(ring, Image.new("L", (w, h), strength)))
    img.alpha_composite(sh, (x0, y0))


def drop_shadow(img, box, radius, dx=0, dy=10, blur=12, strength=120):
    x0, y0, x1, y1 = [int(round(v)) for v in box]
    pad = int(blur * 2.5)
    w, h = x1 - x0 + pad * 2, y1 - y0 + pad * 2
    sh = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle(
        (pad + dx, pad + dy, pad + dx + (x1 - x0), pad + dy + (y1 - y0)),
        radius=int(radius), fill=(3, 16, 8, strength))
    sh = sh.filter(ImageFilter.GaussianBlur(blur))
    img.alpha_composite(sh, (x0 - pad, y0 - pad))


def gloss(img, box, radius, top=0.44, strength=120):
    x0, y0, x1, y1 = [int(round(v)) for v in box]
    w, h = x1 - x0, y1 - y0
    hh = max(1, int(h * top))
    g = vgrad((w, hh), [(0, (255, 255, 255, strength)), (0.55, (255, 255, 255, strength // 3)), (1, (255, 255, 255, 0))])
    m = rmask((w, hh), min(radius, hh * 0.95))
    img.alpha_composite(multiply_alpha(g, m), (x0, y0))


def glow(img, cx, cy, r, color, strength=110, blur=14):
    lay = L((W, H))
    for k, rr in enumerate((r, r * 0.7, r * 0.45)):
        ImageDraw.Draw(lay).ellipse((cx - rr, cy - rr, cx + rr, cy + rr), fill=color[:3] + (strength // (k + 2),))
    img.alpha_composite(lay.filter(ImageFilter.GaussianBlur(blur)))


def leaf(img, cx, cy, size, rot, dark=(30, 96, 44), light=(132, 214, 96), vein=True):
    n = int(size * 2.4)
    lay = L((n, n))
    d = ImageDraw.Draw(lay)
    c = n / 2
    half = size * 0.40
    length = size * 0.95
    upper, lower = [], []
    steps = 26
    for i in range(steps + 1):
        t = i / steps
        x = half * (math.sin(math.pi * t) ** 0.75)
        y = (t - 0.5) * length
        upper.append((c + x, c + y * 0.92))
        lower.append((c - x, c + y * 0.92))
    d.polygon(upper + lower[::-1], fill=light + (255,))
    # shading clipped to the leaf
    a = lay.split()[3]
    sh = vgrad((n, n), [(0, dark + (170,)), (0.55, dark + (40,)), (1, (255, 255, 255, 0))])
    lay.alpha_composite(multiply_alpha(sh, a))
    if vein:
        d.line([(c, c - length * 0.42), (c, c + length * 0.44)], fill=(64, 138, 62, 170), width=max(1, int(size * 0.05)))
        for k in range(3):
            yy = c - length * 0.22 + k * length * 0.22
            d.line([(c, yy), (c + half * 0.62, yy + length * 0.12)], fill=(70, 146, 68, 130), width=max(1, int(size * 0.035)))
            d.line([(c, yy), (c - half * 0.62, yy + length * 0.12)], fill=(70, 146, 68, 130), width=max(1, int(size * 0.035)))
    lay = lay.rotate(-rot, resample=Image.BICUBIC)
    img.alpha_composite(lay, (int(cx - n / 2), int(cy - n / 2)))


def flower(img, cx, cy, r, petal=(255, 168, 205), core=(255, 214, 92)):
    n = int(r * 3.2)
    lay = L((n, n))
    d = ImageDraw.Draw(lay)
    c = n / 2
    for i in range(5):
        a = math.radians(i * 72 - 90)
        px, py = c + math.cos(a) * r * 0.58, c + math.sin(a) * r * 0.58
        d.ellipse((px - r * 0.50, py - r * 0.50, px + r * 0.50, py + r * 0.50), fill=petal + (255,))
    a = lay.split()[3]
    sh = vgrad((n, n), [(0, (120, 40, 80, 90)), (0.6, (255, 255, 255, 0))])
    lay.alpha_composite(multiply_alpha(sh, a))
    d.ellipse((c - r * 0.34, c - r * 0.34, c + r * 0.34, c + r * 0.34), fill=core + (255,))
    d.ellipse((c - r * 0.15, c - r * 0.15, c + r * 0.15, c + r * 0.15), fill=(255, 246, 205, 235))
    img.alpha_composite(lay, (int(cx - n / 2), int(cy - n / 2)))


def text(img, xy, s, size, fill, font=F_BLACK, anchor="mm", stroke=0, stroke_fill=None, shadow=None):
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(font, max(6, int(size)))
    if shadow:
        dx, dy, col = shadow
        d.text((xy[0] + dx, xy[1] + dy), fa(s), font=f, fill=col, anchor=anchor,
               stroke_width=stroke, stroke_fill=stroke_fill)
    d.text(xy, fa(s), font=f, fill=fill, anchor=anchor, stroke_width=stroke, stroke_fill=stroke_fill)


def strawberry(img, cx, cy, size):
    d = ImageDraw.Draw(img)
    w2, h2 = size * 0.62, size * 0.80
    d.polygon([(cx - w2, cy - h2 * 0.30), (cx - w2 * 0.55, cy + h2 * 0.70), (cx, cy + h2),
               (cx + w2 * 0.55, cy + h2 * 0.70), (cx + w2, cy - h2 * 0.30)],
              fill=(226, 42, 62, 255))
    d.ellipse((cx - w2, cy - h2 * 0.75, cx + w2, cy + h2 * 0.25), fill=(238, 58, 76, 255))
    d.ellipse((cx - w2 * 0.55, cy - h2 * 0.60, cx - w2 * 0.02, cy - h2 * 0.10), fill=(255, 130, 140, 150))
    for gx, gy in ((-0.45, 0.10), (0.10, 0.30), (0.45, 0.02), (-0.10, 0.62), (0.30, 0.66)):
        d.ellipse((cx + gx * w2 - size * 0.045, cy + gy * h2 - size * 0.055,
                   cx + gx * w2 + size * 0.045, cy + gy * h2 + size * 0.055), fill=(255, 232, 170, 235))
    for i in range(5):
        a = math.radians(i * 72 - 90)
        leaf(img, cx + math.cos(a) * size * 0.30, cy - h2 * 0.72 + math.sin(a) * size * 0.12,
             size * 0.36, math.degrees(a) + 90, dark=(28, 92, 40), light=(104, 190, 74))
    d.ellipse((cx - size * 0.06, cy - h2 * 0.86, cx + size * 0.06, cy - h2 * 0.70), fill=(120, 74, 20, 255))


def lemon(img, cx, cy, size):
    d = ImageDraw.Draw(img)
    d.ellipse((cx - size * 0.85, cy - size * 0.62, cx + size * 0.85, cy + size * 0.62), fill=(246, 200, 30, 255))
    d.ellipse((cx - size * 0.70, cy - size * 0.52, cx + size * 0.55, cy + size * 0.28), fill=(255, 232, 96, 255))
    d.ellipse((cx - size * 0.55, cy - size * 0.46, cx - size * 0.05, cy - size * 0.10), fill=(255, 250, 200, 190))
    leaf(img, cx + size * 0.55, cy - size * 0.52, size * 0.55, 30)


def blueberries(img, cx, cy, r):
    d = ImageDraw.Draw(img)
    for dx, dy in ((-1.2, 0.2), (0, -0.5), (1.1, 0.15), (-0.4, 0.9), (0.7, 1.0)):
        d.ellipse((cx + dx * r - r, cy + dy * r - r, cx + dx * r + r, cy + dy * r + r), fill=(96, 74, 190, 255))
        d.ellipse((cx + dx * r - r * 0.45, cy + dy * r - r * 0.55, cx + dx * r + r * 0.25, cy + dy * r + r * 0.05),
                  fill=(158, 140, 226, 190))


# ------------------------------------------------------------------- scene
def background():
    img = vgrad((W, H), [(0, (128, 198, 240, 255)), (0.20, (150, 210, 224, 255)),
                         (0.42, (104, 182, 126, 255)), (0.72, (48, 118, 70, 255)), (1, (20, 66, 36, 255))])
    glow(img, W * 0.5, -H * 0.05, W * 0.62, (255, 240, 190), 90, 40)
    bok = L((W, H))
    bd = ImageDraw.Draw(bok)
    for x, y, r in ((0.12, 0.16, 26), (0.86, 0.10, 34), (0.72, 0.28, 20), (0.18, 0.42, 30),
                    (0.90, 0.50, 22), (0.08, 0.68, 28), (0.60, 0.74, 18), (0.30, 0.86, 24)):
        bd.ellipse((x * W - r, y * H - r, x * W + r, y * H + r), fill=(255, 250, 214, 60))
    img.alpha_composite(bok.filter(ImageFilter.GaussianBlur(9)))
    fol = L((W, H))
    fd = ImageDraw.Draw(fol)
    for i in range(22):
        t = i / 22
        fd.ellipse((-W * 0.16 + t * W * 0.10 - 74, t * H - 60, -W * 0.16 + t * W * 0.10 + 74, t * H + 100), fill=(14, 54, 28, 235))
        fd.ellipse((W * 1.06 - t * W * 0.10 - 74, (1 - t) * H - 60, W * 1.06 - t * W * 0.10 + 74, (1 - t) * H + 100), fill=(14, 54, 28, 225))
    img.alpha_composite(fol.filter(ImageFilter.GaussianBlur(22)))
    img.alpha_composite(vgrad((W, H), [(0, (0, 0, 0, 0)), (0.7, (0, 0, 0, 0)), (1, (0, 14, 8, 140))]))
    return img


def girl_peek(img, cx, base_y, h):
    u = h / 100.0
    skin, skin_sh = (255, 216, 176, 255), (226, 168, 128, 255)
    hair, hair_hi = (86, 50, 24, 255), (146, 96, 54, 255)
    pink, denim, denim_d = (247, 132, 168, 255), (78, 132, 198, 255), (48, 90, 150, 255)
    straw, straw_d = (242, 198, 118, 255), (198, 146, 62, 255)

    d = ImageDraw.Draw(img)
    # body
    d.ellipse((cx - 26 * u, base_y - 32 * u, cx + 26 * u, base_y + 4 * u), fill=pink)
    d.rounded_rectangle((cx - 21 * u, base_y - 24 * u, cx + 21 * u, base_y - 1 * u), 9 * u, fill=denim)
    d.rounded_rectangle((cx - 25 * u, base_y - 34 * u, cx + 9 * u, base_y - 17 * u), 6 * u, fill=denim_d)
    for dx in (-13, 13):
        d.ellipse((cx + dx * u - 2.3 * u, base_y - 25 * u, cx + dx * u + 2.3 * u, base_y - 20.4 * u), fill=(255, 208, 72, 255))

    head_r = 17.5 * u
    head_cy = base_y - 49 * u
    d.rounded_rectangle((cx - 4.4 * u, head_cy + 10 * u, cx + 4.4 * u, base_y - 26 * u), 3 * u, fill=skin_sh)

    # hair back + head
    d.ellipse((cx - head_r * 1.14, head_cy - head_r * 1.12, cx + head_r * 1.14, head_cy + head_r * 1.14), fill=hair)
    d.ellipse((cx - head_r * 0.94, head_cy - head_r * 0.96, cx + head_r * 0.94, head_cy + head_r * 0.98), fill=skin)

    # fringe: three soft lobes
    d.chord((cx - head_r * 1.02, head_cy - head_r * 1.08, cx + head_r * 1.02, head_cy + head_r * 0.16), 180, 360, fill=hair)
    d.ellipse((cx - head_r * 0.92, head_cy - head_r * 0.92, cx - head_r * 0.18, head_cy + head_r * 0.22), fill=hair)
    d.ellipse((cx + head_r * 0.30, head_cy - head_r * 0.98, cx + head_r * 0.98, head_cy + head_r * 0.10), fill=hair)
    d.arc((cx - head_r * 0.55, head_cy - head_r * 0.95, cx + head_r * 0.25, head_cy - head_r * 0.25), 200, 340,
          fill=hair_hi, width=max(2, int(head_r * 0.10)))

    # cheeks
    for dx in (-0.62, 0.62):
        d.ellipse((cx + dx * head_r - head_r * 0.20, head_cy + head_r * 0.12 - head_r * 0.12,
                   cx + dx * head_r + head_r * 0.20, head_cy + head_r * 0.12 + head_r * 0.12), fill=(255, 140, 152, 110))

    # eyes
    for dx in (-0.42, 0.42):
        ex, ey = cx + dx * head_r, head_cy + head_r * 0.04
        d.ellipse((ex - head_r * 0.26, ey - head_r * 0.30, ex + head_r * 0.26, ey + head_r * 0.30), fill=(255, 255, 255))
        d.ellipse((ex - head_r * 0.18, ey - head_r * 0.22, ex + head_r * 0.18, ey + head_r * 0.22), fill=(96, 58, 30))
        d.ellipse((ex - head_r * 0.11, ey - head_r * 0.15, ex + head_r * 0.05, ey + head_r * 0.01), fill=(24, 12, 6))
        d.ellipse((ex - head_r * 0.12, ey - head_r * 0.18, ex - head_r * 0.02, ey - head_r * 0.05), fill=(255, 255, 255))
        d.ellipse((ex + head_r * 0.06, ey + head_r * 0.08, ex + head_r * 0.13, ey + head_r * 0.15), fill=(255, 255, 255, 210))
    d.arc((cx - head_r * 0.30, head_cy + head_r * 0.26, cx + head_r * 0.30, head_cy + head_r * 0.66),
          18, 162, fill=(152, 62, 52, 255), width=max(2, int(head_r * 0.09)))

    # hat
    hat_y = head_cy - head_r * 0.80
    d.ellipse((cx - head_r * 1.70, hat_y - head_r * 0.26, cx + head_r * 1.70, hat_y + head_r * 0.32), fill=straw)
    d.ellipse((cx - head_r * 1.70, hat_y + head_r * 0.10, cx + head_r * 1.70, hat_y + head_r * 0.32), fill=straw_d)
    d.rounded_rectangle((cx - head_r * 0.86, hat_y - head_r * 0.86, cx + head_r * 0.86, hat_y + head_r * 0.16),
                        head_r * 0.44, fill=straw)
    d.rounded_rectangle((cx - head_r * 0.88, hat_y - head_r * 0.22, cx + head_r * 0.88, hat_y + head_r * 0.08),
                        head_r * 0.16, fill=(232, 84, 107, 255))
    flower(img, cx + head_r * 0.80, hat_y - head_r * 0.20, head_r * 0.24)
    flower(img, cx - head_r * 0.62, hat_y - head_r * 0.04, head_r * 0.19, petal=(255, 244, 250))
    leaf(img, cx + head_r * 1.26, hat_y + head_r * 0.06, head_r * 0.40, 60)


def main():
    img = background()
    rim = W * 0.013
    x0, y0, x1, y1 = W * 0.040, H * 0.078, W * 0.960, H * 0.926
    pw, ph = x1 - x0, y1 - y0

    drop_shadow(img, (x0, y0, x1, y1), W * 0.062, dy=16, blur=20, strength=150)
    shape(img, (x0, y0, x1, y1), W * 0.062,
          [(0, (255, 240, 186, 255)), (0.45, (238, 186, 74, 255)), (1, (140, 88, 12, 255))])
    shape(img, (x0 + rim * 0.42, y0 + rim * 0.42, x1 - rim * 0.42, y1 - rim * 0.42), W * 0.057,
          [(0, (255, 252, 222, 255)), (0.5, (255, 226, 138, 255)), (1, (206, 146, 34, 255))])
    shape(img, (x0 + rim, y0 + rim, x1 - rim, y1 - rim), W * 0.052,
          [(0, (255, 249, 230, 255)), (0.22, (253, 238, 205, 255)), (0.60, (249, 227, 186, 255)), (1, (238, 208, 158, 255))])
    inner_shadow(img, (x0 + rim, y0 + rim, x1 - rim, y1 - rim), W * 0.052, (120, 76, 18), blur=18, offset=(0, 12), strength=110)
    # soft warm vignette in the corners
    vig = L((W, H))
    vd = ImageDraw.Draw(vig)
    for k in range(4):
        vd.rectangle((0, 0, W, H), outline=None)
    vig = vig.filter(ImageFilter.GaussianBlur(20))

    cx = W / 2
    # ---- plaque
    plaque_h = H * 0.098
    plaque_w = pw * 0.70
    px0, py0 = cx - plaque_w / 2, y0 + H * 0.030
    plaque = (px0, py0, px0 + plaque_w, py0 + plaque_h)
    drop_shadow(img, plaque, plaque_h * 0.36, dy=12, blur=14, strength=150)
    shape(img, (plaque[0] - plaque_h * 0.07, plaque[1] - plaque_h * 0.07,
                plaque[2] + plaque_h * 0.07, plaque[3] + plaque_h * 0.07), plaque_h * 0.42,
          [(0, (255, 244, 196, 255)), (0.5, (232, 178, 78, 255)), (1, (150, 96, 20, 255))])
    shape(img, plaque, plaque_h * 0.34, [(0, (150, 96, 48, 255)), (0.32, (114, 70, 34, 255)), (1, (62, 34, 14, 255))])
    inner_shadow(img, plaque, plaque_h * 0.34, (24, 10, 2), blur=10, offset=(0, 6), strength=170)
    d = ImageDraw.Draw(img)
    for i in range(7):
        yy = plaque[1] + plaque_h * (0.14 + i * 0.12)
        d.line([(plaque[0] + plaque_h * 0.16, yy), (plaque[2] - plaque_h * 0.16, yy + plaque_h * 0.015)],
               fill=(74, 42, 18, 70), width=max(1, int(plaque_h * 0.014)))
    gloss(img, plaque, plaque_h * 0.34, 0.36, 55)

    # corner greenery
    for side in (-1, 1):
        ax = plaque[0] - plaque_h * 0.10 if side < 0 else plaque[2] + plaque_h * 0.10
        for k, (dy, s, rot) in enumerate([(-0.30, 0.52, 55), (-0.02, 0.62, 20), (0.26, 0.50, -25)]):
            leaf(img, ax + side * plaque_h * 0.10, plaque[1] + plaque_h * (0.30 + dy * 0.4), plaque_h * s, rot * side)
        leaf(img, ax + side * plaque_h * 0.30, plaque[1] + plaque_h * 0.10, plaque_h * 0.40, 70 * side)
        flower(img, ax + side * plaque_h * 0.05, plaque[1] + plaque_h * 0.02, plaque_h * 0.15)
        flower(img, ax - side * plaque_h * 0.16, plaque[1] + plaque_h * 0.30, plaque_h * 0.10, petal=(255, 240, 250))

    # crown
    ccx, ccy = cx, plaque[1] - plaque_h * 0.30
    cw, chh = plaque_h * 0.86, plaque_h * 0.62
    glow(img, ccx, ccy, chh * 1.5, (255, 226, 130), 70, 16)
    d.polygon([(ccx - cw / 2, ccy + chh * 0.34), (ccx - cw / 2, ccy - chh * 0.12), (ccx - cw * 0.24, ccy + chh * 0.10),
               (ccx, ccy - chh * 0.58), (ccx + cw * 0.24, ccy + chh * 0.10), (ccx + cw / 2, ccy - chh * 0.12),
               (ccx + cw / 2, ccy + chh * 0.34)], fill=(255, 214, 74, 255))
    d.line([(ccx - cw / 2, ccy + chh * 0.34), (ccx - cw / 2, ccy - chh * 0.12), (ccx - cw * 0.24, ccy + chh * 0.10),
            (ccx, ccy - chh * 0.58), (ccx + cw * 0.24, ccy + chh * 0.10), (ccx + cw / 2, ccy - chh * 0.12),
            (ccx + cw / 2, ccy + chh * 0.34)], fill=(150, 92, 12, 255), width=2)
    d.rounded_rectangle((ccx - cw / 2, ccy + chh * 0.14, ccx + cw / 2, ccy + chh * 0.38), chh * 0.10, fill=(228, 162, 28, 255))
    for dx, col in ((-cw * 0.25, (255, 124, 154, 255)), (0, (255, 86, 118, 255)), (cw * 0.25, (255, 124, 154, 255))):
        d.ellipse((ccx + dx - chh * 0.085, ccy + chh * 0.00, ccx + dx + chh * 0.085, ccy + chh * 0.17), fill=col)
    gloss(img, (ccx - cw / 2, ccy - chh * 0.58, ccx + cw / 2, ccy + chh * 0.12), chh * 0.2, 0.55, 80)

    # ---- title
    title = "آفرین!"
    tsize = plaque_h * 0.56
    tcy = (plaque[1] + plaque[3]) / 2 + plaque_h * 0.06
    text(img, (cx + plaque_h * 0.012, tcy + plaque_h * 0.045), title, tsize, (255, 250, 226, 255),
         stroke=int(tsize * 0.15), stroke_fill=(74, 38, 10, 255))
    text(img, (cx, tcy), title, tsize, (255, 228, 126, 255),
         stroke=int(tsize * 0.09), stroke_fill=(128, 68, 14, 255))
    text(img, (cx, tcy - tsize * 0.16), title, tsize * 0.94, (255, 252, 236, 105), anchor="mm")

    # ---- level chip
    chip_h = H * 0.042
    chip_w = pw * 0.66
    chip = (cx - chip_w / 2, plaque[3] + H * 0.028, cx + chip_w / 2, plaque[3] + H * 0.028 + chip_h)
    drop_shadow(img, chip, chip_h / 2, dy=6, blur=7, strength=120)
    shape(img, chip, chip_h / 2, [(0, (128, 80, 36, 255)), (1, (70, 38, 14, 255))], outline=(248, 204, 108, 255), ow=3)
    gloss(img, chip, chip_h / 2, 0.48, 45)
    text(img, (cx, (chip[1] + chip[3]) / 2 + chip_h * 0.06), "سطح ۱ • باغ بهار", chip_h * 0.38,
         (255, 246, 226, 255), stroke=1, stroke_fill=(52, 26, 6, 255))
    leaf(img, chip[2] - chip_h * 0.36, chip[1] + chip_h * 0.48, chip_h * 0.52, 40)
    leaf(img, chip[0] + chip_h * 0.36, chip[1] + chip_h * 0.52, chip_h * 0.48, -140)

    # ---- stars
    socket = W * 0.152
    gap = W * 0.028
    total = socket * 3 + gap * 2
    sx0 = cx - total / 2
    stars_y = chip[3] + H * 0.052
    for i in range(3):
        box = (sx0 + i * (socket + gap), stars_y, sx0 + i * (socket + gap) + socket, stars_y + socket)
        filled = i == 0
        leaf(img, box[0] + socket * 0.10, box[3] - socket * 0.02, socket * 0.34, 46, dark=(44, 120, 52), light=(150, 222, 110))
        leaf(img, box[2] - socket * 0.10, box[3] - socket * 0.02, socket * 0.34, -46, dark=(44, 120, 52), light=(150, 222, 110))
        drop_shadow(img, box, socket * 0.30, dy=7, blur=9, strength=120)
        if filled:
            shape(img, box, socket * 0.30, [(0, (255, 250, 224, 255)), (1, (246, 214, 138, 255))],
                  outline=(255, 216, 120, 255), ow=3)
        else:
            shape(img, box, socket * 0.30, [(0, (232, 222, 204, 255)), (1, (196, 184, 166, 255))],
                  outline=(248, 234, 206, 255), ow=3)
        inner_shadow(img, box, socket * 0.30, (116, 96, 62), blur=9, offset=(0, 7), strength=110)
        sx, sy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
        r = socket * 0.36
        pts = []
        for k in range(10):
            a = math.radians(-90 + k * 36)
            rr = r if k % 2 == 0 else r * 0.46
            pts.append((sx + math.cos(a) * rr, sy + math.sin(a) * rr))
        d = ImageDraw.Draw(img)
        if filled:
            glow(img, sx, sy, r * 1.7, (255, 226, 120), 90, 12)
            d.polygon(pts, fill=(255, 212, 66, 255))
            d.line(pts + [pts[0]], fill=(158, 100, 12, 255), width=3)
            d.polygon([(sx - r * 0.28, sy - r * 0.30), (sx + r * 0.02, sy - r * 0.44), (sx + r * 0.12, sy - r * 0.10)],
                      fill=(255, 246, 190, 200))
        else:
            d.polygon(pts, fill=(176, 168, 152, 255))
            d.line(pts + [pts[0]], fill=(154, 144, 128, 255), width=2)


    # ---- score
    score_w, score_h = pw * 0.74, H * 0.118
    score = (cx - score_w / 2, stars_y + socket + H * 0.036, cx + score_w / 2, stars_y + socket + H * 0.036 + score_h)
    drop_shadow(img, score, W * 0.032, dy=9, blur=11, strength=120)
    shape(img, score, W * 0.032, [(0, (52, 146, 88, 255)), (0.45, (34, 118, 66, 255)), (1, (18, 82, 46, 255))],
          outline=(255, 216, 122, 255), ow=3)
    inner_shadow(img, score, W * 0.032, (4, 40, 20), blur=12, offset=(0, 7), strength=150)
    gloss(img, score, W * 0.032, 0.42, 42)
    text(img, (cx, score[1] + score_h * 0.24), "امتیاز", score_h * 0.18, (214, 242, 220, 255))
    text(img, (cx, score[1] + score_h * 0.68), "۱۴۵۷", score_h * 0.48, (255, 255, 250, 255),
         stroke=int(score_h * 0.055), stroke_fill=(12, 62, 34, 255))
    leaf(img, score[0] + score_w * 0.055, score[1] + score_h * 0.62, score_h * 0.22, 160, dark=(30, 96, 44), light=(120, 200, 92))
    leaf(img, score[2] - score_w * 0.055, score[1] + score_h * 0.62, score_h * 0.22, 20, dark=(30, 96, 44), light=(120, 200, 92))

    # ---- coin capsule
    cap_w, cap_h = pw * 0.56, H * 0.052
    cap = (cx - cap_w / 2, score[3] + H * 0.024, cx + cap_w / 2, score[3] + H * 0.024 + cap_h)
    drop_shadow(img, cap, cap_h / 2, dy=6, blur=8, strength=110)
    shape(img, cap, cap_h / 2, [(0, (255, 252, 236, 255)), (1, (246, 228, 186, 255))], outline=(216, 162, 56, 255), ow=3)
    gloss(img, cap, cap_h / 2, 0.5, 80)
    coin_r = cap_h * 0.35
    cxc, cyc = cap[2] - cap_h * 0.64, (cap[1] + cap[3]) / 2
    d = ImageDraw.Draw(img)
    d.ellipse((cxc - coin_r, cyc - coin_r, cxc + coin_r, cyc + coin_r), fill=(178, 118, 10, 255))
    d.ellipse((cxc - coin_r * 0.90, cyc - coin_r * 0.94, cxc + coin_r * 0.90, cyc + coin_r * 0.86), fill=(255, 206, 68, 255))
    d.ellipse((cxc - coin_r * 0.64, cyc - coin_r * 0.66, cxc + coin_r * 0.64, cyc + coin_r * 0.60), fill=(255, 228, 132, 255))
    pts = []
    for k in range(10):
        a = math.radians(-90 + k * 36)
        rr = coin_r * 0.50 if k % 2 == 0 else coin_r * 0.23
        pts.append((cxc + math.cos(a) * rr, cyc + math.sin(a) * rr))
    d.polygon(pts, fill=(250, 184, 40, 255))
    text(img, (cap[0] + cap_w * 0.34, (cap[1] + cap[3]) / 2 + cap_h * 0.05), "+۸۶ سکه", cap_h * 0.38,
         (146, 88, 6, 255))

    # ---- buttons
    btn_h = H * 0.070
    inset = pw * 0.085
    prim = (cx - (pw / 2 - inset), cap[3] + H * 0.030, cx + (pw / 2 - inset), cap[3] + H * 0.030 + btn_h)
    drop_shadow(img, (prim[0], prim[1] + btn_h * 0.10, prim[2], prim[3] + btn_h * 0.18), btn_h * 0.46, blur=11, strength=150)
    shape(img, prim, btn_h * 0.46, [(0, (154, 230, 98, 255)), (0.42, (100, 194, 54, 255)), (1, (44, 128, 26, 255))],
          outline=(228, 255, 180, 255), ow=3)
    inner_shadow(img, prim, btn_h * 0.46, (10, 58, 6), blur=7, offset=(0, btn_h * 0.12), strength=120)
    gloss(img, prim, btn_h * 0.46, 0.44, 130)
    cush = (prim[2] - btn_h * 0.30, prim[3] + btn_h * 0.05, prim[2] - btn_h * 0.03, prim[3] + btn_h * 0.16)
    shape(img, (prim[0] + btn_h * 0.05, prim[3] - btn_h * 0.02, prim[2] - btn_h * 0.05, prim[3] + btn_h * 0.13),
          btn_h * 0.40, [(0, (28, 96, 16, 255)), (1, (58, 146, 30, 210))])
    text(img, (cx - btn_h * 0.16, (prim[1] + prim[3]) / 2 + btn_h * 0.04), "سطح بعد", btn_h * 0.38,
         (255, 255, 255, 255), stroke=int(btn_h * 0.06), stroke_fill=(20, 78, 8, 255))
    chx, chy, chr_ = prim[2] - btn_h * 0.40, (prim[1] + prim[3]) / 2, btn_h * 0.24
    d = ImageDraw.Draw(img)
    d.ellipse((chx - chr_, chy - chr_, chx + chr_, chy + chr_), fill=(96, 186, 52, 190), outline=(226, 255, 180, 230), width=3)
    d.line([(chx - chr_ * 0.20, chy - chr_ * 0.42), (chx + chr_ * 0.30, chy), (chx - chr_ * 0.20, chy + chr_ * 0.42)],
           fill=(255, 255, 255, 250), width=max(3, int(btn_h * 0.07)), joint="curve")
    leaf(img, prim[0] + btn_h * 0.52, (prim[1] + prim[3]) / 2 - btn_h * 0.02, btn_h * 0.26, 200)

    sec = (prim[0] + btn_h * 0.34, prim[3] + H * 0.014, prim[2] - btn_h * 0.34, prim[3] + H * 0.014 + btn_h * 0.84)
    drop_shadow(img, (sec[0], sec[1] + btn_h * 0.09, sec[2], sec[3] + btn_h * 0.15), btn_h * 0.42, blur=10, strength=140)
    shape(img, sec, btn_h * 0.42, [(0, (126, 174, 255, 255)), (0.45, (66, 108, 228, 255)), (1, (26, 54, 158, 255))],
          outline=(190, 216, 255, 255), ow=3)
    gloss(img, sec, btn_h * 0.42, 0.46, 130)
    shape(img, (sec[0] + btn_h * 0.05, sec[3] - btn_h * 0.02, sec[2] - btn_h * 0.05, sec[3] + btn_h * 0.12),
          btn_h * 0.36, [(0, (16, 36, 112, 255)), (1, (40, 72, 170, 200))])
    text(img, (cx - btn_h * 0.02, (sec[1] + sec[3]) / 2 + btn_h * 0.04), "نقشه", btn_h * 0.36,
         (255, 255, 255, 255), stroke=int(btn_h * 0.05), stroke_fill=(14, 30, 96, 255))
    # little house glyph on the trailing edge (RTL: the icon leads on the right)
    hx, hy, hs = sec[2] - btn_h * 0.44, (sec[1] + sec[3]) / 2, btn_h * 0.19
    ImageDraw.Draw(img).polygon([(hx - hs, hy + hs * 0.10), (hx, hy - hs * 0.75), (hx + hs, hy + hs * 0.10),
                                 (hx + hs * 0.62, hy + hs * 0.10), (hx + hs * 0.62, hy + hs * 0.80),
                                 (hx - hs * 0.62, hy + hs * 0.80), (hx - hs * 0.62, hy + hs * 0.10)],
                                fill=(255, 255, 255, 245))

    # ---- girl peeking in front of the card's left edge
    girl_peek(img, x0 - W * 0.030, score[3] + H * 0.010, H * 0.300)

    # ---- fruit garnish along the card's bottom edge
    ly = y1 - H * 0.004
    leaf(img, x0 + pw * 0.05, ly - H * 0.012, pw * 0.062, 36)
    leaf(img, x0 + pw * 0.14, ly - H * 0.020, pw * 0.052, -22)
    leaf(img, x1 - pw * 0.05, ly - H * 0.012, pw * 0.062, -36)
    leaf(img, x1 - pw * 0.14, ly - H * 0.020, pw * 0.052, 22)
    strawberry(img, x0 + pw * 0.10, ly - H * 0.010, H * 0.042)
    lemon(img, x1 - pw * 0.10, ly - H * 0.012, H * 0.040)
    blueberries(img, x1 - pw * 0.19, ly - H * 0.006, H * 0.016)
    flower(img, x0 + pw * 0.20, ly - H * 0.016, pw * 0.026, petal=(255, 246, 252))
    flower(img, x1 - pw * 0.24, ly - H * 0.018, pw * 0.024)
    return img


if __name__ == "__main__":
    out = main()
    out.convert("RGB").save("/home/user/ref-art/premium_victory.png")
    print("ok")
