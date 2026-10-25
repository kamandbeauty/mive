"""Composite preview of the 1.7.0 home screen from the shipped assets.

Geometry is copied from HomeLayout (412x915). Everything you see is either a
file from assets/art or a text layer — this is the "all elements are images"
state the user asked for.

Run:  python3 game-android/design-preview/mock_home_170.py
"""
import math
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.normpath(os.path.join(HERE, "..", "app/src/main/assets/art"))
sys.path.insert(0, HERE)
from premium_victory import text  # noqa: E402

W, H = 412, 915
MARGIN = W * 0.045


def load(name):
    for ext in (".webp", ".png"):
        p = os.path.join(ART, name + ext)
        if os.path.exists(p):
            return Image.open(p).convert("RGBA")
    raise FileNotFoundError(name)


def nine(img, src, rect, inset):
    bw, bh = src.size
    i = min(inset, bw * 0.45, bh * 0.45)
    x0, y0, x1, y1 = rect
    iw = min(i * (x1 - x0) / bw, (x1 - x0) / 2.05)
    ih = min(i * (y1 - y0) / bh, (y1 - y0) / 2.05)
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
            piece = piece.resize((max(1, int(math.ceil(dw)) + 1), max(1, int(math.ceil(dh)) + 1)), Image.LANCZOS)
            img.alpha_composite(piece, (int(math.floor(dxs[col])), int(math.floor(dys[r]))))


def sprite(img, name, cx, cy, size, rot=0.0):
    s = load(name)
    w = size * s.width / s.height
    s = s.resize((max(1, int(w)), max(1, int(size))), Image.LANCZOS)
    if rot:
        s = s.rotate(-rot, resample=Image.BICUBIC, expand=True)
    img.alpha_composite(s, (int(cx - s.width / 2), int(cy - s.height / 2)))


def icon(img, name, cx, cy, size, color):
    size = max(2, int(round(size)))
    s = load(name).resize((size, size), Image.LANCZOS)
    tint = Image.new("RGBA", s.size, color + (255,))
    out = Image.new("RGBA", s.size, (0, 0, 0, 0))
    out.paste(tint, (0, 0), s.getchannel("A"))
    img.alpha_composite(out, (int(cx - size / 2), int(cy - size / 2)))


