package com.tradej.broker.dhan.config;

import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.broker.dhan.DhanBrokerConnection;

/**
 * Hardcoded Dhan broker capabilities.
 *
 * @deprecated Use {@link DhanBrokerConnection#defaultCapabilities()} or
 *             {@link DhanBrokerStartup#defaultCapabilities()} instead. This
 *             class is retained only for backward compatibility and will be
 *             removed in a future release.
 */
@Deprecated
public final class DhanBrokerCapabilities {
    private DhanBrokerCapabilities() {
    }

    /**
     * @deprecated Use {@link DhanBrokerConnection#defaultCapabilities()}.
     */
    @Deprecated
    public static BrokerCapabilities live() {
        return DhanBrokerConnection.defaultCapabilities();
    }
}
