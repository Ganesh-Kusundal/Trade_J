import {useState, useEffect, useCallback} from 'react';
import {
  FlaskConical,
  BrainCircuit,
  TrendingUp,
  BarChart3,
  Play,
  RotateCcw,
  CheckCircle2,
  XCircle,
  AlertCircle,
  Clock,
  Layers,
  Gauge,
  ChevronRight,
  ChevronDown,
  Table2,
  LineChart,
  ArrowLeft,
  User,
  Hash,
  Target,
  Calendar,
  Activity,
  ListOrdered,
} from 'lucide-react';
import {researchApi} from '@/api/client';
import type {
  Experiment,
  ExperimentRun,
  BacktestRunResult,
  TradeLogEntry,
  SweepTaskRequest,
  SweepResultResponse,
  SweepResultItem,
} from '@/dto/types';

type TabId = 'dashboard' | 'strategy-lab' | 'equity-curve';

export function ResearchPanel() {
  const [activeTab, setActiveTab] = useState<TabId>('dashboard');

  return (
    <div className="h-full flex flex-col text-[10px] font-mono bg-[#070709]">
      {/* Tab Navigation */}
      <div className="h-9 border-b border-[#1c1c1e] flex items-center px-2 shrink-0 gap-0.5 bg-[#09090b]">
        {[
          {id: 'dashboard' as TabId, label: 'Dashboard', icon: FlaskConical},
          {id: 'strategy-lab' as TabId, label: 'Strategy Lab', icon: BrainCircuit},
          {id: 'equity-curve' as TabId, label: 'Equity Curve', icon: TrendingUp},
        ].map(({id, label, icon: Icon}) => (
          <button
            key={id}
            onClick={() => setActiveTab(id)}
            className={`flex items-center gap-1.5 h-7 px-3 rounded-sm text-[9px] font-bold tracking-wider transition cursor-pointer ${
              activeTab === id
                ? 'bg-[#00d2ff]/10 text-[#00d2ff] border-b-2 border-[#00d2ff]'
                : 'text-zinc-500 hover:text-zinc-300 hover:bg-zinc-800/30 border-b-2 border-transparent'
            }`}
          >
            <Icon size={12} />
            {label}
          </button>
        ))}
        <div className="flex-1" />
        <span className="text-[8px] text-zinc-600 font-semibold tracking-widest uppercase mr-2">Research Platform v1</span>
      </div>

      {/* Tab Content */}
      <div className="flex-1 overflow-hidden">
        {activeTab === 'dashboard' && <DashboardTab />}
        {activeTab === 'strategy-lab' && <StrategyLabTab />}
        {activeTab === 'equity-curve' && <EquityCurveTab />}
      </div>
    </div>
  );
}

// ── Shared Helpers ──────────────────────────────────────────────

