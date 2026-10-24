#!/usr/bin/env python3
"""
Builds the data files for the Mivehchin Android game:
  game-android/app/src/main/assets/levels/levelinfo.tsv
  game-android/app/src/main/assets/data/*.json
  game-android/app/src/main/assets/i18n/fa.json
  game-android/app/src/main/res/raw/sfx_*.wav

Run:  python3 tools/make-game-assets.py
"""

import json
import math
import random
import struct
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "game-android"
ASSETS = ROOT / "app/src/main/assets"
RAW = ROOT / "app/src/main/res/raw"

LEVEL_NAMES = [
    "باغِ بهار", "دشتِ هویج", "کوچهٔ انگور", "مزرعهٔ توت",
    "بازارِ میوه", "گلخانهٔ شیشه‌ای", "باغِ انار", "تپهٔ لیمو",
    "جزیرهٔ آناناس", "دهکدهٔ سبزیجات", "باغِ شبانه", "قصرِ میوه",
]

FRUITS = ["توت‌فرنگی", "پرتقال", "لیمو", "سیب سبز", "انگور", "بادمجان", "گل‌کلم"]


ROWS = 9
COLS = 8

# Board shapes, exactly like the analysed game: one 8-bit column mask per row.
SHAPES = {
    "diamond":   [0, 56, 124, 254, 254, 254, 124, 56, 0],
    "full":      [255, 255, 255, 255, 255, 255, 255, 255, 255],
    "rounded":   [126, 255, 255, 255, 255, 255, 255, 255, 126],
    "pyramid":   [16, 56, 124, 254, 254, 254, 254, 254, 254],
    "hourglass": [255, 126, 60, 24, 24, 60, 126, 255, 255],
    "cross":     [24, 24, 126, 126, 126, 126, 24, 24, 24],
}


