/**
 * Browser-side auth client. The only thing this module stores in
 * the browser is the session id (a 192-bit random token from the
 * backend's {@code POST /api/v1/auth/login}). The broker access
 * token NEVER crosses the browser after login — it lives in
 * {@code SessionStore} on the server only.
 *
 * <h2>Flow</h2>
 * <ol>
 *   <li>User enters broker credentials in the UI.</li>
 *   <li>{@link #login} POSTs them to {@code /api/v1/auth/login};
 *       the server returns a session id.</li>
 *   <li>The session id is stored in localStorage (the ONLY thing
 *       persisted across reloads).</li>
 *   <li>On every REST call the browser sends {@code X-Session-Id}.</li>
 *   <li>For the WebSocket feed, the browser asks the server for
 *       the full {@link BrokerSession} via
 *       {@code /api/v1/auth/broker-session}; the server reads the
 *       credential from {@code SessionStore} and returns it. The
 *       browser does NOT persist this response.</li>
 *   <li>{@link #logout} POSTs to {@code /api/v1/auth/logout} and
 *       clears the session id from localStorage.</li>
 * </ol>
 */

export interface BrokerSession {
  broker: string;
  accessToken: string;
  clientId?: string;
  sessionToken?: string;
  instrumentKeys?: string[];
}

const SESSION_KEY = "tj_session_id";

export function getSessionId(): string | null {
  return localStorage.getItem(SESSION_KEY);
}

export function clearSession(): void {
  localStorage.removeItem(SESSION_KEY);
}

export interface LoginResult {
  sessionId: string;
  broker: string;
  source: string;
  issuedAtMs: number;
  expiresAtMs: number;
}

export async function login(
  broker: string,
  accessToken: string,
  clientId?: string,
  sessionToken?: string,
): Promise<LoginResult> {
  const res = await fetch("/api/v1/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ broker, accessToken, clientId, sessionToken }),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Login failed: ${res.status} ${text}`);
  }
  const data = (await res.json()) as LoginResult;
  localStorage.setItem(SESSION_KEY, data.sessionId);
  return data;
}

export async function logout(): Promise<void> {
  const sid = getSessionId();
  if (sid) {
    try {
      await fetch("/api/v1/auth/logout", {
        method: "POST",
        headers: { "X-Session-Id": sid },
      });
    } catch {
      // best-effort
    }
  }
  clearSession();
}

export interface WhoAmI {
  sessionId: string;
  broker: string;
  source: string;
  expiresAtMs: number;
}

export async function whoami(): Promise<WhoAmI | null> {
  const sid = getSessionId();
  if (!sid) return null;
  const res = await fetch("/api/v1/auth/whoami", {
    headers: { "X-Session-Id": sid },
  });
  if (res.status === 401) {
    clearSession();
    return null;
  }
  if (!res.ok) return null;
  return (await res.json()) as WhoAmI;
}

/**
 * Fetch the broker session for the WebSocket feed. The server
 * reads the credential from {@code SessionStore} and returns it
 * in one-shot. The browser does not persist this response.
 */
export async function fetchBrokerSession(): Promise<BrokerSession | null> {
  const sid = getSessionId();
  if (!sid) return null;
  const res = await fetch("/api/v1/auth/broker-session", {
    headers: { "X-Session-Id": sid },
  });
  if (res.status === 401) {
    clearSession();
    return null;
  }
  if (!res.ok) return null;
  return (await res.json()) as BrokerSession;
}

/**
 * Build a fetch init that adds the X-Session-Id header if there
 * is an active session. Use this for all authenticated REST calls.
 */
export function authedFetchInit(init: RequestInit = {}): RequestInit {
  const sid = getSessionId();
  if (!sid) return init;
  const headers = new Headers(init.headers ?? {});
  headers.set("X-Session-Id", sid);
  return { ...init, headers };
}

export interface SignedBrokerWsUrl {
  url: string;
  broker: string;
  expiresAtMs: number;
  signature: string;
  ttlMs: number;
}

/**
 * Fetch a 30-second signed broker WebSocket URL. The credential
 * is NEVER in the browser beyond the 30-sec window. The browser
 * opens a WebSocket to the returned URL on the broker; the URL
 * is unusable after expiresAtMs.
 *
 * <p>This is the v2 of the broker-credential flow. The
 * {@link fetchBrokerSession} variant (which returns the raw
 * credential) is preserved for backward compat.
 */
export async function fetchSignedBrokerWsUrl(
  broker: string,
  instruments: string[] = []
): Promise<SignedBrokerWsUrl | null> {
  const sid = getSessionId();
  if (!sid) return null;
  const params = new URLSearchParams();
  params.set("broker", broker);
  if (instruments.length > 0) {
    params.set("instruments", instruments.join(","));
  }
  const res = await fetch(`/api/v1/auth/ws-url?${params.toString()}`, {
    headers: { "X-Session-Id": sid },
  });
  if (res.status === 401) {
    clearSession();
    return null;
  }
  if (!res.ok) return null;
  return (await res.json()) as SignedBrokerWsUrl;
}

