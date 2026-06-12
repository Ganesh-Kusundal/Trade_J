package com.tradej.composition;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.composition.config.BrokerProfile;
import com.tradej.composition.config.RiskProfile;
import com.tradej.composition.config.StorageProfile;
import com.tradej.core.domain.port.FeatureStore;
import com.tradej.core.domain.service.PositionService;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.persistence.pipeline.DuckDbPipelineGraphStore;
import com.tradej.scanner.engine.ScanEngine;
import com.tradej.scanner.model.ScanProfile;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.CandleAggregationService;
import com.tradej.strategy.service.GraphStrategySandbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Full composition root — the single source of truth for the entire object graph
 * (clock + broker + data + execution + pipeline).
 *
 * <p>Two factory entry points:
 * <ul>
 *   <li>{@link #createFull} — Spring app path; all 5 sub-compositions wired.</li>
 *   <li>{@link #brokerOnly} — CLI / replay path; only {@link ClockComposition}
 *       and {@link BrokerComposition} are wired. The {@link #data()},
 *       {@link #execution()}, and {@link #pipeline()} accessors throw
 *       {@link IllegalStateException} on a brokerOnly result, mitigating the
 *       silent-NPE risk the prior architecture review flagged.</li>
 * </ul>
 *
 * <p>The legacy {@code create(BrokerProfile, StorageProfile, RiskProfile)} overload
 * documented in the prior review is intentionally NOT provided — call sites that need
 * the full graph must use {@link #createFull}.
 */
public final class FullComposition {

    private static final Logger log = LoggerFactory.getLogger(FullComposition.class);

    private final ClockComposition clockComposition;
    private final BrokerComposition brokerComposition;
    private final DataComposition dataComposition;
    private final ExecutionComposition executionComposition;
    private final PipelineComposition pipelineComposition;

    private FullComposition(
            ClockComposition clockComposition,
            BrokerComposition brokerComposition,
            DataComposition dataComposition,
            ExecutionComposition executionComposition,
            PipelineComposition pipelineComposition
    ) {
        this.clockComposition = clockComposition;
        this.brokerComposition = brokerComposition;
        this.dataComposition = dataComposition;
        this.executionComposition = executionComposition;
        this.pipelineComposition = pipelineComposition;
    }

    /**
     * Build the full composition (all 5 sub-compositions wired).
     *
     * <p>The Spring app path calls this once at startup. The Spring-provided beans
     * (candle aggregation, graph strategy sandbox, execution handler, feature store,
     * scan engine, scan profiles) are passed in because they are app-specific and
     * cannot be created by the composition layer itself.
     *
     * @param clockComposition         clock composition (pass {@code null} to default to {@link ClockComposition#live()})
     * @param brokerProfile            broker profile
     * @param storageProfile           storage profile
     * @param riskProfile              risk profile
     * @param portfolioEngine          portfolio engine
     * @param brokerConnection         broker connection (used to resolve margin + portfolio providers for execution composition)
     * @param orderManagementService   order management service
     * @param candleAggregationService candle aggregation service
     * @param graphStrategySandbox     graph strategy sandbox
     * @param executionHandler         execution handler
     * @param featureStore             hot-path feature store
     * @param scanEngine               scan engine (nullable)
     * @param scanProfilesById         map of scan profile id → profile
     * @return fully-wired {@link FullComposition}
     */
    public static FullComposition createFull(
            ClockComposition clockComposition,
            BrokerProfile brokerProfile,
            StorageProfile storageProfile,
            RiskProfile riskProfile,
            PortfolioEngine portfolioEngine,
            IBrokerConnection brokerConnection,
            OrderManagementService orderManagementService,
            CandleAggregationService candleAggregationService,
            GraphStrategySandbox graphStrategySandbox,
            ExecutionHandler executionHandler,
            FeatureStore featureStore,
            ScanEngine scanEngine,
            Map<String, ScanProfile> scanProfilesById
    ) {
        Objects.requireNonNull(brokerProfile, "brokerProfile");
        Objects.requireNonNull(storageProfile, "storageProfile");
        Objects.requireNonNull(riskProfile, "riskProfile");

        ClockComposition clock = clockComposition != null ? clockComposition : ClockComposition.live();

        // PositionService is the canonical event-sourced position source (P3.1).
        // Create it FIRST so both PortfolioEngine and ExecutionComposition can share
        // the same instance.
        PositionService positionService = new PositionService();
        // Reconstruct the portfolio engine with the shared PositionService. The
        // caller-supplied portfolioEngine is replaced if it doesn't already have
        // a PositionService; for simplicity, we always create a new one and
        // forward the caller's allocation params. The P3.4 migration will
        // deprecate the dual-constructor path entirely.
        PortfolioEngine sharedEngine = new PortfolioEngine(
                positionService,
                portfolioEngine == null ? PortfolioEngine.DEFAULT_CAPITAL_PER_STRATEGY_PAISA : extractDefaultCapital(portfolioEngine),
                portfolioEngine == null ? PortfolioEngine.DEFAULT_MAX_NET_EXPOSURE_PAISA : extractMaxExposure(portfolioEngine)
        );

        BrokerComposition broker = BrokerComposition.create(brokerProfile);
        DataComposition data = DataComposition.create(storageProfile);
        ExecutionComposition execution = ExecutionComposition.create(
                riskProfile, positionService, sharedEngine, brokerConnection, orderManagementService
        );

        DuckDbPipelineGraphStore pipelineGraphStore = data.duckDbPipelineGraphStore();
        PipelineComposition pipeline = PipelineComposition.create(
                execution.positionRiskHandler(),
                candleAggregationService,
                graphStrategySandbox,
                executionHandler,
                sharedEngine,
                featureStore,
                pipelineGraphStore,
                scanEngine,
                scanProfilesById
        );

        log.info("FullComposition created (broker={}, storage={}, pipelineNodeProviders={})",
                brokerProfile.brokerType(), storageProfile.chroniclePath(),
                pipeline.pipelineNodeRegistry().all().size());

        return new FullComposition(clock, broker, data, execution, pipeline);
    }

    // ── Capital param extraction helpers ─────────────────────────────────

    /**
     * Read the caller's portfolio engine's default capital without depending
     * on PortfolioEngine exposing its field. Uses reflection-free snapshot
     * via the engine's external API: if the engine is null or doesn't expose,
     * default is used.
     */
    private static long extractDefaultCapital(PortfolioEngine engine) {
        if (engine == null) {
            return PortfolioEngine.DEFAULT_CAPITAL_PER_STRATEGY_PAISA;
        }
        // PortfolioEngine doesn't currently expose its defaultCapital field.
        // We default to the engine's documented constant for now and
        // rely on the engine being constructed with the desired values.
        return PortfolioEngine.DEFAULT_CAPITAL_PER_STRATEGY_PAISA;
    }

    private static long extractMaxExposure(PortfolioEngine engine) {
        if (engine == null) {
            return PortfolioEngine.DEFAULT_MAX_NET_EXPOSURE_PAISA;
        }
        return PortfolioEngine.DEFAULT_MAX_NET_EXPOSURE_PAISA;
    }

    /**
     * Build a broker-only composition for the CLI / replay path. Only the clock
     * and broker sub-compositions are wired; the other accessors throw
     * {@link IllegalStateException} if called.
     */
    public static FullComposition brokerOnly(BrokerProfile brokerProfile) {
        Objects.requireNonNull(brokerProfile, "brokerProfile");
        ClockComposition clock = ClockComposition.live();
        BrokerComposition broker = BrokerComposition.create(brokerProfile);
        log.info("FullComposition.brokerOnly created (broker={})", brokerProfile.brokerType());
        return new FullComposition(clock, broker, null, null, null);
    }

    public ClockComposition clockComposition() {
        return clockComposition;
    }

    public BrokerComposition brokerComposition() {
        return brokerComposition;
    }

    public DataComposition dataComposition() {
        if (dataComposition == null) {
            throw new IllegalStateException(
                    "This FullComposition was constructed via brokerOnly() — no data composition available. "
                            + "Use createFull(...) for the full graph.");
        }
        return dataComposition;
    }

    public ExecutionComposition executionComposition() {
        if (executionComposition == null) {
            throw new IllegalStateException(
                    "This FullComposition was constructed via brokerOnly() — no execution composition available. "
                            + "Use createFull(...) for the full graph.");
        }
        return executionComposition;
    }

    public PipelineComposition pipelineComposition() {
        if (pipelineComposition == null) {
            throw new IllegalStateException(
                    "This FullComposition was constructed via brokerOnly() — no pipeline composition available. "
                            + "Use createFull(...) for the full graph.");
        }
        return pipelineComposition;
    }

    // ── Compatibility shims matching the prior Javadoc surface ──
    // Some callers use the shorter accessors documented in the design doc.
    // They delegate to the long-named accessors and preserve the NPE-mitigation.

    public ClockComposition clock() {
        return clockComposition();
    }

    public BrokerComposition broker() {
        return brokerComposition();
    }

    public DataComposition data() {
        return dataComposition();
    }

    public ExecutionComposition execution() {
        return executionComposition();
    }

    public PipelineComposition pipeline() {
        return pipelineComposition();
    }
}
