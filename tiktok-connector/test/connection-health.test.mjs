import test from 'node:test';
import assert from 'node:assert/strict';
import { isStreamStalled, describeError } from '../src/connection-health.mjs';

test('watchdog reconnects a silent connected stream, not an active or disconnected stream', () => {
  assert.equal(isStreamStalled(true, 1000, 90999), false);
  assert.equal(isStreamStalled(true, 1000, 91000), true);
  assert.equal(isStreamStalled(false, 1000, 91000), false);
});

test('diagnostics redact credentials and signed URLs', () => {
  assert.equal(describeError(new Error('key secret https://example.com?token=abc\nfailed'), ['secret']),
    'key [redacted] [URL] failed');
});
