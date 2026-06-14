/**
 * Runtime mode client. Reads and mutates the process-wide
 * {@link com.tradej.core.domain.runtime.RuntimeMode} held by
 * {@code RuntimeModeHolder} on the backend.
 *
 * <p>Closes the audit's Deliverable 12 gap #8 — the UI can flip between
 * LIVE and PAPER without a server restart. REPLAY and BACKTEST are
 * dev/test-only modes and are rejected with 400.
 */
export type RuntimeModeName = "LIVE" | "PAPER" | "REPLAY" | "BACKTEST";

export interface RuntimeModeResponse {
  mode: RuntimeModeName;
  userToggleable: boolean;
}

export async function fetchRuntimeMode(): Promise<RuntimeModeResponse> {
  const res = await fetch("/api/v1/runtime/mode", { method: "GET" });
  if (!res.ok) {
    throw new Error(`Failed to fetch runtime mode: ${res.status}`);
  }
  return res.json();
}

export async function setRuntimeMode(mode: "LIVE" | "PAPER"): Promise<RuntimeModeResponse> {
  const res = await fetch("/api/v1/runtime/mode", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ mode }),
  });
  if (!res.ok) {
    let detail = `HTTP ${res.status}`;
    try {
      const body = await res.json();
      if (body && typeof body.message === "string") {
        detail = body.message;
      } else if (body && typeof body.error === "string") {
        detail = body.error;
      }
    } catch {
      // ignore JSON parse errors
    }
    throw new Error(`Failed to set runtime mode to ${mode}: ${detail}`);
  }
  return res.json();
}
