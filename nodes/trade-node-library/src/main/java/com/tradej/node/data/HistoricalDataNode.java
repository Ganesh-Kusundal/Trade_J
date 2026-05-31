package com.tradej.node.data;

import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.RollingOptionBar;
import com.tradej.core.domain.model.RollingOptionSeriesRequest;
import com.tradej.core.domain.port.HistoricalBarRepository;
import com.tradej.core.domain.port.RollingOptionHistoricalRepository;
import com.tradej.core.domain.value.AssetClass;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.node.NodeCategory;
import com.tradej.node.NodeContext;
import com.tradej.node.NodeDescriptor;
import com.tradej.node.NodeExecutor;
import com.tradej.node.NodeResult;
import com.tradej.node.PortDescriptor;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Produces historical market data for equity candles or rolling option bars. */
public class HistoricalDataNode implements NodeExecutor {

    public static final String NODE_TYPE = "historical-data";
    public static final String REPOSITORY_KEY = "historicalBarRepository";
    public static final String OPTION_REPOSITORY_KEY = "rollingOptionHistoricalRepository";

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Override
    public NodeDescriptor descriptor() {
        return new NodeDescriptor(
                NODE_TYPE,
                "Historical Data",
                "Loads historical OHLCV bars for equity or rolling option series",
                NodeCategory.DATA_INGEST,
                List.of(
                        new com.tradej.pipeline.platform.model.PropertyDescriptor(
                                "assetClass",
                                com.tradej.pipeline.platform.model.PropertyDescriptor.PropertyType.STRING,
                                "Asset Class",
                                "EQUITY or ROLLING_OPTION",
                                AssetClass.EQUITY.name(),
                                false,
                                List.of(),
                                Map.of()
                        ),
                        new com.tradej.pipeline.platform.model.PropertyDescriptor(
                                "symbol",
                                com.tradej.pipeline.platform.model.PropertyDescriptor.PropertyType.STRING,
                                "Symbol",
                                "Trading symbol or option underlying",
                                null,
                                true,
                                List.of(com.tradej.pipeline.platform.model.ValidationRule.required("Symbol is required")),
                                Map.of()
                        ),
                        new com.tradej.pipeline.platform.model.PropertyDescriptor(
                                "startDate",
                                com.tradej.pipeline.platform.model.PropertyDescriptor.PropertyType.STRING,
                                "Start Date",
                                "ISO-8601 start date",
                                null,
                                true,
                                List.of(),
                                Map.of()
                        ),
                        new com.tradej.pipeline.platform.model.PropertyDescriptor(
                                "endDate",
                                com.tradej.pipeline.platform.model.PropertyDescriptor.PropertyType.STRING,
                                "End Date",
                                "ISO-8601 end date",
                                null,
                                true,
                                List.of(),
                                Map.of()
                        ),
                        new com.tradej.pipeline.platform.model.PropertyDescriptor(
                                "interval",
                                com.tradej.pipeline.platform.model.PropertyDescriptor.PropertyType.STRING,
                                "Interval",
                                "Candle interval (1m, 5m, 1d, ...) for equity",
                                "1m",
                                false,
                                List.of(),
                                Map.of()
                        ),
                        new com.tradej.pipeline.platform.model.PropertyDescriptor(
                                "expiryKind",
                                com.tradej.pipeline.platform.model.PropertyDescriptor.PropertyType.STRING,
                                "Expiry Kind",
                                "WEEK or MONTH for rolling options",
                                "WEEK",
                                false,
                                List.of(),
                                Map.of()
                        ),
                        new com.tradej.pipeline.platform.model.PropertyDescriptor(
                                "expiryCode",
                                com.tradej.pipeline.platform.model.PropertyDescriptor.PropertyType.NUMBER,
                                "Expiry Code",
                                "Rolling expiry code (1, 2, ...)",
                                1,
                                false,
                                List.of(),
                                Map.of()
                        ),
                        new com.tradej.pipeline.platform.model.PropertyDescriptor(
                                "strikeOffset",
                                com.tradej.pipeline.platform.model.PropertyDescriptor.PropertyType.NUMBER,
                                "Strike Offset",
                                "ATM offset for rolling options",
                                0,
                                false,
                                List.of(),
                                Map.of()
                        ),
                        new com.tradej.pipeline.platform.model.PropertyDescriptor(
                                "optionType",
                                com.tradej.pipeline.platform.model.PropertyDescriptor.PropertyType.STRING,
                                "Option Type",
                                "CALL or PUT",
                                "CALL",
                                false,
                                List.of(),
                                Map.of()
                        ),
                        new com.tradej.pipeline.platform.model.PropertyDescriptor(
                                "intervalMin",
                                com.tradej.pipeline.platform.model.PropertyDescriptor.PropertyType.NUMBER,
                                "Option Interval Min",
                                "Rolling option bar interval in minutes",
                                5,
                                false,
                                List.of(),
                                Map.of()
                        )
                ),
                List.of(),
                List.of(new PortDescriptor(
                        "bars", "OHLCV bars", PortDescriptor.PortType.MULTI, true,
                        List.of("MarketBar"), Map.of()
                )),
                Map.of("family", "data")
        );
    }

