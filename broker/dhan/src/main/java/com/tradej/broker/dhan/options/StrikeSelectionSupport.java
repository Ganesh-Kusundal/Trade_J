package com.tradej.broker.dhan.options;

import com.tradej.core.domain.value.OptionType;
import com.tradej.core.domain.value.StrikeSelectionKind;

import java.util.List;

/**
 * Pure strike-selection logic shared by {@link com.tradej.broker.dhan.adapter.DhanOptionsAdapter}.
 */
public final class StrikeSelectionSupport {
    private StrikeSelectionSupport() {
    }

    public static long selectFromSortedStrikes(
            List<Long> strikes,
            long spotPricePaisa,
            OptionType optionType,
            StrikeSelectionKind selectionKind,
            int depth
    ) {
        if (strikes == null || strikes.isEmpty()) {
            throw new IllegalArgumentException("Strikes list must not be empty");
        }
        int atmIndex = nearestStrikeIndex(strikes, spotPricePaisa);
        if (selectionKind == StrikeSelectionKind.ATM) {
            return strikes.get(atmIndex);
        }
        int shift = Math.max(depth, 1);
        boolean call = optionType == OptionType.CALL;
        int targetIndex = switch (selectionKind) {
            case OTM -> call ? atmIndex + shift : atmIndex - shift;
            case ITM -> call ? atmIndex - shift : atmIndex + shift;
            case ATM -> atmIndex;
        };
        targetIndex = Math.max(0, Math.min(targetIndex, strikes.size() - 1));
        return strikes.get(targetIndex);
    }

    static int nearestStrikeIndex(List<Long> strikes, long spotPricePaisa) {
        int bestIndex = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int index = 0; index < strikes.size(); index++) {
            long distance = Math.abs(strikes.get(index) - spotPricePaisa);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = index;
            }
        }
        return bestIndex;
    }
}
