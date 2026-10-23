// Build script for the demo target. Not shipped in the packaged app.
import * as esbuild from 'esbuild';
import fs from 'node:fs';
import path from 'node:path';

const outdir = path.join(process.cwd(), 'dist');
fs.rmSync(outdir, { recursive: true, force: true });
fs.mkdirSync(outdir, { recursive: true });

const common = {
  bundle: true,
  minify: true,
  sourcemap: false,
  target: 'node20',
  logLevel: 'info',
};

await esbuild.build({
  ...common,
  entryPoints: ['src/main/index.js'],
  outfile: 'dist/main.js',
  platform: 'node',
  external: ['electron'],
});

await esbuild.build({
  ...common,
  entryPoints: ['src/preload/index.js'],
  outfile: 'dist/preload.js',
  platform: 'node',
  external: ['electron'],
});

await esbuild.build({
  ...common,
  target: 'chrome120',
  entryPoints: ['src/renderer/index.js'],
  outfile: 'dist/renderer.js',
  platform: 'browser',
  sourcemap: true,
});

fs.copyFileSync('src/renderer/index.html', 'dist/index.html');
fs.mkdirSync('dist/assets', { recursive: true });
fs.writeFileSync('dist/assets/empty.bin', '');
