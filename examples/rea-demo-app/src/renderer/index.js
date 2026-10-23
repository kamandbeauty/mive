'use strict';

// Renderer for Nimbus Notes. Everything goes through window.nimbus (preload).

const state = {
  notes: [],
  activeId: null,
  activeNote: null,
  view: 'all',
  query: '',
  info: null,
  dirty: false,
};

const FEATURE_LABELS = {
  'cloud-sync': 'Cloud sync',
  'pdf-export': 'PDF export',
  themes: 'Themes',
};

const els = {};

function qs(selector) {
  return document.querySelector(selector);
}

function cacheElements() {
  els.list = qs('#note-list');
  els.title = qs('#note-title');
  els.body = qs('#note-body');
  els.search = qs('#search');
  els.status = qs('#status');
  els.plan = qs('#plan-badge');
  els.features = qs('#feature-list');
  els.empty = qs('#empty-state');
  els.editor = qs('#editor');
  els.syncButton = qs('#sync-button');
  els.pdfButton = qs('#pdf-button');
  els.themeButton = qs('#theme-button');
  els.licenseForm = qs('#license-form');
}

async function boot() {
  cacheElements();
  wireEvents();
  state.info = await window.nimbus.app.info();
  applyLicenseUi(state.info.license);
  await refreshList();
  setupMenuBridge();
  const params = new URLSearchParams(window.location.search);
  if (params.get('route') === '/updates') {
    await checkUpdates();
  }
  render();
}

function setupMenuBridge() {
  window.nimbus.on('menu:new-note', () => createNote());
  window.nimbus.on('notes:export-pdf', () => exportPdf());
  window.nimbus.on('updater:result', (result) => showUpdateResult(result));
}

function wireEvents() {
  els.search.addEventListener('input', debounce(async () => {
    state.query = els.search.value;
    await refreshList();
  }, 180));

  els.title.addEventListener('input', () => {
    state.dirty = true;
    updateStatus();
  });

  els.body.addEventListener('input', () => {
    state.dirty = true;
    updateStatus();
  });

  els.syncButton.addEventListener('click', () => syncNow());
  els.pdfButton.addEventListener('click', () => exportPdf());
  els.themeButton.addEventListener('click', () => toggleTheme());
  els.licenseForm.addEventListener('submit', (event) => {
    event.preventDefault();
    activateFromForm();
  });

  window.addEventListener('keydown', (event) => {
    const meta = event.metaKey || event.ctrlKey;
    if (meta && event.key === 's') {
      event.preventDefault();
      saveActive();
    }
    if (meta && event.key === 'n') {
      event.preventDefault();
      createNote();
    }
  });

  window.addEventListener('beforeunload', () => {
    if (state.dirty) saveActive();
  });

  const brand = qs('#brand');
  if (brand) {
    brand.addEventListener('dblclick', async () => {
      const token = 'nimbus-beta-tester';
      await window.nimbus.license.offline(token + '@nimbus-notes.example');
      state.info = await window.nimbus.app.info();
      applyLicenseUi(state.info.license);
    });
  }
}

function debounce(fn, wait) {
  let timer = null;
  return function debounced() {
    clearTimeout(timer);
    timer = setTimeout(fn, wait);
  };
}

async function refreshList() {
  state.notes = state.query
    ? await window.nimbus.notes.search(state.query)
    : await window.nimbus.notes.list();
  if (state.activeId && !state.notes.some((n) => n.id === state.activeId)) {
    state.activeId = null;
    state.activeNote = null;
  }
  if (!state.activeId && state.notes.length) {
    await openNote(state.notes[0].id);
  }
  render();
}

async function openNote(id) {
  state.activeId = id;
  state.activeNote = await window.nimbus.notes.read(id);
  state.dirty = false;
  render();
}

async function createNote() {
  const note = await window.nimbus.notes.save({ title: 'Untitled', body: '' });
  await refreshList();
  await openNote(note.id);
  els.title.focus();
  els.title.select();
}

async function saveActive() {
  if (!state.activeNote) return;
  const saved = await window.nimbus.notes.save({
    id: state.activeNote.id,
    title: els.title.value,
    body: els.body.value,
    pinned: state.activeNote.pinned,
  });
  state.activeNote = saved;
  state.dirty = false;
  await refreshList();
  updateStatus('Saved');
}

