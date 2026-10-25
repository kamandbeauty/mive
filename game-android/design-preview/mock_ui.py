"""Composition mock for the 1.5.0 home screen and the in-game HUD.

This is a geometry preview, not a pixel-accurate renderer: it mirrors the exact
fractions used by HomeLayout.kt / HudGeometry and Screens.kt so we can eyeball
the hierarchy at 320x640, 360x800 and 412x915 before flashing a device.
"""
import math
import sys
from PIL import Image, ImageDraw, ImageFont

FONT_DIR = "/home/user/mive/game-android/app/src/main/assets/fonts/"
F_REG = FONT_DIR + "Vazirmatn-FD-Regular.ttf"
F_BOLD = FONT_DIR + "Vazirmatn-FD-Bold.ttf"
F_BLACK = FONT_DIR + "Vazirmatn-FD-Black.ttf"

try:
    import arabic_reshaper
    from bidi.algorithm import get_display
    def fa_text(s):
        return get_display(arabic_reshaper.reshape(s))
except Exception:  # pragma: no cover
    def fa_text(s):
        return s


def font(path, size):
    return ImageFont.truetype(path, max(6, int(size)))


def text(d, xy, s, size, fill, path=F_BOLD, anchor="mm"):
    d.text(xy, fa_text(s), font=font(path, size), fill=fill, anchor=anchor)


def rr(d, box, r, fill=None, outline=None, width=1):
    d.rounded_rectangle(box, radius=max(1, r), fill=fill, outline=outline, width=width)


def vgrad(img, box, top, bottom):
    x0, y0, x1, y1 = [int(round(v)) for v in box]
    if x1 <= x0 or y1 <= y0:
        return
    h = y1 - y0
    for i in range(h):
        k = i / max(1, h - 1)
        c = tuple(int(top[j] + (bottom[j] - top[j]) * k) for j in range(3))
        ImageDraw.Draw(img).line([(x0, y0 + i), (x1, y0 + i)], fill=c)


def grad_rr(img, d, box, r, top, bottom, outline=None, w=2):
    x0, y0, x1, y1 = [int(round(v)) for v in box]
    if x1 <= x0 or y1 <= y0:
        return
    layer = Image.new("RGB", (x1 - x0 + 1, y1 - y0 + 1))
    vgrad(layer, (0, 0, x1 - x0, y1 - y0), top, bottom)
    mask = Image.new("L", layer.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, layer.size[0] - 1, layer.size[1] - 1), radius=max(1, r), fill=255)
    img.paste(layer, (x0, y0), mask)
    if outline:
        d.rounded_rectangle((x0, y0, x1, y1), radius=max(1, r), outline=outline, width=w)


def garden(w, h):
    img = Image.new("RGB", (w, h), (62, 138, 92))
    vgrad(img, (0, 0, w, h), (62, 138, 92), (7, 28, 20))
    d = ImageDraw.Draw(img)
    for i in range(26):
        x = (i * 137) % w
        y = int(h * (0.2 + 0.8 * ((i * 53) % 100) / 100))
        r = w * (0.004 + 0.004 * (i % 3))
        d.ellipse((x - r, y - r, x + r, y + r), fill=(255, 255, 255, 30))
    return img


