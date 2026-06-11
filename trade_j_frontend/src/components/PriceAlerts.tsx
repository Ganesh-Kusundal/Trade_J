import React, { useState, useEffect, useCallback } from "react";

interface PriceAlert {
  id: string;
  symbol: string;
  exchange: string;
  targetPrice: number;
  direction: "ABOVE" | "BELOW";
  triggered: boolean;
  createdAt: number;
}

interface PriceAlertsProps {
  currentSymbol: string;
  currentExchange: string;
  lastPrice: number;
  segment: string;
}

export default function PriceAlerts({ currentSymbol, currentExchange, lastPrice, segment }: PriceAlertsProps) {
  const [alerts, setAlerts] = useState<PriceAlert[]>(() => {
    try {
      const saved = localStorage.getItem("tj_alerts");
      return saved ? JSON.parse(saved) : [];
    } catch { return []; }
  });
  const [showForm, setShowForm] = useState(false);
  const [targetPrice, setTargetPrice] = useState("");
  const [direction, setDirection] = useState<"ABOVE" | "BELOW">("ABOVE");

  useEffect(() => {
    localStorage.setItem("tj_alerts", JSON.stringify(alerts));
  }, [alerts]);

  // Check alerts against current price
  useEffect(() => {
    if (lastPrice <= 0) return;
    let changed = false;
    const updated = alerts.map(alert => {
      if (alert.triggered) return alert;
      if (alert.symbol !== currentSymbol || alert.exchange !== currentExchange) return alert;
      const hit = alert.direction === "ABOVE"
        ? lastPrice >= alert.targetPrice
        : lastPrice <= alert.targetPrice;
      if (hit) {
        changed = true;
        // Browser notification
        if (typeof Notification !== "undefined" && Notification.permission === "granted") {
          new Notification(`Price Alert: ${alert.symbol}`, {
            body: `${alert.symbol} is now ${alert.direction} ${alert.targetPrice} (current: ${(typeof lastPrice === 'number' && isFinite(lastPrice) ? lastPrice : 0).toFixed(2)})`,
          });
        }
        // Sound
        try {
          const ctx = new AudioContext();
          const osc = ctx.createOscillator();
          const gain = ctx.createGain();
          osc.connect(gain);
          gain.connect(ctx.destination);
          osc.frequency.value = 880;
          gain.gain.value = 0.15;
          osc.start();
          osc.stop(ctx.currentTime + 0.3);
        } catch {}
        return { ...alert, triggered: true };
      }
      return alert;
    });
    if (changed) setAlerts(updated);
  }, [lastPrice, currentSymbol, currentExchange, alerts]);

  // Request notification permission
  useEffect(() => {
    if (typeof Notification !== "undefined" && Notification.permission === "default") {
      Notification.requestPermission();
    }
  }, []);

  const handleAdd = () => {
    const price = parseFloat(targetPrice);
    if (isNaN(price) || price <= 0) return;
    const alert: PriceAlert = {
      id: Date.now().toString(36),
      symbol: currentSymbol,
      exchange: currentExchange,
      targetPrice: price,
      direction,
      triggered: false,
      createdAt: Date.now(),
    };
    setAlerts(prev => [...prev, alert]);
    setTargetPrice("");
    setShowForm(false);
  };

  const handleRemove = (id: string) => {
    setAlerts(prev => prev.filter(a => a.id !== id));
  };

  const activeAlerts = alerts.filter(a => !a.triggered);
  const triggeredAlerts = alerts.filter(a => a.triggered);

  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded-lg flex flex-col h-full font-mono text-[10px]">
      <div className="flex items-center justify-between px-2 py-1 border-b border-[#21262d]">
        <span className="font-bold text-slate-300 text-[9px] uppercase tracking-wider">
          Alerts ({activeAlerts.length})
        </span>
        <button onClick={() => setShowForm(!showForm)}
          className="text-[#f0b429] font-bold text-[10px] hover:text-[#f0b429]/80 cursor-pointer">+</button>
      </div>
      {showForm && (
        <div className="flex flex-col gap-1 px-2 py-1.5 border-b border-[#21262d]">
          <div className="text-[8px] text-slate-500 font-bold">
            {currentExchange}:{currentSymbol} — Last: {lastPrice > 0 && isFinite(lastPrice) ? lastPrice.toFixed(2) : "—"}
          </div>
          <div className="flex gap-1">
            <button onClick={() => setDirection("ABOVE")}
              className={`px-1.5 py-0.5 rounded text-[9px] font-bold cursor-pointer ${direction === "ABOVE" ? "bg-[#26a69a]/20 text-[#26a69a]" : "bg-[#161b22] text-slate-400"}`}>
              ABOVE
            </button>
            <button onClick={() => setDirection("BELOW")}
              className={`px-1.5 py-0.5 rounded text-[9px] font-bold cursor-pointer ${direction === "BELOW" ? "bg-[#ef5350]/20 text-[#ef5350]" : "bg-[#161b22] text-slate-400"}`}>
              BELOW
            </button>
            <input value={targetPrice} onChange={e => setTargetPrice(e.target.value)}
              onKeyDown={e => e.key === "Enter" && handleAdd()}
              placeholder="Price..." type="number" step="0.05"
              className="flex-1 bg-[#161b22] border border-[#21262d] text-slate-100 text-[10px] px-1.5 py-0.5 rounded outline-none" />
            <button onClick={handleAdd} className="text-[#26a69a] font-bold text-[10px] cursor-pointer">Set</button>
          </div>
        </div>
      )}
      <div className="flex-1 overflow-y-auto">
        {activeAlerts.length === 0 && triggeredAlerts.length === 0 ? (
          <div className="flex items-center justify-center h-full text-slate-600 text-[10px]">
            No alerts set
          </div>
        ) : (
          <>
            {activeAlerts.map(alert => (
              <div key={alert.id} className="flex items-center justify-between px-2 py-1 hover:bg-[#161b22] border-b border-[#21262d]/30">
                <div className="flex flex-col">
                  <span className="font-bold text-slate-200">{alert.symbol}</span>
                  <span className={`text-[8px] font-bold ${alert.direction === "ABOVE" ? "text-[#26a69a]" : "text-[#ef5350]"}`}>
                    {alert.direction} {(typeof alert.targetPrice === 'number' && isFinite(alert.targetPrice) ? alert.targetPrice : 0).toFixed(2)}
                  </span>
                </div>
                <button onClick={() => handleRemove(alert.id)}
                  className="text-slate-600 hover:text-[#ef5350] cursor-pointer text-[9px]">×</button>
              </div>
            ))}
            {triggeredAlerts.length > 0 && (
              <div className="px-2 py-0.5 text-[8px] text-slate-600 font-bold uppercase border-t border-[#21262d]">
                Triggered ({triggeredAlerts.length})
              </div>
            )}
            {triggeredAlerts.slice(0, 5).map(alert => (
              <div key={alert.id} className="flex items-center justify-between px-2 py-0.5 opacity-50 border-b border-[#21262d]/30">
                <span className="text-slate-400">{alert.symbol} {alert.direction} {(typeof alert.targetPrice === 'number' && isFinite(alert.targetPrice) ? alert.targetPrice : 0).toFixed(2)}</span>
                <button onClick={() => handleRemove(alert.id)}
                  className="text-slate-600 hover:text-[#ef5350] cursor-pointer text-[9px]">×</button>
              </div>
            ))}
          </>
        )}
      </div>
    </div>
  );
}
