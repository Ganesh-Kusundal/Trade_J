import { fetchJson } from "./client";
import type {
  PlaceOrderRequest,
  OrderResponse,
  OrderProjectionResponse,
} from "./backend-contracts";

export function placeOrder(request: PlaceOrderRequest) {
  return fetchJson<OrderResponse>("/orders", {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export function listOrders(status = "active") {
  return fetchJson<OrderProjectionResponse[]>(`/orders?status=${status}`);
}

export function cancelOrder(orderId: string) {
  return fetchJson<{ orderId: string; cancelled: boolean }>(`/orders/${orderId}/cancel`, {
    method: "POST",
  });
}
