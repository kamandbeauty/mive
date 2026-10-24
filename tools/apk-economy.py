#!/usr/bin/env python3
"""
apk-economy.py — extract economy / monetization facts from an APK (static, offline).

Designed for games shipped with a native engine (Cocos2d-x / Unity) plus a Java ad
mediation stack: it reads the manifest, DEX class table, bundled JSON/TSV game data,
i18n blobs and the main native libraries, then prints what the binary itself declares
about how it makes money.

Everything printed is read from the file. Anything derived (e.g. "$/coin") is marked
as [inference] in the Markdown report.

Usage:
    python3 tools/apk-economy.py game.apk --md economy.md --json economy.json

Requires: androguard (pip install androguard)
"""

from __future__ import annotations

import argparse
import json
import math
import re
import statistics
import sys
import zipfile
from collections import Counter, defaultdict
from pathlib import Path

# ---------------------------------------------------------------- ad / monetization SDKs
# class-descriptor prefix -> label. Longest prefix wins.
SDK_PREFIXES = {
    "com/applovin/": "AppLovin MAX",
    "com/ironsource/": "ironSource / LevelPlay",
    "com/mbridge/msdk/": "Mintegral",
    "com/bytedance/sdk/": "Pangle / ByteDance",
    "com/bykv/": "Pangle (ByteDance)",
    "com/unity3d/services/": "Unity Ads",
    "com/unity3d/ads/": "Unity Ads",
    "com/vungle/": "Vungle",
    "com/fyber/": "Fyber / Digital Turbine",
    "com/digitalturbine/": "Fyber / Digital Turbine",
    "com/smaato/": "Smaato",
    "com/chartboost/": "Chartboost",
    "com/yandex/mobile/ads/": "Yandex Mobile Ads",
    "com/yandex/div": "Yandex Ads (divkit)",
    "com/google/android/gms/ads/": "AdMob / GMA",
    "com/google/ads/": "AdMob / GMA",
    "com/facebook/ads/": "Meta Audience Network",
    "com/iab/omid/": "IAB OM SDK (viewability)",
    "com/tapjoy/": "Tapjoy",
    "com/inmobi/": "InMobi",
    "com/ogury/": "Ogury",
    "com/criteo/": "Criteo",
    "com/amazon/aps/": "Amazon APS",
    "com/pubmatic/": "PubMatic",
    "com/mopub/": "MoPub (legacy)",
    "com/startapp/": "Start.io",
    "com/chartboost/sdk/": "Chartboost",
    # analytics / attribution (they price the funnel, not the ad)
    "io/appmetrica/": "AppMetrica (analytics)",
    "com/appsflyer/": "AppsFlyer (attribution)",
    "com/google/firebase/": "Firebase (analytics)",
    "com/adjust/sdk/": "Adjust (attribution)",
    "com/kochava/": "Kochava (attribution)",
    "net/aihelp/": "AiHelp (support desk)",
}

# hosts we don't care about when listing the game's own backends
SDK_HOSTS = re.compile(
    r"(applovin|ironsource|mintegral|vungle|fyber|adcolony|chartboost|mopub|inmobi|"
    r"smaato|amazon|pubmatic|tapjoy|bytedance|pangle|appmetrica|appsflyer|crashlytics|"
    r"sentry|google|facebook|unity|yandex|apple|android|w3\.org|whatwg|json|xmlpull|"
    r"apache|openssl|zlib|libpng|freetype|exoplayer|cocos|sqlite|curl|nghttp|icu|"
    r"protobuf|gms|doubleclick|googlesyndication|googletagmanager|omid|iab\.|cloudflare|"
    r"exoplayer\.dev|kotlin|okhttp|squareup|example\.com|schemas\.|schemas\.android|"
    r"localhost|127\.0\.0\.1|amazonaws|googleapis|gstatic|segment\.io|mixpanel|amplitude)",
    re.I,
)

PRICE_RE = re.compile(r"^\$\s?(\d{1,3}\.\d{2})$")
PLACEMENT_RE = re.compile(
    r"^(show|has|close|free|reward|buy)[A-Za-z0-9_]{0,32}(Ad|AD|ads)[A-Za-z0-9_]{0,32}$", re.I
)
FALSE_POSITIVES = {"freeaddrinfo", "FreeAddrInfo", "hasAddress", "showAddress"}

ECON_KEY_RE = re.compile(
    r"^(coin|diamond|gem|energy|life|heart|spin|turntable|lottery|pass|battlepass|gift|"
    r"offer|sale|shop|pay|payid|price|video|ad_|revival|task|streak|rank|tower)[a-z0-9_]*$",
    re.I,
)


