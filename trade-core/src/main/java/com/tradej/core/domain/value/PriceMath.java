package com.tradej.core.domain.value;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PriceMath {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private PriceMath() {
    }

    public static long toPaisa(BigDecimal price) {
        if (price == null) {
            return 0L;
        }
        return price.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static long toPaisa(String price) {
        if (price == null || price.isBlank()) {
            return 0L;
        }
        return toPaisa(new BigDecimal(price));
    }

    public static BigDecimal fromPaisa(long paisa) {
        return BigDecimal.valueOf(paisa, 2);
    }
}
