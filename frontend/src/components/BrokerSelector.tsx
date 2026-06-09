const BROKERS = ['dhan', 'upstox', 'icici'] as const;

export type Broker = (typeof BROKERS)[number];

interface BrokerSelectorProps {
  value: Broker;
  onChange: (broker: Broker) => void;
}

export function BrokerSelector({ value, onChange }: BrokerSelectorProps) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
      <label
        style={{
          color: 'var(--text-label)',
          fontSize: '11px',
          textTransform: 'uppercase',
          letterSpacing: '1px',
        }}
      >
        BROKER
      </label>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value as Broker)}
        style={{ width: '140px' }}
      >
        {BROKERS.map((b) => (
          <option key={b} value={b}>
            {b.toUpperCase()}
          </option>
        ))}
      </select>
    </div>
  );
}
