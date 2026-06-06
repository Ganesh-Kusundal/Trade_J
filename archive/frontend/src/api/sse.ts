type SSECallback<T> = (data: T) => void;

interface SSEConnection<T> {
  close: () => void;
}

export function connectSSE<T = unknown>(
  url: string,
  eventName: string,
  onData: SSECallback<T>,
  onError?: (err: Error) => void,
): SSEConnection<T> {
  const source = new EventSource(url);

  source.addEventListener(eventName, (event: MessageEvent) => {
    try {
      const data = JSON.parse(event.data) as T;
      onData(data);
    } catch (e) {
      onError?.(e instanceof Error ? e : new Error(String(e)));
    }
  });

  source.addEventListener('connected', () => {
    console.log(`SSE connected: ${url}`);
  });

  source.onerror = () => {
    const err = new Error(`SSE connection error: ${url}`);
    onError?.(err);
  };

  return {
    close: () => source.close(),
  };
}

// ── Pre-configured connections ───────────────────────────────────
export function connectReadModelSSE(
  onData: (data: {version: number; [key: string]: unknown}) => void,
  onError?: (err: Error) => void,
) {
  return connectSSE('/api/v1/stream/read-model', 'read-model', onData, onError);
}

export function connectPipelineMetricsSSE(
  onData: (data: Record<string, unknown>) => void,
  onError?: (err: Error) => void,
) {
  return connectSSE('/api/v1/pipeline/stream/metrics', 'pipeline-metrics', onData, onError);
}
