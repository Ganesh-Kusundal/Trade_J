package com.tradej.broker.dhan.options;

import com.tradej.core.domain.value.OptionType;
import com.tradej.core.domain.value.StrikeSelectionKind;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class StrikeSelectionSupportTest {
    private static final List<Long> STRIKES = List.of(3_000_000L, 3_010_000L, 3_020_000L);

    @Test
    void selectsAtmNearestToSpot() {
        long atm = StrikeSelectionSupport.selectFromSortedStrikes(
                STRIKES,
                3_012_000L,
                OptionType.CALL,
                StrikeSelectionKind.ATM,
                0
        );
        assertEquals(3_010_000L, atm);
    }

    @Test
    void selectsOtmAndItmCallStrikes() {
        long spot = 3_012_000L;
        long otm = StrikeSelectionSupport.selectFromSortedStrikes(
                STRIKES, spot, OptionType.CALL, StrikeSelectionKind.OTM, 1);
        long itm = StrikeSelectionSupport.selectFromSortedStrikes(
                STRIKES, spot, OptionType.CALL, StrikeSelectionKind.ITM, 1);
        assertEquals(3_020_000L, otm);
        assertEquals(3_000_000L, itm);
    }

    @Test
    void clampsOtmDepthAtUpperBound() {
        long strike = StrikeSelectionSupport.selectFromSortedStrikes(
                STRIKES, 3_010_000L, OptionType.CALL, StrikeSelectionKind.OTM, 99);
        assertEquals(3_020_000L, strike);
    }

    @Test
    void rejectsEmptyStrikeList() {
        assertThrows(IllegalArgumentException.class, () -> StrikeSelectionSupport.selectFromSortedStrikes(
                List.of(), 3_010_000L, OptionType.CALL, StrikeSelectionKind.ATM, 0));
    }
}
