import {describe, expect, it, vi, beforeEach, afterEach} from 'vitest';
import {render, screen, fireEvent} from '@testing-library/react';
import {TradingPanel} from './TradingPanel';
import {useStudioStore} from '@/store/useStudioStore';

const mockPositions = [
  {symbol: 'SBIN', side: 'LONG' as const, quantity: 100, entryPrice: 750_00, currentPrice: 760_00, pnl: 1000_00, pnlPercent: 1.33},
  {symbol: 'RELIANCE', side: 'SHORT' as const, quantity: 25, entryPrice: 2500_00, currentPrice: 2480_00, pnl: 500_00, pnlPercent: 0.80},
];

const mockTrades = [
  {id: 't1', symbol: 'SBIN', side: 'BUY' as const, quantity: 100, price: 750_00, timestamp: 1716537900000},
  {id: 't2', symbol: 'SBIN', side: 'SELL' as const, quantity: 50, price: 760_00, timestamp: 1716538000000},
];

function resetStore() {
  useStudioStore.setState({
    positions: [],
    trades: [],
    selectedSymbol: 'SBIN',
  });
}

describe('TradingPanel', () => {
  beforeEach(() => resetStore());
  afterEach(() => {
    useStudioStore.setState({positions: [], trades: []});
  });

  it('renders Order Entry header', () => {
    render(<TradingPanel />);
    expect(screen.getByText('Order Entry')).toBeInTheDocument();
  });

  it('renders BUY and SELL buttons', () => {
    render(<TradingPanel />);
    expect(screen.getByText('BUY')).toBeInTheDocument();
    expect(screen.getByText('SELL')).toBeInTheDocument();
  });

  it('shows BUY button with green styling', () => {
    render(<TradingPanel />);
    const buyBtn = screen.getByText('BUY');
    expect(buyBtn.className).toContain('10b981');
  });

  it('shows SELL button with red styling', () => {
    render(<TradingPanel />);
    const sellBtn = screen.getByText('SELL');
    expect(sellBtn.className).toContain('ef4444');
  });

  it('shows "No open positions" when empty', () => {
    render(<TradingPanel />);
    expect(screen.getByText('No open positions')).toBeInTheDocument();
  });

  it('shows "No trades yet" when empty', () => {
    render(<TradingPanel />);
    expect(screen.getByText('No trades yet')).toBeInTheDocument();
  });

  it('renders positions section header', () => {
    render(<TradingPanel />);
    expect(screen.getByText('Positions')).toBeInTheDocument();
  });

  it('renders recent trades section header', () => {
    render(<TradingPanel />);
    expect(screen.getByText('Recent Trades')).toBeInTheDocument();
  });

  it('displays positions when populated', () => {
    useStudioStore.setState({positions: mockPositions});
    render(<TradingPanel />);
    expect(screen.getByText('SBIN')).toBeInTheDocument();
    expect(screen.getByText('RELIANCE')).toBeInTheDocument();
    expect(screen.getByText('LONG')).toBeInTheDocument();
    expect(screen.getByText('SHORT')).toBeInTheDocument();
    expect(screen.queryByText('No open positions')).not.toBeInTheDocument();
  });

  it('displays position quantity and price', () => {
    useStudioStore.setState({positions: mockPositions});
    render(<TradingPanel />);
    expect(screen.getByText(/100 @ ₹/)).toBeInTheDocument();
    expect(screen.getByText(/25 @ ₹/)).toBeInTheDocument();
  });

  it('displays position P&L with correct color for positive', () => {
    useStudioStore.setState({positions: [mockPositions[0]]});
    render(<TradingPanel />);
    const pnlElement = screen.getByText(/₹\s*1000\.00/);
    expect(pnlElement.className).toContain('10b981');
  });

  it('displays trades when populated', () => {
    useStudioStore.setState({trades: mockTrades});
    render(<TradingPanel />);
    expect(screen.getAllByText('BUY').length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText('SELL').length).toBeGreaterThanOrEqual(2);
    expect(screen.queryByText('No trades yet')).not.toBeInTheDocument();
  });

  it('calls placeOrder on BUY click (no error)', () => {
    const consoleSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
    render(<TradingPanel />);
    fireEvent.click(screen.getByText('BUY'));
    expect(consoleSpy).toHaveBeenCalledWith('Order placement via REST API not yet implemented');
    consoleSpy.mockRestore();
  });

  it('calls placeOrder on SELL click (no error)', () => {
    const consoleSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
    render(<TradingPanel />);
    fireEvent.click(screen.getByText('SELL'));
    expect(consoleSpy).toHaveBeenCalled();
    consoleSpy.mockRestore();
  });
});
