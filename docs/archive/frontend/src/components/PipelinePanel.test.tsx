import {describe, expect, it, vi, beforeEach, afterEach, type Mock} from 'vitest';
import {render, screen, fireEvent, waitFor} from '@testing-library/react';
import {PipelinePanel} from './PipelinePanel';
import {usePipelineStore} from '@/store/usePipelineStore';
import type {NodeTypeDescriptor} from '@/dto/types';

const mockGraph = vi.hoisted(() => ({
  id: 'graph-1',
  name: 'Institutional Baseline',
  version: 3,
  executionMode: 'LIVE',
  nodes: [{id: 'n1', typeId: 'scanner', config: {}}],
  edges: [{from: 'n1', to: 'n2', eventType: 'SCAN_COMPLETED'}],
}));

const mockNodeTypes = vi.hoisted(() => ({
  scanner: {
    typeId: 'scanner',
    displayName: 'Scanner Node',
    category: 'input',
    description: 'Scans the market',
    inputEvents: [],
    outputEvents: [{type: 'SCAN_COMPLETED', description: 'Scan done'}],
    configFields: [{key: 'profile', type: 'string', label: 'Profile', defaultValue: 'baseline', options: []}],
  },
  execution: {
    typeId: 'execution',
    displayName: 'Execution Node',
    category: 'output',
    description: 'Executes trades',
    inputEvents: [],
    outputEvents: [],
    configFields: [],
  },
}));

vi.mock('@/api/client', () => ({
  pipelineApi: {
    nodeTypes: vi.fn().mockResolvedValue(mockNodeTypes),
    nodeTypeCategories: vi.fn().mockResolvedValue(['input', 'output']),
    activeGraph: vi.fn().mockResolvedValue(mockGraph),
    activeDagGraphs: vi.fn().mockResolvedValue({'graph-1': mockGraph}),
    compile: vi.fn().mockResolvedValue({success: true, message: 'Compiled', version: 4}),
    persist: vi.fn().mockResolvedValue({success: true, graphId: 'graph-1', version: 3, executionMode: 'LIVE'}),
  },
}));

function resetStore() {
  usePipelineStore.setState({
    nodeTypes: {},
    categories: [],
    activeGraphs: {},
    loading: false,
    error: null,
    metrics: {},
  });
}