def helper(d, cx, feet, h):
    """Rough silhouette of Laleh so we can judge her footprint."""
    u = h / 100.0
    skin, hair, dress, straw = (255, 221, 187), (107, 62, 30), (79, 166, 74), (242, 194, 91)
    head_r, head_cy = 15 * u, feet - 58 * u
    d.rounded_rectangle((cx - 7 * u, feet - 22 * u, cx + 7 * u, feet - 2 * u), 3 * u, fill=(232, 185, 143))
    d.polygon([(cx - 11 * u, feet - 34 * u), (cx + 11 * u, feet - 34 * u), (cx + 19 * u, feet - 17 * u),
               (cx, feet - 11 * u), (cx - 19 * u, feet - 17 * u)], fill=dress)
    d.rounded_rectangle((cx - 11.4 * u, feet - 40 * u, cx + 11.4 * u, feet - 26 * u), 6 * u, fill=(44, 117, 52))
    d.rounded_rectangle((cx - 7 * u, feet - 33 * u, cx + 7 * u, feet - 15 * u), 4 * u, fill=(255, 246, 229))
    d.line((cx - 10 * u, feet - 38 * u, cx - 17 * u, feet - 20 * u), fill=skin, width=int(5.4 * u))
    d.line((cx + 10 * u, feet - 38 * u, cx + 17 * u, feet - 20 * u), fill=skin, width=int(5.4 * u))
    d.ellipse((cx - head_r * 1.06, head_cy - head_r * 1.06, cx + head_r * 1.06, head_cy + head_r * 1.06), fill=hair)
    d.ellipse((cx - head_r * 0.92, head_cy - head_r * 0.92, cx + head_r * 0.92, head_cy + head_r * 0.92), fill=skin)
    for dx in (-0.40 * head_r, 0.40 * head_r):
        d.ellipse((cx + dx - 2.1 * u, head_cy - 1.6 * u, cx + dx + 2.1 * u, head_cy + 5 * u), fill=(58, 36, 21))
    hat_y = head_cy - head_r * 0.72
    d.ellipse((cx - head_r * 1.55, hat_y - 3.4 * u, cx + head_r * 1.55, hat_y + 4.6 * u), fill=straw)
    d.rounded_rectangle((cx - head_r * 0.80, hat_y - head_r * 0.72, cx + head_r * 0.80, hat_y + 1.6 * u),
                        head_r * 0.46, fill=straw)
    d.rounded_rectangle((cx - head_r * 0.82, hat_y - head_r * 0.20, cx + head_r * 0.82, hat_y + 1.2 * u),
                        head_r * 0.18, fill=(232, 84, 107))


def fruit(d, kind, cx, cy, size):
    colors = [(232, 51, 74), (245, 147, 22), (245, 197, 24), (116, 204, 51), (139, 92, 246), (110, 75, 224), (242, 232, 207)]
    c = colors[kind % 7]
    r = size * 0.5
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=c)
    d.ellipse((cx - r * 0.45, cy - r * 0.6, cx - r * 0.05, cy - r * 0.2), fill=(255, 255, 255))


def pill(d, box, fill, outline=None):
    rr(d, box, (box[3] - box[1]) / 2, fill=fill, outline=outline, width=2)