# ---------------------------------------------------------------- helpers
def read_manifest(apk_path: str) -> dict:
    import logging

    for name in ("androguard", "asn1crypto"):
        logging.getLogger(name).setLevel(logging.CRITICAL)
    try:
        from loguru import logger

        logger.remove()
    except Exception:
        pass
    from androguard.core.apk import APK

    apk = APK(apk_path)
    app = apk.get_android_manifest_xml().find("application")
    A = "{http://schemas.android.com/apk/res/android}"
    meta = {}
    if app is not None:
        for m in app.findall("meta-data"):
            k, v = m.get(A + "name"), m.get(A + "value")
            if k:
                meta[k] = str(v) if v is not None else ""
    return {
        "package": apk.get_package(),
        "app_name": (app.get(A + "label") if app is not None else None),
        "version_name": apk.get_androidversion_name(),
        "version_code": apk.get_androidversion_code(),
        "min_sdk": apk.get_min_sdk_version(),
        "target_sdk": apk.get_target_sdk_version(),
        "meta": meta,
        "permissions": sorted(apk.get_permissions() or []),
    }


def dex_classes(apk_path: str) -> tuple[Counter, list[str]]:
    """Return (prefix counts, all class descriptors)."""
    import logging

    for name in ("androguard", "asn1crypto"):
        logging.getLogger(name).setLevel(logging.CRITICAL)
    try:
        from loguru import logger

        logger.remove()
    except Exception:
        pass
    from androguard.core.apk import APK
    from androguard.core.dex import DEX

    apk = APK(apk_path)
    classes: set[str] = set()
    for blob in apk.get_all_dex():
        for s in DEX(blob).get_strings():
            s = str(s)
            if s.startswith("L") and s.endswith(";"):
                classes.add(s)
    counts = Counter()
    for cls in classes:
        body = cls[1:-1]
        best = None
        for pref, label in SDK_PREFIXES.items():
            if body.startswith(pref) and (best is None or len(pref) > len(best[0])):
                best = (pref, label)
        if best:
            counts[best[1]] += 1
    return counts, sorted(classes)


def zip_text(z: zipfile.ZipFile, name: str, limit: int = 4_000_000) -> str:
    try:
        info = z.getinfo(name)
        if info.file_size > limit:
            return ""
        return z.read(name).decode("utf-8", "replace")
    except Exception:
        return ""


def strings_of(data: bytes, minlen: int = 4) -> list[str]:
    out, cur = [], bytearray()
    for b in data:
        if 32 <= b < 127:
            cur.append(b)
        else:
            if len(cur) >= minlen:
                out.append(cur.decode("ascii", "replace"))
            cur.clear()
    if len(cur) >= minlen:
        out.append(cur.decode("ascii", "replace"))
    return out


# ---------------------------------------------------------------- extractors
def economy_configs(z: zipfile.ZipFile) -> list[dict]:
    """Bundled JSON files that contain monetization knobs."""
    out = []
    for name in z.namelist():
        if not name.lower().endswith(".json") or not name.startswith("assets/"):
            continue
        if z.getinfo(name).file_size > 300_000:
            continue
        txt = zip_text(z, name, 300_000)
        if not txt.strip().startswith(("{", "[")):
            continue
        try:
            data = json.loads(txt)
        except Exception:
            continue
        keys = set()

        def walk(o):
            if isinstance(o, dict):
                for k, v in o.items():
                    keys.add(k)
                    walk(v)
            elif isinstance(o, list):
                for v in o[:20]:
                    walk(v)

        walk(data)
        econ = {
            k: v
            for k, v in (
                (k, data.get(k) if isinstance(data, dict) else None)
                for k in ("show", "times", "cd", "level", "unlock_level", "price", "payId")
            )
            if v is not None
        }
        interesting = [k for k in keys if ECON_KEY_RE.match(k)]
        if econ or {"payId", "price", "discount_percent", "probability"} & keys:
            out.append(
                {
                    "file": name,
                    "knobs": econ,
                    "keys": sorted(interesting)[:25],
                    "raw_size": len(txt),
                }
            )
    return sorted(out, key=lambda r: r["file"])


