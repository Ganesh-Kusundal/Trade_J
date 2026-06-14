import { useState, useEffect } from "react";
import { INDEX_CONFIG } from "../config/terminal.config";

export interface IndexData {
  name: string;
  value: number | null;
  change: number | null;
  exchange: string;
  isApprox: boolean;
  isAvailable: boolean; // NEW: tracks if real data is available
}

const INDEX_SYMBOLS: Record<string, string> = INDEX_CONFIG.reduce((acc, idx) => {
  acc[idx.name] = idx.apiSymbol;
  return acc;
}, {} as Record<string, string>);

function sanitizeIndexValue(value: number | null | undefined): number | null {
  if (value == null || isNaN(value)) return null;
  if (value < 0) return null; // Reject negative values
  return value;
}

export function sanitizeIndex(key: string, value: number | null | undefined): number | null {
  return sanitizeIndexValue(value);
}

export function useMarketIndices() {
  const [indices, setIndices] = useState<IndexData[]>(
    INDEX_CONFIG.map(idx => ({
      name: idx.name,
      exchange: idx.exchange,
      value: null, // REMOVED: No fallback values - start with null
      change: null,
      isApprox: false,
      isAvailable: false, // No data available yet
    }))
  );

  useEffect(() => {
    const poll = async () => {
      const { marketApi } = await import("../generated/api");
      const updated = await Promise.all(INDEX_CONFIG.map(async (idx) => {
        try {
          const symbol = INDEX_SYMBOLS[idx.name] || idx.name;
          const data = await marketApi.ltp(symbol, idx.segment);
          
          // Ensure we're using absolute LTP value, not change
          let rawValue = data.ltpPaisa / 100;
          
          // If value is invalid, return null (no fallback)
          if (rawValue < 0 || isNaN(rawValue)) {
            console.warn(`[Index] Invalid LTP for ${idx.name}: ${rawValue}`);
            return { 
              name: idx.name, 
              exchange: idx.exchange, 
              value: null, 
              change: null, 
              isApprox: false,
              isAvailable: false,
            };
          }
          
          const sanitized = sanitizeIndexValue(rawValue);
          
          console.log(`[Index] ${idx.name}: raw=${rawValue}, sanitized=${sanitized}`);
          
          return { 
            name: idx.name, 
            exchange: idx.exchange, 
            value: sanitized, 
            change: null, 
            isApprox: false,
            isAvailable: sanitized != null,
          };
        } catch {
          // Error - return null instead of fallback
          return { 
            name: idx.name, 
            exchange: idx.exchange, 
            value: null, 
            change: null, 
            isApprox: false,
            isAvailable: false,
          };
        }
      }));
      setIndices(updated);
    };

    poll();
    const iv = setInterval(poll, 10000);
    return () => clearInterval(iv);
  }, []);

  return indices;
}
