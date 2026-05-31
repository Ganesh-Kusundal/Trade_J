import {describe, expect, it, vi, beforeEach, afterEach} from 'vitest';
import {render, screen, fireEvent, waitFor} from '@testing-library/react';
import {ScannerPanel} from './ScannerPanel';
import {useStudioStore} from '@/store/useStudioStore';

const mockScanResult = vi.hoisted(() => ({
  run: {
    runId: 'scan-abc-123-def',
    profileId: 'institutional-baseline',
    startedAtMs: 1716537900000,
    finishedAtMs: 1716538000000,
    status: 'SUCCESS',
    universeSize: 150,
    hitCount: 3,
    partialFailureCount: 0,
    errorMessage: null,
  },
  hits: [
    {symbol: 'SBIN', exchangeSegment: 'NSE_EQ', assetClass: 'EQUITY', underlying: null, score: 9.5, reasons: ['trend_strength'], snapshot: {}, promoted: true},
    {symbol: 'RELIANCE', exchangeSegment: 'NSE_EQ', assetClass: 'EQUITY', underlying: null, score: 8.1, reasons: ['volume_expansion'], snapshot: {}, promoted: true},
    {symbol: 'TCS', exchangeSegment: 'NSE_EQ', assetClass: 'EQUITY', underlying: null, score: 6.2, reasons: ['opening_drive'], snapshot: {}, promoted: false},
  ],
}));

vi.mock('@/api/client', () => ({
  scanApi: {
    latest: vi.fn().mockResolvedValue(mockScanResult),
    run: vi.fn().mockResolvedValue(mockScanResult),
  },
}));

function resetStore() {
  useStudioStore.setState({
    scanResult: null,
    scanLoading: false,
    scannerFilter: {profile: 'institutional-baseline'},
  });
}

describe('ScannerPanel', () => {
  beforeEach(() => resetStore());
  afterEach(() => {
    useStudioStore.setState({scanResult: null, scanLoading: false});
  });

  it('renders Scanner header', () => {
    render(<ScannerPanel />);
    expect(screen.getByText('Scanner')).toBeInTheDocument();
  });

  it('renders RUN SCAN button', () => {
    render(<ScannerPanel />);
    expect(screen.getByText('RUN SCAN')).toBeInTheDocument();
  });

  it('shows "No scan results" when empty and not loading', async () => {
    render(<ScannerPanel />);
    await waitFor(() => {
      expect(screen.getByText(/No scan results available/)).toBeInTheDocument();
    });
  });

  it('shows loading state when scanning', () => {
    useStudioStore.setState({scanLoading: true});
    render(<ScannerPanel />);
    expect(screen.getByText('RUNNING...')).toBeInTheDocument();
    expect(screen.getByText('Running scan...')).toBeInTheDocument();
  });

  it('disables RUN SCAN button while loading', () => {
    useStudioStore.setState({scanLoading: true});
    render(<ScannerPanel />);
    expect(screen.getByText('RUNNING...')).toBeDisabled();
  });

  it('displays scan results when available', async () => {
    useStudioStore.setState({scanResult: mockScanResult});
    render(<ScannerPanel />);
    await waitFor(() => {
      expect(screen.getByText(/scan-abc/)).toBeInTheDocument();
    });
    expect(screen.getByText(/institutional-baseline/)).toBeInTheDocument();
    expect(screen.getByText('SUCCESS')).toBeInTheDocument();
  });

  it('displays promoted scan hits', async () => {
    useStudioStore.setState({scanResult: mockScanResult});
    render(<ScannerPanel />);
    await waitFor(() => {
      expect(screen.getAllByText('PROMOTED').length).toBe(2);
    });
    expect(screen.getByText('SBIN')).toBeInTheDocument();
    expect(screen.getByText('RELIANCE')).toBeInTheDocument();
  });

  it('displays non-promoted scan hits', async () => {
    useStudioStore.setState({scanResult: mockScanResult});
    render(<ScannerPanel />);
    await waitFor(() => {
      expect(screen.getByText('TCS')).toBeInTheDocument();
    });
    expect(screen.getByText('EQUITY')).toBeInTheDocument();
  });

  it('shows score for each hit', async () => {
    useStudioStore.setState({scanResult: mockScanResult});
    render(<ScannerPanel />);
    await waitFor(() => {
      expect(screen.getByText(/9.50/)).toBeInTheDocument();
    });
  });

  it('selects symbol when a hit is clicked', async () => {
    useStudioStore.setState({scanResult: mockScanResult});
    render(<ScannerPanel />);
    await waitFor(() => {
      fireEvent.click(screen.getByText('SBIN'));
    });
    expect(useStudioStore.getState().selectedSymbol).toBe('SBIN');
  });

  it('calls scanApi.run when RUN SCAN is clicked', async () => {
    const {scanApi} = await import('@/api/client');
    render(<ScannerPanel />);
    fireEvent.click(screen.getByText('RUN SCAN'));
    await waitFor(() => {
      expect(scanApi.run).toHaveBeenCalledWith('institutional-baseline');
    });
  });

  it('shows hit count in scan metadata', async () => {
    useStudioStore.setState({scanResult: mockScanResult});
    render(<ScannerPanel />);
    await waitFor(() => {
      expect(screen.getByText('3')).toBeInTheDocument();
    });
  });
});
