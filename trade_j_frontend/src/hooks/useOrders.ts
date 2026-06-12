import { useState, useEffect } from "react";

export interface OrderEntry {
  orderId: string;
  symbol: string;
  side: string;
  quantity: number;
  filledQuantity: number;
  pricePaisa: number;
  status: string;
  orderType: string;
}

export function useOrders(refreshTrigger?: number) {
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

  return { orders, tab, setTab };
}