def main():
    bg = load("bg_garden")
    scale = max(W / bg.width, H / bg.height)
    bg = bg.resize((int(bg.width * scale), int(bg.height * scale)), Image.LANCZOS)
    img = Image.new("RGBA", (W, H))
    img.alpha_composite(bg, (int((W - bg.width) / 2), int((H - bg.height) / 2)))
    d = ImageDraw.Draw(img)

    # ---------------------------------------------------------- top chrome
    chipH = H * 0.056
    chipTop = H * 0.028
    gearH = chipH * 1.18
    gear = (W - MARGIN - gearH, chipTop - chipH * 0.09, W - MARGIN, chipTop - chipH * 0.09 + gearH)
    nine(img, load("card_glass"), gear, 52)
    icon(img, "ic_gear", (gear[0] + gear[2]) / 2, (gear[1] + gear[3]) / 2, gearH * 0.56, (255, 246, 229))

    coinW = W * 0.285
    coin = (MARGIN, chipTop, MARGIN + coinW, chipTop + chipH)
    nine(img, load("card_glass"), coin, 52)
    sprite(img, "coin_star", coin[0] + chipH * 0.44, (coin[1] + coin[3]) / 2, chipH * 0.70)
    text(img, (coin[2] - chipH * 0.24, (coin[1] + coin[3]) / 2 + chipH * 0.16),
         "۱۵۰", chipH * 0.46, (255, 246, 229, 255), anchor="rs")

    livesW = W * 0.245
    lives = (coin[2] + W * 0.018, chipTop, coin[2] + W * 0.018 + livesW, chipTop + chipH)
    nine(img, load("card_glass"), lives, 52)
    icon(img, "ic_heart", lives[0] + chipH * 0.44, (lives[1] + lives[3]) / 2, chipH * 0.62, (255, 143, 160))
    text(img, (lives[2] - chipH * 0.22, (lives[1] + lives[3]) / 2 + chipH * 0.15),
         "۵/۵", chipH * 0.42, (255, 246, 229, 255), anchor="rs")

    # ----------------------------------------------------------------- logo
    logoH = H * 0.086
    logoTop = H * 0.100
    logo = (W * 0.055, logoTop, W * 0.945, logoTop + logoH)
    nine(img, load("plaque"), logo, 46)
    sprite(img, "crown", W / 2, logo[1] - logoH * 0.22, logoH * 0.62)
    tsize = min(W * 0.112, logoH * 0.62)
    ty = (logo[1] + logo[3]) / 2 + tsize * 0.36
    text(img, (W / 2 + tsize * 0.012, ty + tsize * 0.10), "میوه‌چین", tsize,
         (255, 250, 226, 255), anchor="ms", stroke=int(tsize * 0.14), stroke_fill=(70, 36, 8, 255))
    text(img, (W / 2, ty), "میوه‌چین", tsize, (255, 214, 90, 255), anchor="ms",
         stroke=int(tsize * 0.08), stroke_fill=(86, 44, 6, 255))

    taglineY = logo[3] + H * 0.042
    text(img, (W / 2, taglineY), "سه‌تا کنار هم بچین، باغ را پر کن!", W * 0.032,
         (240, 228, 204, 255), anchor="ms", stroke=2, stroke_fill=(18, 48, 32, 200))

    # --------------------------------------------------------------- helper
    dockH = H * 0.112
    dock = (MARGIN, H * 0.918 - dockH, W - MARGIN, H * 0.918)
    playH = H * 0.090
    playBottom = dock[1] - H * 0.058
    play = (W * 0.11, playBottom - playH, W * 0.89, playBottom)
    heroTop = taglineY + H * 0.016
    heroBottom = play[1] - H * 0.026
    helperH = min((heroBottom - heroTop) * 0.96, H * 0.315)
    feet = heroBottom - H * 0.006
    d.ellipse((W / 2 - helperH * 0.46, feet - helperH * 0.045, W / 2 + helperH * 0.46, feet + helperH * 0.035),
              fill=(43, 107, 69, 200))
    sprite(img, "char_idle", W / 2, feet - helperH / 2, helperH)
    for name, dx, dy, sz in (("strawberry", -0.36, 0.30, 0.20), ("lemon", 0.36, 0.30, 0.20),
                             ("berries", -0.44, 0.44, 0.15), ("coin_star", 0.44, 0.44, 0.15)):
        sprite(img, name, W / 2 + W * dx, feet - helperH * dy, helperH * sz)

    # ---------------------------------------------------------- play button
    nine(img, load("btn_lime"), play, 52)
    icon(img, "ic_play", play[2] - playH * 0.60, (play[1] + play[3]) / 2, playH * 0.58, (20, 51, 11))
    text(img, (play[0] + (play[2] - play[0]) / 2 - playH * 0.20, (play[1] + play[3]) / 2 + playH * 0.15),
         "بازی", playH * 0.40, (20, 51, 11, 255), anchor="ms")

    captionY = play[3] + H * 0.030
    text(img, (W / 2, captionY), "ادامه از سطح ۱۲", W * 0.032, (231, 217, 190, 255), anchor="ms")

    # ----------------------------------------------------------------- dock
    gap = W * 0.013
    tileW = (dock[2] - dock[0] - gap * 3) / 4
    labels = ["نقشه", "فروشگاه", "گردونه", "جایزه‌ها"]
    icons = ["ic_map", "ic_cart", "ic_wheel", "ic_star"]
    for i in range(4):
        left = dock[2] - (i + 1) * tileW - i * gap
        tile = (left, dock[1], left + tileW, dock[3])
        nine(img, load("card_glass"), tile, 52)
        cx = (tile[0] + tile[2]) / 2
        cy = tile[1] + (tile[3] - tile[1]) * 0.40
        r = tileW * 0.27
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(43, 107, 69, 255))
        icon(img, icons[i], cx, cy, r * 1.25, (255, 246, 229))
        text(img, (cx, tile[3] - (tile[3] - tile[1]) * 0.17), labels[i], W * 0.026,
             (255, 246, 229, 255), anchor="ms")
    return img


if __name__ == "__main__":
    out = main()
    out.convert("RGB").save(os.path.join(HERE, "premium_home_170.png"))
    print("ok")
