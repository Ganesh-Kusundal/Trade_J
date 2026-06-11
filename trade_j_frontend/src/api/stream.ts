import type { ReadModelSnapshot } from "./backend-contracts";

type Listener = (snapshot: ReadModelSnapshot) => void;

export function subscribeReadModel(listener: Listener): () => void {
  const es = new EventSource("/api/v1/stream/read-model");

  es.addEventListener("read-model", (event) => {
    try {
      const snapshot: ReadModelSnapshot = JSON.parse(event.data);
      listener(snapshot);
    } catch (e) {
      console.error("SSE parse error", e);
    }
  });

  es.onerror = () => {
    console.warn("SSE connection error, will auto-reconnect");
  };

  return () => es.close();
}
