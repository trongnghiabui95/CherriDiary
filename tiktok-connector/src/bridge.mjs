import { randomUUID } from 'node:crypto';

export function normalizeComment(data) {
  const comment = typeof data.comment === 'string' ? data.comment.trim().slice(0, 4000) : '';
  if (!comment) return null;
  const id = data.common?.msgId ?? data.msgId;
  const user = data.user?.uniqueId ?? data.uniqueId;
  return {
    eventId: String(id || randomUUID()).slice(0, 100),
    comment,
    tiktokId: typeof user === 'string' ? user.replace(/^@/, '').slice(0, 100) : null
  };
}

export class BackendError extends Error {
  constructor(status) { super(`Backend HTTP ${status}`); this.status = status; }
}

export class BackendBridge {
  constructor({ baseUrl, username, password, sessionId, fetchImpl = fetch }) {
    this.url = baseUrl.replace(/\/+$/, '') + '/';
    this.username = username; this.password = password;
    this.sessionId = sessionId; this.fetch = fetchImpl;
    this.token = null;
  }
  async request(path, body, auth = true) {
    const response = await this.fetch(this.url + path, {
      method: 'POST', signal: AbortSignal.timeout(15000),
      headers: { 'Content-Type': 'application/json', ...(auth ? { Authorization: `Bearer ${this.token}` } : {}) },
      body: JSON.stringify(body)
    });
    if (!response.ok) throw new BackendError(response.status);
    return response.json();
  }
  async login() {
    const result = await this.request('auth/login', { username: this.username, password: this.password }, false);
    if (!result.accessToken) throw new Error('Backend returned no accessToken');
    this.token = result.accessToken;
  }
  async forward(event) {
    if (!this.token) await this.login();
    const path = `live-sessions/${this.sessionId}/comments`;
    try { return await this.request(path, event); }
    catch (error) {
      if (error.status !== 401) throw error;
      await this.login();
      return this.request(path, event);
    }
  }
}
