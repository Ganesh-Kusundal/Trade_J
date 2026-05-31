import {describe, expect, it, vi, beforeEach, type Mock} from 'vitest';
import {render, screen, fireEvent, waitFor} from '@testing-library/react';
import {AdminPanel} from './AdminPanel';
import {adminApi, healthApi} from '@/api/client';

const mockRuntime = vi.hoisted(() => ({
  websocketConnected: true,
  circuitBreakerOpen: false,
  subscriptions: 2,
  catalogLoaded: true,
  catalogSize: 5000,
  brokerPreflightPassed: true,
  startupCompleted: true,
}));

const mockStrategies = vi.hoisted(() => [
  {name: 'breakout-plugin'},
  {name: 'mean-reversion-plugin'},
]);

const mockHealth = vi.hoisted(() => ({
  status: 'UP',
  components: {broker: {status: 'UP'}},
}));

const mockSummary = vi.hoisted(() => ({
  startupCompleted: true,
  totalTicks: 1200,
  strategyPlugins: ['breakout-plugin'],
}));

vi.mock('@/api/client', () => ({
  adminApi: {
    runtime: vi.fn().mockResolvedValue(mockRuntime),
    strategies: vi.fn().mockResolvedValue(mockStrategies),
    summary: vi.fn().mockResolvedValue(mockSummary),
    killSwitch: vi.fn().mockResolvedValue({enabled: true, acknowledged: true}),
  },
  healthApi: {
    check: vi.fn().mockResolvedValue(mockHealth),
  },
}));

function resetMockImplementations() {
  (adminApi.runtime as Mock).mockResolvedValue(mockRuntime);
  (adminApi.strategies as Mock).mockResolvedValue(mockStrategies);
  (adminApi.summary as Mock).mockResolvedValue(mockSummary);
  (adminApi.killSwitch as Mock).mockResolvedValue({enabled: true, acknowledged: true});
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

  it('loads and displays health data', async () => {
    render(<AdminPanel />);
    await waitFor(() => expect(healthApi.check).toHaveBeenCalled());
    await waitFor(() => expect(screen.getByText('System Health')).toBeInTheDocument());
    expect(screen.getByText('UP')).toBeInTheDocument();
  });

  it('loads and displays runtime info from backend shape', async () => {
    render(<AdminPanel />);
    await waitFor(() => expect(adminApi.runtime).toHaveBeenCalled());
    await waitFor(() => expect(screen.getByText('websocketConnected')).toBeInTheDocument());
    expect(screen.getAllByText('true').length).toBeGreaterThan(0);
  });

  it('loads and displays strategy plugins', async () => {
    render(<AdminPanel />);
    await waitFor(() => expect(adminApi.strategies).toHaveBeenCalled());
    await waitFor(() => {
      expect(screen.getByText('breakout-plugin')).toBeInTheDocument();
      expect(screen.getByText('mean-reversion-plugin')).toBeInTheDocument();
    });
  });

  it('calls killSwitch API with enable when currently off', async () => {
    render(<AdminPanel />);
    await waitFor(() => expect(screen.getByText('ENABLE KILL SWITCH')).toBeInTheDocument());
    fireEvent.click(screen.getByText('ENABLE KILL SWITCH'));
    await waitFor(() => expect(adminApi.killSwitch).toHaveBeenCalledWith(true));
  });

  it('shows kill switch ON after toggle response', async () => {
    render(<AdminPanel />);
    fireEvent.click(screen.getByText('ENABLE KILL SWITCH'));
    await waitFor(() => expect(screen.getByText('ON')).toBeInTheDocument());
  });

  it('handles empty strategies gracefully', async () => {
    (adminApi.strategies as Mock).mockResolvedValue([]);
    render(<AdminPanel />);
    await waitFor(() => expect(screen.getByText('No strategy plugins loaded')).toBeInTheDocument());
  });

  it('surfaces runtime load errors', async () => {
    (adminApi.runtime as Mock).mockRejectedValue(new Error('503 Service Unavailable'));
    render(<AdminPanel />);
    await waitFor(() => expect(screen.getByText(/503 Service Unavailable/)).toBeInTheDocument());
  });
});
