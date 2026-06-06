import {describe, expect, it, vi, beforeEach, afterEach} from 'vitest';
import {render, screen, fireEvent, act, waitFor} from '@testing-library/react';
import {LeftSidebar} from './LeftSidebar';
import {useStudioStore} from '@/store/useStudioStore';

vi.mock('@/api/client', () => ({
  studioApi: {
    startupCandidates: vi.fn().mockResolvedValue({
      scanDate: '2026-05-29',
      scanTime: '10:00:00',
      requestedScanTime: '09:45:00',
      selectionMode: 'baseline',
      provenance: {},
      candidates: [],
    }),
  },
  scanApi: {latest: vi.fn().mockResolvedValue({run: {}, hits: []})},
}));

const mockCandidates = [
  {symbol: 'SBIN', rank: 1, exchangeSegment: 'NSE_EQ', masterScore: 9.5, rsScore: 8.2, volumeExpansionScore: 7.1, trendEfficiencyScore: 6.5, openingDriveScore: 5.0, closePaisa: 78000, barTimeMs: 1716537900000},
  {symbol: 'RELIANCE', rank: 2, exchangeSegment: 'NSE_EQ', masterScore: 8.1, rsScore: 7.0, volumeExpansionScore: 6.2, trendEfficiencyScore: 5.8, openingDriveScore: 4.5, closePaisa: 250000, barTimeMs: 1716537900000},
];

function resetStore() {
  useStudioStore.setState({
    sidebarOpen: true,
    startupCandidates: mockCandidates,
    startupScanDate: '2026-05-29',
    startupScanTime: '10:00:00',
    startupRequestedScanTime: '09:45:00',
    startupSelectionMode: 'baseline',
    selectedSymbol: 'SBIN',
    wsConnected: true,
    indicatorParams: {amplitude: 2, channelDeviation: 2, atrPeriod: 100},
    activeView: 'chart',
  });
}

describe('LeftSidebar', () => {
  beforeEach(() => resetStore());
  afterEach(() => {
    useStudioStore.setState({
      sidebarOpen: true,
      startupCandidates: [],
      startupScanDate: null,
      startupScanTime: null,
      startupRequestedScanTime: null,
      startupSelectionMode: null,
      selectedSymbol: '',
      wsConnected: false,
      activeView: 'chart',
    });
  });

  it('renders nothing when sidebar is closed', () => {
    useStudioStore.setState({sidebarOpen: false});
    const {container} = render(<LeftSidebar />);
    expect(container.innerHTML).toBe('');
  });

  it('renders all navigation tabs', () => {
    render(<LeftSidebar />);
    expect(screen.getByText('CHART')).toBeInTheDocument();
    expect(screen.getByText('SCANNER')).toBeInTheDocument();
    expect(screen.getByText('PIPELINE')).toBeInTheDocument();
    expect(screen.getByText('ADMIN')).toBeInTheDocument();
  });

  it('highlights the active navigation tab', async () => {
    render(<LeftSidebar />);
    await waitFor(() => {
      const chartTab = screen.getByText('CHART');
      expect(chartTab.className).toContain('00d2ff');
    });
  });

  it('changes active view when a nav tab is clicked', () => {
    render(<LeftSidebar />);
    fireEvent.click(screen.getByText('ADMIN'));
    expect(useStudioStore.getState().activeView).toBe('admin');
  });

  it('displays startup scan metadata', () => {
    render(<LeftSidebar />);
    expect(screen.getByText(/2026-05-29/)).toBeInTheDocument();
    expect(screen.getByText(/09:45:00/)).toBeInTheDocument();
    expect(screen.getByText(/baseline/)).toBeInTheDocument();
  });

  it('shows connection status indicator', () => {
    render(<LeftSidebar />);
    const scanHeader = screen.getByText('STARTUP SCAN');
    expect(scanHeader).toBeInTheDocument();
  });

  it('shows watchlist with ranked candidates', () => {
    render(<LeftSidebar />);
    expect(screen.getByText('SBIN')).toBeInTheDocument();
    expect(screen.getByText('RELIANCE')).toBeInTheDocument();
    expect(screen.getByText('2 ranked')).toBeInTheDocument();
  });

  it('shows candidate scores', () => {
    render(<LeftSidebar />);
    expect(screen.getByText(/score 9.50/)).toBeInTheDocument();
    expect(screen.getByText(/RS 8.20/)).toBeInTheDocument();
    expect(screen.getByText((content) => /^#\s*1\b/.test(content.trim()))).toBeInTheDocument();
  });

  it('selects symbol when a candidate is clicked', () => {
    render(<LeftSidebar />);
    fireEvent.click(screen.getByText('SBIN'));
    expect(useStudioStore.getState().selectedSymbol).toBe('SBIN');
  });

  it('highlights the selected symbol', () => {
    useStudioStore.setState({selectedSymbol: 'RELIANCE'});
    render(<LeftSidebar />);
    const reliance = screen.getByText('RELIANCE');
    // The highlight class (border-l-2 border-[#00d2ff]) is on the outer container div,
    // which is 2 levels up from the symbol text span
    const containerDiv = reliance.parentElement?.parentElement;
    expect(containerDiv?.className).toContain('00d2ff');
  });

  it('shows indicator controls', () => {
    render(<LeftSidebar />);
    expect(screen.getByText('Indicators')).toBeInTheDocument();
    expect(screen.getByText('Amplitude')).toBeInTheDocument();
    expect(screen.getByText('Deviation')).toBeInTheDocument();
    expect(screen.getAllByDisplayValue('2')).toHaveLength(2);
  });

  it('updates indicator amplitude via input', () => {
    render(<LeftSidebar />);
    const amplitudeInputs = screen.getAllByDisplayValue('2');
    fireEvent.change(amplitudeInputs[0], {target: {value: '5'}});
    expect(useStudioStore.getState().indicatorParams.amplitude).toBe(5);
  });

  it('shows "—" and defaults when no scan data', () => {
    useStudioStore.setState({
      startupScanDate: null,
      startupScanTime: null,
      startupRequestedScanTime: null,
      startupSelectionMode: null,
      startupCandidates: [],
    });
    render(<LeftSidebar />);
    expect(screen.getByText((content) => content.includes('—'))).toBeInTheDocument();
    expect(screen.getByText('0 ranked')).toBeInTheDocument();
  });

  it('shows fallback scan time when not provided', () => {
    useStudioStore.setState({startupRequestedScanTime: null});
    render(<LeftSidebar />);
    expect(screen.getByText(/09:45:00/)).toBeInTheDocument();
  });
});
