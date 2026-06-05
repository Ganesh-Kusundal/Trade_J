package com.tradej.broker.dhan.validator;

import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.dhan.adapter.DhanInstrumentResolver;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import com.tradej.broker.dhan.exceptions.DhanValidationException;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.mapper.DhanApiConverters;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.OrderPreview;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates Dhan order requests against safety rules and exchange requirements.
 *
 * <p>Implements the safety rules from the DhanHQ skill reference:
 * <ul>
 *   <li>Segment × ProductType compatibility matrix</li>
 *   <li>Lot size validation for F&O instruments</li>
 *   <li>Notional value limits (default ₹50,000)</li>
 *   <li>Default to LIMIT orders for safety</li>
 *   <li>Market order LTP requirement for notional calculation</li>
 * </ul>
 *
 * <p>Can operate in two modes:
 * <ul>
 *   <li><b>Strict mode</b> (default): throws {@link DhanValidationException} on ERROR-level issues</li>
 *   <li><b>Warn mode</b>: logs warnings but allows orders through</li>
 * </ul>
 */
public final class DhanOrderValidator {
    private static final Logger log = LoggerFactory.getLogger(DhanOrderValidator.class);

    // Segment × ProductType compatibility matrix (per DhanHQ API docs)
    private static final Map<ExchangeSegment, Set<ProductType>> ALLOWED_PRODUCT_TYPES = Map.of(
            ExchangeSegment.NSE_EQ, Set.of(ProductType.INTRADAY, ProductType.CNC, ProductType.MARGIN),
            ExchangeSegment.BSE_EQ, Set.of(ProductType.INTRADAY, ProductType.CNC, ProductType.MARGIN),
            ExchangeSegment.NSE_FNO, Set.of(ProductType.INTRADAY, ProductType.MARGIN),
            ExchangeSegment.BSE_FNO, Set.of(ProductType.INTRADAY, ProductType.MARGIN),
            ExchangeSegment.MCX_COMM, Set.of(ProductType.INTRADAY, ProductType.MARGIN),
            ExchangeSegment.NSE_CURRENCY, Set.of(ProductType.INTRADAY, ProductType.MARGIN),
            ExchangeSegment.BSE_CURRENCY, Set.of(ProductType.INTRADAY, ProductType.MARGIN),
            ExchangeSegment.IDX_I, Set.of(ProductType.INTRADAY, ProductType.MARGIN)
    );

    // Segments that are F&O/commodity/currency (no CNC/MTF allowed)
    private static final Set<ExchangeSegment> FNO_COMMODITY_CURRENCY_SEGMENTS = Set.of(
            ExchangeSegment.NSE_FNO,
            ExchangeSegment.BSE_FNO,
            ExchangeSegment.MCX_COMM,
            ExchangeSegment.NSE_CURRENCY,
            ExchangeSegment.BSE_CURRENCY,
            ExchangeSegment.IDX_I
    );

    private final DhanInstrumentResolver instrumentResolver;
    private final DhanConnectionSettings settings;
    private final MarketDataProvider marketDataProvider;
    private final boolean strictMode;

    public DhanOrderValidator(
            DhanInstrumentResolver instrumentResolver,
            DhanConnectionSettings settings,
            MarketDataProvider marketDataProvider
    ) {
        this(instrumentResolver, settings, marketDataProvider, true);
    }

    public DhanOrderValidator(
            DhanInstrumentResolver instrumentResolver,
            DhanConnectionSettings settings,
            MarketDataProvider marketDataProvider,
            boolean strictMode
    ) {
        this.instrumentResolver = instrumentResolver;
        this.settings = settings;
        this.marketDataProvider = marketDataProvider;
        this.strictMode = strictMode;
    }

