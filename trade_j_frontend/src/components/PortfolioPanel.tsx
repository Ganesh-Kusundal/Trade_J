import React, { useState, useEffect } from "react";

interface OrderEntry {
  orderId: string;
  symbol: string;
  side: string;
  quantity: number;
  filledQuantity: number;
  pricePaisa: number;
  status: string;
  orderType: string;
}

interface PortfolioPanelProps {
  segment: string;
  refreshTrigger?: number;
}

export default function PortfolioPanel({ segment, refreshTrigger }: PortfolioPanelProps) {
  const [orders, setOrders] = useState<OrderEntry[]>([]);
  const [tab, setTab] = useState<"active" | "completed">("active");

  useEffect(() => {
    const fetchOrders = async () => {
      try {
        const { orderApi } = await import("../generated/api");
        const data = await orderApi.list(tab === "active" ? "active" : "all");
        setOrders(Array.isArray(data) ? data as unknown as OrderEntry[] : []);
      } catch {}
    };
    fetchOrders();
    const iv = setInterval(fetchOrders, 5000);
    return () => clearInterval(iv);
  }, [tab, refreshTrigger]);

  const activeOrders = orders.filter(o => o.status !== "TRADED" && o.status !== "CANCELLED" && o.status !== "REJECTED");
  const completedOrders = orders.filter(o => o.status === "TRADED" || o.status === "CANCELLED" || o.status === "REJECTED");
  const displayOrders = tab === "active" ? activeOrders : completedOrders;

  const statusColor = (status: string) => {
    switch (status) {
      case "TRADED": return "text-[#26a69a]";
      case "OPEN": case "PENDING": return "text-[#f0b429]";
      case "CANCELLED": case "REJECTED": return "text-[#ef5350]";
      default: return "text-slate-400";
    }
  };

  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded-lg flex flex-col h-full font-mono text-[10px]">
      <div className="flex items-center justify-between px-2 py-1 border-b border-[#21262d]">
        <span className="font-bold text-slate-300 text-[9px] uppercase tracking-wider">Orders</span>
        <div className="flex gap-1">
          <button onClick={() => setTab("active")}
            className={`px-1.5 py-0.5 rounded text-[9px] font-bold cursor-pointer ${tab === "active" ? "bg-[#f0b429]/15 text-[#f0b429]" : "text-slate-500 hover:text-slate-300"}`}>
            Active ({activeOrders.length})
          </button>
          <button onClick={() => setTab("completed")}
            className={`px-1.5 py-0.5 rounded text-[9px] font-bold cursor-pointer ${tab === "completed" ? "bg-[#f0b429]/15 text-[#f0b429]" : "text-slate-500 hover:text-slate-300"}`}>
            History ({completedOrders.length})
          </button>
        </div>
      </div>
      <div className="grid grid-cols-5 px-2 py-0.5 text-[8px] text-slate-500 font-bold uppercase border-b border-[#21262d]/50">
        <span>Symbol</span>
        <span>Side</span>
        <span className="text-right">Qty</span>
        <span className="text-right">Price</span>
        <span className="text-right">Status</span>
      </div>
      <div className="flex-1 overflow-y-auto">
        {displayOrders.length === 0 ? (
          <div className="flex items-center justify-center h-full text-slate-600 text-[10px]">
            {tab === "active" ? "No active orders" : "No order history"}
          </div>
        ) : (
          displayOrders.map(order => (
            <div key={order.orderId} className="grid grid-cols-5 px-2 py-0.5 hover:bg-[#161b22] text-[9px] border-b border-[#21262d]/30">
              <span className="font-bold text-slate-200 truncate">{order.symbol}</span>
              <span className={order.side === "BUY" ? "text-[#26a69a]" : "text-[#ef5350]"}>
                {order.side}
              </span>
              <span className="text-right text-slate-300">
                {order.filledQuantity}/{order.quantity}
              </span>
              <span className="text-right text-slate-300">
                {order.pricePaisa > 0 && isFinite(order.pricePaisa) ? (order.pricePaisa / 100).toFixed(2) : "MKT"}
              </span>
              <span className={`text-right font-bold ${statusColor(order.status)}`}>
                {order.status}
              </span>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
