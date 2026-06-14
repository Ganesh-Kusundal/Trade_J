/**
 * Kill switch client. Posts to {@code /api/v1/risk/kill-switch} which
 * delegates to {@code KillSwitchCoordinator.engage} in the backend.
 */
export async function armKillSwitch(): Promise<{ status: string; armedAtMs: number }> {
  const res = await fetch("/api/v1/risk/kill-switch", { method: "POST" });
  if (!res.ok) throw new Error(`Kill switch failed: ${res.status}`);
  return res.json();
}

export async function disarmKillSwitch(): Promise<{ status: string }> {
  const res = await fetch("/api/v1/risk/kill-switch/disarm", { method: "POST" });
  if (!res.ok) throw new Error(`Kill switch disarm failed: ${res.status}`);
  return res.json();
}
