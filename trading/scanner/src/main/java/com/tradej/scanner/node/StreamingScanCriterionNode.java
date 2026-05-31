package com.tradej.scanner.node;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.ScanHitProduced;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.pipeline.runtime.BasePipelineNode;
import com.tradej.scanner.criterion.ScanCriterion;
import com.tradej.scanner.criterion.StreamingScanCriterion;
import com.tradej.scanner.model.ScanAsset;
import com.tradej.scanner.model.ScanContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pipeline node that wraps a single {@link ScanCriterion} and evaluates it
 * reactively against incoming events.
 * <p>
 * If the criterion implements {@link StreamingScanCriterion}, it receives every
 * matching event for incremental evaluation. Otherwise, the criterion is evaluated
 * in snapshot mode against the latest cached quote/candle data.
 * <p>
 * Emits {@link ScanHitProduced} events when the criterion matches.
 */
public final class StreamingScanCriterionNode extends BasePipelineNode {

    private final ScanCriterion criterion;
    private final ScanAsset asset;
    private final String profileId;
    private final Map<String, Object> latestSnapshot;

    // For snapshot-mode evaluation: cache latest data per symbol
    private final ConcurrentHashMap<String, ScanContext> latestContext = new ConcurrentHashMap<>();

    public StreamingScanCriterionNode(ScanCriterion criterion, ScanAsset asset, String profileId) {
        this.criterion = Objects.requireNonNull(criterion);
        this.asset = Objects.requireNonNull(asset);
        this.profileId = profileId;
        this.latestSnapshot = new LinkedHashMap<>();
        latestSnapshot.put("profileId", profileId);
        latestSnapshot.put("criterionType", criterion.type());
    }

    @Override
    protected void onInit() {
        // no-op
    }

    @Override
    protected void processEvent(DomainEvent event) {
        if (criterion instanceof StreamingScanCriterion streaming) {
            // no-op
            // Check subscription
            boolean subscribed = streaming.subscribedEventTypes().isEmpty();
            if (!subscribed) {
                for (var type : streaming.subscribedEventTypes()) {
                    if (type.isInstance(event)) {
                        subscribed = true;
                        break;
                    }
                }
            }
            if (!subscribed) {
                return;
            }

            String symbol = symbolOf(event);
            if (symbol.isEmpty()) {
                return;
            }

            ScanContext ctx = buildContext(symbol, event);
            streaming.onEvent(event, ctx);
            if (criterion.matches(ctx)) {
                emitHit(ctx, streaming.score(ctx));
            }
        } else {
            // Snapshot mode: update cached data and re-evaluate
            String symbol = symbolOf(event);
            if (symbol.isEmpty()) {
                return;
            }
            updateSnapshot(symbol, event);
            ScanContext ctx = latestContext.get(symbol);
            if (ctx != null && criterion.matches(ctx)) {
                emitHit(ctx, criterion.score(ctx));
            }
        }
    }

    /**
     * Builds or retrieves a ScanContext for the given symbol.
     * For streaming criteria, the context is built fresh each time from
     * the latest cached data.
     */
    private ScanContext buildContext(String symbol, DomainEvent event) {
        ScanContext existing = latestContext.get(symbol);
        if (existing != null) {
            return existing;
        }
        ScanContext fresh = new ScanContext(asset, null, null, List.of());
        latestContext.put(symbol, fresh);
        return fresh;
    }

    /**
     * Updates the snapshot cache for a symbol. In production, this would
     * extract quote/candle data from the event and build a ScanContext with
     * actual market data. Currently creates a minimal context.
     */
    private void updateSnapshot(String symbol, DomainEvent event) {
        latestContext.put(symbol, new ScanContext(asset, null, null, List.of()));
    }

    private void emitHit(ScanContext ctx, double score) {
        context.publish(new ScanHitProduced(
                EventMetadata.correlated("", 0),
                profileId,
                criterion.type(),
                asset.key() != null ? asset.key().symbol() : "",
                asset.key() != null ? asset.key().exchangeSegment().name() : "",
                asset.assetClass() != null ? asset.assetClass().name() : "",
                asset.underlying() != null ? asset.underlying() : "",
                score,
                List.of(criterion.reason(ctx)),
                latestSnapshot
        ));
    }

    private static String symbolOf(DomainEvent event) {
        return com.tradej.core.support.MdcHelper.symbolOf(event).orElse("");
    }
}
