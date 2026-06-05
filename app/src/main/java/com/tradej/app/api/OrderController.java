package com.tradej.app.api;

import com.tradej.app.api.dto.ModifyOrderApiRequest;
import com.tradej.app.api.dto.OrderProjectionResponse;
import com.tradej.app.api.dto.OrderResponse;
import com.tradej.app.api.dto.PlaceOrderRequest;
import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.runtime.RuntimeMode;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.execution.risk.PositionRiskHandler;
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
    private final RuntimeModeHolder runtimeModeHolder;
    private final PositionRiskHandler positionRiskHandler;

    public OrderController(
            OrderManagementService orderManagementService,
            RuntimeModeHolder runtimeModeHolder,
            PositionRiskHandler positionRiskHandler
    ) {
        this.orderManagementService = orderManagementService;
        this.runtimeModeHolder = runtimeModeHolder;
        this.positionRiskHandler = positionRiskHandler;
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
        if (runtimeModeHolder.mode() != RuntimeMode.LIVE) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Order placement only allowed in LIVE mode", "mode", runtimeModeHolder.mode()));
        }
        if (positionRiskHandler.isKillSwitchActive()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Kill switch active"));
        }
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
        Order order = orderManagementService.placeOrder(orderRequest);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @PutMapping("/{orderId}")
    public ResponseEntity<?> modify(
            @PathVariable String orderId,
            @RequestBody ModifyOrderApiRequest request
    ) {
        if (runtimeModeHolder.mode() != RuntimeMode.LIVE) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Order modify only allowed in LIVE mode"));
        }
        ModifyOrderRequest modify = new ModifyOrderRequest(
                orderId,
                request.quantity(),
                request.pricePaisa(),
                request.triggerPricePaisa(),
                request.orderType(),
                request.validity());
        Order order = orderManagementService.modifyOrder(modify);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable String orderId) {
        if (runtimeModeHolder.mode() != RuntimeMode.LIVE) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Order cancel only allowed in LIVE mode"));
        }
        boolean cancelled = orderManagementService.cancelOrder(orderId);
        return ResponseEntity.ok(Map.of("orderId", orderId, "cancelled", cancelled));
    }
}
