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
    candles: [],
    markers: [],
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
      candles: [],
      markers: [],
    });
  });

  it('renders nothing when sidebar is closed', () => {
    useStudioStore.setState({sidebarOpen: false});
    const {container} = render(<LeftSidebar />);
    expect(container.innerHTML).toBe('');
  });

  it('renders MARKET INTELLIGENCE header', () => {
    render(<LeftSidebar />);
    expect(screen.getByText('MARKET INTELLIGENCE')).toBeInTheDocument();
  });

  it('renders Market Watch section', () => {
    render(<LeftSidebar />);
    expect(screen.getByText('Market Watch')).toBeInTheDocument();
  });

  it('renders Structure Scanner section', () => {
    render(<LeftSidebar />);
    expect(screen.getByText('Structure Scanner')).toBeInTheDocument();
  });

  it('renders Indicator Controls section', () => {
    render(<LeftSidebar />);
    expect(screen.getByText('Indicator Controls')).toBeInTheDocument();
  });

  it('displays startup scan metadata', () => {
    render(<LeftSidebar />);
    expect(screen.getByText(/2026-05-29/)).toBeInTheDocument();
    expect(screen.getByText(/09:45:00/)).toBeInTheDocument();
    expect(screen.getByText(/baseline/)).toBeInTheDocument();
  });

  it('shows watchlist with ranked candidates', () => {
    render(<LeftSidebar />);
    expect(screen.getByText('SBIN')).toBeInTheDocument();
    expect(screen.getByText('RELIANCE')).toBeInTheDocument();
  });

  it('shows candidate scores', () => {
    render(<LeftSidebar />);
    expect(screen.getByText(/score 9.50/)).toBeInTheDocument();
    expect(screen.getByText((content) => /^#\s*1\b/.test(content.trim()))).toBeInTheDocument();
  });

  it('selects symbol when a candidate is clicked', () => {
    render(<LeftSidebar />);
    fireEvent.click(screen.getByText('SBIN'));
    expect(useStudioStore.getState().selectedSymbol).toBe('SBIN');
  });

  it('shows indicator controls with sliders', () => {
    render(<LeftSidebar />);
    expect(screen.getByText('Amplitude')).toBeInTheDocument();
    expect(screen.getByText('Channel Deviation')).toBeInTheDocument();
    const sliders = screen.getAllByRole('slider');
    expect(sliders).toHaveLength(2);
  });

  it('shows SYSTEM ACTIVE footer', () => {
    render(<LeftSidebar />);
    expect(screen.getByText('SYSTEM ACTIVE')).toBeInTheDocument();
    expect(screen.getByText('SCANNER v2.8')).toBeInTheDocument();
  });

  it('shows empty state when no scan data', () => {
    useStudioStore.setState({
      startupScanDate: null,
      startupScanTime: null,
      startupRequestedScanTime: null,
      startupSelectionMode: null,
      startupCandidates: [],
    });
    render(<LeftSidebar />);
    expect(screen.getByText(/Waiting for startup scan candidates/)).toBeInTheDocument();
  });

  it('shows fallback scan time when not provided', () => {
    useStudioStore.setState({startupRequestedScanTime: null});
    render(<LeftSidebar />);
    expect(screen.getByText(/n\/a/)).toBeInTheDocument();
  });
});
