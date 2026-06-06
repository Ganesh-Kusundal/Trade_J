// Gateway binary protocol — 9-byte header (1 topic + 8 sequence) + JSON payload
const HEADER_SIZE = 9;

const TOPIC_NAMES: Record<number, string> = {
  0: 'MARKET_TICK',
  1: 'MARKET_DEPTH',
  2: 'CANDLE_DEVELOPING',
  3: 'CANDLE_CLOSED',
  4: 'ORDER_UPDATE',
  5: 'POSITION_UPDATE',
  6: 'STRATEGY_SIGNAL',
  7: 'PNL_UPDATE',
  8: 'REPLAY_CONTROL',
  9: 'PIPELINE_HEALTH',
  10: 'SCAN_COMPLETED',
};

export interface GatewayFrame {
  topic: string;
  topicId: number;
  sequence: number;
  payload: Record<string, unknown>;
}

type MessageHandler = (frame: GatewayFrame) => void;
type StatusHandler = (connected: boolean) => void;

let socket: WebSocket | null = null;
let reconnectTimeout: ReturnType<typeof setTimeout> | null = null;
let reconnectAttempt = 0;
let intentionalClose = false;
let messageHandler: MessageHandler | null = null;
let statusHandler: StatusHandler | null = null;

const INITIAL_BACKOFF = 1000;
const MAX_BACKOFF = 30000;

function decodeFrame(data: ArrayBuffer): GatewayFrame {
  const view = new DataView(data);
  const topicId = view.getUint8(0);
  const sequence = Number(
    (BigInt(view.getUint8(1)) << 56n) |
    (BigInt(view.getUint8(2)) << 48n) |
    (BigInt(view.getUint8(3)) << 40n) |
    (BigInt(view.getUint8(4)) << 32n) |
    (BigInt(view.getUint8(5)) << 24n) |
    (BigInt(view.getUint8(6)) << 16n) |
    (BigInt(view.getUint8(7)) << 8n) |
    BigInt(view.getUint8(8))
  );
  const payloadBytes = new Uint8Array(data, HEADER_SIZE);
  const payloadStr = new TextDecoder().decode(payloadBytes);
  const payload = JSON.parse(payloadStr) as Record<string, unknown>;

  return {
    topic: TOPIC_NAMES[topicId] || `UNKNOWN_${topicId}`,
    topicId,
    sequence,
    payload,
  };
}

function scheduleReconnect(url: string) {
  if (intentionalClose) return;
  const delay = Math.min(INITIAL_BACKOFF * Math.pow(2, reconnectAttempt), MAX_BACKOFF);
  reconnectAttempt++;
  reconnectTimeout = setTimeout(() => connectGateway(url), delay);
}

export function connectGateway(url: string) {
  if (socket) {
    intentionalClose = true;
    socket.close();
    intentionalClose = false;
  }

  try {
    socket = new WebSocket(url);
    socket.binaryType = 'arraybuffer';
  } catch {
    scheduleReconnect(url);
    return;
  }

  socket.onopen = () => {
    reconnectAttempt = 0;
    if (socket) {
      (window as unknown as {__tradejGatewaySocket?: WebSocket}).__tradejGatewaySocket = socket;
    }
    // Subscribe to all gateway topics (binary frame, since handler extends BinaryWebSocketHandler)
    const encoder = new TextEncoder();
    socket?.send(encoder.encode('SUBSCRIBE ALL'));
    statusHandler?.(true);
  };

  socket.onmessage = (event) => {
    // Binary frames (≥9 bytes) are gateway protocol messages
    if (event.data instanceof ArrayBuffer && event.data.byteLength >= HEADER_SIZE) {
      try {
        const frame = decodeFrame(event.data);
        messageHandler?.(frame);
      } catch (e) {
        console.warn('Failed to decode gateway frame:', e);
      }
      return;
    }

    // Text messages — could be error/info from gateway
    if (typeof event.data === 'string') {
      try {
        const parsed = JSON.parse(event.data);
        messageHandler?.({
          topic: 'TEXT',
          topicId: -1,
          sequence: 0,
          payload: parsed,
        });
      } catch {
        console.debug('Gateway text message:', event.data);
      }
    }
  };

  socket.onclose = () => {
    (window as unknown as {__tradejGatewaySocket?: WebSocket}).__tradejGatewaySocket = undefined;
    socket = null;
    statusHandler?.(false);
    if (!intentionalClose) scheduleReconnect(url);
  };

  socket.onerror = () => {};
}

export function disconnectGateway() {
  intentionalClose = true;
  if (reconnectTimeout) clearTimeout(reconnectTimeout);
  if (socket) {
    socket.close();
    socket = null;
  }
  statusHandler?.(false);
}

export function onGatewayMessage(handler: MessageHandler) {
  messageHandler = handler;
}

export function onGatewayStatus(handler: StatusHandler) {
  statusHandler = handler;
}