def home(w, h, path):
    img = garden(w, h)
    d = ImageDraw.Draw(img)
    m = w * 0.045
    chip_h, chip_top = h * 0.056, h * 0.028
    settings = (w - m - chip_h, chip_top, w - m, chip_top + chip_h)
    coin = (m, chip_top, m + w * 0.285, chip_top + chip_h)
    lives = (coin[2] + w * 0.018, chip_top, coin[2] + w * 0.018 + w * 0.245, chip_top + chip_h)
    logo = (w * 0.055, h * 0.100, w * 0.945, h * 0.186)
    tag_y = logo[3] + h * 0.030
    dock_h = h * 0.112
    dock = (m, h * 0.918 - dock_h, w - m, h * 0.918)
    gap = w * 0.008 * 1.6
    tw = (dock[2] - dock[0] - gap * 3) / 4
    tiles = [(dock[2] - (i + 1) * tw - i * gap, dock[1], dock[2] - i * tw - i * gap, dock[3]) for i in range(4)]
    play_h = h * 0.090
    play_bottom = dock[1] - h * 0.058
    play = (w * 0.11, play_bottom - play_h, w * 0.89, play_bottom)
    cap_y = play[3] + h * 0.030
    hero = (w * 0.10, tag_y + h * 0.016, w * 0.90, play[1] - h * 0.026)

    rr(d, settings, chip_h * 0.36, fill=(40, 70, 58), outline=(120, 190, 160), width=2)
    text(d, ((settings[0] + settings[2]) / 2, (settings[1] + settings[3]) / 2), "⚙", chip_h * 0.5, (255, 246, 229))

    for box, val, accent, icon in ((lives, "۵/۵", (185, 58, 80), "♥"), (coin, "۱۵۰", (43, 107, 69), "◎")):
        rr(d, (box[0] + 2, box[1] + chip_h * 0.10, box[2] + 2, box[3] + chip_h * 0.10), chip_h * 0.42, fill=(0, 0, 0))
        grad_rr(img, d, box, chip_h * 0.42, (accent[0] + 40, accent[1] + 40, accent[2] + 40), accent,
                outline=(255, 201, 77) if accent[1] > 70 else (255, 157, 174), w=2)
        text(d, (box[0] + chip_h * 0.44, (box[1] + box[3]) / 2), icon, chip_h * 0.42, (255, 246, 229))
        text(d, (box[2] - chip_h * 0.24, (box[1] + box[3]) / 2), val, chip_h * 0.46, (255, 246, 229),
             F_BLACK, anchor="rm")

    # logo with ribbon tails
    lg_h = logo[3] - logo[1]
    d.polygon([(logo[0], logo[1] + lg_h * 0.18), (logo[0] - w * 0.045, logo[1] + lg_h * 0.36),
               (logo[0], logo[1] + lg_h * 0.54)], fill=(110, 66, 32))
    d.polygon([(logo[2], logo[1] + lg_h * 0.18), (logo[2] + w * 0.045, logo[1] + lg_h * 0.36),
               (logo[2], logo[1] + lg_h * 0.54)], fill=(110, 66, 32))
    grad_rr(img, d, logo, lg_h * 0.30, (247, 208, 106), (201, 132, 19), outline=(255, 246, 220), w=3)
    text(d, ((logo[0] + logo[2]) / 2, (logo[1] + logo[3]) / 2), "میوه‌چین", w * 0.112, (255, 246, 229), F_BLACK)

    text(d, (w / 2, tag_y), "سه‌تا کنار هم بچین، باغ را پر کن!", w * 0.034, (231, 217, 190))
    text(d, (hero[0] + 26, hero[1] + 10), "hero", h * 0.016, (200, 255, 200), F_REG, anchor="lm")

    girl_h = min((hero[3] - hero[1]) * 0.96, h * 0.315)
    feet = hero[3] - h * 0.006
    helper(d, w * 0.5, feet, girl_h)
    for i, (kind, dx, lift, sc) in enumerate([(0, -0.36, 0.30, 0.20), (2, 0.36, 0.30, 0.20),
                                             (4, -0.44, 0.44, 0.15), (6, 0.44, 0.44, 0.15)]):
        fruit(d, kind, w * 0.5 + w * dx, feet - girl_h * lift, girl_h * sc)

    grad_rr(img, d, play, play_h * 0.40, (217, 251, 144), (87, 153, 42), outline=(198, 243, 107), w=3)
    text(d, ((play[0] + play[2]) / 2 - play_h * 0.22, (play[1] + play[3]) / 2), "بازی", play_h * 0.36, (20, 51, 11), F_BLACK)
    text(d, (w / 2, cap_y), "ادامه از سطح ۱", w * 0.032, (231, 217, 190))

    labels = ["نقشه", "فروشگاه", "گردونه", "جایزه‌ها"]
    for i, tile in enumerate(tiles):
        grad_rr(img, d, tile, tw * 0.26, (28, 84, 64), (12, 44, 33), outline=(255, 233, 200), w=2)
        cy = tile[1] + (tile[3] - tile[1]) * 0.40
        r = tw * 0.27
        d.ellipse(((tile[0] + tile[2]) / 2 - r, cy - r, (tile[0] + tile[2]) / 2 + r, cy + r), fill=(47, 122, 78))
        text(d, ((tile[0] + tile[2]) / 2, tile[3] - (tile[3] - tile[1]) * 0.13), labels[i],
             min(w * 0.030, tw * 0.80), (255, 246, 229))
    img.save(path)
    return path


