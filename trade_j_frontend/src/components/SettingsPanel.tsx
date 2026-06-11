import React, { useState, useEffect } from "react";

interface Settings {
  defaultBroker: string;
  defaultExchange: string;
  defaultTimeframe: string;
  soundEnabled: boolean;
  notificationsEnabled: boolean;
  chartGridVisible: boolean;
  chartCrosshairMode: "normal" | "magnet";
  orderConfirmation: boolean;
  maxSlippagePercent: number;
  theme: "dark" | "light";
  fontSize: "small" | "medium" | "large";
}

const DEFAULT_SETTINGS: Settings = {
  defaultBroker: "Dhan",
  defaultExchange: "NSE",
  defaultTimeframe: "1m",
  soundEnabled: true,
  notificationsEnabled: true,
  chartGridVisible: true,
  chartCrosshairMode: "normal",
  orderConfirmation: true,
  maxSlippagePercent: 0.5,
  theme: "dark",
  fontSize: "small",
};

interface SettingsPanelProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function SettingsPanel({ isOpen, onClose }: SettingsPanelProps) {
  const [settings, setSettings] = useState<Settings>(() => {
    try {
      const saved = localStorage.getItem("tj_settings");
      return saved ? { ...DEFAULT_SETTINGS, ...JSON.parse(saved) } : DEFAULT_SETTINGS;
    } catch { return DEFAULT_SETTINGS; }
  });

  const [activeTab, setActiveTab] = useState<"general" | "trading" | "chart" | "notifications">("general");

  useEffect(() => {
    localStorage.setItem("tj_settings", JSON.stringify(settings));
  }, [settings]);

