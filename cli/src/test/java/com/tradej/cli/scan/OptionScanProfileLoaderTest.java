package com.tradej.cli.scan;

import com.tradej.scanner.model.ScanProfile;
import com.tradej.scanner.option.OptionExpiryPolicy;
import com.tradej.scanner.option.OptionSideFilter;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionScanProfileLoaderTest {

    @Test
    void loadsOptionLiquidityProfileFromJson() throws Exception {
        ScanProfile profile = ScanProfileJsonLoader.load(
                Path.of("config/scan-profiles.json"),
                "option-liquidity"
        );

        assertTrue(profile.isOptionLiquidityProfile());
        assertNotNull(profile.optionScan());
        assertEquals(OptionExpiryPolicy.NEAREST, profile.optionScan().expiryPolicy());
        assertEquals(OptionSideFilter.BOTH, profile.optionScan().sides());
        assertEquals(5000L, profile.optionScan().minOpenInterest());
        assertEquals(100L, profile.optionScan().minVolume());
        assertEquals(250.0, profile.optionScan().maxSpreadBps());
        assertEquals(5, profile.optionScan().topNPerUnderlying());
        assertEquals(20, profile.optionScan().topNGlobal());
        assertEquals(3, profile.universe().underlyings().size());
    }
}
