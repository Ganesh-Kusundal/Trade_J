/**
 * Replay control-plane client.
 *
 * Sends JSON replay commands to the backend WebSocket gateway. The same
 * commands are accepted by {@code DefaultReplayCommandProcessor} in
 * the composition module and routed to the single
 * {@code ReplayService} façade. Replies arrive on the
 * {@code REPLAY_CONTROL} topic.
 */
import { marketBus } from "./MarketDataBus";

export type ReplayOp = "start" | "pause" | "resume" | "step" | "stop";

export interface ReplayCommand {
  op: ReplayOp;
  sessionId?: string;
  symbol?: string;
  interval?: string;
  fromMs?: number;
  toMs?: number;
  speed?: number;
  n?: number;
}

export interface ReplayControlState {
  sessionId: string;
  state: "PLAYING" | "PAUSED" | "STOPPED" | "UNKNOWN";
  symbol: string;
  interval: string;
  fromMs: number;
  toMs: number;
  speed: number;
}

let socket: WebSocket | null = null;
let pending: Array<(s: ReplayControlState) => void> = [];

function ensureSocket(url: string): WebSocket {
  if (socket && socket.readyState === WebSocket.OPEN) return socket;
  if (socket) socket.close();
  socket = new WebSocket(url);
  socket.binaryType = "arraybuffer";
  socket.addEventListener("message", (event) => {
    if (!(event.data instanceof ArrayBuffer)) return;
    const text = new TextDecoder().decode(event.data);
    try {
      const state = JSON.parse(text) as ReplayControlState;
      marketBus.publish({ type: "REPLAY_CONTROL", state });
      const p = pending.shift();
      if (p) p(state);
    } catch {
      // ignore malformed
    }
  });
  return socket;
}

export function sendReplayCommand(
  url: string,
  cmd: ReplayCommand
): Promise<ReplayControlState> {
  return new Promise((resolve, reject) => {
    const ws = ensureSocket(url);
    const onOpen = () => {
      try {
        ws.send(JSON.stringify(cmd));
        pending.push(resolve);
      } catch (e) {
        reject(e);
      } finally {
        ws.removeEventListener("open", onOpen);
      }
    };
    if (ws.readyState === WebSocket.OPEN) {
      onOpen();
    } else {
      ws.addEventListener("open", onOpen);
      ws.addEventListener("error", reject, { once: true });
    }
  });
}
