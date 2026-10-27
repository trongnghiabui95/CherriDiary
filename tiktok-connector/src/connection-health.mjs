export function isStreamStalled(connected, lastDataAt, now = Date.now()) {
  return connected && now - lastDataAt >= 90000;
}

export function describeError(error, secrets = []) {
  let message = String(error?.message || error?.info || error || 'Unknown error');
  for (const secret of secrets.filter(Boolean)) message = message.split(secret).join('[redacted]');
  return message.replace(/https?:\/\/\S+/g, '[URL]').replace(/[\r\n]+/g, ' ').slice(0, 700);
}
