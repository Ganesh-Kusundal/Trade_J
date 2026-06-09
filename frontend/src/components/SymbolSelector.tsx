import { useState, useRef } from 'react';
import { NIFTY50 } from '../data/nifty50';

interface SymbolSelectorProps {
  value: string;
  onSelect: (symbol: string) => void;
}

export function SymbolSelector({ value, onSelect }: SymbolSelectorProps) {
  const [input, setInput] = useState(value);
  const [filtered, setFiltered] = useState<string[]>([]);
  const [showDropdown, setShowDropdown] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  function handleChange(val: string) {
    const upper = val.toUpperCase();
    setInput(upper);

    if (upper.length === 0) {
      setFiltered(NIFTY50.slice(0, 10));
    } else {
      setFiltered(
        NIFTY50.filter((s) => s.includes(upper)).slice(0, 10),
      );
    }
    setShowDropdown(true);
  }

  function handleSelect(sym: string) {
    setInput(sym);
    setShowDropdown(false);
    onSelect(sym);
  }

  function handleKeyDown(e: React.KeyboardEvent) {
    if (e.key === 'Enter' && input) {
      handleSelect(input.toUpperCase());
    }
    if (e.key === 'Escape') {
      setShowDropdown(false);
    }
  }

  return (
    <div style={{ position: 'relative', display: 'flex', alignItems: 'center', gap: '8px' }}>
      <label
        style={{
          color: 'var(--text-label)',
          fontSize: '11px',
          textTransform: 'uppercase',
          letterSpacing: '1px',
        }}
      >
        SYMBOL
      </label>
      <input
        ref={inputRef}
        type="text"
        value={input}
        onChange={(e) => handleChange(e.target.value)}
        onFocus={() => {
          handleChange(input);
        }}
        onBlur={() => setTimeout(() => setShowDropdown(false), 150)}
        onKeyDown={handleKeyDown}
        placeholder="NIFTY50..."
        style={{ width: '180px', textTransform: 'uppercase' }}
      />
      {showDropdown && filtered.length > 0 && (
        <div
          style={{
            position: 'absolute',
            top: '100%',
            left: '68px',
            width: '180px',
            background: 'var(--bg-secondary)',
            border: '1px solid var(--border)',
            zIndex: 100,
            maxHeight: '240px',
            overflowY: 'auto',
          }}
        >
          {filtered.map((sym) => (
            <div
              key={sym}
              onMouseDown={() => handleSelect(sym)}
              style={{
                padding: '6px 10px',
                cursor: 'pointer',
                color: sym === input ? 'var(--accent)' : 'var(--text-primary)',
                background: sym === input ? 'var(--accent-dim)' : 'transparent',
              }}
              onMouseEnter={(e) => {
                (e.target as HTMLElement).style.background = 'var(--bg-hover)';
              }}
              onMouseLeave={(e) => {
                (e.target as HTMLElement).style.background =
                  sym === input ? 'var(--accent-dim)' : 'transparent';
              }}
            >
              {sym}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
