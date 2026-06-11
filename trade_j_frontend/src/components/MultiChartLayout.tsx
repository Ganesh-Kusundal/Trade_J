import React, { useState } from "react";

interface MultiChartLayoutProps {
  children: React.ReactNode;
  onAddChart?: () => void;
}

export default function MultiChartLayout({ children, onAddChart }: MultiChartLayoutProps) {
  const [layout, setLayout] = useState<"single" | "dual-h" | "dual-v">("single");

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center gap-1 px-2 py-0.5 border-b border-[#21262d] bg-[#0d1117]">
        <span className="text-[8px] text-slate-500 font-bold uppercase mr-1">Layout:</span>
        {(["single", "dual-h", "dual-v"] as const).map(l => (
          <button key={l} onClick={() => setLayout(l)}
            className={`px-1.5 py-0.5 rounded text-[8px] font-bold cursor-pointer ${
              layout === l ? "bg-[#f0b429]/15 text-[#f0b429]" : "text-slate-500 hover:text-slate-300"
            }`}>
            {l === "single" ? "1×1" : l === "dual-h" ? "1×2" : "2×1"}
          </button>
        ))}
      </div>
      <div className={`flex-1 overflow-hidden ${
        layout === "dual-h" ? "grid grid-cols-2 gap-1 p-1" :
        layout === "dual-v" ? "grid grid-rows-2 gap-1 p-1" :
        "flex"
      }`}>
        {children}
      </div>
    </div>
  );
}
