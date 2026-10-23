'use strict';

// "Cloud" sync client. Talks to the licensing + sync backend.

const { net } = require('electron');

const API_BASE = 'https://api.nimbus-notes.example/v1';

async function request(pathname, options) {
  const opts = options || {};
  const response = await net.fetch(API_BASE + pathname, {
    method: opts.method || 'GET',
    headers: Object.assign(
      {
        'content-type': 'application/json',
        'x-client-version': require('../../package.json').version,
      },
      opts.headers || {},
    ),
    body: opts.body ? JSON.stringify(opts.body) : undefined,
  });
  if (!response.ok) {
    throw new Error('sync request failed: ' + response.status);
  }
  return response.json();
}

async function pushNotes(token, notes) {
  return request('/sync/push', {
    method: 'POST',
    headers: { authorization: 'Bearer ' + token },
    body: { notes, device: 'desktop' },
  });
}

async function pullNotes(token, since) {
  const query = since ? '?since=' + encodeURIComponent(since) : '';
  return request('/sync/pull' + query, {
    headers: { authorization: 'Bearer ' + token },
  });
}

async function activateLicense(email, licenseKey) {
  return request('/license/activate', {
    method: 'POST',
    body: { email, key: licenseKey, product: 'nimbus-notes' },
  });
}

module.exports = { API_BASE, pushNotes, pullNotes, activateLicense, request };
