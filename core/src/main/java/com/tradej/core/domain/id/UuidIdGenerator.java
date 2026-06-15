package com.tradej.core.domain.id;

import java.util.UUID;

/**
 * Default {@link IdGenerator} that uses {@link UUID#randomUUID()} for all ID types.
 * Suitable for LIVE mode where uniqueness across distributed instances matters more
 * than determinism.
 */
public final class UuidIdGenerator implements IdGenerator {

    @Override
    public String generateSignalId() {
        return "SIG-" + UUID.randomUUID();
    }

    @Override
    public String generateOrderId() {
        return "ORD-" + UUID.randomUUID();
    }

    @Override
    public String generateTradeId() {
        return "TRD-" + UUID.randomUUID();
    }

    @Override
    public String generateFillId() {
        return "FILL-" + UUID.randomUUID();
    }

    @Override
    public String generateEventId() {
        return "EVT-" + UUID.randomUUID();
    }
}
