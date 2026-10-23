# tools/

Small helpers that sit around REA (REA itself stays unmodified).

## `rea-surface-diff.mjs`

Compares the observable surface of two REA JavaScript-application Evidence
files: IPC channels, `contextBridge` members, endpoints and long string
literals. Use it when `rea compare-application-versions` returns mostly
`unknown` because both builds are minified.

```bash
node tools/rea-surface-diff.mjs old-evidence.json new-evidence.json [--json]
```

## `apk-triage.py`

Offline static triage for an Android APK, for environments without a JDK/JADX
(or as a second opinion next to them). Reports manifest facts, permissions,
exported components, signing schemes and certificate identity, ABI/native
libraries, engine fingerprints (Flutter, Unity IL2CPP/Mono, React Native,
Cordova, Cocos, Godot, Unreal, Xamarin, GameMaker, Defold, libGDX, AIR),
SDK/tracker fingerprints from DEX, capability signals, endpoints and
secret-like strings. Also calls `rea identify-flutter-build` and `apkid` when
they are installed.

```bash
python3 -m venv .venv && . .venv/bin/activate
pip install androguard          # apkid is optional
python3 tools/apk-triage.py TARGET.apk --json triage.json --md triage.md
```

Nothing is executed and no network access is performed.
