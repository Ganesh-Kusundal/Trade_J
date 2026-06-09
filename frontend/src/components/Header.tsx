interface HeaderProps {
  broker: string;
  symbol: string;
  lastPrice: number | null;
}

export function Header({ broker, symbol, lastPrice }: HeaderProps) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: '24px',
        padding: '8px 16px',
        background: 'var(--bg-secondary)',
        borderBottom: '1px solid var(--border)',
        height: '40px',
        flexShrink: 0,
      }}
    >
      <span
        style={{
          color: 'var(--amber)',
          fontWeight: 700,
          fontSize: '14px',
          letterSpacing: '2px',
        }}
      >
        TRADE-J
      </span>

      <span style={{ color: 'var(--text-muted)' }}>|</span>

      <span style={{ color: 'var(--text-label)' }}>
        {broker.toUpperCase()}
      </span>

      <span style={{ color: 'var(--text-muted)' }}>|</span>

      <span style={{ color: 'var(--text-primary)', fontWeight: 500 }}>
        {symbol}
      </span>

      {lastPrice !== null && (
        <>
          <span style={{ color: 'var(--text-muted)' }}>|</span>
          <span
            style={{
              color: lastPrice >= 0 ? 'var(--green)' : 'var(--red)',
              fontWeight: 500,
            }}
          >
            {lastPrice.toFixed(2)}
          </span>
        </>
      )}

      <div style={{ flex: 1 }} />

      <span style={{ color: 'var(--text-muted)', fontSize: '11px' }}>
        {new Date().toLocaleTimeString('en-IN', { hour12: false })}
      </span>
    </div>
  );
}