def level_economy(z: zipfile.ZipFile) -> dict:
    """Parse levels/*.tsv (id \\t json) and describe the difficulty curve."""
    res = {}
    for name in z.namelist():
        if not name.startswith("assets/levels/") or not name.endswith(".tsv"):
            continue
        txt = zip_text(z, name, 40_000_000)
        rows = []
        for line in txt.splitlines():
            if "\t" not in line:
                continue
            _, js = line.split("\t", 1)
            try:
                rows.append(json.loads(js))
            except Exception:
                continue
        if not rows:
            continue
        moves = [r["Move_Count"] for r in rows if isinstance(r.get("Move_Count"), int)]
        stars = [
            r["Rank_Score"][2]
            for r in rows
            if isinstance(r.get("Rank_Score"), list) and len(r["Rank_Score"]) >= 3
        ]
        kinds = Counter()
        for r in rows:
            for t in r.get("Target") or []:
                if isinstance(t, list) and t:
                    kinds[t[0]] += 1
        bands = {}
        if moves:
            ids = [r.get("levelId") for r in rows]
            lo, hi = min(i for i in ids if isinstance(i, int)), max(
                i for i in ids if isinstance(i, int)
            )
            step = 500
            for start in range(lo, hi + 1, step):
                sel = [
                    r["Move_Count"]
                    for r in rows
                    if isinstance(r.get("Move_Count"), int)
                    and isinstance(r.get("levelId"), int)
                    and start <= r["levelId"] < start + step
                ]
                if sel:
                    bands[f"{start}-{start + step - 1}"] = {
                        "n": len(sel),
                        "median_moves": statistics.median(sel),
                    }
        res[name] = {
            "levels": len(rows),
            "moves": {
                "min": min(moves),
                "median": statistics.median(moves),
                "max": max(moves),
            }
            if moves
            else None,
            "three_star_score": {"min": min(stars), "median": statistics.median(stars), "max": max(stars)}
            if stars
            else None,
            "target_kinds": kinds.most_common(10),
            "bands": bands,
        }
    return res


def price_ladder(z: zipfile.ZipFile) -> dict:
    """Hard-coded price strings in i18n blobs (fallback copy, Play Billing overrides them)."""
    prices, discounts, samples = Counter(), Counter(), []
    for name in z.namelist():
        if "/i18n" not in name or z.getinfo(name).file_size > 4_000_000:
            continue
        txt = zip_text(z, name)
        for w in re.findall(r"[ -~]{3,80}", txt):
            for tok in w.split("|"):
                tok = tok.strip()
                m = PRICE_RE.match(tok)
                if m:
                    prices[f"${float(m.group(1)):.2f}"] += 1
                if re.fullmatch(r"\d{1,2}%", tok):
                    discounts[tok] += 1
            if re.search(r"Remove AD|Lives|Double the prize|OFF", w):
                samples.append(w.strip()[:90])
    return {
        "prices": sorted(prices, key=lambda p: float(p[1:])),
        "discounts": discounts.most_common(5),
        "samples": samples[:15],
    }


def native_signals(z: zipfile.ZipFile, main_lib: str = "libMyGame.so") -> dict:
    placements, econ_keys, prices, hosts = set(), set(), set(), Counter()
    names = [n for n in z.namelist() if n.endswith("/" + main_lib)]
    for name in names[:2]:  # one ABI is enough
        data = z.read(name)
        if len(data) > 60_000_000:
            continue
        ss = strings_of(data)
        for s in ss:
            if PLACEMENT_RE.match(s) and s not in FALSE_POSITIVES:
                placements.add(s)
            elif ECON_KEY_RE.match(s) and len(s) < 40:
                econ_keys.add(s)
            m = PRICE_RE.match(s)
            if m:
                prices.add(f"${float(m.group(1)):.2f}")
            if s.startswith(("http://", "https://")):
                hosts[re.sub(r"^https?://", "", s).split("/")[0]] += 1
    own_hosts = []
    for h, c in hosts.most_common():
        if "." not in h or SDK_HOSTS.search(h):
            continue
        tld = h.split(".")[-1]
        if len(tld) >= 2 and not tld.isdigit():
            own_hosts.append((h, c))
        if len(own_hosts) >= 20:
            break
    return {
        "libs_scanned": names[:2],
        "placements": sorted(placements),
        "economy_keys": sorted(econ_keys)[:80],
        "prices": sorted(prices, key=lambda p: float(p[1:])),
        "backend_hosts": own_hosts,
    }


def manifest_monetization(meta: dict) -> dict:
    keep = {}
    for k, v in meta.items():
        if any(t in k for t in ("ads.", "applovin", "billingclient", "stamp", "aps.", "smaato", "facebook.sdk")):
            keep[k] = v
    return keep


