#!/usr/bin/env python3
"""apk-triage.py — offline static triage for an Android APK.

Complements REA: REA's Android *code* analysis needs JADX + a full JDK, which
are not always available (and cannot be downloaded in restricted sandboxes).
This script gives a dependency-light first pass over the same artifact:

  * archive / component / permission facts from the binary manifest
  * signing scheme and certificate identity (v1 / v2 / v3)
  * engine fingerprint (Flutter, Unity IL2CPP/Mono, React Native, Cordova,
    Cocos, Godot, Unreal, Xamarin, GameMaker ...)
  * native libraries per ABI and notable asset payloads
  * DEX class/string inventory with SDK/tracker fingerprints
  * capability + risk signals (dynamic code loading, exec, JS bridges,
    accessibility, overlay, telephony identifiers, cleartext endpoints ...)

Nothing is executed and no network access is performed.

Usage:
    python3 apk-triage.py TARGET.apk [--json out.json] [--md out.md] [--top N]

Requires: androguard (pip install androguard). apkid is used when present.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import logging
import re
import shutil
import subprocess
import sys
import zipfile
from collections import Counter, defaultdict
from pathlib import Path

logging.getLogger("androguard").setLevel(logging.CRITICAL)
logging.getLogger("asn1crypto").setLevel(logging.CRITICAL)
try:  # androguard logs through loguru, not the stdlib logger
    from loguru import logger as _loguru

    _loguru.remove()
except Exception:  # pragma: no cover - optional dependency
    pass

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

RISKY_PERMISSIONS = {
    "android.permission.READ_SMS": "reads SMS",
    "android.permission.RECEIVE_SMS": "intercepts SMS",
    "android.permission.SEND_SMS": "sends SMS (billing risk)",
    "android.permission.READ_PHONE_STATE": "device identifiers / call state",
    "android.permission.READ_PHONE_NUMBERS": "phone numbers",
    "android.permission.ACCESS_FINE_LOCATION": "precise location",
    "android.permission.ACCESS_COARSE_LOCATION": "approximate location",
    "android.permission.ACCESS_BACKGROUND_LOCATION": "background location",
    "android.permission.CAMERA": "camera",
    "android.permission.RECORD_AUDIO": "microphone",
    "android.permission.READ_CONTACTS": "contacts",
    "android.permission.READ_CALL_LOG": "call log",
    "android.permission.MANAGE_EXTERNAL_STORAGE": "all-files access",
    "android.permission.REQUEST_INSTALL_PACKAGES": "can install APKs",
    "android.permission.SYSTEM_ALERT_WINDOW": "draws over other apps",
    "android.permission.BIND_ACCESSIBILITY_SERVICE": "accessibility service",
    "android.permission.QUERY_ALL_PACKAGES": "enumerates installed apps",
    "android.permission.READ_EXTERNAL_STORAGE": "external storage read",
    "android.permission.WRITE_EXTERNAL_STORAGE": "external storage write",
    "com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE": "Play install referrer",
    "android.permission.POST_NOTIFICATIONS": "notifications",
}

CAPABILITY_SIGNATURES = {
    "dynamic-code-loading": [
        "Ldalvik/system/DexClassLoader;",
        "Ldalvik/system/PathClassLoader;",
        "Ldalvik/system/InMemoryDexClassLoader;",
    ],
    "process-exec": ["Ljava/lang/Runtime;->exec", "Ljava/lang/ProcessBuilder;->"],
    "reflection": ["Ljava/lang/reflect/Method;->invoke", "Ljava/lang/Class;->forName"],
    "js-bridge": ["Landroid/webkit/WebView;->addJavascriptInterface"],
    "webview-debug": ["Landroid/webkit/WebView;->setWebContentsDebuggingEnabled"],
    "accessibility": ["Landroid/accessibilityservice/AccessibilityService;"],
    "overlay": ["Landroid/view/WindowManager$LayoutParams;->TYPE_APPLICATION_OVERLAY"],
    "clipboard": ["Landroid/content/ClipboardManager;->"],
    "device-identifiers": ["Landroid/telephony/TelephonyManager;->getDeviceId", "Landroid/telephony/TelephonyManager;->getImei"],
    "root-check": ["/system/app/Superuser.apk", "/system/xbin/su", "su -c"],
    "emulator-check": ["goldfish", "ranchu", "generic_x86", "ro.kernel.qemu"],
    "debugger-check": ["Landroid/os/Debug;->isDebuggerConnected"],
    "media-codec": ["Landroid/media/MediaCodec"],
    "packer/unpack": ["Ljava/util/zip/ZipInputStream;", "Ldalvik/system/DexFile;->loadDex"],
    "screenshot-block": ["Landroid/view/WindowManager$LayoutParams;->FLAG_SECURE"],
    "cookie-exfil": ["CookieManager;->getCookie"],
}

TRACKER_PREFIXES = {
    "Google Play Services": ["com/google/android/gms/", "com/google/firebase/"],
    "Google Ads / AdMob": ["com/google/android/gms/ads/", "com/google/ads/"],
    "Facebook SDK": ["com/facebook/"],
    "AppsFlyer": ["com/appsflyer/"],
    "Adjust": ["com/adjust/sdk/"],
    "Unity Ads": ["com/unity3d/ads/"],
    "Unity IAP": ["com/unity/purchasing/", "com/unity3d/iap/"],
    "Unity Engine": ["com/unity3d/"],
    "IronSource": ["com/ironsource/"],
    "AppLovin": ["com/applovin/"],
    "Vungle": ["com/vungle/"],
    "AdColony": ["com/adcolony/"],
    "Mintegral": ["com/mbridge/", "com/mintegral/"],
    "Tapsell (IR)": ["ir/tapsell/", "com/tapsell/"],
    "Adad (IR)": ["ir/adad/", "com/adad/", "com/adadco/"],
    "Yektanet (IR)": ["com/yektanet/"],
    "Sentry": ["io/sentry/"],
    "Crashlytics": ["com/crashlytics/"],
    "Amplitude": ["com/amplitude/"],
    "Mixpanel": ["com/mixpanel/"],
    "Kochava": ["com/kochava/"],
    "Branch": ["io/branch/"],
    "OneSignal": ["com/onesignal/"],
    "Bazaar (IR)": ["com/farsitel/bazaar/", "ir/cafebazaar/"],
    "Myket (IR)": ["ir/myket/", "com/myket/"],
    "Zarinpal (IR)": ["com/zarinpal/"],
}

ENGINE_MARKERS = {
    "Flutter": {
        "files": ["lib/arm64-v8a/libflutter.so", "lib/armeabi-v7a/libflutter.so", "assets/flutter_assets/"],
        "tokens": ["libflutter.so", "flutter_assets", "kernel_blob.bin", "libapp.so"],
    },
    "Unity IL2CPP": {
        "files": ["lib/arm64-v8a/libil2cpp.so", "assets/bin/Data/Managed/Metadata/global-metadata.dat"],
        "tokens": ["libil2cpp.so", "global-metadata.dat", "unity_default_resources"],
    },
    "Unity Mono": {"files": ["lib/armeabi-v7a/libmonobdwgc-2.0.so"], "tokens": ["libmono.so", "libmonobdwgc"]},
    "React Native": {"files": ["assets/index.android.bundle"], "tokens": ["index.android.bundle", "libreact_nativemodule"]},
    "Hermes": {"tokens": ["libhermes.so", "libhermes-executor"]},
    "Cordova / Capacitor": {"tokens": ["assets/www/", "cordova.js", "capacitor.config.js"]},
    "Cocos2d-x": {"tokens": ["libcocos2d.so", "libcocos2djs.so", "assets/cc.config"]},
    "Godot": {"tokens": ["libgodot_android.so", ".pck"]},
    "Unreal Engine": {"tokens": ["libUE4.so", "libUnreal.so", "assets/UE4Game"]},
    "Xamarin": {"tokens": ["libmonodroid.so", "libxamarin-app.so", "assemblies/"]},
    "GameMaker": {"tokens": ["libyoyo.so", "game.unx"]},
    "Defold": {"tokens": ["libdmengine.so", "game.projectc"]},
    "libGDX": {"tokens": ["libgdx.so"]},
    "Adobe AIR": {"tokens": ["libcaptive_runtime.so", "assets/META-INF/AIR/"]},
}

URL_RE = re.compile(r"https?://[A-Za-z0-9._~:/?#\[\]@!$&'()*+,;=%-]{4,200}")
# Namespace URIs are schema identifiers, never network endpoints.
SCHEMA_URL_MARKERS = ("schemas.android.com", "w3.org/", "xmlpull.org", "apache.org/licenses", "json-schema.org", "ns.adobe.com", "schemas.microsoft.com")
DOMAIN_RE = re.compile(r"\b(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+(?:com|net|org|io|ir|co|app|dev|xyz|info|me|ru|cn|tv|game|gg|link|site|online|pro|shop|cloud)\b", re.I)
IP_RE = re.compile(r"\b(?:(?:25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(?:25[0-5]|2[0-4]\d|1?\d?\d)\b")
EMAIL_RE = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
KEYWORDS = ("api_key", "apikey", "api-key", "secret", "token", "password", "passwd", "client_secret", "bearer ", "license")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def framework_markers(names: list[str]) -> dict:
    detected = {}
    for engine, spec in ENGINE_MARKERS.items():
        hits = [token for token in spec["tokens"] if any(token in name for name in names)]
        if hits:
            detected[engine] = sorted(set(hits))[:6]
    return detected


def exported_components(manifest_xml) -> dict:
    out = {"activities": [], "services": [], "receivers": [], "providers": []}
    if manifest_xml is None:
        return out
    application = manifest_xml.find("application")
    if application is None:
        return out
    tags = {
        "activity": "activities",
        "activity-alias": "activities",
        "service": "services",
        "receiver": "receivers",
        "provider": "providers",
    }
    for child in list(application):
        key = tags.get(child.tag)
        if not key:
            continue
        name = child.get(ANDROID_NS + "name") or ""
        exported_attr = child.get(ANDROID_NS + "exported")
        has_filter = child.find("intent-filter") is not None
        permission = child.get(ANDROID_NS + "permission")
        if exported_attr == "true" or (exported_attr is None and has_filter):
            out[key].append(
                {
                    "name": name,
                    "permission": permission,
                    "intent_filters": [
                        {
                            "actions": [a.get(ANDROID_NS + "name") for a in flt.findall("action")],
                            "categories": [c.get(ANDROID_NS + "name") for c in flt.findall("category")],
                        }
                        for flt in child.findall("intent-filter")
                    ],
                }
            )
    return out


def apkid_summary(path: Path) -> dict | None:
    binary = shutil.which("apkid")
    if not binary:
        return None
    try:
        proc = subprocess.run([binary, "-j", str(path)], capture_output=True, text=True, timeout=300)
        data = json.loads(proc.stdout or "{}")
    except Exception as error:  # pragma: no cover - best effort
        return {"error": str(error)}
    merged: dict[str, list[str]] = defaultdict(list)
    for item in data.get("files", []):
        for category, matches in (item.get("matches") or {}).items():
            for match in matches:
                merged[category].append(match)
    return {key: sorted(set(values)) for key, values in merged.items()}


def rea_flutter(path: Path) -> dict | None:
    binary = shutil.which("rea")
    if not binary:
        return None
    try:
        proc = subprocess.run(
            [binary, "identify-flutter-build", str(path), "--json"],
            capture_output=True,
            text=True,
            timeout=600,
        )
        record = json.loads(proc.stdout)
        return record.get("normalized_result", record)
    except Exception as error:  # pragma: no cover - best effort
        return {"error": str(error)}


def analyse(path: Path, top: int) -> dict:
    from androguard.core.apk import APK
    from androguard.core.dex import DEX

    report: dict = {"artifact": {}, "manifest": {}, "signing": {}, "native": {}, "engines": {}, "assets": {}, "dex": {}, "strings": {}, "signals": {}, "permissions": {}}

    entries = []
    with zipfile.ZipFile(path) as archive:
        for info in archive.infolist():
            entries.append((info.filename, info.file_size, info.compress_size))
    names = [name for name, _, _ in entries]
    total_uncompressed = sum(size for _, size, _ in entries)
    groups = Counter(name.split("/")[0] for name in names)
    report["artifact"] = {
        "path": str(path),
        "bytes": path.stat().st_size,
        "sha256": sha256_file(path),
        "entries": len(entries),
        "uncompressed_bytes": total_uncompressed,
        "top_level": dict(groups.most_common(20)),
        "largest_entries": [
            {"name": name, "bytes": size}
            for name, size, _ in sorted(entries, key=lambda item: item[1], reverse=True)[:top]
        ],
    }

    apk = APK(str(path))
    manifest_xml = None
    try:
        manifest_xml = apk.get_android_manifest_xml()
    except Exception:
        manifest_xml = None

    application_node = manifest_xml.find("application") if manifest_xml is not None else None
    flags = {}
    if application_node is not None:
        for attr, label in (
            ("debuggable", "debuggable"),
            ("allowBackup", "allow_backup"),
            ("usesCleartextTraffic", "cleartext_traffic"),
            ("extractNativeLibs", "extract_native_libs"),
            ("networkSecurityConfig", "network_security_config"),
            ("testOnly", "test_only"),
            ("requestLegacyExternalStorage", "legacy_external_storage"),
        ):
            value = application_node.get(ANDROID_NS + attr)
            if value is not None:
                flags[label] = value

    permissions = sorted(apk.get_permissions())
    report["permissions"] = {
        "count": len(permissions),
        "all": permissions,
        "risky": [
            {"permission": perm, "why": RISKY_PERMISSIONS[perm]}
            for perm in permissions
            if perm in RISKY_PERMISSIONS
        ],
    }
    report["manifest"] = {
        "package": apk.get_package(),
        "app_name": apk.get_app_name(),
        "version_name": apk.get_androidversion_name(),
        "version_code": apk.get_androidversion_code(),
        "min_sdk": apk.get_min_sdk_version(),
        "target_sdk": apk.get_target_sdk_version(),
        "max_sdk": apk.get_max_sdk_version(),
        "effective_target_sdk": apk.get_effective_target_sdk_version(),
        "flags": flags,
        "components": {
            "activities": len(apk.get_activities()),
            "services": len(apk.get_services()),
            "receivers": len(apk.get_receivers()),
            "providers": len(apk.get_providers()),
            "libraries": len(apk.get_libraries()),
        },
        "exported": exported_components(manifest_xml),
    }

    v1, v2, v3 = apk.is_signed_v1(), apk.is_signed_v2(), apk.is_signed_v3()
    certs = []
    try:
        for cert in apk.get_certificates() or []:
            validity = cert["tbs_certificate"]["validity"]
            certs.append(
                {
                    "subject": dict(cert.subject.native),
                    "issuer": dict(cert.issuer.native),
                    "sha256": cert.sha256.hex(),
                    "not_before": str(validity["not_before"].native),
                    "not_after": str(validity["not_after"].native),
                    "serial": str(cert.serial_number),
                    "signature_algorithm": cert["signature_algorithm"]["algorithm"].native,
                }
            )
    except Exception as error:
        certs.append({"error": str(error)})
    debug_cert = any(
        (c.get("subject", {}).get("common_name") == "Android Debug") for c in certs if "subject" in c
    )
    report["signing"] = {"v1": v1, "v2": v2, "v3": v3, "debug_certificate": debug_cert, "certificates": certs}

    abis = sorted({name.split("/")[1] for name in names if name.startswith("lib/") and name.count("/") >= 2})
    native = [name for name in names if name.endswith(".so")]
    report["native"] = {
        "abis": abis,
        "libraries": sorted(Path(n).name for n in native),
        "libraries_by_abi": {abi: sorted(Path(n).name for n in native if n.startswith(f"lib/{abi}/")) for abi in abis},
        "bytes": sum(size for name, size, _ in entries if name.endswith(".so")),
    }

    report["engines"] = framework_markers(names)

    asset_entries = [name for name in names if name.startswith("assets/")]
    ext = Counter(Path(name).suffix.lower() or "(none)" for name in asset_entries)
    interesting = [
        name
        for name in asset_entries
        if re.search(r"(\.bundle|\.jsbundle|\.pck|\.lua|\.dart|\.wasm|\.bc|\.json|\.xml|\.db|\.sqlite|\.zip|\.pak|\.bytes|\.assetbundle|\.dat)$", name, re.I)
    ]
    report["assets"] = {
        "count": len(asset_entries),
        "bytes": sum(size for name, size, _ in entries if name.startswith("assets/")),
        "extensions": dict(ext.most_common(12)),
        "notable": sorted(interesting)[:top],
    }

    class_names: list[str] = []
    strings: set[str] = set()
    dex_sizes = []
    for index, blob in enumerate(apk.get_all_dex()):
        dex = DEX(blob)
        dex_sizes.append(len(blob))
        classes = list(dex.get_classes())
        class_names.extend(cls.get_name() for cls in classes)
        strings.update(str(value) for value in dex.get_strings())

    packages = Counter()
    for name in class_names:
        parts = name.lstrip("L").split("/")
        if len(parts) >= 3:
            packages["/".join(parts[:3]) + "/"] += 1
        elif len(parts) >= 2:
            packages["/".join(parts[:2]) + "/"] += 1

    trackers = {}
    for label, prefixes in TRACKER_PREFIXES.items():
        hits = sum(1 for name in class_names if any(name.lstrip("L").startswith(prefix) for prefix in prefixes))
        if hits:
            trackers[label] = hits

    haystack = "\n".join(class_names) + "\n" + "\n".join(strings)
    signals = {}
    for label, tokens in CAPABILITY_SIGNATURES.items():
        hits = [token for token in tokens if token in haystack]
        if hits:
            signals[label] = hits

    urls = sorted(
        {u for u in URL_RE.findall(haystack) if len(u) <= 200 and not any(m in u for m in SCHEMA_URL_MARKERS)}
    )
    domains = Counter(d.lower() for d in DOMAIN_RE.findall(haystack))
    cleartext = sorted({u for u in urls if u.startswith("http://")})
    ips = sorted({ip for ip in IP_RE.findall(haystack) if not ip.startswith(("0.", "127.", "255."))})[:top]
    emails = sorted({e for e in EMAIL_RE.findall(haystack) if len(e) < 80})[:top]
    secret_pattern = re.compile(
        r"(?i)(api[_-]?key|secret|token|passw(?:or)?d|bearer|license[_-]?key|client[_-]?id)[\"']?\s*[:=]\s*[\"']?([^\s\"']{8,120})"
    )
    secret_like = []
    for candidate in strings:
        if len(candidate) > 200 or "\n" in candidate:
            continue
        match = secret_pattern.search(candidate)
        if match:
            secret_like.append(candidate.strip())
    secret_like = sorted(set(secret_like))[:top]
    long_b64 = sorted({s for s in strings if len(s) >= 60 and re.fullmatch(r"[A-Za-z0-9+/=_-]+", s)}, key=len, reverse=True)[:top]

    report["dex"] = {
        "dex_files": len(dex_sizes),
        "dex_bytes": dex_sizes,
        "classes": len(class_names),
        "strings": len(strings),
        "top_packages": dict(packages.most_common(top)),
        "trackers": dict(sorted(trackers.items(), key=lambda item: item[1], reverse=True)),
    }
    report["signals"] = signals
    report["strings"] = {
        "urls": len(urls),
        "urls_sample": urls[:top],
        "cleartext_urls": cleartext[:top],
        "domains": dict(domains.most_common(top)),
        "ips": ips,
        "emails": emails,
        "secret_like": secret_like,
        "long_base64": [{"length": len(v), "value": v[:80] + ("…" if len(v) > 80 else "")} for v in long_b64],
    }

    report["rea"] = {"flutter": rea_flutter(path), "apkid": apkid_summary(path)}
    return report


def to_markdown(report: dict) -> str:
    art = report["artifact"]
    man = report["manifest"]
    sig = report["signing"]
    dex = report["dex"]
    lines = [
        f"# APK triage — `{man.get('package')}`",
        "",
        f"- **File:** `{Path(art['path']).name}` ({art['bytes']:,} bytes, sha256 `{art['sha256'][:16]}…`)",
        f"- **App:** {man.get('app_name')} v{man.get('version_name')} ({man.get('version_code')})",
        f"- **SDK:** min {man.get('min_sdk')} / target {man.get('target_sdk')}",
        f"- **Components:** {man['components']['activities']} activities, {man['components']['services']} services, "
        f"{man['components']['receivers']} receivers, {man['components']['providers']} providers",
        f"- **Signing:** v1={sig['v1']} v2={sig['v2']} v3={sig['v3']}"
        + (" — ⚠️ **debug certificate**" if sig.get("debug_certificate") else ""),
        f"- **DEX:** {dex['dex_files']} file(s), {dex['classes']:,} classes, {dex['strings']:,} strings",
        "",
        "## Manifest flags",
    ]
    for key, value in (man.get("flags") or {}).items():
        lines.append(f"- `{key}` = `{value}`")
    if not man.get("flags"):
        lines.append("- (none explicitly set)")

    lines += ["", "## Permissions"]
    risky = report["permissions"]["risky"]
    lines.append(f"{report['permissions']['count']} declared, {len(risky)} flagged:")
    for item in risky:
        lines.append(f"- `{item['permission']}` — {item['why']}")

    exported = man.get("exported") or {}
    lines += ["", "## Exported components"]
    total_exported = sum(len(v) for v in exported.values())
    if not total_exported:
        lines.append("- none")
    singular = {"activities": "activity", "services": "service", "receivers": "receiver", "providers": "provider"}
    for kind, items in exported.items():
        for item in items:
            guard = f" (permission: `{item['permission']}`)" if item.get("permission") else " — **no permission guard**"
            lines.append(f"- {singular.get(kind, kind)}: `{item['name']}`{guard}")

    lines += ["", "## Engines and native code"]
    lines.append(f"- ABIs: {', '.join(report['native']['abis']) or 'none'} ({report['native']['bytes']:,} bytes of `.so`)")
    for engine, hits in (report.get("engines") or {}).items():
        lines.append(f"- **{engine}**: {', '.join(hits)}")
    libs = report["native"]["libraries"]
    if libs:
        lines.append(f"- Libraries ({len(libs)}): " + ", ".join(f"`{n}`" for n in libs[:20]))

    trackers = dex.get("trackers") or {}
    if trackers:
        lines += ["", "## SDK / tracker fingerprints"]
        for name, count in trackers.items():
            lines.append(f"- {name} ({count} classes)")

    signals = report.get("signals") or {}
    if signals:
        lines += ["", "## Capability signals"]
        for name, hits in signals.items():
            lines.append(f"- **{name}**: " + ", ".join(f"`{h}`" for h in hits[:4]))

    strings = report["strings"]
    lines += ["", "## Network endpoints"]
    lines.append(f"{strings['urls']} distinct URLs, {len(strings['cleartext_urls'])} cleartext.")
    for url in strings["urls_sample"][:20]:
        lines.append(f"- {url}")
    if strings["ips"]:
        lines.append("- IPs: " + ", ".join(strings["ips"][:10]))

    if strings["secret_like"]:
        lines += ["", "## Secret-like strings (review manually)", ""]
        for item in strings["secret_like"][:15]:
            lines.append(f"- `{item}`")

    flutter = (report.get("rea") or {}).get("flutter")
    lines += ["", "## REA cross-check"]
    if flutter and not flutter.get("error"):
        lines.append(f"- `identify_flutter_build`: flutter_detected={flutter.get('flutter_detected')}, ABIs={flutter.get('abis')}")
    else:
        lines.append("- REA `identify_flutter_build` unavailable")
    apkid = (report.get("rea") or {}).get("apkid")
    if apkid:
        lines.append(f"- apkid: {json.dumps(apkid, ensure_ascii=False)}")
    return "\n".join(lines) + "\n"


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="Offline APK triage that complements REA.")
    parser.add_argument("target", type=Path)
    parser.add_argument("--json", type=Path, help="write the full report as JSON")
    parser.add_argument("--md", type=Path, help="write a Markdown summary")
    parser.add_argument("--top", type=int, default=25, help="how many rows to keep per section (default 25)")
    args = parser.parse_args(argv)

    if not args.target.is_file():
        print(f"error: {args.target} is not a file", file=sys.stderr)
        return 2

    report = analyse(args.target, args.top)

    if args.json:
        args.json.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    if args.md:
        args.md.write_text(to_markdown(report), encoding="utf-8")
    if not args.json and not args.md:
        print(json.dumps(report, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
