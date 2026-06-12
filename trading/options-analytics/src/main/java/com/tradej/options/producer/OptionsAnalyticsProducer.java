package com.tradej.options.producer;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.GammaExposureComputed;
import com.tradej.core.domain.event.GreeksComputed;
import com.tradej.core.domain.event.MaxPainComputed;
import com.tradej.core.domain.event.OptionChainUpdated;
import com.tradej.core.domain.model.OptionGreeks;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.options.calculator.MaxPainCalculator;
import com.tradej.options.surface.VolatilitySurfaceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically fetches the option chain for each configured underlying
 * and publishes the four options events
 * ({@link OptionChainUpdated}, {@link MaxPainComputed},
 * {@link GreeksComputed}, {@link GammaExposureComputed}) onto the bus.
 *
 * <p>Spring-free so that the {@code trading-options-analytics} module
 * remains usable outside the Spring app. Wired and started by
 * {@code app}'s {@code OptionsAnalyticsProducerConfiguration} when
 * {@code trade.options.analytics-enabled=true}.
 */
public class OptionsAnalyticsProducer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OptionsAnalyticsProducer.class);

    private final IBrokerConnection brokerConnection;
    private final EventBus eventBus;
    private final EventMetadataFactory metadataFactory;
    private final VolatilitySurfaceBuilder surfaceBuilder;
    private final List<String> underlyings;
    private final long intervalMs;
    private final ScheduledExecutorService executor;
    private volatile boolean running;

    public OptionsAnalyticsProducer(
            IBrokerConnection brokerConnection,
            EventBus eventBus,
            EventMetadataFactory metadataFactory,
            VolatilitySurfaceBuilder surfaceBuilder,
            List<String> underlyings,
            long intervalMs
    ) {
        this.brokerConnection = Objects.requireNonNull(brokerConnection, "brokerConnection");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.metadataFactory = Objects.requireNonNull(metadataFactory, "metadataFactory");
        this.surfaceBuilder = surfaceBuilder;
        this.underlyings = underlyings == null ? List.of() : List.copyOf(underlyings);
        this.intervalMs = Math.max(1_000L, intervalMs);
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "options-analytics-producer");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        running = true;
        executor.scheduleAtFixedRate(this::poll, 0L, intervalMs, TimeUnit.MILLISECONDS);
        log.info("OptionsAnalyticsProducer started underlyings={} intervalMs={}", underlyings, intervalMs);
    }

    public void poll() {
        if (!running) return;
        for (String underlying : underlyings) {
            try {
                publishFor(underlying);
            } catch (RuntimeException e) {
                log.warn("Options analytics failed for {}: {}", underlying, e.getMessage());
            }
        }
    }

    void publishFor(String underlying) {
        OptionsProvider provider = brokerConnection.getCapability(OptionsProvider.class).orElse(null);
        if (provider == null) return;

        ExchangeSegment segment = ExchangeSegment.NSE_FNO;
        List<LocalDate> expiries = provider.getExpiries(underlying, segment);
        if (expiries == null || expiries.isEmpty()) return;
        LocalDate expiry = expiries.getFirst();

        var chain = provider.getOptionChain(underlying, segment, expiry);
        if (chain == null) return;

        eventBus.publish(new OptionChainUpdated(metadataFactory.root(), chain));

        MaxPainCalculator.MaxPainResult maxPain = MaxPainCalculator.compute(chain);
        eventBus.publish(new MaxPainComputed(
                metadataFactory.root(),
                underlying, expiry,
                maxPain.strikePaisa(),
                maxPain.totalPainPaisa()
        ));

        if (surfaceBuilder != null) {
            var surface = surfaceBuilder.build(chain);
            // Approximate the at-the-money IV by picking the strike
            // closest to spot and reading its IV from the surface map.
            long spot = chain.spotPricePaisa();
            long atmStrikePaisa = findClosestStrike(surface.ivByStrikePaisa().keySet(), spot);
            double atmIv = surface.getIv(atmStrikePaisa);
            OptionGreeks greeks = new OptionGreeks(
                    /* delta  */ null,
                    /* theta  */ null,
                    /* gamma  */ null,
                    /* vega   */ null,
                    Double.isNaN(atmIv) ? null : atmIv
            );
            eventBus.publish(new GreeksComputed(
                    metadataFactory.root(),
                    new InstrumentKey(underlying, segment),
                    greeks
            ));
            // Net gamma is approximated as (number of strikes) * at-the-money IV.
            // A full model would sum BS-gamma per strike × open interest.
            double netGamma = surface.ivByStrikePaisa().size()
                    * (Double.isNaN(atmIv) ? 0.0 : atmIv);
            eventBus.publish(new GammaExposureComputed(
                    metadataFactory.root(),
                    underlying, netGamma,
                    expiry.atStartOfDay(java.time.ZoneId.of("Asia/Kolkata"))
                            .toInstant().toEpochMilli()
            ));
        }
    }

    private static long findClosestStrike(java.util.Set<Long> strikes, long target) {
        long best = 0L;
        long bestDelta = Long.MAX_VALUE;
        for (long s : strikes) {
            long delta = Math.abs(s - target);
            if (delta < bestDelta) {
                bestDelta = delta;
                best = s;
            }
        }
        return best;
    }

    @Override
    public void close() {
        running = false;
        executor.shutdownNow();
        log.info("OptionsAnalyticsProducer stopped");
    }
}
