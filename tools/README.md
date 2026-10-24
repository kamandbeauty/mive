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

## `analyze-apk.sh`

One-shot wrapper around the two tools above plus REA:

```bash
tools/analyze-apk.sh TARGET.apk [OUTDIR]
```

It bootstraps a venv (`/tmp/rea-apk-venv`) with `androguard` + `apkid` on first
run, then writes `triage.md`, `triage.json`, `apkid.json` and, when REA is
installed and the APK is Flutter, `rea-flutter.json` / `rea-dart-aot.json`.
A cold run takes ~30 s; an already-bootstrapped run is a few seconds.

## `apk-economy.py`

Economy / monetization extraction for a game APK: ad-and-monetization SDK
class footprint, billing and ad keys from the manifest, bundled economy JSON
(rewarded/interstitial gates, offers, payIds), hard-coded price points from
i18n blobs, level-economy statistics from `levels/*.tsv`, rewarded-ad
placement names and the game's own backend hosts from the native library.

```bash
python3 tools/apk-economy.py TARGET.apk --md economy.md --json economy.json
```

Static only: it never runs the app and never talks to a server, so anything
server-configurable (prices, caps, probabilities) is the *bundled default*, not
the live value. Output marks nothing as inference — that distinction belongs in
the report you write on top of it.

## Getting a target into a restricted environment

This sandbox only reaches `github.com`, `codeload.github.com`,
`api.github.com`, `registry.npmjs.org`, `pypi.org` and
`files.pythonhosted.org`. Vendor CDNs (app stores, file hosts) are blocked, so
an APK that lives behind such a link has to arrive one of these ways:

1. **Attach the file to the conversation.** It lands in the workspace and is
   analysed in place. Preferred.
2. **Commit it to any GitHub repository** (≤100 MB per file). It can then be
   pulled without touching the release CDN:

   ```bash
   # whole repo (works for binaries committed to the tree)
   curl -L -o repo.tgz https://codeload.github.com/OWNER/REPO/tar.gz/refs/heads/main

   # one file, by blob SHA (base64, 100 MB limit)
   SHA=$(gh api "repos/OWNER/REPO/git/trees/main?recursive=1" \
         --jq '.tree[] | select(.path=="app.apk") | .sha')
   gh api "repos/OWNER/REPO/git/blobs/$SHA" --jq '.content' | base64 -d > app.apk
   ```

   GitHub *release* assets do **not** work: the download redirects to
   `release-assets.githubusercontent.com`, which is outside the allowlist.
3. **Run the analysis on your own machine** and share the outputs
   (`triage.json`, `triage.md`, `rea-flutter.json`).

## Notes on this environment

Installed packages outside the workspace, plus `node_modules` / `.venv` style
caches, do not survive between turns. Both scripts therefore bootstrap what
they need on demand: `analyze-apk.sh` recreates its venv, and
`rea-surface-diff.mjs` needs nothing beyond Node.
