package com.tradej.broker.upstox.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.upstox.historical.UpstoxHistoricalDataService;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import com.tradej.broker.upstox.rest.UpstoxHistoricalDataRestClient;
import com.tradej.broker.upstox.rest.UpstoxMarketDataRestClient;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.Quote;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class UpstoxMarketDataProvider implements MarketDataProvider {

    private final UpstoxMarketDataRestClient restClient;
    private final UpstoxInstrumentResolver instrumentResolver;
    private final UpstoxHistoricalDataService historicalDataService;

    public UpstoxMarketDataProvider(UpstoxMarketDataRestClient restClient,
                                    UpstoxInstrumentResolver instrumentResolver,
                                    UpstoxHistoricalDataRestClient historicalRestClient) {
        this.restClient = restClient;
        this.instrumentResolver = instrumentResolver;
        this.historicalDataService = new UpstoxHistoricalDataService(historicalRestClient, instrumentResolver);
    }

    private String upstoxKey(InstrumentKey k) {
        return instrumentResolver.requireInstrumentKey(k);
    }

    private String joinKeys(Collection<InstrumentKey> keys) {
        StringBuilder sb = new StringBuilder();
        for (InstrumentKey k : keys) {
            if (!sb.isEmpty()) {
                sb.append(",");
            }
            sb.append(upstoxKey(k));
        }
        return sb.toString();
    }

    @Override
    public long getLtpPaisa(InstrumentKey instrumentKey) {
        String key = upstoxKey(instrumentKey);
        JsonNode root = restClient.getLtp(key);
        JsonNode data = root.get("data");
        if (data != null && data.has(key)) {
            return UpstoxPriceParser.pricePaisaFromNode(data.get(key));
        }
        if (data != null) {
            String symbolKey = instrumentKey.exchangeSegment().name() + ":" + instrumentKey.symbol();
            if (data.has(symbolKey)) {
                return UpstoxPriceParser.pricePaisaFromNode(data.get(symbolKey));
            }
        }
        throw new IllegalStateException("Upstox LTP response missing price for " + instrumentKey);
    }

    @Override
    public Quote getQuote(InstrumentKey instrumentKey) {
        JsonNode root = restClient.getQuote(upstoxKey(instrumentKey));
        return parseQuote(root, instrumentKey);
    }

    @Override
    public MarketDepth getDepth(InstrumentKey instrumentKey) {
        JsonNode root = restClient.getDepth(upstoxKey(instrumentKey));
        return parseDepth(root, instrumentKey);
    }

    @Override
    public Quote getOhlcSnapshot(InstrumentKey instrumentKey) {
        JsonNode root = restClient.getOhlc(upstoxKey(instrumentKey));
        return parseQuote(root, instrumentKey);
    }

    @Override
    public List<Candle> getCandles(CandleHistoryRequest request) {
        return historicalDataService.fetchCandles(request);
    }

    @Override
    public Map<InstrumentKey, Long> getLtpBatch(Collection<InstrumentKey> instrumentKeys) {
        Map<InstrumentKey, Long> result = new HashMap<>();
        String keys = joinKeys(instrumentKeys);
        JsonNode root = restClient.getLtp(keys);
        JsonNode data = root.get("data");
        if (data != null) {
            for (InstrumentKey key : instrumentKeys) {
                String uk = upstoxKey(key);
                JsonNode entry = data.get(uk);
                if (entry != null) {
                    result.put(key, UpstoxPriceParser.pricePaisaFromNode(entry));
                }
            }
        }
        return result;
    }

    @Override
    public Map<InstrumentKey, Quote> getQuoteBatch(Collection<InstrumentKey> instrumentKeys) {
        Map<InstrumentKey, Quote> result = new HashMap<>();
        String keys = joinKeys(instrumentKeys);
        JsonNode root = restClient.getQuote(keys);
        JsonNode data = root.get("data");
        if (data != null) {
            for (InstrumentKey key : instrumentKeys) {
                String uk = upstoxKey(key);
                JsonNode entry = data.get(uk);
                if (entry != null) {
                    result.put(key, parseQuoteFromNode(entry, key));
                }
            }
        }
        return result;
    }

    @Override
    public Map<InstrumentKey, Quote> getOhlcBatch(Collection<InstrumentKey> instrumentKeys) {
        Map<InstrumentKey, Quote> result = new HashMap<>();
        String keys = joinKeys(instrumentKeys);
        JsonNode root = restClient.getOhlc(keys);
        JsonNode data = root.get("data");
        if (data != null) {
            for (InstrumentKey key : instrumentKeys) {
                String uk = upstoxKey(key);
                JsonNode entry = data.get(uk);
                if (entry != null) {
                    result.put(key, parseQuoteFromNode(entry, key));
                }
            }
        }
        return result;
    }

    private Quote parseQuote(JsonNode root, InstrumentKey key) {
        JsonNode data = root.get("data");
        if (data != null) {
            String uk = upstoxKey(key);
            JsonNode entry = data.get(uk);
            if (entry != null) {
                return parseQuoteFromNode(entry, key);
            }
        }
        throw new IllegalStateException("Upstox quote response missing data for " + key);
    }

    private Quote parseQuoteFromNode(JsonNode node, InstrumentKey key) {
        return new Quote(instrument(key), UpstoxPriceParser.optionalPricePaisa(node, "last_price"), UpstoxPriceParser.optionalPricePaisa(node, "open"), UpstoxPriceParser.optionalPricePaisa(node, "high"), UpstoxPriceParser.optionalPricePaisa(node, "low"), UpstoxPriceParser.optionalPricePaisa(node, "close"), node.has("volume") ? node.get("volume").asLong() : 0L, 0L, 0L, 0L, System.currentTimeMillis());
    }

    private MarketDepth parseDepth(JsonNode root, InstrumentKey key) {
        JsonNode data = root.get("data");
        if (data == null) {
            return null;
        }
        List<DepthLevel> bids = new ArrayList<>();
        List<DepthLevel> asks = new ArrayList<>();
        JsonNode depth = data.get("depth");
        if (depth != null) {
            JsonNode bidLevels = depth.get("bid");
            if (bidLevels != null) {
                for (JsonNode level : bidLevels) {
                    bids.add(new DepthLevel(
                            UpstoxPriceParser.optionalPricePaisa(level, "price"),
                            level.get("quantity").asLong(),
                            level.get("orders").asInt()
                    ));
                }
            }
            JsonNode askLevels = depth.get("ask");
            if (askLevels != null) {
                for (JsonNode level : askLevels) {
                    asks.add(new DepthLevel(
                            UpstoxPriceParser.optionalPricePaisa(level, "price"),
                            level.get("quantity").asLong(),
                            level.get("orders").asInt()
                    ));
                }
            }
        }
        return new MarketDepth(instrument(key), bids, asks,
                Math.max(bids.size(), asks.size()),
                System.currentTimeMillis());
    }

    private Instrument instrument(InstrumentKey key) {
        Instrument inst = instrumentResolver.resolve(key);
        return inst != null ? inst : new Instrument(key.symbol(), key.symbol(),
                null, key.exchangeSegment(), "EQ", key.symbol(), null, null, null, 1L, 5L);
    }
}
