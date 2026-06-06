import type {PlaceOrderRequest} from '@/dto/types';

interface OrderConfirmationModalProps {
  open: boolean;
  request: PlaceOrderRequest | null;
  killSwitchActive: boolean;
  onConfirm: () => void;
  onCancel: () => void;
  loading?: boolean;
}

export function OrderConfirmationModal({
  open,
  request,
  killSwitchActive,
  onConfirm,
  onCancel,
  loading,
}: OrderConfirmationModalProps) {
  if (!open || !request) return null;

  const notional = (request.quantity * request.pricePaisa) / 100;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
      <div className="bg-[#09090b] border border-zinc-800 rounded-lg p-5 w-80 shadow-2xl font-mono text-[10px]">
        <div className="text-[11px] font-bold text-zinc-200 uppercase tracking-wider mb-3">Confirm Order</div>
        {killSwitchActive && (
          <div className="mb-3 p-2 bg-[#ef4444]/15 border border-[#ef4444]/40 text-[#ef4444] rounded-xs">
            Kill switch is active — order may be rejected
          </div>
        )}
        <div className="space-y-2 text-zinc-400">
          <div className="flex justify-between">
            <span>Symbol</span>
            <span className="text-zinc-200 font-bold">{request.symbol}</span>
          </div>
          <div className="flex justify-between">
            <span>Side</span>
            <span className={request.side === 'BUY' ? 'text-[#10b981]' : 'text-[#ef4444]'}>{request.side}</span>
          </div>
          <div className="flex justify-between">
            <span>Qty</span>
            <span className="text-zinc-200">{request.quantity}</span>
          </div>
          <div className="flex justify-between">
            <span>Price</span>
            <span className="text-zinc-200">₹{(request.pricePaisa / 100).toFixed(2)}</span>
          </div>
          <div className="flex justify-between">
            <span>Notional</span>
            <span className="text-zinc-200">₹{notional.toFixed(2)}</span>
          </div>
        </div>
        <div className="flex gap-2 mt-4">
          <button
            onClick={onCancel}
            disabled={loading}
            className="flex-1 h-8 border border-zinc-700 text-zinc-400 rounded-xs hover:border-zinc-600 cursor-pointer"
          >
            Cancel
          </button>
          <button
            onClick={onConfirm}
            disabled={loading || killSwitchActive}
            className="flex-1 h-8 bg-[#00d2ff] text-[#070709] font-bold rounded-xs hover:bg-[#00b2d6] disabled:opacity-40 cursor-pointer"
          >
            {loading ? 'Placing…' : 'Confirm'}
          </button>
        </div>
      </div>
    </div>
  );
}
