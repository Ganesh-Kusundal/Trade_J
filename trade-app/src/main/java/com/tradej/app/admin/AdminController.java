package com.tradej.app.admin;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.core.domain.port.EventBus;
import com.tradej.execution.reconcile.OrderReconciler;
import com.tradej.execution.service.TradingCircuitBreaker;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {
    private final IBrokerConnection brokerConnection;
    private final TradingCircuitBreaker tradingCircuitBreaker;
    private final OrderReconciler orderReconciler;
    private final EventBus eventBus;
    private final RuntimeHealthState runtimeHealthState;

    public AdminController(
            IBrokerConnection brokerConnection,
            TradingCircuitBreaker tradingCircuitBreaker,
            OrderReconciler orderReconciler,
            EventBus eventBus,
            RuntimeHealthState runtimeHealthState
    ) {
        this.brokerConnection = brokerConnection;
        this.tradingCircuitBreaker = tradingCircuitBreaker;
        this.orderReconciler = orderReconciler;
        this.eventBus = eventBus;
        this.runtimeHealthState = runtimeHealthState;
    }

    @GetMapping("/runtime")
    ResponseEntity<Map<String, Object>> runtime() {
        return ResponseEntity.ok(Map.of(
                "websocketConnected", brokerConnection.websocket().isConnected(),
                "circuitBreakerOpen", tradingCircuitBreaker.isOpen(),
                "subscriptions", brokerConnection.websocket().subscriptions().size(),
                "catalogLoaded", runtimeHealthState.catalogLoaded(),
                "catalogSize", runtimeHealthState.catalogSize(),
                "brokerPreflightPassed", runtimeHealthState.brokerPreflightPassed(),
                "startupCompleted", runtimeHealthState.startupCompleted()
        ));
    }

    @PostMapping("/risk/kill-switch/{enabled}")
    ResponseEntity<Map<String, Object>> setKillSwitch(@PathVariable boolean enabled) {
        boolean acknowledged = brokerConnection.orders().setKillSwitch(enabled);
        return ResponseEntity.ok(Map.of("enabled", enabled, "acknowledged", acknowledged));
    }

    @PostMapping("/reconcile")
    ResponseEntity<Map<String, Object>> reconcile(@org.springframework.web.bind.annotation.RequestBody(required = false) Map<String, Long> expectedNetPositions) {
        if (expectedNetPositions == null || expectedNetPositions.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "rejected",
                    "reason", "explicit expectedNetPositions payload is required"
            ));
        }
        orderReconciler.reconcile(expectedNetPositions, eventBus::publish);
        return ResponseEntity.ok(Map.of("status", "triggered"));
    }
}
