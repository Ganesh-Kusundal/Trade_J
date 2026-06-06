package com.tradej.cli.command;

import com.tradej.cli.CliContext;
import com.tradej.cli.output.OutputFormatter;
import com.tradej.cli.output.TablePrinter;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.model.Candle;
import com.tradej.persistence.replay.HistoricalRangeService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Standalone historical data commands using direct DuckDB access.
 *
 * <p>All commands query the DuckDB warehouse directly — no {@code trade-app}
 * or Spring required. Falls back gracefully with a descriptive message when
 * the warehouse file is not found.
 */
public final class CliHistoricalCommands extends CliCommandSupport {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(IST);

    /** Default DuckDB warehouse path (matching TradingProperties analytics defaults). */
    private static final String DEFAULT_WAREHOUSE = "runtime-dev/historical.duckdb";

    public CliHistoricalCommands(CliContext context, OutputFormatter out) {
        super(context, out);
    }

    /**
     * `tradej historical candles --symbol RELIANCE --interval 5m --from 1717146000000 --to 1717232400000 --limit 100`
     */
    public void candles(String symbol, String interval, long fromMs, long toMs, int limit) {
        try (HistoricalRangeService svc = openService()) {
            List<Candle> candles = svc.queryCandles(symbol, interval, fromMs, toMs, limit);
            if (candles.isEmpty()) {
                out().println("No candles found for " + symbol + " " + interval + " in the requested range.");
                return;
            }
            if (context().json()) {
                out().print(candles);
                return;
            }
            List<String[]> rows = new ArrayList<>();
            for (Candle c : candles) {
                rows.add(new String[]{
                        FMT.format(Instant.ofEpochMilli(c.startTimeMs())),
                        String.valueOf(c.openPaisa()),
                        String.valueOf(c.highPaisa()),
                        String.valueOf(c.lowPaisa()),
                        String.valueOf(c.closePaisa()),
                        String.valueOf(c.volume())
                });
            }
            TablePrinter.print(new String[]{"Time", "Open", "High", "Low", "Close", "Vol"}, rows);
        } catch (IllegalStateException e) {
            // DuckDB not available — propagate so CliOperations can fall back to attach mode
            throw e;
        } catch (Exception e) {
            out().println("Error querying candles: " + e.getMessage());
        }
    }

    /**
     * `tradej historical ticks --symbol RELIANCE --from 1717146000000 --to 1717232400000 --limit 100`
     */
    public void ticks(String symbol, long fromMs, long toMs, int limit) {
        try (HistoricalRangeService svc = openService()) {
            List<MarketTickEvent> ticks = svc.queryTicks(symbol, fromMs, toMs, limit);
            if (ticks.isEmpty()) {
                out().println("No ticks found for " + symbol + " in the requested range.");
                return;
            }
            if (context().json()) {
                out().print(ticks);
                return;
            }
            out().println("Found " + ticks.size() + " ticks for " + symbol);
            int show = Math.min(10, ticks.size());
            for (int i = 0; i < show; i++) {
                MarketTickEvent t = ticks.get(i);
                out().println("  " + FMT.format(Instant.ofEpochMilli(t.exchangeTimestampEpochMs()))
                        + " ltp=" + t.ltpPaisa() + " qty=" + t.lastTradeQuantity());
            }
            if (ticks.size() > show) {
                out().println("  ... and " + (ticks.size() - show) + " more ticks");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            out().println("Error querying ticks: " + e.getMessage());
        }
    }

    /**
     * `tradej historical stats --symbol RELIANCE --from 1717146000000 --to 1717232400000`
     */
    public void stats(String symbol, long fromMs, long toMs) {
        try (HistoricalRangeService svc = openService()) {
            var stats = svc.rangeStats(symbol, fromMs, toMs);
            if (context().json()) {
                out().print(stats);
                return;
            }
            out().println("=== Range Statistics for " + symbol + " ===");
            out().println("  Range:        " + FMT.format(Instant.ofEpochMilli(fromMs))
                    + " → " + FMT.format(Instant.ofEpochMilli(toMs)));
            out().println("  Ticks:        " + stats.tickCount());
            out().println("  Candles:      " + stats.candleCount());
            out().println("  Orders:       " + stats.orderCount());
            out().println("  Fills:        " + stats.fillCount());
            out().println("  Fill Events:  " + stats.fillEventCount());
            if (stats.tickCount() > 0) {
                out().println("  First Tick:   " + FMT.format(Instant.ofEpochMilli(stats.firstTickMs())));
                out().println("  Last Tick:    " + FMT.format(Instant.ofEpochMilli(stats.lastTickMs())));
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            out().println("Error querying stats: " + e.getMessage());
        }
    }

    /**
     * `tradej historical orders --symbol RELIANCE --from 1717146000000 --to 1717232400000`
     */
    public void orders(String symbol, long fromMs, long toMs, int limit) {
        try (HistoricalRangeService svc = openService()) {
            var orders = svc.queryOrders(symbol, fromMs, toMs, limit);
            if (orders.isEmpty()) {
                out().println("No orders found for " + symbol + " in the requested range.");
                return;
            }
            if (context().json()) {
                out().print(orders);
                return;
            }
            List<String[]> rows = new ArrayList<>();
            for (var o : orders) {
                rows.add(new String[]{o.orderId(), o.symbol(), o.status(),
                        String.valueOf(o.quantity()), String.valueOf(o.pricePaisa())});
            }
            TablePrinter.print(new String[]{"OrderId", "Symbol", "Status", "Qty", "Price"}, rows);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            out().println("Error querying orders: " + e.getMessage());
        }
    }

    /**
     * `tradej historical fills --symbol RELIANCE --from 1717146000000 --to 1717232400000`
     */
    public void fills(String symbol, long fromMs, long toMs, int limit) {
        try (HistoricalRangeService svc = openService()) {
            var fills = svc.queryFills(symbol, fromMs, toMs, limit);
            if (fills.isEmpty()) {
                out().println("No fills found for " + symbol + " in the requested range.");
                return;
            }
            if (context().json()) {
                out().print(fills);
                return;
            }
            List<String[]> rows = new ArrayList<>();
            for (var f : fills) {
                rows.add(new String[]{f.tradeId(), f.orderId(), f.symbol(),
                        String.valueOf(f.quantity()), String.valueOf(f.pricePaisa())});
            }
            TablePrinter.print(new String[]{"TradeId", "OrderId", "Symbol", "Qty", "Price"}, rows);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            out().println("Error querying fills: " + e.getMessage());
        }
    }

    // ── Internal helpers ──

    private HistoricalRangeService openService() {
        Path warehouse = findWarehouse();
        if (!Files.exists(warehouse)) {
            throw new IllegalStateException(
                    "DuckDB warehouse not found at " + warehouse.toAbsolutePath()
                    + ". Run `tradej download start equity` or `tradej analytics query-equity` first.");
        }
        return new HistoricalRangeService(warehouse);
    }

    private Path findWarehouse() {
        // Check default location
        Path defaultPath = Path.of(DEFAULT_WAREHOUSE);
        if (Files.exists(defaultPath)) {
            return defaultPath;
        }
        // Fall back to analytics-configured warehouse
        return defaultPath;
    }
}