    /**
     * Validate an order request and return a preview with validation results.
     *
     * @param request the order request to validate
     * @return OrderPreview with estimated notional, margin, and validation issues
     */
    public OrderPreview previewOrder(OrderRequest request) {
        List<OrderPreview.ValidationIssue> issues = new ArrayList<>();

        // 1. Resolve instrument
        DhanInstrumentDefinition instrument;
        try {
            instrument = instrumentResolver.requireDhanDefinition(request.symbol(), request.exchangeSegment());
        } catch (IllegalArgumentException ex) {
            issues.add(new OrderPreview.ValidationIssue(
                    OrderPreview.ValidationIssue.Severity.ERROR,
                    DhanValidationException.ValidationCode.INSTRUMENT_NOT_FOUND.name(),
                    "Instrument not found: " + ex.getMessage(),
                    "symbol",
                    request.symbol(),
                    "Verify the symbol and exchange segment are correct"
            ));
            return OrderPreview.invalid(
                    request.symbol(),
                    request.exchangeSegment(),
                    request.side(),
                    request.quantity(),
                    request.pricePaisa(),
                    request.triggerPricePaisa(),
                    request.productType(),
                    0L,
                    0L,
                    issues
            );
        }

        // 2. Validate product type for segment
        validateProductType(instrument, request.productType(), issues);

        // 3. Validate lot size for F&O
        validateLotSize(instrument, request.quantity(), issues);

        // 4. Determine price for notional calculation
        long priceForNotional = determinePriceForNotional(request, instrument, issues);

        // 5. Calculate notional and validate
        long notionalPaisa = request.quantity() * priceForNotional;
        validateNotional(notionalPaisa, issues);

        // 6. Estimate margin (simplified - real implementation would call margin API)
        long estimatedMarginPaisa = estimateMargin(instrument, request, priceForNotional);

        // 7. Validate order type (warn if MARKET)
        validateOrderType(request.orderType(), issues);

        boolean valid = issues.stream().noneMatch(i -> i.severity() == OrderPreview.ValidationIssue.Severity.ERROR);

        if (valid) {
            return OrderPreview.valid(
                    request.symbol(),
                    request.exchangeSegment(),
                    request.side(),
                    request.quantity(),
                    request.pricePaisa(),
                    request.triggerPricePaisa(),
                    request.productType(),
                    notionalPaisa,
                    estimatedMarginPaisa,
                    issues
            );
        } else {
            return OrderPreview.invalid(
                    request.symbol(),
                    request.exchangeSegment(),
                    request.side(),
                    request.quantity(),
                    request.pricePaisa(),
                    request.triggerPricePaisa(),
                    request.productType(),
                    notionalPaisa,
                    estimatedMarginPaisa,
                    issues
            );
        }
    }

    /**
     * Validate an order request, throwing exception on errors if in strict mode.
     *
     * @param request the order request to validate
     * @throws DhanValidationException if validation fails and strict mode is enabled
     */
    public void validateOrThrow(OrderRequest request) {
        OrderPreview preview = previewOrder(request);
        if (!preview.valid() && strictMode) {
            var firstError = preview.errors().get(0);
            throw new DhanValidationException(
                    DhanValidationException.ValidationCode.valueOf(firstError.code()),
                    firstError.message(),
                    firstError.field(),
                    firstError.rejectedValue(),
                    firstError.suggestion()
            );
        }
        if (!preview.valid()) {
            // Log all errors in warn mode
            for (var error : preview.errors()) {
                log.warn("Order validation error (warn mode): {}", error.message());
            }
        }
        // Log warnings in both modes
        for (var warn : preview.warnings()) {
            log.warn("Order validation warning: {}", warn.message());
        }
    }

    /**
     * Validate product type compatibility with exchange segment.
     */
    private void validateProductType(
            DhanInstrumentDefinition instrument,
            ProductType productType,
            List<OrderPreview.ValidationIssue> issues
    ) {
        ExchangeSegment segment = instrument.exchangeSegment();
        Set<ProductType> allowed = ALLOWED_PRODUCT_TYPES.getOrDefault(segment, Set.of());

        if (!allowed.contains(productType)) {
            String msg = String.format(
                    "Product type %s not allowed for segment %s. Allowed: %s",
                    productType, segment, allowed
            );
            issues.add(new OrderPreview.ValidationIssue(
                    OrderPreview.ValidationIssue.Severity.ERROR,
                    DhanValidationException.ValidationCode.INVALID_PRODUCT_TYPE_FOR_SEGMENT.name(),
                    msg,
                    "productType",
                    productType.name(),
                    "Use " + allowed + " for " + segment
            ));
        }

        // Extra check: CNC/MTF explicitly forbidden for F&O/commodity/currency
        if (FNO_COMMODITY_CURRENCY_SEGMENTS.contains(segment)
                && (productType == ProductType.CNC || productType == ProductType.CARRY_FORWARD)) {
            String msg = String.format(
                    "Product type %s is not allowed for F&O/commodity/currency segment %s. Use INTRADAY or MARGIN.",
                    productType, segment
            );
            issues.add(new OrderPreview.ValidationIssue(
                    OrderPreview.ValidationIssue.Severity.ERROR,
                    DhanValidationException.ValidationCode.INVALID_PRODUCT_TYPE_FOR_SEGMENT.name(),
                    msg,
                    "productType",
                    productType.name(),
                    "Use INTRADAY or MARGIN for " + segment
            ));
        }
    }

    /**
     * Validate quantity is a multiple of lot size for F&O instruments.
     */
    private void validateLotSize(
            DhanInstrumentDefinition instrument,
            long quantity,
            List<OrderPreview.ValidationIssue> issues
    ) {
        long lotSize = instrument.lotSize();
        if (lotSize > 1 && quantity % lotSize != 0) {
            String msg = String.format(
                    "Quantity %d is not a multiple of lot size %d for %s",
                    quantity, lotSize, instrument.canonicalSymbol()
            );
            issues.add(new OrderPreview.ValidationIssue(
                    OrderPreview.ValidationIssue.Severity.ERROR,
                    DhanValidationException.ValidationCode.INVALID_LOT_SIZE.name(),
                    msg,
                    "quantity",
                    quantity,
                    "Adjust quantity to a multiple of " + lotSize
            ));
        }
    }

