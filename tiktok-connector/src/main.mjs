import { TikTokLiveConnection, WebcastEvent, ControlEvent } from 'tiktok-live-connector';
import { BackendBridge, normalizeComment } from './bridge.mjs';
import { setTimeout as delay } from 'node:timers/promises';

const username = (process.env.TIKTOK_USERNAME || '').trim().replace(/^@/, '');
const sessionId = process.env.CHERRI_LIVE_SESSION_ID || '';
if (!/^[\w.]{1,100}$/.test(username) || !/^[1-9]\d*$/.test(sessionId) || !process.env.CHERRI_PASSWORD) {
  console.error('Set TIKTOK_USERNAME, CHERRI_LIVE_SESSION_ID and CHERRI_PASSWORD in .env.connector.');
  process.exit(1);
}
const bridge = new BackendBridge({
  baseUrl: process.env.CHERRI_API_URL || 'http://localhost:8080/api/v1/',
  username: process.env.CHERRI_USERNAME || 'admin', password: process.env.CHERRI_PASSWORD, sessionId
});
const connection = new TikTokLiveConnection(username, {
  processInitialData: false,
  ...(process.env.EULER_API_KEY ? { signApiKey: process.env.EULER_API_KEY } : {})
});
let stopping = false;
let pumping = false;
let sent = 0;
const queue = [];
async function pump() {
  if (pumping) return;
  pumping = true;
  let retry = 1000;
  try {
    while (!stopping && queue.length) {
      try {
        await bridge.forward(queue[0]); queue.shift(); sent++; retry = 1000;
        if (sent === 1 || sent % 25 === 0) console.info(`Forwarded ${sent} comments to Cherri session ${sessionId}.`);
      } catch (error) {
        if ([400, 403, 404, 409].includes(error.status)) {
          console.error(`Cannot forward: ${error.message}. Check account permissions and active Cherri session; connector stopped.`);
          stopping = true; await connection.disconnect(); process.exitCode = 1; break;
        }
        console.warn(`Backend unavailable (${error.status || 'network/auth'}); retrying in ${retry / 1000}s.`);
        await delay(retry); retry = Math.min(retry * 2, 60000);
      }
    }
  } finally { pumping = false; }
}
connection.on(WebcastEvent.CHAT, data => {
  const event = normalizeComment(data);
  if (!event || stopping) return;
  if (queue.length >= 2000) { console.warn('Comment queue full; newest event dropped.'); return; }
  queue.push(event); void pump();
});
connection.on(ControlEvent.ERROR, () => console.warn('TikTok connector reported an error. Check live availability or signing configuration.'));
connection.on(ControlEvent.DISCONNECTED, () => console.warn('TikTok disconnected; reconnect loop will retry.'));
for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, () => {
  stopping = true; void connection.disconnect();
});
console.info(`Listening for @${username}; forwarding to Cherri session ${sessionId}.`);
while (!stopping) {
  try {
    if (!connection.isConnected && !connection.isConnecting) {
      const state = await connection.connect();
      console.info(`TikTok connected. Room ID: ${state.roomId}. Cherri session: ${sessionId}.`);
    }
  } catch (error) {
    // Do not print provider objects: they can contain credentials or signed URLs.
    console.warn(`TikTok connection failed (${error.constructor?.name || 'Error'}). Creator must be LIVE; a signing key may be required.`);
  }
  if (!stopping) await delay(60000);
}
