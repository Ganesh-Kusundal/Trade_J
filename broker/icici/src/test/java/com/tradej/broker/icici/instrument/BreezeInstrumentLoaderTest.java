package com.tradej.broker.icici.instrument;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("unit")
class BreezeInstrumentLoaderTest {

    @Test
    void parsesQuotedCsvFields() {
        List<String> fields = BreezeSecurityMasterCsv.parseLine(
                "\"2885\",\"RELIND\",\"EQ\",\"RELIANCE INDUSTRIES\",0.01,1,\"\",\"\",0,10,\"INE002A01018\",0,0,1611.8,1290,1611.8,8.19,\"\",\"\",\"\",0,0,0,1485.5,1215.5,\"\",0,\"\",\"\",\"\",0,\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"0\",0,0,0,\"\",\"\",\"\",\"\",\"\",\"RELIANCE\"");
        assertEquals("2885", fields.get(0));
        assertEquals("RELIND", fields.get(1));
        assertEquals("RELIANCE", fields.get(fields.size() - 1));
    }

    @Test
    void loadsRelianceByShortNameAndExchangeAliasFromSecurityMaster() throws Exception {
        Path zip = Path.of("/tmp/SecurityMaster.zip");
        assumeTrue(Files.exists(zip), "Download SecurityMaster.zip to /tmp for loader integration check");

        BreezeInstrumentLoader loader = new BreezeInstrumentLoader();
        var catalog = loader.loadFromPath(zip);

        assertFalse(catalog.isEmpty());
        var byShortName = catalog.get(BreezeInstrumentLoader.key("RELIND", com.tradej.core.domain.value.ExchangeSegment.NSE_EQ));
        var byAlias = catalog.get(BreezeInstrumentLoader.key("RELIANCE", com.tradej.core.domain.value.ExchangeSegment.NSE_EQ));
        assertNotNull(byShortName, "RELIND should resolve from SecurityMaster");
        assertNotNull(byAlias, "RELIANCE alias should resolve to same instrument");
        assertEquals(byShortName, byAlias);
        assertEquals("2885", byShortName.token());
        assertEquals("4.1!2885", byShortName.scriptCode());
        assertEquals("RELIND", byShortName.breezeStockCode());
        assertEquals("RELIANCE", byShortName.tradingSymbol());
        assertEquals("RELIANCE", byShortName.toInstrument().canonicalSymbol());
    }
}