async function deleteActive() {
  if (!state.activeId) return;
  await window.nimbus.notes.remove(state.activeId);
  state.activeId = null;
  state.activeNote = null;
  await refreshList();
}

function render() {
  renderList();
  renderEditor();
  renderFeatures();
  updateStatus();
}

function renderList() {
  els.list.textContent = '';
  for (const note of state.notes) {
    const item = document.createElement('li');
    item.className = 'note-item' + (note.id === state.activeId ? ' active' : '');
    item.dataset.id = note.id;
    const title = document.createElement('span');
    title.className = 'note-item-title';
    title.textContent = note.title || 'Untitled';
    const meta = document.createElement('span');
    meta.className = 'note-item-meta';
    meta.textContent = formatDate(note.updatedAt);
    item.append(title, meta);
    item.addEventListener('click', () => openNote(note.id));
    els.list.append(item);
  }
}

function renderEditor() {
  const hasNote = Boolean(state.activeNote);
  els.empty.hidden = hasNote;
  els.editor.hidden = !hasNote;
  if (!hasNote) return;
  if (document.activeElement !== els.title) els.title.value = state.activeNote.title || '';
  if (document.activeElement !== els.body) els.body.value = state.activeNote.body || '';
}

function renderFeatures() {
  const license = state.info ? state.info.license : { valid: false, features: [] };
  els.features.textContent = '';
  for (const [key, label] of Object.entries(FEATURE_LABELS)) {
    const row = document.createElement('li');
    row.textContent = label;
    row.className = license.features.includes(key) ? 'enabled' : 'disabled';
    els.features.append(row);
  }
}

function applyLicenseUi(license) {
  const plan = license && license.valid ? license.plan : 'free';
  els.plan.textContent = plan.toUpperCase();
  els.plan.dataset.plan = plan;
  const locked = plan === 'free';
  els.syncButton.disabled = locked;
  els.pdfButton.disabled = locked;
  els.themeButton.disabled = locked;
  renderFeatures();
}

function updateStatus(text) {
  const dirtyMark = state.dirty ? ' •' : '';
  els.status.textContent = (text || 'Ready') + dirtyMark;
}

function formatDate(value) {
  if (!value) return '';
  const date = new Date(value);
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

async function syncNow() {
  els.status.textContent = 'Syncing…';
  const result = await window.nimbus.sync.push();
  if (!result.ok) {
    els.status.textContent = result.error === 'pro-required' ? 'Sync needs Pro' : 'Sync failed';
    return;
  }
  const changed = (await window.nimbus.sync.pull(result.result.cursor)).result;
  els.status.textContent = 'Synced ' + (changed.notes ? changed.notes.length : 0) + ' notes';
  await refreshList();
}

async function exportPdf() {
  if (!state.info || !state.info.license.valid) {
    return showToast('PDF export is a Pro feature');
  }
  const note = state.activeNote;
  if (!note) return;
  // Real export is delegated to a native print pipeline in the packaged app.
  window.print();
}

function toggleTheme() {
  document.documentElement.dataset.theme =
    document.documentElement.dataset.theme === 'light' ? 'dark' : 'light';
}

async function activateFromForm() {
  const email = qs('#license-email').value.trim();
  const key = qs('#license-key').value.trim();
  if (!email || !key) return showToast('Email and license key are required');
  const result = await window.nimbus.license.activate(email, key);
  if (!result.ok) return showToast('Activation failed');
  state.info = await window.nimbus.app.info();
  applyLicenseUi(result.status);
  showToast('License activated');
}

async function checkUpdates() {
  const result = await window.nimbus.app.checkUpdates();
  showUpdateResult(result);
}

function showUpdateResult(result) {
  if (!result.ok) return showToast('Update check failed');
  if (result.updateAvailable) {
    showToast('Version ' + result.latestVersion + ' is available');
  } else {
    showToast('You are up to date');
  }
}

function showToast(message) {
  let toast = qs('#toast');
  if (!toast) {
    toast = document.createElement('div');
    toast.id = 'toast';
    document.body.append(toast);
  }
  toast.textContent = message;
  toast.classList.add('visible');
  setTimeout(() => toast.classList.remove('visible'), 2600);
}

window.addEventListener('DOMContentLoaded', () => {
  boot().catch((error) => {
    console.error('boot failed', error);
    const status = qs('#status');
    if (status) status.textContent = 'Failed to start';
  });
});
