'use strict';

const { contextBridge, ipcRenderer } = require('electron');

// The entire renderer-facing surface is listed here on purpose.
const api = {
  notes: {
    list: () => ipcRenderer.invoke('notes:list'),
    read: (id) => ipcRenderer.invoke('notes:read', id),
    save: (note) => ipcRenderer.invoke('notes:save', note),
    remove: (id) => ipcRenderer.invoke('notes:remove', id),
    search: (query) => ipcRenderer.invoke('notes:search', query),
  },
  license: {
    status: () => ipcRenderer.invoke('license:status'),
    activate: (email, key) => ipcRenderer.invoke('license:activate', { email, key }),
    offline: (email) => ipcRenderer.invoke('license:offline', email),
  },
  sync: {
    push: () => ipcRenderer.invoke('sync:push'),
    pull: (since) => ipcRenderer.invoke('sync:pull', since),
  },
  app: {
    info: () => ipcRenderer.invoke('app:info'),
    checkUpdates: () => ipcRenderer.invoke('app:check-updates'),
    importMarkdown: () => ipcRenderer.invoke('dialog:choose-import'),
  },
  on: (channel, callback) => {
    const allowed = ['menu:new-note', 'notes:export-pdf', 'updater:result'];
    if (allowed.includes(channel)) {
      ipcRenderer.on(channel, (_event, payload) => callback(payload));
    }
  },
};

contextBridge.exposeInMainWorld('nimbus', api);
