interface ExportableTrade {
  time: number;
  price: number;
  quantity: number;
  side: string;
}

interface ExportableOrder {
  orderId: string;
  symbol: string;
  side: string;
  quantity: number;
  filledQuantity: number;
  pricePaisa: number;
  status: string;
  orderType: string;
}

export function exportTradesToCSV(trades: ExportableTrade[], filename = "trades.csv"): void {
  const header = "Time,Price,Quantity,Side";
  const rows = trades.map(t => {
    const time = new Date(t.time * 1000).toISOString();
    return `${time},${t.price.toFixed(2)},${t.quantity},${t.side}`;
  });
  downloadCSV([header, ...rows].join("\n"), filename);
}

export function exportOrdersToCSV(orders: ExportableOrder[], filename = "orders.csv"): void {
  const header = "OrderId,Symbol,Side,Qty,FilledQty,Price,Type,Status";
  const rows = orders.map(o => {
    const price = o.pricePaisa > 0 ? (o.pricePaisa / 100).toFixed(2) : "MKT";
    return `${o.orderId},${o.symbol},${o.side},${o.quantity},${o.filledQuantity},${price},${o.orderType},${o.status}`;
  });
  downloadCSV([header, ...rows].join("\n"), filename);
}

function downloadCSV(content: string, filename: string): void {
  const blob = new Blob([content], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.style.display = "none";
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