    @Override
    public NodeResult execute(NodeContext context, Map<String, Object> config) {
        AssetClass assetClass = AssetClass.valueOf(String.valueOf(
                config.getOrDefault("assetClass", AssetClass.EQUITY.name())));
        String symbol = String.valueOf(config.get("symbol"));
        LocalDate startDate = LocalDate.parse(String.valueOf(config.get("startDate")));
        LocalDate endDate = LocalDate.parse(String.valueOf(config.get("endDate")));
        if (assetClass == AssetClass.ROLLING_OPTION) {
            RollingOptionHistoricalRepository repository = resolveOptionRepository(context);
            long fromMs = startDate.atStartOfDay(IST).toInstant().toEpochMilli();
            long toMs = endDate.plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli();
            List<RollingOptionBar> bars = repository.queryBars(new RollingOptionSeriesRequest(
                    symbol,
                    String.valueOf(config.getOrDefault("expiryKind", "WEEK")),
                    intValue(config.get("expiryCode"), 1),
                    intValue(config.get("strikeOffset"), 0),
                    OptionType.fromCode(String.valueOf(config.getOrDefault("optionType", "CALL"))),
                    intValue(config.get("intervalMin"), 5),
                    fromMs,
                    toMs,
                    50_000
            ));
            return NodeResult.ok("bars", bars.stream().map(this::toOptionBarMap).toList());
        }
        HistoricalBarRepository repository = resolveRepository(context);
        String interval = config.containsKey("interval")
                ? String.valueOf(config.get("interval"))
                : "1m";
        List<Candle> candles = repository.queryCandles(
                InstrumentKey.of(symbol, ExchangeSegment.NSE_EQ),
                interval,
                startDate,
                endDate
        );
        List<Map<String, Object>> bars = candles.stream().map(this::toBarMap).toList();
        return NodeResult.ok("bars", bars);
    }

    private HistoricalBarRepository resolveRepository(NodeContext context) {
        Object repository = context.sharedState().get(REPOSITORY_KEY);
        if (repository instanceof HistoricalBarRepository historicalBarRepository) {
            return historicalBarRepository;
        }
        throw new IllegalStateException("HistoricalBarRepository not available in node shared state");
    }

    private RollingOptionHistoricalRepository resolveOptionRepository(NodeContext context) {
        Object repository = context.sharedState().get(OPTION_REPOSITORY_KEY);
        if (repository instanceof RollingOptionHistoricalRepository rollingOptionHistoricalRepository) {
            return rollingOptionHistoricalRepository;
        }
        throw new IllegalStateException("RollingOptionHistoricalRepository not available in node shared state");
    }

    private Map<String, Object> toBarMap(Candle candle) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("symbol", candle.symbol());
        map.put("interval", candle.interval());
        map.put("startTimeMs", candle.startTimeMs());
        map.put("endTimeMs", candle.endTimeMs());
        map.put("openPaisa", candle.openPaisa());
        map.put("highPaisa", candle.highPaisa());
        map.put("lowPaisa", candle.lowPaisa());
        map.put("closePaisa", candle.closePaisa());
        map.put("volume", candle.volume());
        map.put("closed", candle.closed());
        return map;
    }

    private Map<String, Object> toOptionBarMap(RollingOptionBar bar) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("timestampMs", bar.timestampMs());
        map.put("openPaisa", bar.openPaisa());
        map.put("highPaisa", bar.highPaisa());
        map.put("lowPaisa", bar.lowPaisa());
        map.put("closePaisa", bar.closePaisa());
        map.put("volume", bar.volume());
        map.put("iv", bar.iv());
        map.put("oi", bar.oi());
        map.put("spotPaisa", bar.spotPaisa());
        map.put("strikePaisa", bar.strikePaisa());
        return map;
    }

    private static int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
