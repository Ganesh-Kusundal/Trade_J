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

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Realistic simulated market data provider using geometric Brownian motion.
 * Produces OHLCV bars with price continuity, correlated volume, and
 * market-session-aware timestamps (skips weekends for equity).
 */
public final class SimulatedMarketDataProvider implements MarketDataProvider {

    private final Map<String, Long> basePricesPaisa;
    private final Map<String, Long> lastPricesPaisa = new ConcurrentHashMap<>();

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final double DRIFT = 0.0001;
    private static final double VOLATILITY = 0.012;
    private static final long MS_PER_DAY = 86_400_000L;

    public SimulatedMarketDataProvider() {
        this(Map.ofEntries(
            Map.entry("RELIANCE", 250_000L), Map.entry("TCS", 380_000L),
            Map.entry("HDFCBANK", 165_000L), Map.entry("INFY", 155_000L),
            Map.entry("SBIN", 75_000L), Map.entry("ICICIBANK", 101_000L),
            Map.entry(IndexSymbols.NIFTY, 2400_000L),
            Map.entry(IndexSymbols.NIFTY_BANK, 5100_000L),
            Map.entry(IndexSymbols.NIFTY_FIN_SERVICE, 2300_000L),
            Map.entry("GOLD", 7350_000L),
            Map.entry("SILVER", 9200_000L),
            Map.entry("CRUDEOIL", 620_000L),
            Map.entry("NATURALGAS", 28_000L),
            Map.entry("COPPER", 82_000L),
            Map.entry("USDINR", 8_345L),
            Map.entry("EURINR", 9_025L)
        ));
    }

    public SimulatedMarketDataProvider(Map<String, Long> basePricesPaisa) {
        this.basePricesPaisa = new ConcurrentHashMap<>(basePricesPaisa);
        basePricesPaisa.forEach(this.lastPricesPaisa::put);
    }

    @Override
    public long getLtpPaisa(InstrumentKey key) {
        String sym = key.symbol().toUpperCase();
        long last = lastPricesPaisa.getOrDefault(sym, basePrice(sym));
        String exchange = segmentToExchange(key.exchangeSegment().name());
        if (!isMarketOpen(exchange)) {
            return last;
        }
        long newPrice = last + (long)(last * ThreadLocalRandom.current().nextDouble(-0.0003, 0.0003));
        lastPricesPaisa.put(sym, newPrice);
        return newPrice;
    }

    @Override
    public Quote getQuote(InstrumentKey key) {
        long ltp = getLtpPaisa(key);
        long base = basePrice(key.symbol());
        Instrument inst = toInstrument(key);
        long dayHigh = ltp + base / 200;
        long dayLow = ltp - base / 200;
        return new Quote(inst, ltp, ltp, dayHigh, dayLow, base,
                randomLong(1_000_000, 5_000_000), randomLong(100_000, 500_000),
                randomLong(100_000, 500_000), randomLong(500_000, 2_000_000),
                System.currentTimeMillis());
    }

    @Override
    public MarketDepth getDepth(InstrumentKey key) {
        String sym = key.symbol().toUpperCase();
        long ltp = lastPricesPaisa.getOrDefault(sym, basePrice(sym));
        long tickSize = resolveTickSize(sym);

        var bids = new ArrayList<DepthLevel>();
        var asks = new ArrayList<DepthLevel>();
        for (int i = 1; i <= 5; i++) {
            long askPrice = ltp + tickSize * i;
            long bidPrice = ltp - tickSize * i;
            asks.add(new DepthLevel(askPrice, randomLong(10, 500), i));
            bids.add(new DepthLevel(bidPrice, randomLong(10, 500), i));
        }
        // asks already ascending (ltp+tick, ltp+2tick, ...), bids already descending
        return new MarketDepth(toInstrument(key), bids, asks, 5, System.currentTimeMillis());
    }

    @Override
    public Quote getOhlcSnapshot(InstrumentKey key) { return getQuote(key); }

