package com.tradej.broker.upstox.adapter;

import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import com.tradej.broker.upstox.mapper.UpstoxDomainMapper;
import com.tradej.broker.upstox.rest.UpstoxOrderRestClient;
import com.tradej.broker.upstox.rest.UpstoxPortfolioRestClient;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderPreview;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.Side;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UpstoxOrderCommandAdapter implements OrderCommand {

    private static final Logger LOG = LoggerFactory.getLogger(UpstoxOrderCommandAdapter.class);

    private final UpstoxOrderRestClient restClient;
    private final UpstoxPortfolioRestClient portfolioRestClient;
    private final UpstoxDomainMapper mapper;
    private final UpstoxInstrumentResolver instrumentResolver;

    public UpstoxOrderCommandAdapter(
            UpstoxOrderRestClient restClient,
            UpstoxDomainMapper mapper,
            UpstoxInstrumentResolver instrumentResolver
    ) {
        this(restClient, null, mapper, instrumentResolver);
    }

    public UpstoxOrderCommandAdapter(
            UpstoxOrderRestClient restClient,
            UpstoxPortfolioRestClient portfolioRestClient,
            UpstoxDomainMapper mapper,
            UpstoxInstrumentResolver instrumentResolver
    ) {
        this.restClient = restClient;
        this.portfolioRestClient = portfolioRestClient;
        this.mapper = mapper;
        this.instrumentResolver = instrumentResolver;
    }

    @Override
    public Order placeOrder(OrderRequest request) {
        Instrument instrument = instrumentResolver.resolve(
                new InstrumentKey(request.symbol(), request.exchangeSegment()));
        String instrumentKey = instrumentResolver.requireInstrumentKey(
                new InstrumentKey(request.symbol(), request.exchangeSegment()));
        Map<String, Object> payload = mapper.toPlaceOrderPayload(request, instrumentKey);
        var response = restClient.placeOrder(payload);
        return mapper.toOrder(response, request, instrument);
    }

    @Override
    public Order modifyOrder(ModifyOrderRequest request) {
        Instrument instrument = instrumentResolver.resolve(
                new InstrumentKey(request.symbol(), request.exchangeSegment()));
        Map<String, Object> payload = mapper.toModifyOrderPayload(request);
        var response = restClient.modifyOrder(payload);
        return mapper.toOrder(response, null, instrument);
    }

    @Override
    public boolean cancelOrder(String orderId) {
        var response = restClient.cancelOrder(orderId);
        return mapper.isSuccess(response);
    }

    @Override
    public List<String> cancelAllOpenOrders() {
        var orderBook = restClient.getOrderBook();
        List<String> cancelled = new ArrayList<>();
        var orders = orderBook.get("data");            if (orders != null && orders.isArray()) {
                for (var order : orders) {
                String status = order.has("status") ? order.get("status").asText() : "";
                if ("OPEN".equals(status) || "PENDING".equals(status)) {
                    String orderId = order.get("order_id").asText();
                    try {
                        restClient.cancelOrder(orderId);
                        cancelled.add(orderId);
                    } catch (Exception ignored) {}
                }
            }
        }
        return cancelled;
    }

    @Override
    public List<String> cancelAndSquareOffIntradayPositions() {
        List<String> results = new ArrayList<>();
        // 1. Cancel all open orders
        results.addAll(cancelAllOpenOrders());

        // 2. Fetch positions and square off
        if (portfolioRestClient == null) {
            LOG.warn("PortfolioRestClient not available; skipping position square-off");
            return results;
        }
        try {
            var positions = portfolioRestClient.getPositions();
            var data = positions.get("data");
            if (data != null && data.isArray()) {
                for (var pos : data) {
                    long quantity = pos.has("quantity") ? pos.get("quantity").asLong() : 0L;
                    if (quantity == 0) continue;
                    String symbol = pos.has("trading_symbol") ? pos.get("trading_symbol").asText() : "";
                    if (symbol.isBlank()) continue;
                    Side exitSide = quantity > 0 ? Side.SELL : Side.BUY;
                    long absQty = Math.abs(quantity);
                    ExchangeSegment segment = UpstoxDomainMapper.parseSegment(pos);
                    String instrumentKey;
                    try {
                        instrumentKey = instrumentResolver.requireInstrumentKey(new InstrumentKey(symbol, segment));
                    } catch (Exception e) {
                        continue;
                    }
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("instrument_key", instrumentKey);
                    payload.put("quantity", absQty);
                    payload.put("product", "MIS");
                    payload.put("validity", "DAY");
                    payload.put("order_type", "MARKET");
                    payload.put("transaction_type", exitSide == Side.BUY ? "BUY" : "SELL");
                    try {
                        var response = restClient.placeOrder(payload);
                        String orderId = response.has("data") && response.get("data").has("order_id")
                                ? response.get("data").get("order_id").asText() : "";
                        if (!orderId.isBlank()) {
                            results.add(orderId);
                        }
                    } catch (Exception e) {
                        LOG.warn("Failed to square off {}: {}", symbol, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to square off positions: {}", e.getMessage());
        }
        return results;
    }

    @Override
    public boolean setKillSwitch(boolean enabled) {
        // Upstox API does not provide a kill switch endpoint.
        // The platform's web/mobile interface has a "Disable Trading" option under
        // Profile → Settings, but this is not exposed via the public REST API.
        LOG.info("Upstox API does not support kill switch. enabled=" + enabled);
        return false;
    }

    @Override
    public OrderPreview previewOrder(OrderRequest request) {
        // Upstox doesn't have a dedicated preview endpoint; compute a best-effort
        // preview using notional = quantity * pricePaisa and product-type margin rates.
        String instrumentKey = instrumentResolver.requireInstrumentKey(
                new com.tradej.core.domain.model.InstrumentKey(request.symbol(), request.exchangeSegment()));

        // Determine the effective price for notional calculation.
        // For MARKET orders pricePaisa may be 0 — fall back to triggerPricePaisa.
        long effectivePricePaisa = request.pricePaisa();
        if (effectivePricePaisa <= 0) {
            effectivePricePaisa = request.triggerPricePaisa();
        }

        long estimatedNotionalPaisa = request.quantity() * effectivePricePaisa;

        // Estimate margin requirement based on product type.
        long estimatedMarginPaisa = switch (request.productType()) {
            case INTRADAY, INTRADAY_MARGIN -> estimatedNotionalPaisa * 20 / 100;
            case CNC, DELIVERY             -> estimatedNotionalPaisa;
            case MARGIN, MARGIN_FUNDING    -> estimatedNotionalPaisa * 25 / 100;
            case CARRY_FORWARD             -> estimatedNotionalPaisa;
        };

        return OrderPreview.valid(
                request.symbol(),
                request.exchangeSegment(),
                request.side(),
                request.quantity(),
                request.pricePaisa(),
                request.triggerPricePaisa(),
                request.productType(),
                estimatedNotionalPaisa,
                estimatedMarginPaisa
        );
    }
}
