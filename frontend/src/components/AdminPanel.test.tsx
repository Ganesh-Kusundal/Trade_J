import {describe, expect, it, vi, beforeEach, afterEach, type Mock} from 'vitest';
import {render, screen, fireEvent, waitFor} from '@testing-library/react';
import {AdminPanel} from './AdminPanel';
import {adminApi, healthApi} from '@/api/client';

const mockRuntime = vi.hoisted(() => ({
  version: '1.0.0',
  uptime: '2h 15m',
  mode: 'LIVE',
  broker: 'Dhan',
  gatewayEnabled: true,
  scanEnabled: true,
}));

const mockStrategies = vi.hoisted(() => [
  {id: 'strat-1', name: 'Breakout', status: 'RUNNING', metrics: {trades: 10}},
  {id: 'strat-2', name: 'MeanReversion', status: 'PAUSED', metrics: {trades: 5}},
]);

const mockHealth = vi.hoisted(() => ({
  status: 'UP',
  broker: 'ok',
  websocket: true,
  diskSpace: 'ok',
}));

const mockSummary = vi.hoisted(() => ({
  killSwitch: false,
  totalPnL: 2500.50,
  openPositions: 3,
  dailyTrades: 12,
}));

vi.mock('@/api/client', () => ({
  adminApi: {
    runtime: vi.fn().mockResolvedValue(mockRuntime),
    strategies: vi.fn().mockResolvedValue(mockStrategies),
    summary: vi.fn().mockResolvedValue(mockSummary),
    killSwitch: vi.fn().mockResolvedValue({killSwitch: true}),
  },
  healthApi: {
    check: vi.fn().mockResolvedValue(mockHealth),
  },
}));

function resetMockImplementations() {
  (adminApi.runtime as Mock).mockResolvedValue(mockRuntime);
  (adminApi.strategies as Mock).mockResolvedValue(mockStrategies);
  (adminApi.summary as Mock).mockResolvedValue(mockSummary);
  (adminApi.killSwitch as Mock).mockResolvedValue({killSwitch: true});
  (healthApi.check as Mock).mockResolvedValue(mockHealth);
}

describe('AdminPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    resetMockImplementations();
  });

  it('renders Admin header', () => {
    render(<AdminPanel />);
    expect(screen.getByText('Admin')).toBeInTheDocument();
  });

  it('renders refresh button', () => {
    render(<AdminPanel />);
    expect(screen.getByRole('button', {name: 'Refresh'})).toBeInTheDocument();
  });

  it('loads and displays health data', async () => {
    render(<AdminPanel />);
    await waitFor(() => {
      expect(healthApi.check).toHaveBeenCalled();
    });
    await waitFor(() => {
      expect(screen.getByText('System Health')).toBeInTheDocument();
    });
    expect(screen.getByText('UP')).toBeInTheDocument();
  });

  it('shows health status with correct colors', async () => {
    render(<AdminPanel />);
    await waitFor(() => {
      expect(screen.getByText('UP')).toBeInTheDocument();
    });
    const upElement = screen.getByText('UP');
    expect(upElement.className).toContain('10b981');
  });

  it('loads and displays runtime info', async () => {
    render(<AdminPanel />);
    await waitFor(() => {
      expect(adminApi.runtime).toHaveBeenCalled();
    });
    await waitFor(() => {
      expect(screen.getByText('LIVE')).toBeInTheDocument();
    });
    expect(screen.getByText('Dhan')).toBeInTheDocument();
  });

  it('loads and displays strategies', async () => {
    render(<AdminPanel />);
    await waitFor(() => {
      expect(adminApi.strategies).toHaveBeenCalled();
    });
    await waitFor(() => {
      expect(screen.getByText('Breakout')).toBeInTheDocument();
      expect(screen.getByText('MeanReversion')).toBeInTheDocument();
    });
    expect(screen.getByText('RUNNING')).toBeInTheDocument();
    expect(screen.getByText('PAUSED')).toBeInTheDocument();
  });

  it('renders kill switch toggle button', () => {
    render(<AdminPanel />);
    expect(screen.getByText('TOGGLE KILL SWITCH')).toBeInTheDocument();
  });

  it('calls killSwitch API on button click', async () => {
    render(<AdminPanel />);
    await waitFor(() => {
      expect(adminApi.summary).toHaveBeenCalled();
    });
    fireEvent.click(screen.getByText('TOGGLE KILL SWITCH'));
    await waitFor(() => {
      expect(adminApi.killSwitch).toHaveBeenCalledWith(true);
    });
  });

  it('toggles kill switch with current state', async () => {
    const mockSummaryWithKill = {...mockSummary, killSwitch: true};
    (adminApi.summary as Mock).mockResolvedValue(mockSummaryWithKill);
    render(<AdminPanel />);
    await waitFor(() => {
      expect(adminApi.summary).toHaveBeenCalled();
    });
    fireEvent.click(screen.getByText('TOGGLE KILL SWITCH'));
    await waitFor(() => {
      expect(adminApi.killSwitch).toHaveBeenCalledWith(false);
    });
  });

  it('loads and displays summary', async () => {
    render(<AdminPanel />);
    await waitFor(() => {
      expect(adminApi.summary).toHaveBeenCalled();
    });
    await waitFor(() => {
      expect(screen.getByText('Summary')).toBeInTheDocument();
    });
    expect(screen.getByText(/2500.50/)).toBeInTheDocument();
  });

  it('shows kill switch state', async () => {
    render(<AdminPanel />);
    await waitFor(() => {
      expect(screen.getByText('false')).toBeInTheDocument();
    });
  });

  it('reloads all data after kill switch toggle', async () => {
    render(<AdminPanel />);
    await waitFor(() => {
      expect(adminApi.runtime).toHaveBeenCalledTimes(1);
    });
    fireEvent.click(screen.getByText('TOGGLE KILL SWITCH'));
    await waitFor(() => {
      expect(adminApi.runtime).toHaveBeenCalledTimes(2);
    });
  });

  it('handles empty strategies gracefully', async () => {
    (adminApi.strategies as Mock).mockResolvedValue([]);
    render(<AdminPanel />);
    await waitFor(() => {
      expect(screen.getByText('Strategies')).toBeInTheDocument();
    });
  });

  it('handles API errors gracefully', async () => {
    (adminApi.runtime as Mock).mockRejectedValue(new Error('Network error'));
    (healthApi.check as Mock).mockRejectedValue(new Error('Network error'));
    render(<AdminPanel />);
    await waitFor(() => {
      expect(adminApi.runtime).toHaveBeenCalled();
    });
    // Should not crash - just not show runtime section
    expect(screen.queryByText('Runtime')).not.toBeInTheDocument();
  });
});