    @Override
    public List<Candle> getCandles(CandleHistoryRequest request) {
        String symbol = request.instrument().symbol();
        String interval = request.interval();
        String segment = request.instrument().exchangeSegment().name();
        boolean isDaily = "1d".equalsIgnoreCase(interval) || "1D".equals(interval);

        // Determine interval in minutes
        int intervalMinutes = switch (interval) {
            case "1m" -> 1;
            case "5m" -> 5;
            case "15m" -> 15;
            case "30m" -> 30;
            case "1h" -> 60;
            case "4h" -> 240;
            default -> 1440; // 1d = 1440 minutes
        };

        // Determine trading hours based on segment
        int openHour, openMinute, closeHour, closeMinute;
        if (segment.contains("MCX")) {
            openHour = 9; openMinute = 0; closeHour = 23; closeMinute = 30;
        } else {
            openHour = 9; openMinute = 15; closeHour = 15; closeMinute = 30;
        }
        int tradingMinutesPerDay = (closeHour * 60 + closeMinute) - (openHour * 60 + openMinute);
        int barsPerDay = tradingMinutesPerDay / intervalMinutes;

        // Calculate bar count
        int barCount;
        if (isDaily) {
            barCount = request.fromDate() != null && request.toDate() != null
                    ? (int) Math.min(750, Math.max(1, ChronoUnit.DAYS.between(request.fromDate(), request.toDate())))
                    : 750;
        } else {
            // Intraday: generate bars for the last N trading days based on interval
            int tradingDays = switch (interval) {
                case "1m" -> 5;
                case "5m" -> 15;
                case "15m" -> 30;
                case "30m" -> 45;
                case "1h" -> 90;
                case "4h" -> 180;
                default -> 5;
            };
            barCount = Math.min(barsPerDay * tradingDays, 2000);
        }

        long seed = basePrice(symbol);
        double basePriceRupees = seed / 100.0;
        double meanReversion = 0.05;
        double vol = resolveVolatility(symbol);
        // Reduce volatility for intraday to avoid unrealistic swings
        double effectiveVol = isDaily ? vol : vol * 0.3;
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        List<Candle> candles = new ArrayList<>();
        LocalDate today = LocalDate.now(IST);
        List<LocalDate> tradingDays = computeTradingDays(today, isDaily ? barCount : barCount / Math.max(barsPerDay, 1) + 1);

        double prevClose = basePriceRupees;

        if (isDaily) {
            // Daily candle generation (existing logic)
            for (int i = 0; i < barCount && i < tradingDays.size(); i++) {
                double open = prevClose;
                double reversion = meanReversion * (basePriceRupees - prevClose) / basePriceRupees;
                double r1 = rng.nextGaussian() * effectiveVol + reversion;
                double close = open * (1 + r1);
                double r2 = Math.abs(rng.nextGaussian()) * effectiveVol * 0.4;
                double r3 = Math.abs(rng.nextGaussian()) * effectiveVol * 0.4;
                double intraHigh = Math.max(open, close) * (1 + r2);
                double intraLow = Math.min(open, close) * (1 - r3);

                long openPaisa = Math.round(open * 100);
                long closePaisa = Math.round(close * 100);
                long highPaisa = Math.max(Math.round(intraHigh * 100), Math.max(openPaisa, closePaisa));
                long lowPaisa = Math.min(Math.round(intraLow * 100), Math.min(openPaisa, closePaisa));

                double absChange = Math.abs(close - open) / open;
                long volume = (long)(500_000 + absChange * 50_000_000 + rng.nextLong(0, 200_000));

                LocalDate day = tradingDays.get(i);
                ZonedDateTime start = day.atTime(openHour, openMinute).atZone(IST);
                ZonedDateTime end = day.atTime(closeHour, closeMinute).atZone(IST);
                long startMs = start.toInstant().toEpochMilli();
                long endMs = end.toInstant().toEpochMilli();

                candles.add(new Candle(symbol, interval, startMs, endMs,
                        openPaisa, highPaisa, lowPaisa, closePaisa, volume, true));

                prevClose = close;
            }
        } else {
            // Intraday candle generation
            long intervalMs = (long) intervalMinutes * 60_000L;
            int barsGenerated = 0;

            for (int dayIdx = 0; dayIdx < tradingDays.size() && barsGenerated < barCount; dayIdx++) {
                LocalDate day = tradingDays.get(dayIdx);
                long dayStartMs = day.atTime(openHour, openMinute).atZone(IST).toInstant().toEpochMilli();
                long dayEndMs = day.atTime(closeHour, closeMinute).atZone(IST).toInstant().toEpochMilli();

                for (long barStartMs = dayStartMs; barStartMs < dayEndMs && barsGenerated < barCount; barStartMs += intervalMs) {
                    long barEndMs = Math.min(barStartMs + intervalMs, dayEndMs);

                    double open = prevClose;
                    double reversion = meanReversion * (basePriceRupees - prevClose) / basePriceRupees;
                    double r1 = rng.nextGaussian() * effectiveVol + reversion;
                    double close = open * (1 + r1);
                    double r2 = Math.abs(rng.nextGaussian()) * effectiveVol * 0.3;
                    double r3 = Math.abs(rng.nextGaussian()) * effectiveVol * 0.3;
                    double intraHigh = Math.max(open, close) * (1 + r2);
                    double intraLow = Math.min(open, close) * (1 - r3);

                    long openPaisa = Math.round(open * 100);
                    long closePaisa = Math.round(close * 100);
                    long highPaisa = Math.max(Math.round(intraHigh * 100), Math.max(openPaisa, closePaisa));
                    long lowPaisa = Math.min(Math.round(intraLow * 100), Math.min(openPaisa, closePaisa));

                    double absChange = Math.abs(close - open) / open;
                    long volume = (long)(5_000 + absChange * 5_000_000 + rng.nextLong(0, 20_000));

                    candles.add(new Candle(symbol, interval, barStartMs, barEndMs,
                            openPaisa, highPaisa, lowPaisa, closePaisa, volume, true));

                    prevClose = close;
                    barsGenerated++;
                }
            }
        }

        String canon = symbol.toUpperCase();
        if (!candles.isEmpty()) {
            lastPricesPaisa.put(canon, candles.get(candles.size() - 1).closePaisa());
        }

        return candles;
    }