def hud(w, h, path, goals=True):
    img = garden(w, h)
    d = ImageDraw.Draw(img)
    m = w * 0.045
    chrome_h, ct = h * 0.062, h * 0.026
    back = (w - m - chrome_h, ct, w - m, ct + chrome_h)
    title = (w * 0.235, ct, w * 0.765, ct + chrome_h)
    coin = (m, ct + chrome_h * 0.06, m + w * 0.145, ct + chrome_h * 0.94)
    rt, rh = ct + chrome_h + h * 0.016, h * 0.070
    moves = (m, rt, m + w * 0.205, rt + rh)
    score = (moves[2] + w * 0.018, rt, w - m, rt + rh)
    gt, gh = rt + rh + h * 0.012, h * 0.044
    goalbox = (m, gt, w - m, gt + gh)
    band_t, band_h = h * 0.846, h * 0.098
    guide = (m, band_t, m + band_h * 0.96, band_t + band_h)
    booster = (guide[2] + w * 0.022, band_t, w - m, band_t + band_h)
    gap = w * 0.008 * 2.4
    cw = (booster[2] - booster[0] - gap * 2) / 3
    cards = [(booster[0] + i * (cw + gap), band_t, booster[0] + i * (cw + gap) + cw, band_t + band_h) for i in range(3)]
    board_top = goalbox[3] + h * 0.020 if goals else rt + rh + h * 0.022
    board_bottom = band_t - h * 0.020

    rr(d, back, chrome_h * 0.36, fill=(40, 80, 62), outline=(120, 190, 160), width=2)
    text(d, ((back[0] + back[2]) / 2, (back[1] + back[3]) / 2), "›", chrome_h * 0.6, (255, 246, 229))
    grad_rr(img, d, title, chrome_h * 0.34, (192, 138, 82), (122, 71, 33), outline=(60, 30, 10), w=2)
    text(d, ((title[0] + title[2]) / 2, title[1] + chrome_h * 0.46), "سطح ۱", chrome_h * 0.40, (255, 246, 229), F_BLACK)
    text(d, ((title[0] + title[2]) / 2, title[3] - chrome_h * 0.13), "باغ بهاری", chrome_h * 0.22, (255, 240, 210))

    pill(d, coin, (10, 34, 24), (255, 233, 200))
    d.ellipse((coin[0] + chrome_h * 0.10, (coin[1] + coin[3]) / 2 - chrome_h * 0.18,
               coin[0] + chrome_h * 0.46, (coin[1] + coin[3]) / 2 + chrome_h * 0.18), fill=(255, 201, 77))
    text(d, (coin[2] - chrome_h * 0.10, (coin[1] + coin[3]) / 2), "۱۵۰", rh * 0.42, (255, 246, 229), F_BLACK, "rm")

    grad_rr(img, d, moves, rh * 0.32, (23, 116, 79), (5, 44, 28), outline=(198, 243, 107), w=2)
    text(d, ((moves[0] + moves[2]) / 2, moves[1] + rh * 0.32), "حرکت", rh * 0.22, (168, 203, 190))
    text(d, ((moves[0] + moves[2]) / 2, moves[1] + rh * 0.80), "۲۴", rh * 0.42, (255, 246, 229), F_BLACK)

    grad_rr(img, d, score, rh * 0.32, (28, 84, 64), (12, 44, 33), outline=(255, 201, 77), w=2)
    text(d, (score[0] + score[2] - (score[0] + score[2]) / 2 * 0.0 + w * 0.02, score[1] + rh * 0.40), "", rh * 0.2, (255, 255, 255))
    text(d, (score[2] - w * 0.02, score[1] + rh * 0.40), "۱۲۰۰", rh * 0.34, (255, 246, 229), F_BLACK, "rm")
    text(d, (score[0] + w * 0.02, score[1] + rh * 0.38), "هدف ۵۰۰۰", rh * 0.24, (231, 217, 190), F_BOLD, "lm")
    d.rounded_rectangle((score[0] + w * 0.02, score[3] - rh * 0.24, score[2] - w * 0.02, score[3] - rh * 0.10),
                        rh * 0.07, fill=(8, 26, 18))
    d.rounded_rectangle((score[0] + w * 0.02, score[3] - rh * 0.24, score[0] + w * 0.02 + (score[2] - score[0]) * 0.36, score[3] - rh * 0.10),
                        rh * 0.07, fill=(52, 199, 89))

    if goals:
        chip_h = gh
        chip_w = min((goalbox[2] - goalbox[0]) * 0.30, ((goalbox[2] - goalbox[0]) - gap * 2) / 3)
        total = chip_w * 3 + gap * 2
        x = (w - total) / 2
        for i in range(3):
            rr(d, (x, gt, x + chip_w, gt + chip_h), chip_h * 0.46, fill=(10, 36, 24), outline=(255, 240, 220), width=1)
            fruit(d, i, x + chip_w - chip_h * 0.54, gt + chip_h / 2, chip_h * 0.92)
            text(d, (x + chip_h * 0.34, gt + chip_h / 2), f"۰/{2 + i}", chip_h * 0.40, (255, 246, 229), F_BLACK, "lm")
            x += chip_w + gap

    # board
    board_left, board_right = w * 0.055, w * 0.945
    d.rounded_rectangle((board_left, board_top, board_right, board_bottom), w * 0.05, fill=(138, 85, 40))
    inner = (board_left + w * 0.012, board_top + w * 0.012, board_right - w * 0.012, board_bottom - w * 0.012)
    d.rounded_rectangle(inner, w * 0.04, fill=(59, 34, 17))
    cols, rows = 8, 9
    cwid = (inner[2] - inner[0]) / cols
    chei = (inner[3] - inner[1]) / rows
    cell = min(cwid, chei)
    ox = inner[0] + ((inner[2] - inner[0]) - cell * cols) / 2
    oy = inner[1] + ((inner[3] - inner[1]) - cell * rows) / 2
    for r in range(rows):
        for c in range(cols):
            x0, y0 = ox + c * cell, oy + r * cell
            d.rounded_rectangle((x0 + cell * 0.06, y0 + cell * 0.06, x0 + cell * 0.94, y0 + cell * 0.94),
                                cell * 0.24, fill=(18, 50, 34))
            fruit(d, (r * 3 + c * 5) % 7, x0 + cell / 2, y0 + cell / 2, cell * 0.82)

    # guide portrait + boosters
    rr(d, guide, (guide[3] - guide[1]) / 2, fill=(43, 107, 73), outline=(255, 201, 77), width=2)
    helper(d, (guide[0] + guide[2]) / 2, guide[3] + (guide[3] - guide[1]) * 0.34, (guide[3] - guide[1]) * 2.5)
    labels = ["چکش", "برهم‌زن", "حرکت"]
    for i, card in enumerate(cards):
        grad_rr(img, d, card, cwid * 0.26, (28, 84, 64), (12, 44, 33), outline=(255, 233, 200), w=2)
        cx, cy = (card[0] + card[2]) / 2, card[1] + band_h * 0.37
        r = cwid * 0.24
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(49, 88, 60))
        text(d, (cx, cy), "✚", r * 1.0, (255, 246, 229))
        text(d, (cx, card[3] - band_h * 0.11), labels[i], cwid * 0.20, (231, 217, 190))
        bx, by = card[0] + cwid * 0.14, card[1] + cwid * 0.14
        d.ellipse((bx - cwid * 0.145, by - cwid * 0.145, bx + cwid * 0.145, by + cwid * 0.145), fill=(198, 243, 107))
        text(d, (bx, by), "۳", cwid * 0.17, (16, 51, 31), F_BLACK)
    img.save(path)
    return path



