import test from 'node:test';
import assert from 'node:assert/strict';
import { BackendBridge, normalizeComment } from '../src/bridge.mjs';

test('TikTok chat maps to backend payload and preserves provider ID for retries', () => {
  assert.deepEqual(normalizeComment({ common: { msgId: '987' }, user: { uniqueId: '@buyer' }, comment: ' A1 2 ' }),
    { eventId: '987', tiktokId: 'buyer', comment: 'A1 2' });
  assert.equal(normalizeComment({ comment: ' ' }), null);
});
test('expired JWT triggers login and retries the same event', async () => {
  const calls = [];
  let logins = 0; let posts = 0;
  const bridge = new BackendBridge({ baseUrl: 'http://backend/api/v1/', username: 'staff', password: 'secret', sessionId: '12',
    fetchImpl: async (url, options) => {
      calls.push({ url, options });
      if (url.endsWith('auth/login')) return { ok: true, json: async () => ({ accessToken: `token-${++logins}` }) };
      if (++posts === 1) return { ok: false, status: 401 };
      return { ok: true, json: async () => ({ success: true }) };
    } });
  const event = normalizeComment({ msgId: '001', comment: 'hello' });
  await bridge.forward(event);
  assert.equal(logins, 2);
  assert.equal(calls[3].options.headers.Authorization, 'Bearer token-2');
  assert.equal(calls[1].options.body, calls[3].options.body);
  assert.ok(calls[3].url.endsWith('live-sessions/12/comments'));
});
test('ended session errors are preserved rather than silently discarded', async () => {
  const bridge = new BackendBridge({ baseUrl: 'http://backend/api/v1/', sessionId: '1', fetchImpl: async () => ({ ok: false, status: 409 }) });
  bridge.token = 'jwt';
  await assert.rejects(bridge.forward({ eventId: '1', comment: 'hello' }), error => error.status === 409);
});