def shape_for(lid):
    if lid <= 3:
        return "diamond"
    if lid <= 8:
        return "full"
    if lid <= 12:
        return "rounded"
    if lid <= 18:
        return "pyramid"
    if lid <= 24:
        return "hourglass"
    if lid <= 30:
        return "cross"
    cycle = ["diamond", "rounded", "pyramid", "hourglass", "cross", "full"]
    return cycle[((lid - 31) // 5) % len(cycle)]


def mask_from_rows(vals):
    return [[bool((vals[r] >> c) & 1) for c in range(COLS)] for r in range(ROWS)]


def find_runs(grid, mask):
    runs = []
    for r in range(ROWS):
        c = 0
        while c < COLS:
            if not mask[r][c] or grid[r][c] is None:
                c += 1
                continue
            t = grid[r][c]
            e = c + 1
            while e < COLS and mask[r][e] and grid[r][e] == t:
                e += 1
            if e - c >= 3:
                runs.append([(r, x) for x in range(c, e)])
            c = e
    for c in range(COLS):
        r = 0
        while r < ROWS:
            if not mask[r][c] or grid[r][c] is None:
                r += 1
                continue
            t = grid[r][c]
            e = r + 1
            while e < ROWS and mask[e][c] and grid[e][c] == t:
                e += 1
            if e - r >= 3:
                runs.append([(y, c) for y in range(r, e)])
            r = e
    return runs


def has_move(grid, mask):
    for r in range(ROWS):
        for c in range(COLS):
            if not mask[r][c] or grid[r][c] is None:
                continue
            for dr, dc in ((0, 1), (1, 0)):
                rr, cc = r + dr, c + dc
                if rr >= ROWS or cc >= COLS or not mask[rr][cc]:
                    continue
                grid[r][c], grid[rr][cc] = grid[rr][cc], grid[r][c]
                ok = len(find_runs(grid, mask)) > 0
                grid[r][c], grid[rr][cc] = grid[rr][cc], grid[r][c]
                if ok:
                    return True
    return False


def gen_candy(rng, mask, types):
    grid = [[None] * COLS for _ in range(ROWS)]
    for r in range(ROWS):
        for c in range(COLS):
            if not mask[r][c]:
                continue
            banned = set()
            if c >= 2 and grid[r][c - 1] is not None and grid[r][c - 1] == grid[r][c - 2]:
                banned.add(grid[r][c - 1])
            if r >= 2 and grid[r - 1][c] is not None and grid[r - 1][c] == grid[r - 2][c]:
                banned.add(grid[r - 1][c])
            options = [t for t in types if t not in banned]
            grid[r][c] = rng.choice(options or types)
    return grid


def build_candy(rng, mask, types):
    for _ in range(300):
        grid = gen_candy(rng, mask, types)
        if not find_runs(grid, mask) and has_move(grid, mask):
            return grid
    return gen_candy(rng, mask, types)


def ice_cells(rng, mask, count):
    cells = []
    inner = [(r, c) for r in range(1, ROWS - 1) for c in range(1, COLS - 1) if mask[r][c]]
    rng.shuffle(inner)
    for cell in inner:
        if len(cells) >= count:
            break
        cells.append([cell[0], cell[1]])
    return cells


def build_levels():
    rng = random.Random(20261024)
    rows = []
    for lid in range(1, 61):
        name = LEVEL_NAMES[(lid - 1) % len(LEVEL_NAMES)]
        mask_vals = SHAPES[shape_for(lid)]
        mask = mask_from_rows(mask_vals)

        n_types = 5 if lid <= 6 else (6 if lid <= 20 else 7)
        types = list(range(1, n_types + 1))

        moves = max(16, 22 - (lid - 1) // 10)
        base = 1200 + (lid - 1) * 95
        rank = [int(base), int(base * 1.3), int(base * 1.65)]

        targets = []
        if lid >= 4 and lid % 2 == 0:
            targets.append([types[(lid * 3) % len(types)], min(18, 8 + lid // 6)])
        if lid % 6 == 0:
            t2 = types[(lid * 5 + 1) % len(types)]
            if not any(g[0] == t2 for g in targets):
                targets.append([t2, min(14, 6 + lid // 8)])

        ice = []
        if lid >= 8 and (lid % 3 == 0 or lid % 10 == 0):
            ice = ice_cells(rng, mask, min(16, 6 + lid // 5))

        candy = build_candy(rng, mask, types)
        candy_rows = []
        for r in range(ROWS):
            vals = [candy[r][c] if mask[r][c] and candy[r][c] is not None else 0 for c in range(COLS)]
            while vals and vals[-1] == 0:
                vals.pop()
            candy_rows.append(vals)

        obj = {
            "version": 1,
            "Area_Total": [{
                "Order": 0,
                "NewBoard": mask_vals,
                "Candy": candy_rows,
                "Area_Block_Create_Total": ice,
            }],
            "Block_Kind_Count": n_types,
            "Move_Count": moves,
            "levelId": lid,
            "Rank_Score": rank,
            "Target": targets,
            "mTypeColor": types,
            "mColor": [25, 25, 25, 25, 25, 25],
        }
        rows.append((lid, name, json.dumps(obj, ensure_ascii=False, separators=(",", ":"))))
    return rows


def build_economy():
    return {
        "maxLives": 5,
        "lifeRegenSec": 120,
        "startCoins": 150,
        "lifePrice": 70,
        "boosterPrices": {"hammer": 80, "shuffle": 60, "moves": 100},
        "winBase": 20,
        "winPerStar": 15,
        "winPerMove": 3,
        "wheelCooldownSec": 14400,
        "wheelCost": 120,
    }


def build_wheel():
    return {"slices": [
        {"label": "۵۰ سکه", "kind": "coins", "amount": 50, "weight": 26},
        {"label": "۱۰۰ سکه", "kind": "coins", "amount": 100, "weight": 18},
        {"label": "چکش", "kind": "hammer", "amount": 1, "weight": 14},
        {"label": "برهم‌زن", "kind": "shuffle", "amount": 1, "weight": 14},
        {"label": "بستهٔ حرکت", "kind": "moves", "amount": 1, "weight": 12},
        {"label": "یک جان", "kind": "life", "amount": 1, "weight": 10},
        {"label": "۲۰۰ سکه", "kind": "coins", "amount": 200, "weight": 5},
        {"label": "۳۰۰ سکه", "kind": "coins", "amount": 300, "weight": 1},
    ]}


def build_pass():
    tiers = []
    rewards = [
        ("coins", 40), ("hammer", 1), ("coins", 60), ("shuffle", 1),
        ("coins", 80), ("moves", 1), ("coins", 100), ("hammer", 2),
    ]
    for i in range(1, 31):
        kind, amount = rewards[(i - 1) % len(rewards)]
        label = {
            "coins": f"{amount} سکه",
            "hammer": "چکش" if amount == 1 else f"{amount} چکش",
            "shuffle": "برهم‌زن" if amount == 1 else f"{amount} برهم‌زن",
            "moves": "بستهٔ حرکت",
        }[kind]
        tiers.append({"index": i, "stars": i * 2, "label": label, "kind": kind, "amount": amount})
    return {"tiers": tiers}


def build_shop():
    return {"items": [
        {"id": "hammer3", "title": "۳ چکش", "desc": "حذف یک میوهٔ دلخواه از صفحه",
         "kind": "hammer", "amount": 3, "price": 200},
        {"id": "shuffle3", "title": "۳ برهم‌زن", "desc": "چیدمان تازه برای صفحهٔ قفل‌شده",
         "kind": "shuffle", "amount": 3, "price": 150},
        {"id": "moves3", "title": "۳ بستهٔ حرکت", "desc": "پنج حرکت اضافه، وقتی کم آوردی",
         "kind": "moves", "amount": 3, "price": 240},
        {"id": "lifefull", "title": "پر کردن جان‌ها", "desc": "همهٔ پنج جان، همین حالا",
         "kind": "lifefull", "amount": 5, "price": 250},
    ]}


# ------------------------------------------------------------------ sounds
SR = 22050


def tone(freq, dur, vol=0.5, shape="sine", decay=6.0, slide=0.0):
    n = int(SR * dur)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / SR
        f = freq * (1.0 + slide * (i / n))
        phase += 2 * math.pi * f / SR
        if shape == "sine":
            s = math.sin(phase)
        elif shape == "tri":
            s = 2 * abs(2 * ((phase / (2 * math.pi)) % 1) - 1) - 1
        else:
            s = 1.0 if math.sin(phase) > 0 else -1.0
        env = math.exp(-decay * t) * min(1.0, t * 400)
        out.append(s * env * vol)
    return out


def mix(*tracks):
    n = max(len(t) for t in tracks)
    out = [0.0] * n
    for t in tracks:
        for i, v in enumerate(t):
            out[i] += v
    return out


def seq(*tracks):
    out = []
    for t in tracks:
        out.extend(t)
    return out


def write_wav(path, samples, vol=0.85):
    peak = max(1e-9, max(abs(s) for s in samples))
    scale = vol / peak
    data = b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s * scale)) * 32000)) for s in samples)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data)


def build_sounds():
    RAW.mkdir(parents=True, exist_ok=True)
    write_wav(RAW / "sfx_swap.wav", tone(430, 0.09, 0.5, "sine", 9, 0.35))
    write_wav(RAW / "sfx_pop.wav", mix(tone(660, 0.14, 0.5, "sine", 11), tone(990, 0.10, 0.2, "sine", 14)))
    write_wav(RAW / "sfx_special.wav", mix(tone(523, 0.28, 0.4, "tri", 5), tone(784, 0.28, 0.3, "sine", 5)))
    write_wav(RAW / "sfx_coin.wav", seq(tone(1046, 0.07, 0.4, "sine", 10), tone(1568, 0.12, 0.35, "sine", 9)))
    write_wav(RAW / "sfx_win.wav", seq(tone(523, 0.12, 0.4, "tri", 6), tone(659, 0.12, 0.4, "tri", 6),
                                      tone(784, 0.12, 0.4, "tri", 6), tone(1046, 0.30, 0.45, "tri", 3.2)))
    write_wav(RAW / "sfx_lose.wav", seq(tone(392, 0.16, 0.4, "tri", 5), tone(311, 0.16, 0.4, "tri", 5),
                                        tone(233, 0.34, 0.4, "tri", 3.5)))
    write_wav(RAW / "sfx_click.wav", tone(900, 0.045, 0.35, "sine", 18))


def main():
    (ASSETS / "levels").mkdir(parents=True, exist_ok=True)
    (ASSETS / "data").mkdir(parents=True, exist_ok=True)
    (ASSETS / "i18n").mkdir(parents=True, exist_ok=True)

    rows = build_levels()
    with (ASSETS / "levels/levelinfo.tsv").open("w", encoding="utf-8") as f:
        for lid, name, js in rows:
            f.write(f"{lid}\t{name}\t{js}\n")

    for fname, payload in [
        ("economy.json", build_economy()),
        ("wheel.json", build_wheel()),
        ("starpass.json", build_pass()),
        ("shop.json", build_shop()),
    ]:
        (ASSETS / "data" / fname).write_text(
            json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    (ASSETS / "i18n" / "fa.json").write_text(
        json.dumps({"fruits": FRUITS, "levels": LEVEL_NAMES}, ensure_ascii=False, indent=2),
        encoding="utf-8")

    build_sounds()

    print(f"levels: {len(rows)}")
    print("wrote", ASSETS / "levels/levelinfo.tsv")
    for p in sorted((ASSETS / "data").iterdir()):
        print("wrote", p, p.stat().st_size, "B")
    for p in sorted(RAW.glob("sfx_*.wav")):
        print("wrote", p, p.stat().st_size, "B")


if __name__ == "__main__":
    main()
