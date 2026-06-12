import { orderApi } from "../generated/api";
import type {
  PlaceOrderRequest,
  OrderResponse,
  OrderProjectionResponse,
} from "../generated/models";

export function placeOrder(request: PlaceOrderRequest) {
  return orderApi.place(request) as Promise<OrderResponse>;
}

export function listOrders(status: "active" | "all" = "active") {
  return orderApi.list(status) as Promise<OrderProjectionResponse[]>;
}

export function cancelOrder(orderId: string) {
  return orderApi.cancel(orderId) as Promise<OrderResponse>;
}
