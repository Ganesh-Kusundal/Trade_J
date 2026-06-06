import '@testing-library/jest-dom/vitest';

// Polyfill EventSource for jsdom (used by SSE in useSSE hook)
if (!globalThis.EventSource) {
  class MockEventSource extends EventTarget {
    static readonly CONNECTING = 0;
    static readonly OPEN = 1;
    static readonly CLOSED = 2;
    readonly CONNECTING = 0;
    readonly OPEN = 1;
    readonly CLOSED = 2;
    readyState = 0;
    url: string;
    withCredentials = false;
    onopen: ((event: Event) => void) | null = null;
    onmessage: ((event: MessageEvent) => void) | null = null;
    onerror: ((event: Event) => void) | null = null;

    constructor(url: string, _eventSourceInitDict?: EventSourceInit) {
      super();
      this.url = url;
    }

    close() {
      this.readyState = 2;
    }
  }
  globalThis.EventSource = MockEventSource as unknown as typeof EventSource;
}
