package com.tradej.brokergateway.simulation;

import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.core.domain.instrument.IndexSymbols;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.model.Quote;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulated market data provider for paper trading and backtesting.
 * Returns deterministic prices with small random jitter around base prices.
 * Historical candles are generated synthetically.
 */
public final class SimulatedMarketDataProvider implements MarketDataProvider {

    private final Map<String, Long> basePricesPaisa;

    public SimulatedMarketDataProvider() {
        this(Map.of(
            "RELIANCE", 250_000L, "TCS", 380_000L, "HDFCBANK", 165_000L,
            "INFY", 155_000L, "SBIN", 75_000L,
            IndexSymbols.NIFTY, 2400_000L,
            IndexSymbols.NIFTY_BANK, 5100_000L,
            IndexSymbols.NIFTY_FIN_SERVICE, 2300_000L
        ));
    }

    public SimulatedMarketDataProvider(Map<String, Long> basePricesPaisa) {
        this.basePricesPaisa = new ConcurrentHashMap<>(basePricesPaisa);
    }

    @Override
    public long getLtpPaisa(InstrumentKey key) {
        return jitter(basePrice(key.symbol()));
    }

    @Override
    public Quote getQuote(InstrumentKey key) {
        long base = basePrice(key.symbol());
        long ltp = jitter(base);
        Instrument inst = toInstrument(key);
        return new Quote(inst, ltp, jitter(base), jitter(base) + base / 100,
                jitter(base) - base / 100, base,
                randomLong(100_000, 500_000), randomLong(10_000, 50_000),
                randomLong(10_000, 50_000), randomLong(50_000, 200_000),
                System.currentTimeMillis());
    }

    @Override
    public MarketDepth getDepth(InstrumentKey key) {
        long base = basePrice(key.symbol());
        var bids = new ArrayList<DepthLevel>();
        var asks = new ArrayList<DepthLevel>();
        for (int i = 0; i < 5; i++) {
            bids.add(new DepthLevel(jitter(base) - (i + 1) * 50L, randomLong(100, 5000), i + 1));
            asks.add(new DepthLevel(jitter(base) + (i + 1) * 50L, randomLong(100, 5000), i + 1));
        }
        return new MarketDepth(toInstrument(key), bids, asks, 5, System.currentTimeMillis());
    }

    @Override
    public Quote getOhlcSnapshot(InstrumentKey key) { return getQuote(key); }

    @Override
    public List<Candle> getCandles(CandleHistoryRequest request) {
        long base = basePrice(request.instrument().symbol());
        List<Candle> candles = new ArrayList<>();
        long ts = System.currentTimeMillis() - 30L * 24 * 3600_000;
        for (int i = 0; i < 30; i++) {
            long open = jitter(base);
            long close = jitter(base);
            long high = Math.max(open, close) + base / 200;
            long low = Math.min(open, close) - base / 200;
            candles.add(new Candle(request.instrument().symbol(), request.interval(),
                    ts, ts + 86_400_000L, open, high, low, close,
                    randomLong(100_000, 1_000_000), true));
            ts += 86_400_000L;
        }
        return candles;
    }

    @Override
    public Map<InstrumentKey, Long> getLtpBatch(Collection<InstrumentKey> keys) {
        Map<InstrumentKey, Long> result = new HashMap<>();
        keys.forEach(k -> result.put(k, getLtpPaisa(k)));
        return result;
    }

    @Override
    public Map<InstrumentKey, Quote> getQuoteBatch(Collection<InstrumentKey> keys) {
        Map<InstrumentKey, Quote> result = new HashMap<>();
        keys.forEach(k -> result.put(k, getQuote(k)));
        return result;
    }

    @Override
    public Map<InstrumentKey, Quote> getOhlcBatch(Collection<InstrumentKey> keys) {
        return getQuoteBatch(keys);
    }

    // Allow updating base prices for simulation scenarios
    public void setBasePrice(String symbol, long pricePaisa) {
        basePricesPaisa.put(symbol, pricePaisa);
    }

    private long basePrice(String symbol) {
        return basePricesPaisa.getOrDefault(IndexSymbols.canonicalize(symbol), 100_000L);
    }

    private long jitter(long base) {
        double pct = ThreadLocalRandom.current().nextDouble(-0.005, 0.005);
        return base + (long)(base * pct);
    }

    private long randomLong(long min, long max) {
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    private static Instrument toInstrument(InstrumentKey key) {
        return new Instrument(key.symbol(), key.symbol(),
                key.exchangeSegment().exchange(), key.exchangeSegment(),
                "EQ", null, null, null, null, 1L, 5L);
    }
}