    /**
     * Determine the price to use for notional calculation.
     * For LIMIT orders: use limit price.
     * For MARKET orders: fetch live LTP from market data provider.
     */
    private long determinePriceForNotional(
            OrderRequest request,
            DhanInstrumentDefinition instrument,
            List<OrderPreview.ValidationIssue> issues
    ) {
        if (request.orderType().name().contains("LIMIT")) {
            if (request.pricePaisa() <= 0) {
                issues.add(new OrderPreview.ValidationIssue(
                        OrderPreview.ValidationIssue.Severity.ERROR,
                        DhanValidationException.ValidationCode.MARKET_ORDER_REQUIRES_LTP.name(),
                        "LIMIT order requires a positive price",
                        "pricePaisa",
                        request.pricePaisa(),
                        "Provide a valid limit price"
                ));
                return 0L;
            }
            return request.pricePaisa();
        }

        // MARKET order - fetch live LTP from market data provider
        InstrumentKey instrumentKey = instrument.key();
        try {
            long ltpPaisa = marketDataProvider.getLtpPaisa(instrumentKey);
            if (ltpPaisa <= 0) {
                issues.add(new OrderPreview.ValidationIssue(
                        OrderPreview.ValidationIssue.Severity.ERROR,
                        DhanValidationException.ValidationCode.MARKET_ORDER_REQUIRES_LTP.name(),
                        "MARKET order requires live LTP for notional calculation, but LTP is unavailable for " + instrumentKey,
                        "orderType",
                        request.orderType().name(),
                        "Ensure market data feed is connected and instrument is subscribed, or use LIMIT order"
                ));
                return 0L;
            }
            return ltpPaisa;
        } catch (Exception ex) {
            issues.add(new OrderPreview.ValidationIssue(
                    OrderPreview.ValidationIssue.Severity.ERROR,
                    DhanValidationException.ValidationCode.MARKET_ORDER_REQUIRES_LTP.name(),
                    "Failed to fetch LTP for MARKET order notional: " + ex.getMessage(),
                    "orderType",
                    request.orderType().name(),
                    "Ensure market data feed is connected, or use LIMIT order"
            ));
            return 0L;
        }
    }

    /**
     * Validate notional value against configured limit.
     */
    private void validateNotional(
            long notionalPaisa,
            List<OrderPreview.ValidationIssue> issues
    ) {
        long maxNotional = DhanProtocolConstants.MAX_NOTIONAL_PAISA;
        if (notionalPaisa > maxNotional) {
            String msg = String.format(
                    "Order notional ₹%.2f exceeds limit ₹%.2f",
                    notionalPaisa / 100.0,
                    maxNotional / 100.0
            );
            issues.add(new OrderPreview.ValidationIssue(
                    OrderPreview.ValidationIssue.Severity.WARN,
                    DhanValidationException.ValidationCode.NOTIONAL_EXCEEDS_LIMIT.name(),
                    msg,
                    "notionalPaisa",
                    notionalPaisa,
                    "Consider reducing quantity or using a lower limit price"
            ));
        }
    }

    /**
     * Validate order type - warn on MARKET, default to LIMIT for safety.
     */
    private void validateOrderType(
            com.tradej.core.domain.value.OrderType orderType,
            List<OrderPreview.ValidationIssue> issues
    ) {
        if (orderType == com.tradej.core.domain.value.OrderType.MARKET) {
            issues.add(new OrderPreview.ValidationIssue(
                    OrderPreview.ValidationIssue.Severity.WARN,
                    DhanValidationException.ValidationCode.ORDER_TYPE_DEFAULTED_TO_LIMIT.name(),
                    "MARKET orders carry execution risk; consider LIMIT orders for price control",
                    "orderType",
                    "MARKET",
                    "Use LIMIT order with appropriate price for better execution control"
            ));
        }
    }

    /**
     * Simplified margin estimation.
     * Real implementation would call Dhan margin calculator API.
     */
    private long estimateMargin(
            DhanInstrumentDefinition instrument,
            OrderRequest request,
            long pricePaisa
    ) {
        // Simplified: margin ~ notional * marginFactor
        // For INTRADAY: ~20-25% of notional
        // For CNC: ~100% of notional (no leverage)
        // For MARGIN: ~25-30% of notional
        double marginFactor = switch (request.productType()) {
            case CNC -> 1.0;
            case INTRADAY -> 0.25;
            case MARGIN -> 0.30;
            case CARRY_FORWARD -> 0.50; // NRML
        };
        long notional = request.quantity() * pricePaisa;
        return (long) (notional * marginFactor);
    }

    /**
     * Check if strict mode is enabled.
     */
    public boolean isStrictMode() {
        return strictMode;
    }
}