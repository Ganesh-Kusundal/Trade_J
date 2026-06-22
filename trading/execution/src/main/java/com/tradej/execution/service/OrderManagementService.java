package com.tradej.execution.service;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.core.domain.instrument.StandardInstrumentIdentityService;
import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.oms.CancelRequested;
import com.tradej.core.domain.oms.LifecycleState;
import com.tradej.core.domain.oms.OrderAcknowledged;
import com.tradej.core.domain.oms.OrderCancelled;
import com.tradej.core.domain.oms.OrderEvent;
import com.tradej.core.domain.oms.OrderExpired;
import com.tradej.core.domain.oms.OrderFullyFilled;
import com.tradej.core.domain.oms.OrderPartiallyFilled;
import com.tradej.core.domain.oms.OrderProjection;
import com.tradej.core.domain.oms.OrderRejected;
import com.tradej.core.domain.oms.OrderStateMachine;
import com.tradej.core.domain.oms.OrderSubmitted;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.core.domain.time.TradingClock;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import com.tradej.simulation.MatchingEngine;
import com.tradej.simulation.SimulatedOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages order lifecycle with state-machine validation.
 *
 * <p>All order mutations (place, modify, cancel) validate the current
 * {@link LifecycleState} before forwarding to the broker. Broker callbacks
 * drive state transitions via {@link OrderStateMachine}, and every
 * {@link OrderEvent} is persisted to {@link EventSourcedOrderRepository}.
 */
public final class OrderManagementService {

    private static final Logger log = LoggerFactory.getLogger(OrderManagementService.class);

    private final IBrokerConnection brokerConnection;
    private final RuntimeModeHolder runtimeModeHolder;
    private final SimulatedOrderService simulatedOrderService;
    private final TradingClock clock;
    private final EventSourcedOrderRepository orderRepository;
    private final TradingCircuitBreaker circuitBreaker;
    private final ConcurrentHashMap<String, OrderStateMachine> stateMachines = new ConcurrentHashMap<>();
    private volatile MatchingEngine.MatchResult lastSimulatedMatch;

    public OrderManagementService(
            IBrokerConnection brokerConnection,
            RuntimeModeHolder runtimeModeHolder,
            TradingClock clock,
            EventSourcedOrderRepository orderRepository) {
        this(brokerConnection, runtimeModeHolder, null, clock, orderRepository, null);
    }

    public OrderManagementService(
            IBrokerConnection brokerConnection,
            RuntimeModeHolder runtimeModeHolder,
            SimulatedOrderService simulatedOrderService,
            TradingClock clock,
            EventSourcedOrderRepository orderRepository) {
        this(brokerConnection, runtimeModeHolder, simulatedOrderService, clock, orderRepository, null);
    }

