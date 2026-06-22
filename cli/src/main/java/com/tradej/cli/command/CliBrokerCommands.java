package com.tradej.cli.command;

import com.tradej.cli.CliContext;
import com.tradej.cli.output.OutputFormatter;
import com.tradej.cli.output.TablePrinter;
import com.tradej.broker.api.capability.BrokerCapabilityRouter;
import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.core.domain.instrument.RollingExpiryKind;
import com.tradej.core.domain.instrument.RollingExpiryRoll;
import com.tradej.core.domain.instrument.RollingOptionSeriesKey;
import com.tradej.core.domain.instrument.StandardInstrumentIdentityService;
import com.tradej.core.domain.instrument.StrikeOffset;
import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.LivePnlSnapshot;
import com.tradej.core.domain.model.MarginEstimate;
import com.tradej.core.domain.model.MarginEstimateRequest;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.OptionChainEntry;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderPreview;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.Position;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.model.RollingOptionHistoryRequest;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.StrikeSelectionKind;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CliBrokerCommands extends CliCommandSupport {

    public CliBrokerCommands(CliContext context, OutputFormatter out) {
        super(context, out);
    }

    public void balance() {
        Balance balance = portfolio().getBalance();
        if (context().json()) {
            out().print(balance);
            return;
        }
        out().println("Cash:         " + balance.cashPaisa() + " paisa");
        out().println("Utilized:     " + balance.utilizedPaisa() + " paisa");
        out().println("Withdrawable: " + balance.withdrawablePaisa() + " paisa");
    }

    public void holdings() {
        List<Holding> holdings = portfolio().getHoldings();
        if (context().json()) {
            out().print(holdings);
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (Holding holding : holdings) {
            rows.add(new String[]{
                    holding.symbol(),
                    String.valueOf(holding.totalQuantity()),
                    String.valueOf(holding.averagePricePaisa())
            });
        }
        TablePrinter.print(new String[]{"Symbol", "Qty", "AvgPaisa"}, rows);
    }

    public void brokerPositions() {
        List<Position> positions = portfolio().getPositions();
        if (context().json()) {
            out().print(positions);
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (Position position : positions) {
            rows.add(new String[]{
                    position.symbol(),
                    position.exchangeSegment().name(),
                    String.valueOf(position.quantity()),
                    String.valueOf(position.averagePricePaisa()),
                    String.valueOf(position.lastPricePaisa())
            });
        }
        TablePrinter.print(new String[]{"Symbol", "Segment", "Qty", "Avg", "LTP"}, rows);
    }

    public void refreshCatalog(boolean forceRefresh) {
        Path path = session().refreshInstrumentCatalog(forceRefresh);
        int size = session().catalogSize();
        if (context().json()) {
            out().print(Map.of(
                    "catalogPath", path.toString(),
                    "instrumentCount", size,
                    "forceRefresh", forceRefresh,
                    "profile", context().profile().name()
            ));
            return;
        }
        out().println("Instrument catalog refreshed.");
        out().println("  Path:        " + path);
        out().println("  Instruments: " + size);
        out().println("  Profile:     " + context().profile());
        out().println("  Force:       " + forceRefresh);
    }

    public void ltp(String symbol, String segmentName) {
        session().ensureCatalogLoaded();
        InstrumentKey key = instrument(symbol, segmentName);
        long ltp = marketData().getLtpPaisa(key);
        if (context().json()) {
            out().print(Map.of("symbol", symbol, "segment", segmentName, "ltpPaisa", ltp));
        } else {
            out().println(symbol + " " + segmentName + " LTP = " + ltp + " paisa");
        }
    }

    public void quote(String symbol, String segmentName) {
        session().ensureCatalogLoaded();
        Quote quote = marketData().getQuote(instrument(symbol, segmentName));
        out().print(quote);
    }

    public void depth(String symbol, String segmentName) {
        session().ensureCatalogLoaded();
        MarketDepth depth = marketData().getDepth(instrument(symbol, segmentName));
        out().print(depth);
    }

    public void ohlc(String symbol, String segmentName) {
        session().ensureCatalogLoaded();
        Quote quote = marketData().getOhlcSnapshot(instrument(symbol, segmentName));
        out().print(quote);
    }

    public void candles(String symbol, String segmentName, String interval, LocalDate from, LocalDate to) {
        session().ensureCatalogLoaded();
        List<Candle> candles = marketData().getCandles(new CandleHistoryRequest(
                instrument(symbol, segmentName), interval, from, to));
        if (context().json()) {
            out().print(candles);
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (Candle candle : candles) {
            rows.add(new String[]{
                    formatMs(candle.startTimeMs()),
                    String.valueOf(candle.openPaisa()),
                    String.valueOf(candle.highPaisa()),
                    String.valueOf(candle.lowPaisa()),
                    String.valueOf(candle.closePaisa()),
                    String.valueOf(candle.volume())
            });
        }
        TablePrinter.print(new String[]{"Start", "Open", "High", "Low", "Close", "Vol"}, rows);
    }

    public void orderBook() {
        List<Order> orders = orderQuery().getOrderBook();
        if (context().json()) {
            out().print(orders);
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (Order order : orders) {
            rows.add(new String[]{
                    order.orderId(),
                    order.symbol(),
                    order.status().name(),
                    String.valueOf(order.quantity()),
                    String.valueOf(order.pricePaisa())
            });
        }
        TablePrinter.print(new String[]{"OrderId", "Symbol", "Status", "Qty", "Price"}, rows);
    }

    public void trades() {
        List<Trade> trades = orderQuery().getTradeBook();
        out().print(trades);
    }

    public void order(String orderId) {
        Order order = orderQuery().getOrder(orderId);
        out().print(order);
    }

    public void livePnl() {
        session().ensureCatalogLoaded();
        List<Position> positions = portfolio().getPositions();
        if (positions.isEmpty()) {
            out().print(new LivePnlSnapshot(0L, 0L));
            return;
        }
        List<InstrumentKey> keys = positions.stream()
                .map(p -> InstrumentKey.of(p.symbol(), p.exchangeSegment()))
                .toList();
        Map<InstrumentKey, Long> ltps = marketData().getLtpBatch(keys);
        long netPnl = 0L;
        long netQty = 0L;
        for (Position position : positions) {
            InstrumentKey key = InstrumentKey.of(position.symbol(), position.exchangeSegment());
            long last = ltps.getOrDefault(key, position.lastPricePaisa());
            long qty = position.quantity();
            netQty += qty;
            netPnl += (last - position.averagePricePaisa()) * qty;
        }
        out().print(new LivePnlSnapshot(netPnl, netQty));
    }

    public void expiries(String underlying, String segmentName) {
        session().ensureCatalogLoaded();
        List<LocalDate> expiries = options().getExpiries(underlying, parseSegment(segmentName));
        out().print(expiries);
    }

    public void chain(String underlying, String segmentName, LocalDate expiry) {
        session().ensureCatalogLoaded();
        OptionChainSnapshot chain = options().getOptionChain(underlying, parseSegment(segmentName), expiry);
        if (context().json()) {
            out().print(chain);
            return;
        }
        out().println("Underlying " + underlying + " expiry " + expiry + " spot=" + chain.spotPricePaisa());
        out().println("Canonical naming: {UNDERLYING} {dd} {MMM} {STRIKE} CALL|PUT");
        List<String[]> rows = new ArrayList<>();
        for (OptionChainEntry entry : chain.strikes()) {
            String callName = entry.call() == null ? "-" : entry.call().instrument().canonicalSymbol();
            String putName = entry.put() == null ? "-" : entry.put().instrument().canonicalSymbol();
            rows.add(new String[]{
                    String.valueOf(entry.strikePricePaisa()),
                    callName,
                    putName,
                    entry.call() == null ? "-" : String.valueOf(entry.call().ltpPaisa()),
                    entry.put() == null ? "-" : String.valueOf(entry.put().ltpPaisa())
            });
        }
        TablePrinter.print(new String[]{"StrikePaisa", "CallSymbol", "PutSymbol", "CallLTP", "PutLTP"}, rows);
    }

    public void strike(String underlying, String segmentName, String kind, int depth) {
        session().ensureCatalogLoaded();
        ExchangeSegment segment = parseSegment(segmentName);
        long spot;
        try {
            spot = marketData().getLtpPaisa(InstrumentKey.of(underlying, segment));
        } catch (RuntimeException ltpError) {
            List<LocalDate> expiries = options().getExpiries(underlying, segment);
            if (expiries.isEmpty()) {
                throw new IllegalStateException("Unable to resolve spot: no option expiries for " + underlying
                        + " on " + segment + " and LTP lookup failed: " + ltpError.getMessage(), ltpError);
            }
            LocalDate expiry = expiries.getFirst();
            OptionChainSnapshot chain = options().getOptionChain(underlying, segment, expiry);
            spot = chain.spotPricePaisa();
            if (spot <= 0L) {
                throw new IllegalStateException("Unable to resolve spot from option chain for " + underlying
                        + " expiry " + expiry + " after LTP lookup failed: " + ltpError.getMessage(), ltpError);
            }
            out().println("LTP unavailable; using option-chain spot for " + underlying + " expiry " + expiry + ".");
        }
        StrikeSelectionKind selection = StrikeSelectionKind.valueOf(kind.trim().toUpperCase());
        long callStrike = options().selectStrikePaisa(underlying, segment, spot, OptionType.CALL, selection, depth);
        long putStrike = options().selectStrikePaisa(underlying, segment, spot, OptionType.PUT, selection, depth);
        out().print(Map.of(
                "underlying", underlying,
                "spotPaisa", spot,
                "selection", selection.name(),
                "depth", depth,
                "callStrikePaisa", callStrike,
                "putStrikePaisa", putStrike,
                "callStrikeRupees", callStrike / 100L,
                "putStrikeRupees", putStrike / 100L
        ));
    }

    public void margin(
            String symbol,
            String segmentName,
            String side,
            long quantity,
            String productType,
            String orderType,
            long pricePaisa
    ) {
        session().ensureCatalogLoaded();
        MarginEstimate estimate = margin().estimateMargin(new MarginEstimateRequest(
                symbol,
                parseSegment(segmentName),
                Side.valueOf(side.toUpperCase()),
                quantity,
                ProductType.valueOf(productType.toUpperCase()),
                OrderType.valueOf(orderType.toUpperCase()),
                pricePaisa,
                0L
        ));
        out().print(estimate);
    }

    public void previewOrder(
            String symbol,
            String segmentName,
            String side,
            long quantity,
            String orderType,
            long pricePaisa,
            String productType
    ) {
        session().ensureCatalogLoaded();
        OrderRequest request = new OrderRequest(
                symbol,
                parseSegment(segmentName),
                Side.valueOf(side.toUpperCase()),
                quantity,
                OrderType.valueOf(orderType.toUpperCase()),
                pricePaisa,
                0L,
                ProductType.valueOf(productType.toUpperCase()),
                com.tradej.core.domain.value.Validity.DAY,
                null
        );
        OrderPreview preview = orderCommand().previewOrder(request);
        out().print(preview);
    }

    public void rollingOption(
            String underlying,
            String segmentName,
            int intervalMinutes,
            String expiryFlag,
            int expiryCode,
            String strike,
            String optionType,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        String enabled = firstNonBlank(
                System.getenv("DHAN_ROLLING_OPTION_TEST_ENABLED"),
                System.getProperty("dhan.rollingOptionTestEnabled"),
                "false"
        );
        if (!"true".equalsIgnoreCase(enabled)) {
            out().println("Rolling option history is opt-in. Set DHAN_ROLLING_OPTION_TEST_ENABLED=true to enable.");
            return;
        }
        session().ensureCatalogLoaded();
        var seriesKey = new RollingOptionSeriesKey(
                StandardInstrumentIdentityService.INSTANCE.canonicalSymbol(underlying),
                parseSegment(segmentName),
                new RollingExpiryRoll(RollingExpiryKind.fromCode(expiryFlag), expiryCode),
                StrikeOffset.parseSpec(strike),
                OptionType.fromCode(optionType),
                intervalMinutes
        );
        var data = options().getExpiredOptionHistory(new RollingOptionHistoryRequest(
                seriesKey,
                fromDate,
                toDate
        ));
        out().print(data);
    }

    public void batchQuote(String symbol1, String symbol2, String segmentName) {
        session().ensureCatalogLoaded();
        ExchangeSegment segment = parseSegment(segmentName);
        var resolver = session().connection().instruments();
        var keys = List.of(
                resolver.resolveNormalized(symbol1, segment).key(),
                resolver.resolveNormalized(symbol2, segment).key()
        );
        Map<InstrumentKey, Quote> result = marketData().getQuoteBatch(keys);
        for (var entry : result.entrySet()) {
            Quote q = entry.getValue();
            out().println(entry.getKey().symbol() + ": LTP=" + q.ltpPaisa() + " Vol=" + q.volume());
        }
    }

    public void listAlerts() {
        var alerts = BrokerCapabilityRouter.forConnection(session().connection()).find(ConditionalAlertProvider.class);
        if (alerts.isEmpty()) {
            out().println("Alerts not supported by this broker");
            return;
        }
        var list = alerts.get().listAlerts();
        out().println("Alerts: " + list.size());
        for (var alert : list) {
            out().println("  " + alert);
        }
    }

    public void cancelAndSquareOff() {
        var ids = orderCommand().cancelAndSquareOffIntradayPositions();
        out().println("Cancelled and squared off " + ids.size() + " orders");
        for (String id : ids) {
            out().println("  " + id);
        }
    }

    public void bracketOrder(String symbol, String segmentName, String side, long qty, long price, long target, long sl, long trailing) {
        session().ensureCatalogLoaded();
        var bracket = BrokerCapabilityRouter.forConnection(session().connection())
                .find(com.tradej.broker.api.port.BracketOrderProvider.class);
        if (bracket.isEmpty()) { out().println("Bracket orders not supported by this broker"); return; }
        var request = new OrderRequest(symbol, parseSegment(segmentName),
                Side.valueOf(side.toUpperCase()), qty,
                OrderType.LIMIT, price, 0L,
                ProductType.INTRADAY,
                com.tradej.core.domain.value.Validity.DAY, null);
        Order order = bracket.get().placeSuperOrder(request, target, sl, trailing);
        out().println("Bracket order placed: " + order.orderId());
    }

    public void gttOrder(String symbol, String segmentName, String side, long qty, long price, String flag) {
        session().ensureCatalogLoaded();
        var gtt = BrokerCapabilityRouter.forConnection(session().connection())
                .find(com.tradej.broker.api.port.GttOrderProvider.class);
        if (gtt.isEmpty()) { out().println("GTT orders not supported by this broker"); return; }
        var request = new OrderRequest(symbol, parseSegment(segmentName),
                Side.valueOf(side.toUpperCase()), qty,
                OrderType.LIMIT, price, 0L,
                ProductType.CNC,
                com.tradej.core.domain.value.Validity.DAY, null);
        Order order = gtt.get().placeForeverOrder(request, flag, null, null, null);
        out().println("GTT order placed: " + order.orderId());
    }

    public void futuresContracts(String underlying, String segmentName) {
        session().ensureCatalogLoaded();
        var futures = BrokerCapabilityRouter.forConnection(session().connection())
                .find(com.tradej.broker.api.port.FuturesProvider.class);
        if (futures.isEmpty()) { out().println("Futures not supported by this broker"); return; }
        var contracts = futures.get().getContracts(underlying, parseSegment(segmentName));
        out().println("Futures contracts for " + underlying + ": " + contracts.size());
        for (var c : contracts) {
            out().println("  " + c.symbol() + " expiry=" + c.expiry());
        }
    }

    public void healthCheck(String brokerName) {
        out().println("Health check for " + brokerName + ": use 'tradej broker inspect " + brokerName + "'");
    }
}