def main() -> int:
    ap = argparse.ArgumentParser(description="APK economy / monetization extractor")
    ap.add_argument("apk")
    ap.add_argument("--json")
    ap.add_argument("--md")
    ap.add_argument("--top", type=int, default=25)
    a = ap.parse_args()

    path = Path(a.apk)
    if not path.is_file():
        print(f"error: no such file: {path}", file=sys.stderr)
        return 2

    import hashlib

    sha = hashlib.sha256(path.read_bytes()).hexdigest()
    manifest = read_manifest(str(path))
    sdk_counts, classes = dex_classes(str(path))

    with zipfile.ZipFile(path) as z:
        configs = economy_configs(z)
        levels = level_economy(z)
        prices = price_ladder(z)
        native = native_signals(z)
        dex_names = [
            n for n in z.namelist()
            if n.startswith("classes") and n.endswith(".dex") and n.count("/") == 0
        ]
        dex_bytes = sum(z.getinfo(n).file_size for n in dex_names)
        so_bytes = sum(z.getinfo(n).file_size for n in z.namelist() if n.endswith(".so"))

    out = {
        "artifact": {
            "path": str(path),
            "bytes": path.stat().st_size,
            "sha256": sha,
            "dex_files": len(dex_names),
            "dex_bytes": dex_bytes,
            "native_bytes": so_bytes,
            "classes": len(classes),
        },
        "manifest": manifest,
        "manifest_monetization": manifest_monetization(manifest["meta"]),
        "sdk_classes": sdk_counts.most_common(a.top),
        "economy_configs": configs,
        "level_economy": levels,
        "price_ladder": prices,
        "native": native,
    }

    if a.json:
        Path(a.json).write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")

    # ------------------------------------------------------------ markdown
    L = []
    L.append(f"# Economy / monetization snapshot — `{manifest['package']}`\n")
    L.append(
        f"- APK: `{path.name}` — {path.stat().st_size:,} B, sha256 `{sha[:16]}…`\n"
        f"- Version: {manifest['version_name']} ({manifest['version_code']}), "
        f"minSdk {manifest['min_sdk']} / targetSdk {manifest['target_sdk']}\n"
        f"- Code: {len(dex_names)} dex / {out['artifact']['dex_bytes']:,} B, {len(classes):,} classes, "
        f"native {so_bytes:,} B\n"
    )
    L.append("## Billing / ad keys declared in the manifest\n")
    for k, v in out["manifest_monetization"].items():
        masked = v if len(v) < 45 else v[:18] + "…" + v[-6:]
        L.append(f"- `{k}` = `{masked}`\n")
    L.append("\n## Ad & monetization SDK footprint (classes)\n")
    L.append("| SDK | classes |\n|---|---:|\n")
    for name, n in sdk_counts.most_common(18):
        L.append(f"| {name} | {n:,} |\n")
    L.append(f"\n## Bundled economy configuration ({len(configs)} files)\n")
    for c in configs[:40]:
        knob = ", ".join(f"{k}={v}" for k, v in c["knobs"].items())
        L.append(f"- `{c['file']}` — {knob or '(no scalar knobs)'}\n")
    L.append("\n## Level economy\n")
    for f, s in levels.items():
        m = s["moves"]
        t = s["three_star_score"]
        L.append(
            f"- `{f}`: {s['levels']:,} levels; moves min/median/max "
            f"{m['min']}/{m['median']}/{m['max']}; 3★ score {t['min']:,}–{t['max']:,} "
            f"(median {t['median']:,.0f})\n"
        )
        if s["bands"]:
            L.append("  - moves by level band: " + ", ".join(
                f"{b}: {v['median_moves']:.0f}" for b, v in s["bands"].items()) + "\n")
    L.append("\n## Price points baked into the binary\n")
    L.append(f"- prices: {', '.join(prices['prices']) or 'none'}\n")
    L.append(f"- discount labels: {', '.join(f'{k}×{v}' for k, v in prices['discounts']) or 'none'}\n")
    if prices["samples"]:
        L.append("- copy samples: " + "; ".join(f"`{s}`" for s in prices["samples"][:6]) + "\n")
    L.append("\n## Ad placements referenced by the game code\n")
    for p in native["placements"][:60]:
        L.append(f"- `{p}`\n")
    L.append("\n## Backend hosts (non-SDK)\n")
    for h, c in native["backend_hosts"]:
        L.append(f"- `{h}` ({c})\n")
    L.append(
        "\n> Static extraction only. Nothing here was executed on a device; "
        "server-side configuration can change every value above.\n"
    )
    md = "".join(L)
    if a.md:
        Path(a.md).write_text(md, encoding="utf-8")
    print(md)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