    public OrderManagementService(
            IBrokerConnection brokerConnection,
            RuntimeModeHolder runtimeModeHolder,
            SimulatedOrderService simulatedOrderService,
            TradingClock clock,
            EventSourcedOrderRepository orderRepository,
            TradingCircuitBreaker circuitBreaker) {
        this.brokerConnection = brokerConnection;
        this.runtimeModeHolder = runtimeModeHolder;
        this.simulatedOrderService = simulatedOrderService;
        this.clock = clock;
        this.orderRepository = orderRepository;
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * Places a new order after normalising the symbol and forwards to the broker
     * (or simulated matching engine). Does <b>not</b> emit OMS events — the
     * caller ({@link ExecutionHandler}) is responsible for the full event lifecycle.
     */
    public Order placeOrder(OrderRequest request) {
        if (circuitBreaker != null && !circuitBreaker.allowsRequest()) {
            throw new IllegalStateException("Order placement rejected — trading circuit breaker is open");
        }
        String canonicalSymbol = StandardInstrumentIdentityService.INSTANCE.canonicalSymbol(request.symbol());
        OrderRequest normalized = new OrderRequest(
                canonicalSymbol,
                request.exchangeSegment(),
                request.side(),
                request.quantity(),
                request.orderType(),
                request.pricePaisa(),
                request.triggerPricePaisa(),
                request.productType(),
                request.validity(),
                request.correlationId()
        );

        if (runtimeModeHolder.mode().usesSimulatedExecution()) {
            if (simulatedOrderService == null) {
                return legacySimulatedOpenOrder(normalized, clock);
            }
            MatchingEngine.MatchResult match = simulatedOrderService.placeOrder(normalized);
            lastSimulatedMatch = match;
            return match.order();
        }

        lastSimulatedMatch = null;
        return brokerConnection.orders().placeOrder(normalized);
    }

    /**
     * Cancels an order if it is in a cancellable state.
     * Checks local state first to prevent double-cancel and cancel-after-fill,
     * then forwards to the broker only if the order is still active.
     *
     * @return true if the broker accepted the cancel, false if rejected locally or by broker
     */
    public boolean cancelOrder(String orderId) {
        // Check local state first — prevent double-cancel and cancel-after-fill
        OrderStateMachine machine = stateMachines.get(orderId);
        if (machine == null) {
            // Attempt rebuild from persisted event log
            OrderStateMachine rebuilt = orderRepository.rebuildStateMachine(orderId);
            if (rebuilt != null) {
                stateMachines.putIfAbsent(orderId, rebuilt);
                machine = stateMachines.get(orderId);
            }
        }
        if (machine != null) {
            LifecycleState state = machine.toProjection().status();
            if (state.isFinal()) {
                log.info("Rejecting cancel for order {} in terminal state {}", orderId, state);
                return false;
            }
        }

        // Now safe to call broker
        boolean cancelled = brokerConnection.orders().cancelOrder(orderId);
        if (cancelled) {
            persistAndApply(orderId, new CancelRequested(orderId));
        }
        return cancelled;
    }

    /**
     * Modifies an order if it is in a modifiable state ({@code SUBMITTED}
     * or {@code PARTIALLY_FILLED}).
     *
     * @throws IllegalStateException if the order is not modifiable
     */
    public Order modifyOrder(ModifyOrderRequest request) {
        String orderId = request.orderId();
        OrderStateMachine machine = stateMachines.get(orderId);
        if (machine == null) {
            log.warn("Modify requested for unknown orderId={}", orderId);
            return brokerConnection.orders().modifyOrder(request);
        }

        LifecycleState state = machine.toProjection().status();
        if (state != LifecycleState.SUBMITTED && state != LifecycleState.PARTIALLY_FILLED) {
            throw new IllegalStateException(
                    "Cannot modify order " + orderId + " — must be SUBMITTED or PARTIALLY_FILLED, but was " + state);
        }

        return brokerConnection.orders().modifyOrder(request);
    }

    /**
     * Returns the current projection for an order, or empty if unknown.
     */
    public Optional<OrderProjection> getOrderProjection(String orderId) {
        OrderStateMachine machine = stateMachines.get(orderId);
        if (machine != null) {
            return Optional.of(machine.toProjection());
        }
        OrderProjection proj = orderRepository.rebuild(orderId);
        return Optional.ofNullable(proj);
    }

    /**
     * Lists all orders that are not in a terminal state.
     */
    public List<OrderProjection> getActiveOrders() {
        return stateMachines.values().stream()
                .map(OrderStateMachine::toProjection)
                .filter(p -> !p.status().isFinal())
                .toList();
    }

    /**
     * Lists all orders in a terminal state.
     */
    public List<OrderProjection> getCompletedOrders() {
        return stateMachines.values().stream()
                .map(OrderStateMachine::toProjection)
                .filter(p -> p.status().isFinal())
                .toList();
    }

    /**
     * Processes a broker callback event, transitioning the order state machine.
     * Every event is persisted atomically.
     */
        public void onBrokerEvent(OrderEvent event) {
        persistAndApply(event.orderId(), event);
    }

    /**
     * Replays all persisted events into memory. Call on startup to rebuild
     * the in-memory state-machine cache.
     */
    public void replayAll() {
        stateMachines.clear();
        for (String orderId : orderRepository.knownOrderIds()) {
            OrderStateMachine machine = orderRepository.rebuildStateMachine(orderId);
            if (machine != null) {
                stateMachines.put(orderId, machine);
            }
        }
        log.info("Rebuilt {} order state machines from repository", stateMachines.size());
    }

    public Optional<MatchingEngine.MatchResult> lastSimulatedMatch() {
        return Optional.ofNullable(lastSimulatedMatch);
    }

    public void activateKillSwitch() {
        brokerConnection.orders().setKillSwitch(true);
    }

    public void deactivateKillSwitch() {
        brokerConnection.orders().setKillSwitch(false);
    }

    /**
     * Captures a snapshot of all in-memory order state machines.
     * Used by {@link com.tradej.app.pipeline.IsolatedReplayStateManager} for AD-02 isolation.
     */
    public StateSnapshot snapshot() {
        return new StateSnapshot(
                Map.copyOf(stateMachines),
                lastSimulatedMatch);
    }

    /**
     * Restores in-memory order state machines from a previously captured snapshot.
     */
    public void restore(StateSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        stateMachines.clear();
        stateMachines.putAll(snapshot.stateMachines());
        this.lastSimulatedMatch = snapshot.lastSimulatedMatch();
        log.debug("OrderManagementService state restored: {} machines", stateMachines.size());
    }

    public record StateSnapshot(
            Map<String, OrderStateMachine> stateMachines,
            MatchingEngine.MatchResult lastSimulatedMatch) {
    }

    // --- Internal helpers ---

    private void persistAndApply(String orderId, OrderEvent event) {
        orderRepository.append(event);
        stateMachines.compute(orderId, (id, existing) -> {
            if (existing == null) {
                if (event instanceof OrderSubmitted submitted) {
                    OrderStateMachine sm = new OrderStateMachine(
                            orderId, submitted.symbol(), submitted.totalQuantity());
                    sm.on(event);
                    return sm;
                }
                log.warn("First event for order {} was not OrderSubmitted: {}", orderId, event.type());
                return null;
            }
            existing.on(event);
            return existing;
        });
    }

    private static Order legacySimulatedOpenOrder(OrderRequest request, TradingClock clock) {
        return new Order(
                "SIM-" + UUID.randomUUID(),
                request.correlationId(),
                request.symbol(),
                request.exchangeSegment(),
                request.side(),
                request.productType(),
                request.orderType(),
                OrderStatus.OPEN,
                request.quantity(),
                0L,
                request.pricePaisa(),
                request.triggerPricePaisa(),
                clock.millis(),
                null
        );
    }
}