    @Override
    public com.tradej.broker.api.model.HistoricalDataCapabilities capabilities() {
        return com.tradej.broker.api.model.HistoricalDataCapabilities.dhanDefaults();
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

    public void setBasePrice(String symbol, long pricePaisa) {
        basePricesPaisa.put(symbol, pricePaisa);
        lastPricesPaisa.put(symbol, pricePaisa);
    }

    public static boolean isMarketOpen() {
        return isMarketOpen("NSE");
    }

    public static boolean isMarketOpen(String exchange) {
        return "OPEN".equals(getMarketState(exchange));
    }

    public static String getMarketState() {
        return getMarketState("NSE");
    }

    public static String getMarketState(String exchange) {
        ZonedDateTime now = ZonedDateTime.now(IST);
        DayOfWeek dow = now.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return "CLOSED";
        int hhmm = now.getHour() * 100 + now.getMinute();

        return switch (exchange.toUpperCase()) {
            case "MCX" -> {
                if (hhmm >= 900 && hhmm < 2330) yield "OPEN";
                yield "CLOSED";
            }
            case "CDS" -> {
                if (hhmm >= 900 && hhmm < 915) yield "PREOPEN";
                if (hhmm >= 915 && hhmm < 1700) yield "OPEN";
                yield "CLOSED";
            }
            case "NFO" -> {
                if (hhmm >= 900 && hhmm < 915) yield "PREOPEN";
                if (hhmm >= 915 && hhmm < 1530) yield "OPEN";
                if (hhmm >= 1530 && hhmm < 1600) yield "AUCTION";
                yield "CLOSED";
            }
            default -> { // NSE, BSE
                if (hhmm >= 900 && hhmm < 915) yield "PREOPEN";
                if (hhmm >= 915 && hhmm < 1530) yield "OPEN";
                if (hhmm >= 1530 && hhmm < 1600) yield "AUCTION";
                yield "CLOSED";
            }
        };
    }

    private List<LocalDate> computeTradingDays(LocalDate end, int count) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate d = end;
        while (days.size() < count) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                days.add(d);
            }
            d = d.minusDays(1);
        }
        Collections.reverse(days);
        return days;
    }

    private double resolveVolatility(String symbol) {
        String s = symbol.toUpperCase();
        if (s.startsWith("GOLD") || s.startsWith("SILVER")) return 0.005;
        if (s.startsWith("CRUDEOIL") || s.startsWith("NATURALGAS")) return 0.015;
        if (s.contains("USDINR") || s.contains("EURINR")) return 0.002;
        if (s.startsWith("NIFTY") || s.startsWith("BANKNIFTY")) return 0.008;
        return 0.012;
    }

    private static String segmentToExchange(String segment) {
        if (segment == null) return "NSE";
        if (segment.startsWith("MCX")) return "MCX";
        if (segment.startsWith("NSE_CURRENCY") || segment.startsWith("BSE_CURRENCY")) return "CDS";
        if (segment.startsWith("NSE_FNO")) return "NFO";
        if (segment.startsWith("BSE")) return "BSE";
        return "NSE";
    }

    private long resolveTickSize(String symbol) {
        String s = symbol.toUpperCase();
        if (s.startsWith("GOLD")) return 100L;        // ₹1.00 per tick
        if (s.startsWith("SILVER")) return 100L;       // ₹1.00 per tick
        if (s.startsWith("CRUDEOIL")) return 10L;      // ₹0.10 per tick
        if (s.startsWith("NATURALGAS")) return 10L;     // ₹0.10 per tick
        if (s.startsWith("COPPER")) return 5L;          // ₹0.05 per tick
        if (s.contains("USDINR") || s.contains("EURINR")) return 1L; // ₹0.0025 ~= 1 paisa
        if (s.startsWith("NIFTY") || s.startsWith("BANKNIFTY")) return 5L;
        return 5L;                                      // ₹0.05 default equity
    }

    private static final Map<String, Long> COMMODITY_PRICES = Map.of(
        "GOLD", 7350_000L, "SILVER", 9200_000L, "CRUDEOIL", 620_000L,
        "NATURALGAS", 28_000L, "COPPER", 82_000L,
        "USDINR", 8_345L, "EURINR", 9_025L
    );

    private long basePrice(String symbol) {
        String key = symbol.toUpperCase();
        Long fromMap = basePricesPaisa.get(key);
        if (fromMap != null) return fromMap;
        Long fromCommodity = COMMODITY_PRICES.get(key);
        if (fromCommodity != null) return fromCommodity;
        return 100_000L;
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
