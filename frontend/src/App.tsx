import { useState } from 'react';
import { Header } from './components/Header';
import { BrokerSelector, type Broker } from './components/BrokerSelector';
import { SymbolSelector } from './components/SymbolSelector';
import { CandlestickChart } from './components/CandlestickChart';

export default function App() {
  const [broker, setBroker] = useState<Broker>('dhan');
  const [symbol, setSymbol] = useState('RELIANCE');

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100vh',
        width: '100vw',
        overflow: 'hidden',
      }}
    >
      <Header broker={broker} symbol={symbol} lastPrice={null} />

      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '16px',
          padding: '8px 16px',
          background: 'var(--bg-primary)',
          borderBottom: '1px solid var(--border)',
          height: '40px',
          flexShrink: 0,
        }}
      >
        <BrokerSelector value={broker} onChange={setBroker} />
        <SymbolSelector value={symbol} onSelect={setSymbol} />
      </div>

      <CandlestickChart symbol={symbol} />
    </div>
  );
}