function formatTime(iso: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleDateString('en-IN', {day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit'});
}

// ── Dashboard Tab ────────────────────────────────────────────────

function DashboardTab() {
  const [experiments, setExperiments] = useState<Experiment[]>([]);
  const [runResults, setRunResults] = useState<BacktestRunResult[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedExperiment, setSelectedExperiment] = useState<Experiment | null>(null);

  const loadAll = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [exps, results] = await Promise.all([
        researchApi.listExperiments().catch(() => [] as Experiment[]),
        researchApi.getRunResults().catch(() => [] as BacktestRunResult[]),
      ]);
      setExperiments(exps);
      setRunResults(results);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
    setLoading(false);
  }, []);

  useEffect(() => { loadAll(); }, []);

  const handleSelectExperiment = (exp: Experiment) => {
    setSelectedExperiment(exp);
  };

  const handleBack = () => {
    setSelectedExperiment(null);
    loadAll(); // Refresh data on return
  };

  // Compute aggregate metrics
  const totalRuns = runResults.length;
  const avgSharpe = totalRuns > 0 ? runResults.reduce((s, r) => s + r.sharpeRatio, 0) / totalRuns : 0;
  const totalTrades = runResults.reduce((s, r) => s + r.totalTrades, 0);

  const statusBadge = (status: string) => {
    const colors: Record<string, string> = {
      DRAFT: 'bg-zinc-500/20 text-zinc-400 border-zinc-500/30',
      RUNNING: 'bg-[#00d2ff]/10 text-[#00d2ff] border-[#00d2ff]/30',
      COMPLETED: 'bg-[#10b981]/10 text-[#10b981] border-[#10b981]/30',
      FAILED: 'bg-[#ef4444]/10 text-[#ef4444] border-[#ef4444]/30',
      CANCELLED: 'bg-zinc-500/10 text-zinc-500 border-zinc-500/20',
    };
    return colors[status] ?? 'bg-zinc-500/10 text-zinc-500 border-zinc-500/20';
  };

  if (selectedExperiment) {
    return (
      <ExperimentDetailView
        experiment={selectedExperiment}
        onBack={handleBack}
      />
    );
  }

  return (
    <div className="h-full flex flex-col overflow-y-auto">
      {/* Metrics Bar */}
      <div className="grid grid-cols-4 gap-2 p-3 shrink-0">
        {[
          {label: 'Experiments', value: experiments.length, icon: FlaskConical, color: 'text-[#00d2ff]'},
          {label: 'Backtest Runs', value: totalRuns, icon: BarChart3, color: 'text-[#10b981]'},
          {label: 'Total Trades', value: totalTrades.toLocaleString(), icon: TrendingUp, color: 'text-[#f59e0b]'},
          {label: 'Avg Sharpe', value: avgSharpe.toFixed(2), icon: Gauge, color: avgSharpe >= 1 ? 'text-[#10b981]' : 'text-[#ef4444]'},
        ].map(({label, value, icon: Icon, color}) => (
          <div key={label} className="bg-[#0e0e11] border border-[#1c1c1e] rounded-sm p-3">
            <div className="flex items-center gap-2 mb-1.5">
              <Icon size={12} className={color} />
              <span className="text-[8px] text-zinc-500 font-bold uppercase tracking-wider">{label}</span>
            </div>
            <span className={`text-lg font-black font-mono tracking-tight ${color}`}>{value}</span>
          </div>
        ))}
      </div>

      {error && (
        <div className="mx-3 mb-2 bg-[#ef4444]/10 border border-[#ef4444]/30 rounded-sm px-2.5 py-1.5 text-[#ef4444] text-[9px] flex items-center gap-1.5">
          <AlertCircle size={11} />
          {error}
        </div>
      )}

      <div className="flex-1 px-3 pb-3 space-y-3 overflow-y-auto">
        {/* Experiments List */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <span className="text-[9px] text-zinc-400 font-bold uppercase tracking-wider flex items-center gap-1.5">
              <FlaskConical size={11} className="text-zinc-500" />
              Experiments
            </span>
            <button onClick={loadAll} className="text-[8px] text-zinc-600 hover:text-zinc-400 transition cursor-pointer flex items-center gap-1">
              <RotateCcw size={10} />
              Refresh
            </button>
          </div>

          {loading && (
            <div className="text-zinc-600 text-[9px] text-center py-6 animate-pulse">Loading experiments...</div>
          )}

          {!loading && experiments.length === 0 && !error && (
            <div className="text-zinc-600 text-[9px] text-center py-8">
              No experiments yet.
            </div>
          )}

          <div className="grid grid-cols-2 gap-2">
            {experiments.map((exp) => (
              <button
                key={exp.experimentId}
                onClick={() => handleSelectExperiment(exp)}
                className="bg-[#0e0e11] border border-[#1c1c1e] rounded-sm px-3 py-2.5 hover:border-[#00d2ff]/30 hover:bg-[#00d2ff]/[0.02] transition-all text-left group cursor-pointer"
              >
                <div className="flex items-start justify-between mb-1.5">
                  <div className="flex items-center gap-2 min-w-0">
                    <FlaskConical size={12} className="text-[#00d2ff] shrink-0 mt-0.5" />
                    <div className="min-w-0">
                      <span className="text-[10px] text-zinc-200 font-bold block truncate">{exp.name}</span>
                      {exp.hypothesis && (
                        <span className="text-[8px] text-zinc-500 block truncate mt-0.5">{exp.hypothesis}</span>
                      )}
                    </div>
                  </div>
                  <ChevronRight size={11} className="text-zinc-700 group-hover:text-zinc-500 transition shrink-0 mt-1" />
                </div>
                <div className="flex items-center gap-2 flex-wrap">
                  <span className={`text-[7px] font-bold px-1.5 py-0.5 rounded-xs border ${statusBadge(exp.status)}`}>
                    {exp.status}
                  </span>
                  <span className="text-[7px] text-zinc-600 font-semibold uppercase tracking-wider">
                    {exp.pipelineType}
                  </span>
                  {exp.owner && (
                    <span className="text-[7px] text-zinc-600 flex items-center gap-1 ml-auto">
                      <User size={8} />
                      {exp.owner}
                    </span>
                  )}
                </div>
              </button>
            ))}
          </div>
        </div>

        {/* Recent Runs */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <span className="text-[9px] text-zinc-400 font-bold uppercase tracking-wider flex items-center gap-1.5">
              <Layers size={11} className="text-zinc-500" />
              Recent Backtest Runs
            </span>
          </div>

          {runResults.slice(0, 15).map((run) => (
            <div key={run.runId} className="bg-[#0e0e11] border border-[#1c1c1e] rounded-sm px-2.5 py-2 mb-1.5 hover:border-zinc-700 transition">
              <div className="flex items-center justify-between mb-1">
                <div className="flex items-center gap-2">
                  <span className="text-zinc-200 font-bold text-[10px]">{run.configHash.slice(0, 10)}...</span>
                  <span className="text-[8px] text-zinc-600">{run.sessionId.slice(0, 8)}</span>
                </div>
                <span className={`text-[9px] font-bold font-mono ${run.totalProfitLoss >= 0 ? 'text-[#10b981]' : 'text-[#ef4444]'}`}>
                  {run.totalProfitLoss >= 0 ? '+' : ''}{run.totalProfitLoss.toFixed(2)}
                </span>
              </div>
              <div className="flex gap-3 text-[8.5px] text-zinc-500">
                <span>Trades: <span className="text-zinc-300 font-bold">{run.totalTrades}</span></span>
                <span>Win Rate: <span className="text-zinc-300 font-bold">{(run.winRate * 100).toFixed(0)}%</span></span>
                <span>Sharpe: <span className={`font-bold ${run.sharpeRatio >= 1 ? 'text-[#10b981]' : 'text-[#f59e0b]'}`}>{run.sharpeRatio.toFixed(2)}</span></span>
                <span>Sortino: <span className="text-zinc-300 font-bold">{run.sortinoRatio.toFixed(2)}</span></span>
                <span>Max DD: <span className="text-[#ef4444] font-bold">{(run.maxDrawdown * 100).toFixed(1)}%</span></span>
              </div>
            </div>
          ))}
        </div>

        {/* Top Performers */}
        {runResults.length > 0 && (
          <div>
            <span className="text-[9px] text-zinc-400 font-bold uppercase tracking-wider flex items-center gap-1.5 mb-2">
              <CheckCircle2 size={11} className="text-zinc-500" />
              Top Performers
            </span>
            <div className="grid grid-cols-3 gap-2">
              {[...runResults]
                .sort((a, b) => b.sharpeRatio - a.sharpeRatio)
                .slice(0, 3)
                .map((run, i) => (
                  <div key={run.runId} className={`bg-[#0e0e11] border rounded-sm p-2 ${
                    i === 0 ? 'border-[#f59e0b]/40' : 'border-[#1c1c1e]'
                  }`}>
                    <div className="flex items-center gap-1 mb-1">
                      <span className={`text-[8px] font-black ${i === 0 ? 'text-[#f59e0b]' : 'text-zinc-500'}`}>
                        #{i + 1}
                      </span>
                      <span className="text-[8px] text-zinc-600 truncate">{run.configHash.slice(0, 8)}</span>
                    </div>
                    <div className="text-[9px] font-bold font-mono">
                      <span className={run.sharpeRatio >= 1 ? 'text-[#10b981]' : 'text-[#f59e0b]'}>
                        S={run.sharpeRatio.toFixed(2)}
                      </span>
                      <span className="text-zinc-500 mx-1">·</span>
                      <span className="text-zinc-300">WR={(run.winRate * 100).toFixed(0)}%</span>
                    </div>
                  </div>
                ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

// ── Experiment Detail View ───────────────────────────────────────

interface ExperimentDetailViewProps {
  experiment: Experiment;
  onBack: () => void;
}

function ExperimentDetailView({experiment, onBack}: ExperimentDetailViewProps) {
  const [runs, setRuns] = useState<ExperimentRun[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedRun, setExpandedRun] = useState<string | null>(null);
  const [creatingRun, setCreatingRun] = useState(false);

  const loadRuns = useCallback(async () => {
    setLoading(true);
    try {
      const data = await researchApi.getExperimentRuns(experiment.experimentId).catch(() => [] as ExperimentRun[]);
      setRuns(data);
    } catch {}
    setLoading(false);
  }, [experiment.experimentId]);

  useEffect(() => { loadRuns(); }, [loadRuns]);

  const handleCreateRun = async () => {
    setCreatingRun(true);
    try {
      await researchApi.startRun(experiment.experimentId, {});
      await loadRuns();
    } catch {}
    setCreatingRun(false);
  };

  const statusBadge = (status: string) => {
    const colors: Record<string, string> = {
      DRAFT: 'bg-zinc-500/20 text-zinc-400',
      RUNNING: 'bg-[#00d2ff]/10 text-[#00d2ff]',
      COMPLETED: 'bg-[#10b981]/10 text-[#10b981]',
      FAILED: 'bg-[#ef4444]/10 text-[#ef4444]',
      CANCELLED: 'bg-zinc-500/10 text-zinc-500',
      QUEUED: 'bg-zinc-500/20 text-zinc-400',
    };
    return colors[status] ?? 'bg-zinc-500/10 text-zinc-500';
  };

  return (
    <div className="h-full flex flex-col overflow-y-auto">
      {/* Back Navigation */}
      <div className="shrink-0 border-b border-[#1c1c1e] bg-[#09090b]">
        <div className="flex items-center px-3 py-2 gap-2">
          <button
            onClick={onBack}
            className="flex items-center gap-1.5 text-[9px] text-zinc-500 hover:text-zinc-300 transition cursor-pointer font-semibold"
          >
            <ArrowLeft size={12} />
            Back to Dashboard
          </button>
        </div>
      </div>

      {/* Experiment Header */}
      <div className="shrink-0 px-3 py-3 border-b border-[#1c1c1e]">
        <div className="flex items-start justify-between mb-3">
          <div className="flex items-start gap-3">
            <div className="w-8 h-8 rounded-sm bg-[#00d2ff]/10 border border-[#00d2ff]/20 flex items-center justify-center shrink-0 mt-0.5">
              <FlaskConical size={14} className="text-[#00d2ff]" />
            </div>
            <div>
              <h2 className="text-[13px] text-zinc-100 font-black tracking-tight">{experiment.name}</h2>
              {experiment.hypothesis && (
                <p className="text-[9px] text-zinc-500 mt-0.5 max-w-xl leading-relaxed">{experiment.hypothesis}</p>
              )}
            </div>
          </div>
          <span className={`text-[8px] font-bold px-2 py-1 rounded-xs border ${statusBadge(experiment.status)}`}>
            {experiment.status}
          </span>
        </div>

        <div className="flex flex-wrap gap-x-4 gap-y-1.5 text-[8px] text-zinc-500">
          <div className="flex items-center gap-1.5">
            <Hash size={10} className="text-zinc-600" />
            <span className="text-zinc-400 font-semibold">ID:</span>
            <span className="font-mono text-zinc-500">{experiment.experimentId.slice(0, 12)}...</span>
          </div>
          <div className="flex items-center gap-1.5">
            <Activity size={10} className="text-zinc-600" />
            <span className="text-zinc-400 font-semibold">Type:</span>
            <span className="text-[#00d2ff] font-bold">{experiment.pipelineType}</span>
          </div>
          {experiment.owner && (
            <div className="flex items-center gap-1.5">
              <User size={10} className="text-zinc-600" />
              <span className="text-zinc-400 font-semibold">Owner:</span>
              <span className="text-zinc-300">{experiment.owner}</span>
            </div>
          )}
          <div className="flex items-center gap-1.5">
            <Calendar size={10} className="text-zinc-600" />
            <span className="text-zinc-400 font-semibold">Created:</span>
            <span className="text-zinc-300">{experiment.createdAt ? new Date(experiment.createdAt).toLocaleDateString('en-IN', {day: '2-digit', month: 'short', year: 'numeric'}) : '—'}</span>
          </div>
          <div className="flex items-center gap-1.5">
            <ListOrdered size={10} className="text-zinc-600" />
            <span className="text-zinc-400 font-semibold">Runs:</span>
            <span className="text-zinc-300 font-bold">{runs.length}</span>
          </div>
        </div>
      </div>

      {/* Runs Section */}
      <div className="flex-1 px-3 pb-3">
        <div className="flex items-center justify-between mt-3 mb-2">
          <span className="text-[9px] text-zinc-400 font-bold uppercase tracking-wider flex items-center gap-1.5">
            <Target size={11} className="text-zinc-500" />
            Runs
          </span>
          <div className="flex items-center gap-2">
            <button
              onClick={loadRuns}
              className="text-[8px] text-zinc-600 hover:text-zinc-400 transition cursor-pointer flex items-center gap-1"
            >
              <RotateCcw size={10} />
              Refresh
            </button>
            <button
              onClick={handleCreateRun}
              disabled={creatingRun}
              className="h-6 px-2.5 bg-[#00d2ff]/10 border border-[#00d2ff]/30 text-[#00d2ff] rounded-xs text-[8px] font-bold hover:bg-[#00d2ff]/20 transition cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-1"
            >
              {creatingRun ? (
                <>
                  <RotateCcw size={9} className="animate-spin" />
                  Starting...
                </>
              ) : (
                <>
                  <Play size={9} fill="#00d2ff" />
                  New Run
                </>
              )}
            </button>
          </div>
        </div>

        {loading && (
          <div className="text-zinc-600 text-[9px] text-center py-8 animate-pulse">Loading runs...</div>
        )}

        {!loading && runs.length === 0 && (
          <div className="flex flex-col items-center justify-center py-10 text-zinc-600">
            <Target size={20} className="mb-2 text-zinc-700" />
            <p className="text-[9px]">No runs for this experiment yet</p>
            <button
              onClick={handleCreateRun}
              disabled={creatingRun}
              className="mt-2 h-6 px-3 bg-[#00d2ff]/10 border border-[#00d2ff]/30 text-[#00d2ff] rounded-xs text-[8px] font-bold hover:bg-[#00d2ff]/20 transition cursor-pointer disabled:opacity-50 flex items-center gap-1"
            >
              <Play size={9} fill="#00d2ff" />
              Start First Run
            </button>
          </div>
        )}

        {!loading && runs.length > 0 && (
          <div className="space-y-1.5">
            {runs.map((run) => {
              const isExpanded = expandedRun === run.runId;
              return (
                <div key={run.runId} className="bg-[#0e0e11] border border-[#1c1c1e] rounded-sm overflow-hidden">
                  {/* Run Header - Clickable */}
                  <button
                    onClick={() => setExpandedRun(isExpanded ? null : run.runId)}
                    className="w-full flex items-center px-3 py-2 hover:bg-zinc-800/20 transition cursor-pointer text-left"
                  >
                    <div className="flex items-center gap-2.5 flex-1 min-w-0">
                      <span className={`text-[8px] font-bold px-1.5 py-0.5 rounded-xs ${statusBadge(run.status)}`}>
                        {run.status}
                      </span>
                      <span className="text-zinc-300 font-bold text-[10px]">#{run.runNumber}</span>
                      <span className="text-[8px] text-zinc-600">
                        {run.startedAt ? formatTime(run.startedAt) : '—'}
                      </span>
                      <span className="text-zinc-600 text-[8px]">→</span>
                      <span className="text-[8px] text-zinc-600">
                        {run.finishedAt ? formatTime(run.finishedAt) : (run.status === 'RUNNING' ? 'In progress...' : '—')}
                      </span>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      {run.metrics && Object.keys(run.metrics).length > 0 && (
                        <span className="text-[8px] text-[#f59e0b] font-bold">
                          {run.metrics['sharpe'] != null
                            ? `S=${Number(run.metrics['sharpe']).toFixed(2)}`
                            : run.metrics['error']
                            ? 'Failed'
                            : `${Object.keys(run.metrics).length} metrics`}
                        </span>
                      )}
                      <ChevronDown
                        size={11}
                        className={`text-zinc-600 transition ${isExpanded ? 'rotate-180' : ''}`}
                      />
                    </div>
                  </button>

                  {/* Expanded Detail */}
                  {isExpanded && (
                    <div className="border-t border-[#1c1c1e] px-3 py-2.5 space-y-3">
                      {/* Parameters */}
                      <div>
                        <span className="text-[8px] text-zinc-500 font-bold uppercase tracking-wider flex items-center gap-1 mb-1.5">
                          <BarChart3 size={9} className="text-zinc-600" />
                          Parameters
                        </span>
                        {run.parameters && Object.keys(run.parameters).length > 0 ? (
                          <div className="grid grid-cols-3 gap-1.5">
                            {Object.entries(run.parameters).map(([key, val]) => (
                              <div key={key} className="bg-[#070709] border border-[#1c1c1e] rounded-xs px-2 py-1.5">
                                <span className="text-[7px] text-zinc-500 uppercase block mb-0.5">{key}</span>
                                <span className="text-[10px] text-[#00d2ff] font-black font-mono">
                                  {typeof val === 'number' ? val.toFixed(2) : String(val)}
                                </span>
                              </div>
                            ))}
                          </div>
                        ) : (
                          <p className="text-[8px] text-zinc-600 italic">No parameters recorded</p>
                        )}
                      </div>

                      {/* Metrics */}
                      <div>
                        <span className="text-[8px] text-zinc-500 font-bold uppercase tracking-wider flex items-center gap-1 mb-1.5">
                          <Gauge size={9} className="text-zinc-600" />
                          Metrics
                        </span>
                        {run.metrics && Object.keys(run.metrics).length > 0 ? (
                          <div className="grid grid-cols-4 gap-1.5">
                            {Object.entries(run.metrics).map(([key, val]) => (
                              <div key={key} className="bg-[#070709] border border-[#1c1c1e] rounded-xs px-2 py-1.5">
                                <span className="text-[7px] text-zinc-500 uppercase block mb-0.5">{key}</span>
                                <span className={`text-[10px] font-black font-mono ${
                                  key === 'error' ? 'text-[#ef4444]' :
                                  key === 'sharpe' || key === 'sortino' ? (Number(val) >= 1 ? 'text-[#10b981]' : 'text-[#f59e0b]') :
                                  key === 'winRate' ? (Number(val) >= 0.5 ? 'text-[#10b981]' : 'text-[#f59e0b]') :
                                  'text-zinc-300'
                                }`}>
                                  {typeof val === 'number' ? val.toFixed(4) : String(val)}
                                </span>
                              </div>
                            ))}
                          </div>
                        ) : (
                          <p className="text-[8px] text-zinc-600 italic">No metrics yet — run still in progress or no data collected</p>
                        )}
                      </div>

                      {/* Run Info */}
                      <div className="flex gap-4 text-[7px] text-zinc-600 pt-1 border-t border-[#1c1c1e]/50">
                        <span>Run ID: <span className="font-mono text-zinc-500">{run.runId.slice(0, 12)}...</span></span>
                        <span>Started: <span className="text-zinc-400">{run.startedAt ? formatTime(run.startedAt) : '—'}</span></span>
                        <span>Finished: <span className="text-zinc-400">{run.finishedAt ? formatTime(run.finishedAt) : (run.status === 'RUNNING' ? 'In progress' : '—')}</span></span>
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

// ── Strategy Lab Tab ─────────────────────────────────────────────

function StrategyLabTab() {
  const [strategyName, setStrategyName] = useState('TickPriceChange');
  const [symbols, setSymbols] = useState('SBIN');
  const [fromMs, setFromMs] = useState(() => Date.now() - 30 * 86400000);
  const [toMs, setToMs] = useState(() => Date.now());
  const [objectiveMetric, setObjectiveMetric] = useState('sharpe');
  const [maximize, setMaximize] = useState(true);
  const [maxConfigs, setMaxConfigs] = useState(20);
  const [slippageBps, setSlippageBps] = useState(5);
  const [commissionPaisa, setCommissionPaisa] = useState(20);

  // Parameter ranges
  const [paramRanges, setParamRanges] = useState<Record<string, {min: number; max: number; step: number}>>({
    fastPeriod: {min: 5, max: 20, step: 5},
    slowPeriod: {min: 20, max: 60, step: 10},
  });

  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<SweepResultResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selectedResult, setSelectedResult] = useState<SweepResultItem | null>(null);

  const addParam = () => {
    const key = `param${Object.keys(paramRanges).length + 1}`;
    setParamRanges((prev) => ({...prev, [key]: {min: 0, max: 100, step: 10}}));
  };

  const updateParamRange = (key: string, field: 'min' | 'max' | 'step', value: number) => {
    setParamRanges((prev) => ({
      ...prev,
      [key]: {...prev[key], [field]: value},
    }));
  };

  const removeParam = (key: string) => {
    setParamRanges((prev) => {
      const next = {...prev};
      delete next[key];
      return next;
    });
  };

  const runSweep = async () => {
    setRunning(true);
    setError(null);
    setResult(null);
    setSelectedResult(null);

    const totalCombos = Object.values(paramRanges).reduce((acc, r) => {
      return acc * Math.floor((r.max - r.min) / r.step + 1);
    }, 1);

    const request: SweepTaskRequest = {
      sessionId: Math.random().toString(36).slice(2) + Date.now().toString(36),
      strategyName,
      strategyVersion: '1.0',
      symbols: symbols.split(',').map((s) => s.trim()).filter(Boolean),
      fromMs,
      toMs,
      parameterRanges: paramRanges,
      objectiveMetric,
      maximize,
      slippageBps,
      commissionPaisa,
      fillRatio: 1.0,
      maxConfigs: Math.min(maxConfigs, totalCombos),
    };

    try {
      const res = await researchApi.runSweep(request);
      setResult(res);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
    setRunning(false);
  };

  // Calculate total combinations
  const totalCombinations = Object.values(paramRanges).reduce((acc, r) => {
    return acc * Math.floor((r.max - r.min) / r.step + 1);
  }, 1);

  return (
    <div className="h-full flex overflow-hidden">
      {/* Left Panel: Config Editor */}
      <div className="w-[340px] border-r border-[#1c1c1e] flex flex-col shrink-0 overflow-y-auto">
        <div className="p-3 space-y-3">
          <span className="text-[9px] text-zinc-400 font-bold uppercase tracking-wider flex items-center gap-1.5">
            <BrainCircuit size={12} className="text-[#00d2ff]" />
            Strategy Configuration
          </span>

          {/* Strategy Name */}
          <div className="space-y-1">
            <label className="text-[8px] text-zinc-500 font-semibold uppercase tracking-wider">Strategy</label>
            <input
              value={strategyName}
              onChange={(e) => setStrategyName(e.target.value)}
              className="w-full bg-[#0e0e11] border border-zinc-800 h-7 px-2 rounded-xs text-zinc-200 font-bold outline-none text-[10px] focus:border-[#00d2ff]/50 transition"
            />
          </div>

          {/* Symbols */}
          <div className="space-y-1">
            <label className="text-[8px] text-zinc-500 font-semibold uppercase tracking-wider">Symbols (comma-separated)</label>
            <input
              value={symbols}
              onChange={(e) => setSymbols(e.target.value)}
              className="w-full bg-[#0e0e11] border border-zinc-800 h-7 px-2 rounded-xs text-zinc-200 font-bold outline-none text-[10px] focus:border-[#00d2ff]/50 transition"
            />
          </div>

          {/* Date Range */}
          <div className="grid grid-cols-2 gap-2">
            <div className="space-y-1">
              <label className="text-[8px] text-zinc-500 font-semibold uppercase tracking-wider">From (epoch ms)</label>
              <input
                type="number"
                value={fromMs}
                onChange={(e) => setFromMs(Number(e.target.value))}
                className="w-full bg-[#0e0e11] border border-zinc-800 h-7 px-2 rounded-xs text-zinc-200 font-bold outline-none text-[10px] focus:border-[#00d2ff]/50 transition"
              />
            </div>
            <div className="space-y-1">
              <label className="text-[8px] text-zinc-500 font-semibold uppercase tracking-wider">To (epoch ms)</label>
              <input
                type="number"
                value={toMs}
                onChange={(e) => setToMs(Number(e.target.value))}
                className="w-full bg-[#0e0e11] border border-zinc-800 h-7 px-2 rounded-xs text-zinc-200 font-bold outline-none text-[10px] focus:border-[#00d2ff]/50 transition"
              />
            </div>
          </div>

          {/* Metric Selection */}
          <div className="space-y-1">
            <label className="text-[8px] text-zinc-500 font-semibold uppercase tracking-wider">Objective</label>
            <div className="flex gap-1">
              <select
                value={objectiveMetric}
                onChange={(e) => setObjectiveMetric(e.target.value)}
                className="flex-1 bg-[#0e0e11] border border-zinc-800 h-7 px-2 rounded-xs text-zinc-200 font-bold outline-none text-[10px] focus:border-[#00d2ff]/50 transition cursor-pointer"
              >
                {['sharpe', 'sortino', 'profitFactor', 'totalReturn', 'winRate', 'calmar'].map((m) => (
                  <option key={m} value={m}>{m}</option>
                ))}
              </select>
              <button
                onClick={() => setMaximize(!maximize)}
                className={`h-7 px-2 rounded-xs font-bold text-[9px] border transition cursor-pointer ${
                  maximize
                    ? 'bg-[#10b981]/10 border-[#10b981]/40 text-[#10b981]'
                    : 'bg-[#ef4444]/10 border-[#ef4444]/40 text-[#ef4444]'
                }`}
              >
                {maximize ? 'MAX' : 'MIN'}
              </button>
            </div>
          </div>

          {/* Fill Model */}
          <div className="grid grid-cols-2 gap-2">
            <div className="space-y-1">
              <label className="text-[8px] text-zinc-500 font-semibold uppercase tracking-wider">Slippage (bps)</label>
              <input
                type="number"
                value={slippageBps}
                onChange={(e) => setSlippageBps(Number(e.target.value))}
                className="w-full bg-[#0e0e11] border border-zinc-800 h-7 px-2 rounded-xs text-zinc-200 font-bold outline-none text-[10px] focus:border-[#00d2ff]/50 transition"
              />
            </div>
            <div className="space-y-1">
              <label className="text-[8px] text-zinc-500 font-semibold uppercase tracking-wider">Commission (paisa)</label>
              <input
                type="number"
                value={commissionPaisa}
                onChange={(e) => setCommissionPaisa(Number(e.target.value))}
                className="w-full bg-[#0e0e11] border border-zinc-800 h-7 px-2 rounded-xs text-zinc-200 font-bold outline-none text-[10px] focus:border-[#00d2ff]/50 transition"
              />
            </div>
          </div>

          {/* Max Configs */}
          <div className="space-y-1">
            <label className="text-[8px] text-zinc-500 font-semibold uppercase tracking-wider">Max Configs to Evaluate</label>
            <input
              type="number"
              value={maxConfigs}
              onChange={(e) => setMaxConfigs(Number(e.target.value))}
              min={1}
              max={1000}
              className="w-full bg-[#0e0e11] border border-zinc-800 h-7 px-2 rounded-xs text-zinc-200 font-bold outline-none text-[10px] focus:border-[#00d2ff]/50 transition"
            />
          </div>

          {/* Parameter Ranges */}
          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <label className="text-[8px] text-zinc-500 font-semibold uppercase tracking-wider">Parameter Ranges</label>
              <span className="text-[8px] text-zinc-600">{totalCombinations} combinations</span>
            </div>
            {Object.entries(paramRanges).map(([key, range]) => (
              <div key={key} className="bg-[#0e0e11] border border-[#1c1c1e] rounded-xs p-2">
                <div className="flex items-center justify-between mb-1.5">
                  <span className="text-[9px] text-zinc-300 font-bold">{key}</span>
                  <button
                    onClick={() => removeParam(key)}
                    className="text-[8px] text-zinc-600 hover:text-[#ef4444] transition cursor-pointer"
                  >
                    ✕
                  </button>
                </div>
                <div className="grid grid-cols-3 gap-1">
                  {(['min', 'max', 'step'] as const).map((field) => (
                    <div key={field}>
                      <span className="text-[7px] text-zinc-600 uppercase block mb-0.5">{field}</span>
                      <input
                        type="number"
                        value={range[field]}
                        onChange={(e) => updateParamRange(key, field, Number(e.target.value))}
                        className="w-full bg-[#070709] border border-zinc-800 h-6 px-1.5 rounded-xs text-zinc-200 font-bold outline-none text-[9px] focus:border-[#00d2ff]/50 transition"
                      />
                    </div>
                  ))}
                </div>
              </div>
            ))}
            <button
              onClick={addParam}
              className="w-full h-6 border border-dashed border-zinc-800 rounded-xs text-zinc-600 hover:text-zinc-400 hover:border-zinc-700 transition text-[9px] cursor-pointer font-semibold"
            >
              + Add Parameter
            </button>
          </div>

          {/* Run Button */}
          <button
            onClick={runSweep}
            disabled={running}
            className="w-full h-8 bg-[#00d2ff] hover:bg-[#00b2d6] disabled:bg-zinc-700 disabled:text-zinc-500 text-[#070709] font-black rounded-sm transition cursor-pointer text-[10px] flex items-center justify-center gap-2"
          >
            {running ? (
              <>
                <RotateCcw size={12} className="animate-spin" />
                EVALUATING {Math.min(maxConfigs, totalCombinations)} CONFIGS...
              </>
            ) : (
              <>
                <Play size={12} fill="#070709" />
                RUN PARAMETER SWEEP
              </>
            )}
          </button>

          {error && (
            <div className="bg-[#ef4444]/10 border border-[#ef4444]/30 rounded-sm px-2 py-1.5 text-[#ef4444] text-[9px] flex items-center gap-1.5">
              <XCircle size={11} />
              {error}
            </div>
          )}
        </div>
      </div>

      {/* Right Panel: Results */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {!result && !running && (
          <div className="flex-1 flex items-center justify-center text-zinc-600 text-[9px]">
            <div className="text-center">
              <BrainCircuit size={24} className="mx-auto mb-2 text-zinc-700" />
              <p>Configure parameters on the left and run a sweep</p>
              <p className="text-[8px] text-zinc-700 mt-1">{totalCombinations} parameter combinations ready</p>
            </div>
          </div>
        )}

        {running && (
          <div className="flex-1 flex items-center justify-center">
            <div className="text-center">
              <div className="w-8 h-8 border-2 border-[#00d2ff] border-t-transparent rounded-full animate-spin mx-auto mb-3" />
              <p className="text-zinc-400 text-[10px] font-bold">Running {Math.min(maxConfigs, totalCombinations)} backtests...</p>
              <p className="text-zinc-600 text-[8px] mt-1">This may take a moment</p>
            </div>
          </div>
        )}

        {result && !running && (
          <div className="flex flex-col h-full overflow-hidden">
            {/* Results Header */}
            <div className="shrink-0 px-3 py-2 border-b border-[#1c1c1e] bg-[#09090b]">
              <div className="flex items-center justify-between mb-1">
                <span className="text-[10px] text-zinc-200 font-bold flex items-center gap-1.5">
                  <CheckCircle2 size={12} className="text-[#10b981]" />
                  Sweep Results — {result.strategy}
                </span>
                <span className="text-[8px] text-zinc-500">
                  {result.totalConfigs} total · {result.successfulConfigs} successful · {result.failedConfigs} failed · {result.durationMs}ms
                </span>
              </div>
              <div className="flex gap-4 text-[8px] text-zinc-500">
                <span>Best Score: <span className="text-[#f59e0b] font-bold">
                  {result.results.length > 0 ? result.results[0].objectiveScore.toFixed(4) : 'N/A'}
                </span></span>
                <span>Configs Evaluated: <span className="text-zinc-300 font-bold">{result.results.length}</span></span>
              </div>
            </div>

            {/* Results Table */}
            <div className="flex-1 overflow-y-auto">
              <table className="w-full text-[9px]">
                <thead>
                  <tr className="text-[7.5px] text-zinc-500 uppercase tracking-wider font-semibold border-b border-[#1c1c1e]">
                    <th className="text-left px-3 py-1.5 w-6">#</th>
                    {Object.keys(paramRanges).map((key) => (
                      <th key={key} className="text-left px-2 py-1.5">{key}</th>
                    ))}
                    <th className="text-right px-3 py-1.5">Score</th>
                    <th className="text-center px-2 py-1.5 w-10">Status</th>
                    <th className="px-2 py-1.5 w-4" />
                  </tr>
                </thead>
                <tbody>
                  {result.results.map((item, i) => (
                    <tr
                      key={i}
                      onClick={() => setSelectedResult(selectedResult === item ? null : item)}
                      className={`border-b border-[#1c1c1e]/50 hover:bg-zinc-800/20 transition cursor-pointer ${
                        selectedResult === item ? 'bg-[#00d2ff]/5' : ''
                      } ${!item.success ? 'opacity-50' : ''}`}
                    >
                      <td className="px-3 py-1.5 text-zinc-600 font-mono">{i + 1}</td>
                      {Object.keys(paramRanges).map((key) => (
                        <td key={key} className="px-2 py-1.5 text-zinc-300 font-bold font-mono">
                          {item.params[key]?.toFixed(0) ?? '—'}
                        </td>
                      ))}
                      <td className={`text-right px-3 py-1.5 font-bold font-mono ${item.success ? 'text-[#f59e0b]' : 'text-zinc-600'}`}>
                        {item.success ? item.objectiveScore.toFixed(4) : '—'}
                      </td>
                      <td className="text-center px-2 py-1.5">
                        {item.success
                          ? <CheckCircle2 size={10} className="inline text-[#10b981]" />
                          : <XCircle size={10} className="inline text-[#ef4444]" />
                        }
                      </td>
                      <td className="px-2 py-1.5">
                        <ChevronRight size={10} className={`text-zinc-600 transition ${selectedResult === item ? 'rotate-90' : ''}`} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              {/* Selected Result Detail */}
              {selectedResult && selectedResult.success && (
                <div className="m-3 p-2.5 bg-[#0e0e11] border border-[#00d2ff]/20 rounded-sm">
                  <span className="text-[8px] text-zinc-500 font-bold uppercase tracking-wider mb-1.5 block">
                    Configuration Parameters
                  </span>
                  <div className="grid grid-cols-4 gap-2">
                    {Object.entries(selectedResult.params).map(([key, val]) => (
                      <div key={key} className="bg-[#070709] border border-[#1c1c1e] rounded-xs px-2 py-1.5">
                        <span className="text-[7px] text-zinc-500 uppercase block">{key}</span>
                        <span className="text-[10px] text-[#00d2ff] font-black font-mono">{val.toFixed(0)}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

// ── Equity Curve Tab ─────────────────────────────────────────────

function EquityCurveTab() {
  const [runResults, setRunResults] = useState<BacktestRunResult[]>([]);
  const [selectedRun, setSelectedRun] = useState<string | null>(null);
  const [tradeLog, setTradeLog] = useState<TradeLogEntry[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    researchApi.getRunResults()
      .then(setRunResults)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  const loadTradeLog = async (runId: string) => {
    setSelectedRun(runId);
    try {
      const logs = await researchApi.getTradeLog(runId);
      setTradeLog(logs);
    } catch {
      setTradeLog([]);
    }
  };

  // Compute derived metrics for selected run
  const selectedRunData = runResults.find((r) => r.runId === selectedRun);
  const totalPnl = tradeLog.reduce((s, t) => s + t.realizedPnlPaisa, 0);
  const winningTrades = tradeLog.filter((t) => t.realizedPnlPaisa > 0).length;
  const losingTrades = tradeLog.filter((t) => t.realizedPnlPaisa <= 0).length;

  return (
    <div className="h-full flex overflow-hidden">
      {/* Left: Run List */}
      <div className="w-[300px] border-r border-[#1c1c1e] flex flex-col shrink-0 overflow-y-auto">
        <div className="p-3">
          <span className="text-[9px] text-zinc-400 font-bold uppercase tracking-wider flex items-center gap-1.5 mb-3">
            <LineChart size={12} className="text-[#00d2ff]" />
            Backtest Runs
          </span>

          {loading && (
            <div className="text-zinc-600 text-[9px] text-center py-6 animate-pulse">Loading runs...</div>
          )}

          {!loading && runResults.length === 0 && (
            <div className="text-zinc-600 text-[9px] text-center py-8">
              No backtest runs available
            </div>
          )}

          {runResults.map((run) => (
            <button
              key={run.runId}
              onClick={() => loadTradeLog(run.runId)}
              className={`w-full text-left mb-1.5 p-2 rounded-sm border transition cursor-pointer ${
                selectedRun === run.runId
                  ? 'bg-[#00d2ff]/5 border-[#00d2ff]/30'
                  : 'bg-[#0e0e11] border-[#1c1c1e] hover:border-zinc-700'
              }`}
            >
              <div className="flex items-center justify-between mb-1">
                <span className="text-[9px] text-zinc-300 font-bold">{run.configHash.slice(0, 10)}...</span>
                <span className={`text-[9px] font-bold font-mono ${run.totalProfitLoss >= 0 ? 'text-[#10b981]' : 'text-[#ef4444]'}`}>
                  {run.totalProfitLoss >= 0 ? '+' : ''}{run.totalProfitLoss.toFixed(0)}
                </span>
              </div>
              <div className="flex gap-2 text-[7.5px] text-zinc-500">
                <span>{run.totalTrades} trades</span>
                <span>{(run.winRate * 100).toFixed(0)}% WR</span>
                <span>S {run.sharpeRatio.toFixed(2)}</span>
              </div>
            </button>
          ))}
        </div>
      </div>

      {/* Right: Trade Log + Metrics */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {!selectedRun && (
          <div className="flex-1 flex items-center justify-center text-zinc-600 text-[9px]">
            <div className="text-center">
              <TrendingUp size={24} className="mx-auto mb-2 text-zinc-700" />
              <p>Select a run to view trade details</p>
            </div>
          </div>
        )}

        {selectedRun && (
          <>
            {/* Metrics Summary */}
            {selectedRunData && (
              <div className="grid grid-cols-6 gap-2 p-3 shrink-0 border-b border-[#1c1c1e]">
                {[
                  {label: 'Total P&L', value: totalPnl.toFixed(0), color: totalPnl >= 0 ? 'text-[#10b981]' : 'text-[#ef4444]'},
                  {label: 'Trades', value: tradeLog.length.toString(), color: 'text-zinc-200'},
                  {label: 'Wins', value: winningTrades.toString(), color: 'text-[#10b981]'},
                  {label: 'Losses', value: losingTrades.toString(), color: 'text-[#ef4444]'},
                  {label: 'Win Rate', value: tradeLog.length > 0 ? ((winningTrades / tradeLog.length) * 100).toFixed(0) + '%' : '0%', color: 'text-[#f59e0b]'},
                  {label: 'Sharpe', value: selectedRunData.sharpeRatio.toFixed(2), color: selectedRunData.sharpeRatio >= 1 ? 'text-[#10b981]' : 'text-[#f59e0b]'},
                ].map(({label, value, color}) => (
                  <div key={label} className="bg-[#0e0e11] border border-[#1c1c1e] rounded-xs p-2">
                    <span className="text-[7px] text-zinc-500 uppercase tracking-wider block mb-0.5">{label}</span>
                    <span className={`text-[11px] font-black font-mono ${color}`}>{value}</span>
                  </div>
                ))}
              </div>
            )}

            {/* Trade Log Table */}
            <div className="flex-1 overflow-y-auto">
              <table className="w-full text-[9px]">
                <thead>
                  <tr className="text-[7.5px] text-zinc-500 uppercase tracking-wider font-semibold border-b border-[#1c1c1e] sticky top-0 bg-[#070709]">
                    <th className="text-left px-3 py-1.5">Symbol</th>
                    <th className="text-center px-2 py-1.5">Side</th>
                    <th className="text-right px-2 py-1.5">Entry</th>
                    <th className="text-right px-2 py-1.5">Exit</th>
                    <th className="text-right px-2 py-1.5">Qty</th>
                    <th className="text-right px-2 py-1.5">P&L</th>
                    <th className="text-right px-2 py-1.5">MAE</th>
                    <th className="text-right px-2 py-1.5">MFE</th>
                  </tr>
                </thead>
                <tbody>
                  {tradeLog.length === 0 && (
                    <tr>
                      <td colSpan={8} className="text-center text-zinc-600 py-6 text-[9px]">No trade data for this run</td>
                    </tr>
                  )}
                  {tradeLog.map((trade) => {
                    const pnlPaisa = trade.realizedPnlPaisa;
                    return (
                      <tr key={trade.tradeId} className="border-b border-[#1c1c1e]/40 hover:bg-zinc-800/20 transition">
                        <td className="px-3 py-1.5 text-zinc-300 font-bold">{trade.symbol}</td>
                        <td className={`text-center px-2 py-1.5 font-bold ${trade.side === 'BUY' ? 'text-[#10b981]' : 'text-[#ef4444]'}`}>
                          {trade.side}
                        </td>
                        <td className="text-right px-2 py-1.5 text-zinc-300 font-mono">{trade.entryPricePaisa}</td>
                        <td className="text-right px-2 py-1.5 text-zinc-300 font-mono">{trade.exitPricePaisa}</td>
                        <td className="text-right px-2 py-1.5 text-zinc-400 font-mono">{trade.quantity}</td>
                        <td className={`text-right px-2 py-1.5 font-bold font-mono ${pnlPaisa >= 0 ? 'text-[#10b981]' : 'text-[#ef4444]'}`}>
                          {pnlPaisa >= 0 ? '+' : ''}{pnlPaisa}
                        </td>
                        <td className="text-right px-2 py-1.5 text-[#ef4444] font-mono">
                          {trade.maePaisa != null ? Math.abs(trade.maePaisa - trade.entryPricePaisa).toLocaleString() : '—'}
                        </td>
                        <td className="text-right px-2 py-1.5 text-[#10b981] font-mono">
                          {trade.mfePaisa != null ? Math.abs(trade.mfePaisa - trade.entryPricePaisa).toLocaleString() : '—'}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
