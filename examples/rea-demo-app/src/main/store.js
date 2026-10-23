'use strict';

// Tiny note store. Notes are JSON files under app.getPath('userData')/notes.

const fs = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');

class NoteStore {
  constructor(root) {
    this.root = root;
  }

  async ensureRoot() {
    await fs.mkdir(this.root, { recursive: true });
  }

  fileFor(id) {
    if (!/^[a-f0-9]{16}$/.test(id)) {
      throw new Error('invalid note id');
    }
    return path.join(this.root, id + '.json');
  }

  async list() {
    await this.ensureRoot();
    const entries = await fs.readdir(this.root);
    const notes = [];
    for (const entry of entries) {
      if (!entry.endsWith('.json')) continue;
      try {
        const raw = await fs.readFile(path.join(this.root, entry), 'utf8');
        const note = JSON.parse(raw);
        notes.push({
          id: note.id,
          title: note.title,
          updatedAt: note.updatedAt,
          pinned: Boolean(note.pinned),
        });
      } catch {
        // Skip unreadable notes instead of failing the whole listing.
      }
    }
    notes.sort((a, b) => String(b.updatedAt).localeCompare(String(a.updatedAt)));
    return notes;
  }

  async read(id) {
    const raw = await fs.readFile(this.fileFor(id), 'utf8');
    return JSON.parse(raw);
  }

  async save(input) {
    await this.ensureRoot();
    const now = new Date().toISOString();
    const id = input.id || crypto.randomBytes(8).toString('hex');
    const existing = input.id ? await this.read(id).catch(() => null) : null;
    const note = {
      id,
      title: String(input.title || 'Untitled').slice(0, 200),
      body: String(input.body || ''),
      pinned: Boolean(input.pinned),
      createdAt: existing ? existing.createdAt : now,
      updatedAt: now,
    };
    await fs.writeFile(this.fileFor(id), JSON.stringify(note, null, 2), 'utf8');
    return note;
  }

  async remove(id) {
    await fs.unlink(this.fileFor(id));
    return { id, deleted: true };
  }

  async search(query) {
    const q = String(query || '').toLowerCase();
    if (!q) return [];
    const notes = await Promise.all(
      (await this.list()).map((summary) => this.read(summary.id).catch(() => null)),
    );
    return notes
      .filter((note) => note && (note.title.toLowerCase().includes(q) || note.body.toLowerCase().includes(q)))
      .map((note) => ({ id: note.id, title: note.title, excerpt: note.body.slice(0, 80) }));
  }
}

module.exports = { NoteStore };
