// Package the built app into an Electron-style resources/app.asar.
// Reproduces the artifact used in ../../rea-evaluation-fa.md.
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const appDir = path.join(root, 'app');
const releaseDir = path.join(root, 'release/resources');

fs.rmSync(appDir, { recursive: true, force: true });
fs.mkdirSync(appDir, { recursive: true });
fs.cpSync(path.join(root, 'dist'), path.join(appDir, 'dist'), { recursive: true });

// A native addon that the JavaScript never requires: it is unpacked from the
// archive, exactly like a real better-sqlite3 dependency would be.
const addonDir = path.join(appDir, 'node_modules/better-sqlite3/build/Release');
fs.mkdirSync(addonDir, { recursive: true });
fs.writeFileSync(path.join(addonDir, 'better_sqlite3.node'), Buffer.alloc(9000, 0x7f));

const pkg = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
const shipped = {
  name: pkg.name,
  productName: pkg.productName,
  version: pkg.version,
  description: pkg.description,
  main: pkg.main,
  author: pkg.author,
  license: pkg.license,
};
fs.writeFileSync(path.join(appDir, 'package.json'), JSON.stringify(shipped, null, 2));

fs.mkdirSync(releaseDir, { recursive: true });
execFileSync('npx', ['--no-install', 'asar', 'pack', appDir, path.join(releaseDir, 'app.asar'), '--unpack', '*.node'], {
  stdio: 'inherit',
});
console.log('packaged ->', path.join(releaseDir, 'app.asar'));
