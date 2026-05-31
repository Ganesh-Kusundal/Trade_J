import {useEffect, useRef} from 'react';
import {connectSSE} from '@/api/sse';

export function useSSE<T = unknown>(
  url: string | null,
  eventName: string,
  onData: (data: T) => void,
  onError?: (err: Error) => void,
) {
  const onDataRef = useRef(onData);
  const onErrorRef = useRef(onError);
  onDataRef.current = onData;
  onErrorRef.current = onError;

  useEffect(() => {
    if (!url) return;
    const conn = connectSSE<T>(url, eventName, (data) => onDataRef.current(data), (err) => onErrorRef.current?.(err));
    return () => conn.close();
  }, [url, eventName]);
}
