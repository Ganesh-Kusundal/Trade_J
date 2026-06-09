package com.tradej.app.api;

import com.tradej.app.api.dto.ModifyOrderApiRequest;
import com.tradej.app.api.dto.OrderProjectionResponse;
import com.tradej.app.api.dto.OrderResponse;
import com.tradej.app.api.dto.PlaceOrderRequest;
import com.tradej.app.service.OrderApplicationService;
import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.execution.service.OrderManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderManagementService orderManagementService;
    private final OrderApplicationService orderApplicationService;

    public OrderController(
            OrderManagementService orderManagementService,
            OrderApplicationService orderApplicationService
    ) {
        this.orderManagementService = orderManagementService;
        this.orderApplicationService = orderApplicationService;
    }

    @GetMapping
    public ResponseEntity<List<OrderProjectionResponse>> list(
            @RequestParam(defaultValue = "active") String status
    ) {
        List<OrderProjectionResponse> orders = switch (status.toLowerCase()) {
            case "completed", "terminal" -> orderManagementService.getCompletedOrders().stream()
                    .map(OrderProjectionResponse::from)
                    .toList();
            case "all" -> {
                var active = orderManagementService.getActiveOrders().stream()
                        .map(OrderProjectionResponse::from)
                        .toList();
                var completed = orderManagementService.getCompletedOrders().stream()
                        .map(OrderProjectionResponse::from)
                        .toList();
                yield java.util.stream.Stream.concat(active.stream(), completed.stream()).toList();
            }
            default -> orderManagementService.getActiveOrders().stream()
                    .map(OrderProjectionResponse::from)
                    .toList();
        };
        return ResponseEntity.ok(orders);
    }

    @PostMapping
    public ResponseEntity<?> placeRoot(@RequestBody PlaceOrderRequest request) {
        return place(request);
    }

    @PostMapping("/place")
    public ResponseEntity<?> place(@RequestBody PlaceOrderRequest request) {
        String correlationId = request.correlationId() != null && !request.correlationId().isBlank()
                ? request.correlationId()
                : UUID.randomUUID().toString();
        OrderRequest orderRequest = new OrderRequest(
                request.symbol(),
                request.exchangeSegment(),
                request.side(),
                request.quantity(),
                request.orderType(),
                request.pricePaisa(),
                request.effectiveTriggerPricePaisa(),
                request.productType(),
                request.validity(),
                correlationId);
        return mapResult(orderApplicationService.placeOrder(orderRequest));
    }

    @PutMapping("/{orderId}")
    public ResponseEntity<?> modify(
            @PathVariable String orderId,
            @RequestBody ModifyOrderApiRequest request
    ) {
        ModifyOrderRequest modify = new ModifyOrderRequest(
                orderId,
                null,
                null,
                request.quantity(),
                request.pricePaisa(),
                request.triggerPricePaisa(),
                request.orderType(),
                request.validity());
        return mapResult(orderApplicationService.modifyOrder(modify));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable String orderId) {
        var result = orderApplicationService.cancelOrder(orderId);
        boolean cancelled = result.isSuccess();
        return ResponseEntity.ok(Map.of("orderId", orderId, "cancelled", cancelled));
    }

    private ResponseEntity<?> mapResult(com.tradej.execution.command.CommandResult result) {
        return switch (result) {
            case com.tradej.execution.command.CommandResult.Success s ->
                    ResponseEntity.ok(OrderResponse.from(s.order()));
            case com.tradej.execution.command.CommandResult.Rejected r ->
                    ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                            .body(Map.of("error", "Order rejected", "reason", r.reason()));
            case com.tradej.execution.command.CommandResult.Error e ->
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", e.message()));
            default -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Unexpected result"));
        };
    }
}
