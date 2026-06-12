import React from "react";
import { ShieldCheck } from "lucide-react";

export default function CertificationDashboard() {
  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded-lg flex flex-col font-mono text-[10px]">
      <div className="px-3 py-2 border-b border-[#21262d]">
        <h2 className="font-black text-[13px] text-[#f0b429]">🎓 Frontend Integration Certification</h2>
      </div>

      <div className="flex-1 flex items-center justify-center p-6">
        <div className="flex flex-col items-center justify-center text-center">
          <ShieldCheck className="w-12 h-12 text-slate-600 mb-3" />
          <div className="text-sm font-semibold text-slate-300">Certification Dashboard — Pending Backend</div>
          <div className="text-[11px] text-slate-500 mt-1 max-w-xs">
            No certification results REST endpoint exists. Previously this panel showed
            12 hardcoded phases; that mock data has been removed. Backend path:
            GET /api/v1/certification/phases (planned).
          </div>
        </div>
      </div>

      <div className="px-3 py-1.5 border-t border-[#21262d] bg-[#161b22] text-[8px] text-slate-500 text-center">
        Principle: Frontend is VIEW, Backend is SOURCE OF TRUTH
      </div>
    </div>
  );
}
