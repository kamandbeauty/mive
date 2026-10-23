'use strict';

// Minimal updater: asks the update feed for the latest release manifest and
// reports whether the running build is current.

const { net } = require('electron');
const { version: currentVersion } = require('../../package.json');

const UPDATE_FEED = 'https://updates.nimbus-notes.example/stable.json';

function compareVersions(a, b) {
  const pa = String(a).split('.').map(Number);
  const pb = String(b).split('.').map(Number);
  for (let i = 0; i < 3; i += 1) {
    const delta = (pa[i] || 0) - (pb[i] || 0);
    if (delta !== 0) return delta > 0 ? 1 : -1;
  }
  return 0;
}

async function checkForUpdates() {
  const response = await net.fetch(UPDATE_FEED, {
    headers: { 'user-agent': 'NimbusNotes/' + currentVersion },
  });
  if (!response.ok) {
    return { ok: false, currentVersion, error: 'feed-unavailable' };
  }
  const manifest = await response.json();
  const latest = manifest.version;
  return {
    ok: true,
    currentVersion,
    latestVersion: latest,
    updateAvailable: compareVersions(latest, currentVersion) > 0,
    url: manifest.url,
  };
}

module.exports = { UPDATE_FEED, checkForUpdates, compareVersions };