  const update = <K extends keyof Settings>(key: K, value: Settings[K]) => {
    setSettings(prev => ({ ...prev, [key]: value }));
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-[100]">
      <div className="bg-[#161b22] border border-[#21262d] rounded-lg w-[480px] max-h-[80vh] flex flex-col">
        <div className="flex items-center justify-between px-4 py-2 border-b border-[#21262d]">
          <span className="font-bold text-slate-200 text-sm">Settings</span>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-200 cursor-pointer text-lg">×</button>
        </div>
        <div className="flex border-b border-[#21262d]">
          {(["general", "trading", "chart", "notifications"] as const).map(tab => (
            <button key={tab} onClick={() => setActiveTab(tab)}
              className={`px-3 py-1.5 text-[10px] font-bold uppercase cursor-pointer ${
                activeTab === tab ? "text-[#f0b429] border-b-2 border-[#f0b429]" : "text-slate-500 hover:text-slate-300"
              }`}>
              {tab}
            </button>
          ))}
        </div>
        <div className="flex-1 overflow-y-auto p-4 space-y-3 font-mono text-[11px]">
          {activeTab === "general" && (
            <>
              <SettingRow label="Default Broker">
                <select value={settings.defaultBroker} onChange={e => update("defaultBroker", e.target.value)}
                  className="bg-[#0d1117] border border-[#21262d] text-slate-100 text-[11px] px-2 py-1 rounded outline-none">
                  <option>Dhan</option><option>Upstox</option><option>Zerodha</option>
                  <option>AngelOne</option><option>Fyers</option><option>ICICIDirect</option>
                </select>
              </SettingRow>
              <SettingRow label="Default Exchange">
                <select value={settings.defaultExchange} onChange={e => update("defaultExchange", e.target.value)}
                  className="bg-[#0d1117] border border-[#21262d] text-slate-100 text-[11px] px-2 py-1 rounded outline-none">
                  <option>NSE</option><option>BSE</option><option>NFO</option><option>MCX</option><option>CDS</option>
                </select>
              </SettingRow>
              <SettingRow label="Default Timeframe">
                <select value={settings.defaultTimeframe} onChange={e => update("defaultTimeframe", e.target.value)}
                  className="bg-[#0d1117] border border-[#21262d] text-slate-100 text-[11px] px-2 py-1 rounded outline-none">
                  {["1m","5m","15m","1h","4h","1d"].map(tf => <option key={tf}>{tf}</option>)}
                </select>
              </SettingRow>
              <SettingRow label="Theme">
                <select value={settings.theme} onChange={e => update("theme", e.target.value as "dark" | "light")}
                  className="bg-[#0d1117] border border-[#21262d] text-slate-100 text-[11px] px-2 py-1 rounded outline-none">
                  <option value="dark">Dark</option><option value="light">Light</option>
                </select>
              </SettingRow>
              <SettingRow label="Font Size">
                <select value={settings.fontSize} onChange={e => update("fontSize", e.target.value as any)}
                  className="bg-[#0d1117] border border-[#21262d] text-slate-100 text-[11px] px-2 py-1 rounded outline-none">
                  <option value="small">Small</option><option value="medium">Medium</option><option value="large">Large</option>
                </select>
              </SettingRow>
            </>
          )}
          {activeTab === "trading" && (
            <>
              <SettingRow label="Order Confirmation">
                <Toggle checked={settings.orderConfirmation} onChange={v => update("orderConfirmation", v)} />
              </SettingRow>
              <SettingRow label="Max Slippage %">
                <input type="number" step="0.1" value={settings.maxSlippagePercent}
                  onChange={e => update("maxSlippagePercent", parseFloat(e.target.value) || 0)}
                  className="bg-[#0d1117] border border-[#21262d] text-slate-100 text-[11px] px-2 py-1 rounded outline-none w-20" />
              </SettingRow>
            </>
          )}
          {activeTab === "chart" && (
            <>
              <SettingRow label="Grid Visible">
                <Toggle checked={settings.chartGridVisible} onChange={v => update("chartGridVisible", v)} />
              </SettingRow>
              <SettingRow label="Crosshair Mode">
                <select value={settings.chartCrosshairMode} onChange={e => update("chartCrosshairMode", e.target.value as any)}
                  className="bg-[#0d1117] border border-[#21262d] text-slate-100 text-[11px] px-2 py-1 rounded outline-none">
                  <option value="normal">Normal</option><option value="magnet">Magnet</option>
                </select>
              </SettingRow>
            </>
          )}
          {activeTab === "notifications" && (
            <>
              <SettingRow label="Sound Alerts">
                <Toggle checked={settings.soundEnabled} onChange={v => update("soundEnabled", v)} />
              </SettingRow>
              <SettingRow label="Browser Notifications">
                <Toggle checked={settings.notificationsEnabled} onChange={v => update("notificationsEnabled", v)} />
              </SettingRow>
            </>
          )}
        </div>
        <div className="flex justify-end gap-2 px-4 py-2 border-t border-[#21262d]">
          <button onClick={() => { setSettings(DEFAULT_SETTINGS); }}
            className="px-3 py-1 rounded text-[10px] font-bold bg-[#21262d] text-slate-300 hover:bg-[#30363d] cursor-pointer">
            Reset Defaults
          </button>
          <button onClick={onClose}
            className="px-3 py-1 rounded text-[10px] font-bold bg-[#f0b429] text-[#0d1117] hover:bg-[#f0b429]/90 cursor-pointer">
            Done
          </button>
        </div>
      </div>
    </div>
  );
}

function SettingRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between py-1">
      <span className="text-slate-400 text-[10px]">{label}</span>
      {children}
    </div>
  );
}

function Toggle({ checked, onChange }: { checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <button onClick={() => onChange(!checked)}
      className={`w-8 h-4 rounded-full relative cursor-pointer transition-colors ${checked ? "bg-[#26a69a]" : "bg-[#21262d]"}`}>
      <div className={`w-3 h-3 rounded-full bg-white absolute top-0.5 transition-all ${checked ? "left-4.5" : "left-0.5"}`}
        style={{ left: checked ? "18px" : "2px" }} />
    </button>
  );
}