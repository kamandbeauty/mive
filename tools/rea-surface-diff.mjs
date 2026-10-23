#!/usr/bin/env node
// rea-surface-diff.mjs — compare the observable surface of two REA JavaScript
// application Evidence files (for example two shipped versions of an Electron app).
//
// REA's own `compare-application-versions` is deliberately conservative: nodes
// without stable content identity stay "unknown" instead of being guessed. This
// helper answers a narrower, more actionable question with exact string sets:
// which IPC channels, exposed bridge APIs and string literals (URLs, keys,
// feature flags) appeared or disappeared between two builds.
//
// Usage:
//   node rea-surface-diff.mjs old-evidence.json new-evidence.json [--json]
//
// The input may be the full Evidence record (with normalized_result) or the
// normalized_result object itself. Nothing is executed; only saved Evidence is read.

import { readFileSync } from 'node:fs';

function unwrap(file) {
  const record = JSON.parse(readFileSync(file, 'utf8'));
  if (record && record.normalized_result && record.normalized_result.graph) {
    return record.normalized_result;
  }
  if (record && record.graph) return record;
  throw new Error(`${file}: not a REA JavaScript application Evidence file`);
}

function surface(path) {
  const result = unwrap(path);
  const channels = new Set();
  const channelsDetailed = new Map();
  const bridges = new Map(); // api key -> Set(member)
  const literals = new Set();
  const endpoints = new Set();

  for (const node of result.graph?.nodes ?? []) {
    if (node.kind === 'ipc-channel') {
      for (const observation of node.observations ?? []) {
        const p = observation.properties ?? {};
        if (!p.channel) continue;
        const key = `${p.side}:${p.operation}:${p.channel}`;
        channels.add(key);
        channelsDetailed.set(key, {
          side: p.side,
          operation: p.operation,
          channel: p.channel,
          source: observation.evidence?.location?.value?.source ?? null,
        });
      }
    }
    if (node.kind === 'context-bridge-api') {
      for (const observation of node.observations ?? []) {
        const p = observation.properties ?? {};
        if (!p.api_key) continue;
        const set = bridges.get(p.api_key) ?? new Set();
        for (const member of p.members ?? []) set.add(member);
        bridges.set(p.api_key, set);
      }
    }
  }

  for (const node of result.semantic_graph?.nodes ?? []) {
    if (node.kind !== 'literal') continue;
    const value = node.properties?.value;
    if (typeof value !== 'string' || value.length === 0) continue;
    literals.add(value);
    if (/^https?:\/\//i.test(value) || /^wss?:\/\//i.test(value)) endpoints.add(value);
  }

  return { channels, channelsDetailed, bridges, literals, endpoints };
}

const [leftPath, rightPath] = process.argv.slice(2).filter((a) => !a.startsWith('--'));
const asJson = process.argv.includes('--json');
if (!leftPath || !rightPath) {
  console.error('usage: node rea-surface-diff.mjs OLD_EVIDENCE.json NEW_EVIDENCE.json [--json]');
  process.exit(2);
}

const left = surface(leftPath);
const right = surface(rightPath);

const added = (a, b) => [...b].filter((x) => !a.has(x)).sort();
const removed = (a, b) => [...a].filter((x) => !b.has(x)).sort();

const report = {
  ipc_channels: {
    left: left.channels.size,
    right: right.channels.size,
    added: added(left.channels, right.channels).map((k) => right.channelsDetailed.get(k)),
    removed: removed(left.channels, right.channels).map((k) => left.channelsDetailed.get(k)),
  },
  bridge_members: Object.fromEntries(
    [...new Set([...left.bridges.keys(), ...right.bridges.keys()])].map((api) => [
      api,
      {
        added: added(left.bridges.get(api) ?? new Set(), right.bridges.get(api) ?? new Set()),
        removed: removed(left.bridges.get(api) ?? new Set(), right.bridges.get(api) ?? new Set()),
      },
    ]),
  ),
  endpoints: {
    added: added(left.endpoints, right.endpoints),
    removed: removed(left.endpoints, right.endpoints),
  },
  literals: {
    added: added(left.literals, right.literals),
    removed: removed(left.literals, right.literals),
  },
};

if (asJson) {
  console.log(JSON.stringify(report, null, 2));
  process.exit(0);
}

console.log(`IPC channels: ${report.ipc_channels.left} -> ${report.ipc_channels.right}`);
for (const c of report.ipc_channels.added) console.log(`  + ${c.side}:${c.operation}  ${c.channel}  (${c.source})`);
for (const c of report.ipc_channels.removed) console.log(`  - ${c.side}:${c.operation}  ${c.channel}  (${c.source})`);
console.log('Bridge APIs:');
for (const [api, delta] of Object.entries(report.bridge_members)) {
  if (!delta.added.length && !delta.removed.length) continue;
  console.log(`  ${api}: +[${delta.added.join(', ')}] -[${delta.removed.join(', ')}]`);
}
console.log('Endpoints:');
for (const e of report.endpoints.added) console.log(`  + ${e}`);
for (const e of report.endpoints.removed) console.log(`  - ${e}`);
const interesting = [...report.literals.added, ...report.literals.removed].filter(
  (v) => v.length >= 8 && !v.includes(' '),
);
if (interesting.length) {
  console.log('Long literals (possible keys/flags):');
  for (const v of report.literals.added) if (interesting.includes(v)) console.log(`  + ${v}`);
  for (const v of report.literals.removed) if (interesting.includes(v)) console.log(`  - ${v}`);
}
