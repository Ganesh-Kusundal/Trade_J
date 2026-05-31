import {useEffect} from 'react';
import {usePipelineStore} from '@/store/usePipelineStore';
import {useSSE} from '@/hooks/useSSE';
import {pipelineApi} from '@/api/client';

export function PipelinePanel() {
  const nodeTypes = usePipelineStore((s) => s.nodeTypes);
  const activeGraphs = usePipelineStore((s) => s.activeGraphs);
  const loading = usePipelineStore((s) => s.loading);
  const error = usePipelineStore((s) => s.error);
  const metrics = usePipelineStore((s) => s.metrics);
  const loadNodeTypes = usePipelineStore((s) => s.loadNodeTypes);
  const loadActiveGraphs = usePipelineStore((s) => s.loadActiveGraphs);
  const setMetrics = usePipelineStore((s) => s.setMetrics);

  useEffect(() => {
    loadNodeTypes();
    loadActiveGraphs();
  }, []);

  useSSE('/api/v1/pipeline/stream/metrics', 'pipeline-metrics', (data) => {
    setMetrics(data as Record<string, unknown>);
  });

  const compileGraph = async () => {
    const activeGraph = Object.values(activeGraphs)[0];
    if (!activeGraph) return;
    const result = await pipelineApi.compile(activeGraph);
    if (result.success) loadActiveGraphs();
  };

  return (
    <div className="h-full flex flex-col text-[10px] font-mono">
      <div className="h-8 border-b border-[#1c1c1e] flex items-center justify-between px-3 shrink-0">
        <span className="text-[#71717a] font-bold tracking-wider uppercase text-[9px]">Pipeline</span>
        <div className="flex gap-1">
          <button
            onClick={compileGraph}
            className="h-5 px-2 bg-[#00d2ff]/20 border border-[#00d2ff]/40 text-[#00d2ff] font-bold rounded-xs hover:bg-[#00d2ff]/30 transition cursor-pointer text-[9px]"
          >
            COMPILE
          </button>
          <button
            onClick={() => pipelineApi.persist()}
            className="h-5 px-2 bg-zinc-800 border border-zinc-700 text-zinc-300 font-bold rounded-xs hover:bg-zinc-700 transition cursor-pointer text-[9px]"
          >
            PERSIST
          </button>
        </div>
      </div>

      <div className="p-3 space-y-3 flex-1 overflow-y-auto">
        {error && (
          <div className="bg-[#ef4444]/10 border border-[#ef4444]/30 text-[#ef4444] px-2 py-1.5 rounded-xs text-[9px]">
            {error}
          </div>
        )}

        {Object.entries(activeGraphs).map(([id, graph]) => (
          <div key={id} className="bg-[#0e0e11] border border-[#1c1c1e] rounded-xs p-2">
            <div className="flex items-center justify-between mb-1">
              <span className="text-zinc-200 font-bold text-[11px]">{graph.name}</span>
              <span className="text-[#00d2ff] text-[9px] border border-[#00d2ff]/30 px-1">{graph.executionMode}</span>
            </div>
            <div className="flex gap-3 text-[8px] text-zinc-500">
              <span>v{graph.version}</span>
              <span>{graph.nodes?.length ?? 0} nodes</span>
              <span>{graph.edges?.length ?? 0} edges</span>
            </div>
          </div>
        ))}

        {loading && (
          <div className="text-zinc-600 text-[9px] text-center py-4 animate-pulse">Loading pipeline data...</div>
        )}

        {Object.keys(activeGraphs).length === 0 && !loading && (
          <div className="text-zinc-600 text-[9px] text-center py-8">
            No active pipeline graphs found
          </div>
        )}

        {Object.keys(nodeTypes).length > 0 && (
          <div>
            <div className="text-[#71717a] font-semibold uppercase tracking-wider text-[8px] mb-2">Node Registry</div>
            <div className="grid grid-cols-2 gap-1">
              {Object.entries(nodeTypes).slice(0, 20).map(([id, desc]) => (
                <div key={id} className="bg-[#0e0e11] border border-[#1c1c1e] rounded-xs p-1.5">
                  <div className="text-zinc-300 font-bold text-[9px] truncate">{desc.displayName}</div>
                  <div className="text-zinc-600 text-[8px]">{desc.category} · {desc.configFields?.length ?? 0} fields</div>
                </div>
              ))}
            </div>
          </div>
        )}

        {Object.keys(metrics).length > 0 && (
          <div>
            <div className="text-[#71717a] font-semibold uppercase tracking-wider text-[8px] mb-2">Live Metrics</div>
            <div className="grid grid-cols-2 gap-1">
              {Object.entries(metrics).filter(([_, v]) => typeof v === 'number').map(([key, val]) => (
                <div key={key} className="flex justify-between bg-[#0e0e11] border border-[#1c1c1e] rounded-xs px-2 py-1">
                  <span className="text-zinc-500">{key}</span>
                  <span className="text-zinc-200 font-bold">{(val as number).toFixed(2)}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
