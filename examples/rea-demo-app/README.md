# REA demo target — Nimbus Notes

A small, deliberately realistic Electron app used to evaluate REA in
[`../../rea-evaluation-fa.md`](../../rea-evaluation-fa.md). It is **not** a real
product; every endpoint, key and host name is a placeholder (`*.example`).

What it demonstrates for a static analysis pass:

- `contextBridge` preload exposing 5 API groups over 13 literal IPC channels
- a licence system with an embedded HMAC secret and a local "offline" activation path
- an updater that trusts a remote JSON feed
- `contextIsolation` + `sandbox` + no `nodeIntegration`
- a minified esbuild bundle; the renderer ships a source map by mistake (like real apps)
- an unpacked native addon (`better_sqlite3.node`) that no JavaScript requires

## Build the artifact

```bash
npm install                     # esbuild + @electron/asar
node build.mjs                  # -> dist/ (minified, renderer source map)
node package.mjs                # -> release/resources/app.asar (+ app.asar.unpacked)
```

## Analyse it with REA

```bash
rea analyze-javascript-application release/resources/app.asar --json > evidence.json
jq -c '{source:{kind:"inline",evidence:.},view:{kind:"summary"}}' evidence.json > view.json
rea inspect-analysis-view view.json
```

Expect roughly: 1 BrowserWindow, 1 preload, 1 contextBridge API with 5 members,
13 literal IPC channels paired 13/13, one unresolved dynamic channel, one
source-map reference, one native addon, and `sender_validation_observations: 0`.

## Recover readable modules

```bash
export REA_WAKARU_COMMAND=/absolute/path/to/wakaru
rea recover-javascript-sources dist/main.js recovered \
  --extraction-mode structural --rewrite-level standard
```

## Compare the shipped bundle with this source tree

```bash
rea import-reference-source . --json > ref.json
# {"reference": <ref.json>, "application": <evidence.json>}
rea compare-source-to-bundle s2b.json --json
```

The renderer source maps to `unchanged / exact`; change one character in
`src/renderer/index.js`, re-import and it becomes `modified / high`.
