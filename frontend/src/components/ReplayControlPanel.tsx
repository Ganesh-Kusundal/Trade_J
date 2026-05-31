import {useState, useEffect} from 'react';
import {Play, Pause, SkipForward, Square, Gauge} from 'lucide-react';
import {useStudioStore} from '@/store/useStudioStore';

export function ReplayControlPanel() {
  const selectedSymbol = useStudioStore((s) => s.selectedSymbol);
  const selectedExchange = useStudioStore((s) => s.selectedExchange);
  const fromDate = useStudioStore((s) => s.from);
  const toDate = useStudioStore((s) => s.to);

  const [replayActive, setReplayActive] = useState(false);
  const [replayState, setReplayState] = useState('STOPPED');
  const [currentIndex, setCurrentIndex] = useState(0);
  const [totalCandles, setTotalCandles] = useState(0);
  const [speed, setSpeed] = useState(1);
  const [currentTimeMs, setCurrentTimeMs] = useState<number | null>(null);

  const fetchStatus = async () => {
    try {
      const res = await fetch('/api/research/replay/status');
      if (res.ok) {
        const data = await res.json();
        setReplayState(data.state);
        setCurrentIndex(data.currentIndex);
        setTotalCandles(data.totalCandles);
        setSpeed(data.speedMultiplier);
        setCurrentTimeMs(data.currentTimeMs);
        setReplayActive(data.state !== 'STOPPED');
      }
    } catch {}
  };

  useEffect(() => {
    let timer: number;
    if (replayActive && replayState === 'PLAYING') {
      timer = window.setInterval(fetchStatus, 1000);
    }
    return () => clearInterval(timer);
  }, [replayActive, replayState]);

  const handleInitialize = async () => {
    const fromMs = Date.parse(`${fromDate}T09:15:00+05:30`);
    const toMs = Date.parse(`${toDate}T15:30:00+05:30`);
    try {
      const res = await fetch(
        `/api/research/replay/start?symbol=${selectedSymbol}&exchange=${selectedExchange}&fromMs=${fromMs}&toMs=${toMs}`,
        {method: 'POST'}
      );
      if (res.ok) {
        const data = await res.json();
        setReplayState(data.state);
        setCurrentIndex(data.currentIndex);
        setTotalCandles(data.totalCandles);
        setSpeed(data.speedMultiplier);
        setReplayActive(true);
      }
    } catch {}
  };

  const handlePlay = async () => {
    try {
      const res = await fetch('/api/research/replay/play', {method: 'POST'});
      if (res.ok) {
        const data = await res.json();
        setReplayState(data.state);
      }
    } catch {}
  };

  const handlePause = async () => {
    try {
      const res = await fetch('/api/research/replay/pause', {method: 'POST'});
      if (res.ok) {
        const data = await res.json();
        setReplayState(data.state);
      }
    } catch {}
  };

  const handleStep = async () => {
    try {
      const res = await fetch('/api/research/replay/step', {method: 'POST'});
      if (res.ok) {
        const data = await res.json();
        setCurrentIndex(data.currentIndex);
        setCurrentTimeMs(data.currentTimeMs);
      }
    } catch {}
  };

  const handleStop = async () => {
    try {
      const res = await fetch('/api/research/replay/stop', {method: 'POST'});
      if (res.ok) {
        setReplayActive(false);
        setReplayState('STOPPED');
        setCurrentIndex(0);
        setTotalCandles(0);
      }
    } catch {}
  };

  const handleSpeedChange = async (newSpeed: number) => {
    try {
      const res = await fetch(`/api/research/replay/speed?multiplier=${newSpeed}`, {method: 'POST'});
      if (res.ok) {
        setSpeed(newSpeed);
      }
    } catch {}
  };

  const pct = totalCandles > 0 ? (currentIndex / totalCandles) * 100 : 0;

  return (
    <div className="absolute bottom-5 left-1/2 transform -translate-x-1/2 z-40 bg-[#09090b]/92 border border-zinc-800/60 rounded-lg px-6 py-4 backdrop-blur-md shadow-2xl flex flex-col gap-3 min-w-[500px] max-w-[650px]">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <span className="text-[10px] font-bold text-zinc-400 uppercase tracking-widest font-mono">
            Candle Replay Lab
          </span>
          <span
            className={`text-[8px] font-mono font-black px-2 py-0.5 rounded-sm border ${
              replayState === 'PLAYING'
                ? 'text-[#10b981] border-[#10b981]/30 bg-[#10b981]/10'
                : replayState === 'PAUSED'
                ? 'text-[#f59e0b] border-[#f59e0b]/30 bg-[#f59e0b]/10'
                : 'text-zinc-500 border-zinc-800 bg-zinc-900/40'
            }`}
          >
            {replayState}
          </span>
        </div>

        {currentTimeMs && currentTimeMs > 0 && (
          <span className="text-[10px] text-zinc-400 font-mono">
            {new Date(currentTimeMs).toLocaleTimeString()}
          </span>
        )}
      </div>

      {!replayActive ? (
        <button
          onClick={handleInitialize}
          className="w-full bg-[#00d2ff] hover:bg-[#00b2d6] text-[#070709] h-8 font-black rounded-md transition-colors cursor-pointer text-xs font-mono"
        >
          Initialize Replay Session for {selectedSymbol}
        </button>
      ) : (
        <div className="flex flex-col gap-2">
          {/* Controls */}
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              {replayState === 'PLAYING' ? (
                <button
                  onClick={handlePause}
                  className="w-8 h-8 flex items-center justify-center rounded-md bg-zinc-800 hover:bg-zinc-700 text-zinc-300 transition cursor-pointer"
                  title="Pause"
                >
                  <Pause size={14} />
                </button>
              ) : (
                <button
                  onClick={handlePlay}
                  className="w-8 h-8 flex items-center justify-center rounded-md bg-[#10b981] hover:bg-[#0ea5e9] text-black transition cursor-pointer"
                  title="Play"
                >
                  <Play size={14} fill="black" />
                </button>
              )}

              <button
                onClick={handleStep}
                className="w-8 h-8 flex items-center justify-center rounded-md bg-zinc-800 hover:bg-zinc-700 text-zinc-300 transition cursor-pointer"
                title="Step Forward (1m)"
              >
                <SkipForward size={14} />
              </button>

              <button
                onClick={handleStop}
                className="w-8 h-8 flex items-center justify-center rounded-md bg-zinc-800 hover:bg-zinc-700 text-zinc-300 transition cursor-pointer"
                title="Stop Replay"
              >
                <Square size={14} />
              </button>
            </div>

            {/* Speed selection */}
            <div className="flex items-center gap-2">
              <Gauge size={14} className="text-zinc-500" />
              <select
                value={speed}
                onChange={(e) => handleSpeedChange(Number(e.target.value))}
                className="bg-[#0e0e11] border border-zinc-800 h-8 px-2 rounded-md text-zinc-300 font-bold outline-none cursor-pointer hover:border-zinc-700 transition text-[10px]"
              >
                {[1, 2, 5, 10, 20, 50, 100].map((s) => (
                  <option key={s} value={s}>
                    {s}x Speed
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Progress Bar */}
          <div className="flex flex-col gap-1">
            <div className="w-full h-1.5 bg-zinc-900 rounded-full overflow-hidden">
              <div
                className="h-full bg-[#00d2ff] transition-all duration-300"
                style={{width: `${pct}%`}}
              />
            </div>
            <div className="flex justify-between text-[8.5px] font-mono text-zinc-500 font-semibold">
              <span>Bar {currentIndex} of {totalCandles}</span>
              <span>{pct.toFixed(0)}% Complete</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
