'use strict';

// Shared licensing helpers. Deliberately simple, but written the way a real
// (small) vendor would: a signed "pro" blob, an expiry and a machine binding.

const crypto = require('node:crypto');

// Demo-only key material. A real app would embed a public key, not a secret.
const LICENSE_SECRET = 'nimbus-demo-license-key-2024';
const PRO_FEATURES = ['cloud-sync', 'pdf-export', 'themes'];

function signPayload(payload) {
  return crypto
    .createHmac('sha256', LICENSE_SECRET)
    .update(payload)
    .digest('hex');
}

function encodeLicense(record) {
  const body = Buffer.from(JSON.stringify(record), 'utf8').toString('base64url');
  return body + '.' + signPayload(body);
}

function decodeLicense(token) {
  if (typeof token !== 'string' || !token.includes('.')) {
    return null;
  }
  const [body, signature] = token.split('.');
  if (signPayload(body) !== signature) {
    return null;
  }
  try {
    return JSON.parse(Buffer.from(body, 'base64url').toString('utf8'));
  } catch {
    return null;
  }
}

function machineFingerprint(hostname, username) {
  return crypto
    .createHash('sha256')
    .update(hostname + '::' + username)
    .digest('hex')
    .slice(0, 16);
}

function evaluateLicense(token, context) {
  const record = decodeLicense(token);
  const result = {
    valid: false,
    plan: 'free',
    features: [],
    reason: 'no-license',
    machine: machineFingerprint(context.hostname, context.username),
  };
  if (!record) {
    return result;
  }
  if (record.machine && record.machine !== result.machine) {
    result.reason = 'machine-mismatch';
    return result;
  }
  if (record.expiresAt && Date.parse(record.expiresAt) < Date.now()) {
    result.reason = 'expired';
    return result;
  }
  result.valid = true;
  result.plan = record.plan || 'pro';
  result.features = PRO_FEATURES.slice();
  result.reason = 'ok';
  return result;
}

module.exports = {
  PRO_FEATURES,
  encodeLicense,
  decodeLicense,
  evaluateLicense,
  machineFingerprint,
};
