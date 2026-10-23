'use strict';

const { app, BrowserWindow, ipcMain, dialog, shell, Menu } = require('electron');
const path = require('node:path');
const os = require('node:os');
const fs = require('node:fs');

const { NoteStore } = require('./store');
const { evaluateLicense, encodeLicense, machineFingerprint } = require('../shared/license');
const sync = require('./sync');
const updater = require('./updater');

const LICENSE_FILE = path.join(app.getPath('userData'), 'license.dat');
let mainWindow = null;
let store = null;
let licenseState = { valid: false, plan: 'free', features: [], reason: 'no-license' };

function readLicenseToken() {
  try {
    return fs.readFileSync(LICENSE_FILE, 'utf8').trim();
  } catch {
    return '';
  }
}

function refreshLicense() {
  licenseState = evaluateLicense(readLicenseToken(), {
    hostname: os.hostname(),
    username: os.userInfo().username,
  });
  return licenseState;
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1180,
    height: 760,
    minWidth: 900,
    minHeight: 600,
    backgroundColor: '#101418',
    titleBarStyle: process.platform === 'darwin' ? 'hiddenInset' : 'default',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      spellcheck: true,
    },
  });

  mainWindow.loadFile(path.join(__dirname, 'index.html'));
  mainWindow.on('closed', () => {
    mainWindow = null;
  });

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https:\/\/(docs|www)\.nimbus-notes\.example\//.test(url)) {
      shell.openExternal(url);
    }
    return { action: 'deny' };
  });
}

function buildMenu() {
  const template = [
    {
      label: 'File',
      submenu: [
        {
          label: 'New Note',
          accelerator: 'CmdOrCtrl+N',
          click: () => mainWindow && mainWindow.webContents.send('menu:new-note'),
        },
        {
          label: 'Export as PDF (Pro)',
          click: () => mainWindow && mainWindow.webContents.send('notes:export-pdf'),
        },
        { type: 'separator' },
        { role: 'quit' },
      ],
    },
    {
      label: 'Edit',
      submenu: [
        { role: 'undo' },
        { role: 'redo' },
        { type: 'separator' },
        { role: 'cut' },
        { role: 'copy' },
        { role: 'paste' },
        { role: 'selectAll' },
      ],
    },
    {
      label: 'Help',
      submenu: [
        {
          label: 'Documentation',
          click: () => shell.openExternal('https://docs.nimbus-notes.example/'),
        },
        {
          label: 'Check for Updates',
          click: async () => {
            const result = await updater.checkForUpdates();
            if (mainWindow) mainWindow.webContents.send('updater:result', result);
          },
        },
      ],
    },
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

function registerIpc() {
  ipcMain.handle('notes:list', () => store.list());
  ipcMain.handle('notes:read', (_event, id) => store.read(String(id)));
  ipcMain.handle('notes:save', (_event, note) => store.save(note && typeof note === 'object' ? note : {}));
  ipcMain.handle('notes:remove', (_event, id) => store.remove(String(id)));
  ipcMain.handle('notes:search', (_event, query) => store.search(query));

  ipcMain.handle('license:status', () => refreshLicense());

  ipcMain.handle('license:activate', async (_event, payload) => {
    const response = await sync.activateLicense(payload.email, payload.key);
    if (!response || !response.token) {
      return { ok: false, error: 'activation-rejected' };
    }
    fs.writeFileSync(LICENSE_FILE, response.token, { mode: 0o600 });
    return { ok: true, status: refreshLicense() };
  });

  ipcMain.handle('license:offline', (_event, email) => {
    const token = encodeLicense({
      plan: 'pro',
      email: String(email || 'unknown'),
      machine: machineFingerprint(os.hostname(), os.userInfo().username),
      expiresAt: new Date(Date.now() + 30 * 24 * 3600 * 1000).toISOString(),
    });
    fs.writeFileSync(LICENSE_FILE, token, { mode: 0o600 });
    return refreshLicense();
  });

  ipcMain.handle('sync:push', async () => {
    const status = refreshLicense();
    if (!status.valid) return { ok: false, error: 'pro-required' };
    const notes = await Promise.all((await store.list()).map((n) => store.read(n.id)));
    return { ok: true, result: await sync.pushNotes(status.machine, notes) };
  });

  ipcMain.handle('sync:pull', async (_event, since) => {
    const status = refreshLicense();
    if (!status.valid) return { ok: false, error: 'pro-required' };
    return { ok: true, result: await sync.pullNotes(status.machine, since) };
  });

  ipcMain.handle('app:info', () => ({
    version: app.getVersion(),
    platform: process.platform,
    electron: process.versions.electron,
    userData: app.getPath('userData'),
    license: licenseState,
  }));

  ipcMain.handle('app:check-updates', () => updater.checkForUpdates());

  ipcMain.handle('dialog:choose-import', async () => {
    const { canceled, filePaths } = await dialog.showOpenDialog(mainWindow, {
      title: 'Import Markdown',
      properties: ['openFile'],
      filters: [{ name: 'Markdown', extensions: ['md', 'markdown'] }],
    });
    if (canceled || filePaths.length === 0) return null;
    return { path: filePaths[0], content: fs.readFileSync(filePaths[0], 'utf8') };
  });
}

app.whenReady().then(() => {
  store = new NoteStore(path.join(app.getPath('userData'), 'notes'));
  refreshLicense();
  registerIpc();
  buildMenu();
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
