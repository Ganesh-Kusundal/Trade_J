package com.tradej.brokergateway;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.brokergateway.spi.BrokerProvider;
import com.tradej.brokergateway.spi.BrokerRegistry;
import com.tradej.composition.BrokerComposition;
import com.tradej.composition.config.BrokerProfile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Default implementation of {@link BrokerGateway}.
 *
 * <p>Wraps a map of {@link BrokerSource} → {@link BrokerHandle} instances.
 * Created via static factory methods on the {@link BrokerGateway} interface.
 */
public final class DefaultBrokerGateway implements BrokerGateway {

    private final Map<BrokerSource, BrokerHandle> handles;

    DefaultBrokerGateway(Map<BrokerSource, BrokerHandle> handles) {
        this.handles = Map.copyOf(handles);
    }

    // ── Static Factory Methods ──────────────────────────────────────

    static BrokerGateway create(BrokerComposition... compositions) {
        Map<BrokerSource, BrokerHandle> map = new LinkedHashMap<>();
        for (BrokerComposition composition : compositions) {
            BrokerSource source = toSource(composition.profile().brokerType());
            map.put(source, new BrokerHandle(source, composition.brokerConnection()));
        }
        return new DefaultBrokerGateway(map);
    }

    static BrokerGateway of(BrokerSource source, IBrokerConnection connection) {
        return new DefaultBrokerGateway(Map.of(source, new BrokerHandle(source, connection)));
    }

    static BrokerGateway dhan(BrokerProfile.DhanConfig config) {
        BrokerProfile profile = new BrokerProfile(BrokerProfile.BrokerType.DHAN, config, null, null);
        return create(BrokerComposition.create(profile));
    }

    static BrokerGateway fromRegistry(BrokerRegistry registry, BrokerProfile... profiles) {
        Map<BrokerSource, BrokerHandle> handles = new LinkedHashMap<>();
        for (BrokerProfile profile : profiles) {
            BrokerSource source = toSource(profile.brokerType());
            registry.provider(source).ifPresent(provider -> {
                IBrokerConnection conn = provider.connect(profile);
                handles.put(source, new BrokerHandle(source, conn));
            });
        }
        return new DefaultBrokerGateway(handles);
    }

    // ── Instance Methods ────────────────────────────────────────────

    @Override
    public BrokerHandle broker(String name) {
        BrokerSource source = BrokerSource.parse(name);
        return broker(source);
    }

    @Override
    public BrokerHandle broker(BrokerSource source) {
        BrokerHandle handle = handles.get(source);
        if (handle == null) {
            throw new IllegalArgumentException(
                    "Broker '" + source + "' not available. Available: " + handles.keySet());
        }
        return handle;
    }

    @Override
    public Set<BrokerSource> availableBrokers() {
        return handles.keySet();
    }

    @Override
    public boolean hasBroker(String name) {
        try {
            return handles.containsKey(BrokerSource.parse(name));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public BrokerHandle first() {
        return handles.values().iterator().next();
    }

    // ── Internal ────────────────────────────────────────────────────

    private static BrokerSource toSource(BrokerProfile.BrokerType type) {
        return switch (type) {
            case DHAN -> BrokerSource.DHAN;
            case UPSTOX -> BrokerSource.UPSTOX;
            case ICICI -> BrokerSource.ICICI;
            case GATEWAY -> BrokerSource.DHAN;
        };
    }
}