def shop(w, h, path):
    img = garden(w, h)
    d = ImageDraw.Draw(img)
    m = w * 0.045
    # header
    plaque = (w * 0.235, h * 0.026, w * 0.765, h * 0.100)
    grad_rr(img, d, plaque, (plaque[3] - plaque[1]) * 0.34, (192, 138, 82), (122, 71, 33), outline=(60, 30, 10), w=2)
    text(d, ((plaque[0] + plaque[2]) / 2, (plaque[1] + plaque[3]) / 2), "فروشگاه", (plaque[3] - plaque[1]) * 0.46,
         (255, 246, 229), F_BLACK)
    back_h = h * 0.066
    back = (w - m - back_h, h * 0.030, w - m, h * 0.030 + back_h)
    rr(d, back, back_h * 0.36, fill=(40, 80, 62), outline=(120, 190, 160), width=2)
    text(d, ((back[0] + back[2]) / 2, (back[1] + back[3]) / 2), "›", back_h * 0.6, (255, 246, 229))
    chip_h = h * 0.056
    chip = (m, h * 0.035, m + w * 0.14, h * 0.035 + chip_h)
    pill(d, chip, (10, 34, 24), (255, 233, 200))
    text(d, (chip[2] - chip_h * 0.20, (chip[1] + chip[3]) / 2), "۱۵۰", chip_h * 0.42, (255, 246, 229), F_BLACK, "rm")

    bal_h = h * 0.060
    bal = (w * 0.28, h * 0.116, w * 0.72, h * 0.116 + bal_h)
    pill(d, bal, (255, 246, 223), (224, 166, 58))
    text(d, ((bal[0] + bal[2]) / 2 + bal_h * 0.28, (bal[1] + bal[3]) / 2), "۱۵۰", bal_h * 0.46, (138, 90, 5), F_BLACK)
    d.ellipse((bal[0] + bal_h * 0.30, (bal[1] + bal[3]) / 2 - bal_h * 0.34,
               bal[0] + bal_h * 0.98, (bal[1] + bal[3]) / 2 + bal_h * 0.34), fill=(255, 201, 77))

    names = [("چکش", "یک میوه را حذف می‌کند", "۸۰", 2), ("برهم‌زن", "تخته را دوباره می‌چیند", "۶۰", 1),
             ("حرکت", "پنج حرکت بیشتر", "۱۰۰", 0), ("پر کردن جان", "همهٔ جان‌ها پر می‌شود", "۲۲۰", 0),
             ("کیف سکه", "۱۵۰ سکه اضافه", "۱۵۰", 0), ("یک جان", "برای وقتی که جان کم آوردی", "۷۰", 0)]
    top, card_h = h * 0.190, h * 0.098
    gap = h * 0.014
    for i, (title, desc, price, owned) in enumerate(names):
        y = top + i * (card_h + gap)
        rect = (w * 0.045, y, w * 0.955, y + card_h)
        # gold rim + cream body
        rr(d, (rect[0], rect[1] + card_h * 0.06, rect[2], rect[3] + card_h * 0.06), w * 0.030, fill=(20, 10, 4))
        grad_rr(img, d, rect, w * 0.030, (255, 236, 180), (184, 116, 12))
        inner = (rect[0] + w * 0.013, rect[1] + w * 0.013, rect[2] - w * 0.013, rect[3] - w * 0.013)
        grad_rr(img, d, inner, w * 0.026, (255, 252, 243), (255, 230, 190))
        inset = card_h * 0.30
        right = rect[2] - inset
        icon_size = card_h * 0.52
        icx, icy = right - icon_size / 2, (rect[1] + rect[3]) / 2
        d.ellipse((icx - icon_size / 2, icy - icon_size / 2, icx + icon_size / 2, icy + icon_size / 2), fill=(31, 107, 69))
        text(d, (icx, icy), "✚", icon_size * 0.42, (255, 246, 229))
        a_left = rect[0] + card_h * 0.22
        a_w, a_h = card_h * 1.22, card_h * 0.52
        acy = (rect[1] + rect[3]) / 2
        grad_rr(img, d, (a_left, acy - a_h / 2, a_left + a_w, acy + a_h / 2), a_h * 0.40, (255, 224, 138), (201, 136, 20),
                outline=(255, 201, 77), w=2)
        d.ellipse((a_left + a_h * 0.40, acy - a_h * 0.30, a_left + a_h * 1.00, acy + a_h * 0.30), fill=(255, 201, 77))
        text(d, (a_left + a_w * 0.58, acy), price, a_h * 0.46, (67, 40, 10), F_BLACK)
        tright = icx - icon_size / 2 - card_h * 0.14
        text(d, (tright, acy - card_h * 0.06), title, card_h * 0.24, (90, 46, 5), F_BLACK, "rm")
        text(d, (tright, acy + card_h * 0.20), desc, card_h * 0.16, (122, 82, 32), F_REG, "rm")
    img.save(path)
    return path

if __name__ == "__main__":
    out = []
    for (w, h) in [(320, 640), (360, 800), (412, 915)]:
        out.append(home(w, h, f"/home/user/ref-art/ui_home_{w}.png"))
        out.append(hud(w, h, f"/home/user/ref-art/ui_game_{w}.png"))
        out.append(shop(w, h, f"/home/user/ref-art/ui_shop_{w}.png"))
    print("\n".join(out))