describe('PipelinePanel', () => {
  beforeEach(() => resetStore());
  afterEach(() => {
    vi.clearAllMocks();
    usePipelineStore.setState({nodeTypes: {}, activeGraphs: {}, loading: false, error: null, metrics: {}});
  });

  it('renders Pipeline header', () => {
    render(<PipelinePanel />);
    expect(screen.getByText('Pipeline')).toBeInTheDocument();
  });

  it('renders COMPILE and PERSIST buttons', () => {
    render(<PipelinePanel />);
    expect(screen.getByText('COMPILE')).toBeInTheDocument();
    expect(screen.getByText('PERSIST')).toBeInTheDocument();
  });

  it('shows loading state', () => {
    usePipelineStore.setState({loading: true});
    render(<PipelinePanel />);
    expect(screen.getByText('Loading pipeline data...')).toBeInTheDocument();
  });

  it('shows empty state when no graphs', async () => {
    const {pipelineApi} = await import('@/api/client');
    (pipelineApi.activeGraph as Mock).mockImplementationOnce(() => Promise.resolve(null));
    (pipelineApi.activeDagGraphs as Mock).mockImplementationOnce(() => Promise.resolve({}));
    (pipelineApi.nodeTypes as Mock).mockImplementationOnce(() => Promise.resolve({}));
    render(<PipelinePanel />);
    await waitFor(() => {
      expect(screen.getByText('No active pipeline graphs found')).toBeInTheDocument();
    });
  });

  it('shows error banner when error exists', async () => {
    const {pipelineApi} = await import('@/api/client');
    (pipelineApi.activeGraph as Mock).mockImplementationOnce(() => Promise.reject(new Error('Load failed')));
    (pipelineApi.nodeTypes as Mock).mockImplementationOnce(() => Promise.resolve({}));
    render(<PipelinePanel />);
    await waitFor(() => {
      expect(screen.getByText(/Failed to load/)).toBeInTheDocument();
    });
  });

  it('displays active graphs', () => {
    usePipelineStore.setState({activeGraphs: {'graph-1': mockGraph}});
    render(<PipelinePanel />);
    expect(screen.getByText('Institutional Baseline')).toBeInTheDocument();
    expect(screen.getByText(/v3/)).toBeInTheDocument();
    expect(screen.getByText('LIVE')).toBeInTheDocument();
  });

  it('shows graph node and edge counts', () => {
    usePipelineStore.setState({activeGraphs: {'graph-1': mockGraph}});
    render(<PipelinePanel />);
    expect(screen.getByText(/1 nodes/)).toBeInTheDocument();
    expect(screen.getByText(/1 edges/)).toBeInTheDocument();
  });

  it('displays node registry when types are loaded', () => {
    usePipelineStore.setState({nodeTypes: mockNodeTypes as Record<string, NodeTypeDescriptor>});
    render(<PipelinePanel />);
    expect(screen.getByText('Node Registry')).toBeInTheDocument();
    expect(screen.getByText('Scanner Node')).toBeInTheDocument();
    expect(screen.getByText('Execution Node')).toBeInTheDocument();
  });

  it('shows node category in registry', () => {
    usePipelineStore.setState({nodeTypes: mockNodeTypes as Record<string, NodeTypeDescriptor>});
    render(<PipelinePanel />);
    expect(screen.getByText(/input/)).toBeInTheDocument();
    expect(screen.getByText(/output/)).toBeInTheDocument();
  });

  it('displays live metrics when available', () => {
    usePipelineStore.setState({metrics: {throughput: 150.0, latencyMs: 2.5}});
    render(<PipelinePanel />);
    expect(screen.getByText('Live Metrics')).toBeInTheDocument();
    expect(screen.getByText(/150.00/)).toBeInTheDocument();
    expect(screen.getByText(/2.50/)).toBeInTheDocument();
  });

  it('filters out non-numeric metrics', () => {
    usePipelineStore.setState({metrics: {throughput: 100, name: 'test'}});
    render(<PipelinePanel />);
    expect(screen.getByText(/100.00/)).toBeInTheDocument();
    expect(screen.queryByText('test')).not.toBeInTheDocument();
  });

  it('calls pipelineApi.persist on PERSIST click', async () => {
    const {pipelineApi} = await import('@/api/client');
    render(<PipelinePanel />);
    fireEvent.click(screen.getByText('PERSIST'));
    await waitFor(() => {
      expect(pipelineApi.persist).toHaveBeenCalled();
    });
  });

  it('calls pipelineApi.compile on COMPILE click when graph exists', async () => {
    const {pipelineApi} = await import('@/api/client');
    usePipelineStore.setState({activeGraphs: {'graph-1': mockGraph}});
    render(<PipelinePanel />);
    fireEvent.click(screen.getByText('COMPILE'));
    await waitFor(() => {
      expect(pipelineApi.compile).toHaveBeenCalledWith(mockGraph);
    });
  });

  it('does not call compile when no graph exists', async () => {
    const {pipelineApi} = await import('@/api/client');
    (pipelineApi.activeGraph as Mock).mockImplementationOnce(() => Promise.resolve(null));
    (pipelineApi.activeDagGraphs as Mock).mockImplementationOnce(() => Promise.resolve({}));
    (pipelineApi.nodeTypes as Mock).mockImplementationOnce(() => Promise.resolve({}));
    render(<PipelinePanel />);
    fireEvent.click(screen.getByText('COMPILE'));
    await waitFor(() => {
      expect(pipelineApi.compile).not.toHaveBeenCalled();
    });
  });
});
