package com.tradej.brokergateway.explorer;

import com.tradej.brokergateway.BrokerHandle;
import com.tradej.core.domain.value.ExchangeSegment;

/**
 * Inspects a broker by making live calls to each capability and measuring latency.
 *
 * <p>Complements {@link BrokerExplorer} (static capability checks via getCapability())
 * with actual live probes that verify the broker can execute each operation.
 */
public interface BrokerInspector {

    /**
     * Inspect a broker using the given symbol and segment for market data probes.
     *
     * @param broker  the broker handle to inspect
     * @param symbol  symbol for market data probes (e.g. "RELIANCE")
     * @param segment exchange segment (e.g. NSE_EQ)
     * @return inspection report with both static capabilities and live probe results
     */
    BrokerInspectionReport inspect(BrokerHandle broker, String symbol, ExchangeSegment segment);

    /**
     * Inspect a broker using default symbol (RELIANCE) and segment (NSE_EQ).
     */
    default BrokerInspectionReport inspect(BrokerHandle broker) {
        return inspect(broker, "RELIANCE", ExchangeSegment.NSE_EQ);
    }
}
