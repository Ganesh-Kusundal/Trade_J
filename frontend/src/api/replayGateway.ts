/** Sends a replay control command through the gateway WebSocket (REPLAY_CONTROL topic). */
export function sendReplayGatewayCommand(command: string, multiplier?: number) {
  const ws = (window as unknown as {__tradejGatewaySocket?: WebSocket}).__tradejGatewaySocket;
  if (!ws || ws.readyState !== WebSocket.OPEN) {
    return false;
  }
  const payload = JSON.stringify({
    command,
    ...(multiplier != null ? {multiplier} : {}),
  });
  const frame = encodeReplayControlFrame(payload);
  ws.send(frame);
  return true;
}

function encodeReplayControlFrame(jsonPayload: string): ArrayBuffer {
  const payloadBytes = new TextEncoder().encode(jsonPayload);
  const frame = new ArrayBuffer(9 + payloadBytes.length);
  const view = new DataView(frame);
  view.setUint8(0, 8); // REPLAY_CONTROL wire id
  view.setBigUint64(1, BigInt(Date.now()), false);
  new Uint8Array(frame, 9).set(payloadBytes);
  return frame;
}

export function registerGatewaySocket(ws: WebSocket) {
  (window as unknown as {__tradejGatewaySocket?: WebSocket}).__tradejGatewaySocket = ws;
}

export const REPLAY_CONTROL_TOPIC_ID = 8;
