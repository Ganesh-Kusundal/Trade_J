import {create} from 'zustand';
import type {NodeTypeDescriptor, PipelineGraph} from '@/dto/types';
import {pipelineApi} from '@/api/client';

interface PipelineStore {
  nodeTypes: Record<string, NodeTypeDescriptor>;
  categories: string[];
  activeGraphs: Record<string, PipelineGraph>;
  loading: boolean;
  error: string | null;
  metrics: Record<string, unknown>;

  loadNodeTypes: () => Promise<void>;
  loadCategories: () => Promise<void>;
  loadActiveGraphs: () => Promise<void>;
  loadDagGraphs: () => Promise<void>;
  setMetrics: (metrics: Record<string, unknown>) => void;
}

export const usePipelineStore = create<PipelineStore>((set) => ({
  nodeTypes: {},
  categories: [],
  activeGraphs: {},
  loading: false,
  error: null,
  metrics: {},

  loadNodeTypes: async () => {
    set({loading: true, error: null});
    try {
      const types = await pipelineApi.nodeTypes();
      set({nodeTypes: types as Record<string, NodeTypeDescriptor>, loading: false});
    } catch (e) {
      set({error: `Failed to load node types: ${e instanceof Error ? e.message : e}`, loading: false});
    }
  },

  loadCategories: async () => {
    try {
      const categories = await pipelineApi.nodeTypeCategories();
      set({categories});
    } catch {}
  },

  loadActiveGraphs: async () => {
    set({loading: true, error: null});
    try {
      const graph = await pipelineApi.activeGraph();
      const dagGraphs = await pipelineApi.activeDagGraphs();
      const graphs: Record<string, PipelineGraph> = {};
      if (graph && typeof graph === 'object' && 'id' in graph) {
        graphs[(graph as PipelineGraph).id] = graph as PipelineGraph;
      }
      for (const [id, g] of Object.entries(dagGraphs)) {
        graphs[id] = g as PipelineGraph;
      }
      set({activeGraphs: graphs, loading: false});
    } catch (e) {
      set({error: `Failed to load graphs: ${e instanceof Error ? e.message : e}`, loading: false});
    }
  },

  loadDagGraphs: async () => {
    try {
      const dagGraphs = await pipelineApi.activeDagGraphs();
      set((s) => ({
        activeGraphs: {...s.activeGraphs, ...Object.fromEntries(
          Object.entries(dagGraphs).map(([id, g]) => [id, g as PipelineGraph])
        )},
      }));
    } catch {}
  },

  setMetrics: (metrics: Record<string, unknown>) => set({metrics}),
}));
