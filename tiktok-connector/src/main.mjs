import { TikTokLiveConnection, WebcastEvent, ControlEvent } from 'tiktok-live-connector';
import { BackendBridge, normalizeComment } from './bridge.mjs';
import { setTimeout as delay } from 'node:timers/promises';

if (!process.env.CHERRI_PASSWORD) { console.error('Set CHERRI_PASSWORD in .env.connector.'); process.exit(1); }
const bridge = new BackendBridge({ baseUrl: process.env.CHERRI_API_URL || 'http://localhost:8080/api/v1/',
  username: process.env.CHERRI_USERNAME || 'admin', password: process.env.CHERRI_PASSWORD });
const fallback = { enabled: Boolean(process.env.TIKTOK_USERNAME && process.env.CHERRI_LIVE_SESSION_ID),
  username: (process.env.TIKTOK_USERNAME || '').replace(/^@/, ''), liveSessionId: process.env.CHERRI_LIVE_SESSION_ID };
let connection = null;
let current = null;
let stopping = false;
let pumping = false;
let sent = 0;
let nextConnect = 0;
const queue = [];
async function pump() {
  if (pumping) return;
  pumping = true;
  let retry = 1000;
  try {
    while (!stopping && queue.length) {
      const entry = queue[0];
      try {
        await bridge.forward(entry.event, entry.sessionId); queue.shift(); sent++; retry = 1000;
        if (sent === 1 || sent % 25 === 0) console.info(`Forwarded ${sent} comments. Cherri session: ${entry.sessionId}.`);
      } catch (error) {
        if ([400, 403, 404, 409].includes(error.status)) {
          console.warn(`Comment rejected: backend HTTP ${error.status}. Session may have ended.`);
          queue.shift(); continue;
        }
        console.warn(`Backend unavailable (${error.status || 'network/auth'}); retry ${retry / 1000}s.`);
        await delay(retry); retry = Math.min(retry * 2, 60000);
      }
    }
  } finally { pumping = false; }
}
async function changeSource(source) {
  if (connection) { connection.removeAllListeners('disconnected'); await connection.disconnect(); }
  connection = null; current = source; nextConnect = 0;
  if (!source.enabled) { console.info('Waiting for a source selected in Android Live tab.'); return; }
  const sessionId = source.liveSessionId;
  const target = new TikTokLiveConnection(source.username, { processInitialData: false,
    ...(process.env.EULER_API_KEY ? { signApiKey: process.env.EULER_API_KEY } : {}) });
  connection = target;
  target.on(WebcastEvent.CHAT, data => {
    if (stopping || target !== connection) return;
    const event = normalizeComment(data);
    if (!event) return;
    if (queue.length >= 2000) { console.warn('Queue full; event dropped.'); return; }
    queue.push({ event, sessionId }); void pump();
  });
  target.on(ControlEvent.ERROR, () => console.warn('TikTok connector error; checking live availability/signing.'));
  target.on(ControlEvent.DISCONNECTED, () => { nextConnect = Date.now() + 60000; console.warn('TikTok disconnected; reconnect in 60s.'); });
  console.info(`Source @${source.username} -> Cherri session ${sessionId}.`);
}
for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, () => { stopping = true; void connection?.disconnect(); });
while (!stopping) {
  try {
    const selected = await bridge.source();
    const source = selected.liveSessionId == null ? fallback : selected;
    if (!current || current.enabled !== source.enabled || current.username !== source.username || String(current.liveSessionId) !== String(source.liveSessionId)) await changeSource(source);
    if (connection && !connection.isConnected && !connection.isConnecting && Date.now() >= nextConnect) {
      nextConnect = Date.now() + 60000;
      try { const state = await connection.connect(); console.info(`TikTok connected. Room ID: ${state.roomId}. Cherri session: ${current.liveSessionId}.`); }
      catch (error) { console.warn(`Connect failed (${error.constructor?.name || 'Error'}). Check LIVE/signing.`); }
    }
  } catch (error) { console.warn(`Cannot read live source (${error.status || 'network/auth'}). Retrying.`); }
  if (!stopping) await delay(5000);
}