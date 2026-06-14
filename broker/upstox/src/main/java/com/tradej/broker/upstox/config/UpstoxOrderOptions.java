package com.tradej.broker.upstox.config;

/**
 * Broker-local options for the Upstox V3 order endpoints.
 * <p>
 * These fields are not part of the cross-broker domain model; they apply only
 * to Upstox's HFT v3 place/modify order payload and the {@code X-Algo-Name}
 * header. They are exposed as a record so the upstream caller can override
 * defaults without the broker module pulling in new fields on
 * {@code OrderRequest} (cross-module change).
 *
 * @param slice              enable Upstox server-side order slicing. Default {@code true}
 *                           so large orders are auto-split into exchange-compliant chunks
 * @param marketProtection   market protection percentage per V3 docs:
 *                           <ul>
 *                             <li>{@code -1} = automatic (default; safe)</li>
 *                             <li>{@code 0} = none (rejected by exchange for MARKET orders)</li>
 *                             <li>{@code 1}..{@code 25} = custom percentage</li>
 *                           </ul>
 *                           Ignored for LIMIT and SL orders per V3 docs
 * @param disclosedQuantity  volume to display in the market depth; must be &ge; 10% of
 *                           the total order quantity per V3 docs
 * @param algoName           SEBI-registered algo name. When non-null/non-blank, sent as
 *                           the {@code X-Algo-Name} HTTP header on place/modify calls
 */
public record UpstoxOrderOptions(
        boolean slice,
        int marketProtection,
        long disclosedQuantity,
        String algoName
) {

    /** Default options: slicing on, automatic market protection, no disclosed qty, no algo name. */
    public static final UpstoxOrderOptions DEFAULTS = new UpstoxOrderOptions(true, -1, 0L, null);

    public UpstoxOrderOptions {
        if (marketProtection < -1 || marketProtection > 25) {
            throw new IllegalArgumentException(
                    "marketProtection must be -1 (auto), 0 (none), or 1..25 (custom), got: " + marketProtection);
        }
        if (disclosedQuantity < 0) {
            throw new IllegalArgumentException("disclosedQuantity must be non-negative, got: " + disclosedQuantity);
        }
        if (algoName != null && algoName.isBlank()) {
            algoName = null;
        }
    }

    /** Returns a copy with server-side slicing disabled. */
    public UpstoxOrderOptions withSlicingDisabled() {
        return new UpstoxOrderOptions(false, marketProtection, disclosedQuantity, algoName);
    }

    /** Returns a copy with the given algo name (or {@code null} to clear). */
    public UpstoxOrderOptions withAlgoName(String name) {
        return new UpstoxOrderOptions(slice, marketProtection, disclosedQuantity,
                (name == null || name.isBlank()) ? null : name);
    }

    /** Returns a copy with the given disclosed quantity. */
    public UpstoxOrderOptions withDisclosedQuantity(long qty) {
        return new UpstoxOrderOptions(slice, marketProtection, qty, algoName);
    }
}
