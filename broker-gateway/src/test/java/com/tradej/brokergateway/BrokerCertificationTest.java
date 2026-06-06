package com.tradej.brokergateway;

import com.tradej.brokergateway.certification.BrokerCertification;
import com.tradej.brokergateway.certification.CertificationCheck;
import com.tradej.brokergateway.certification.CertificationReport;
import com.tradej.brokergateway.certification.CertificationStatus;
import com.tradej.brokergateway.result.BrokerSource;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BrokerCertificationTest {

    @Test
    void certificationCheckPassCreatesCorrectRecord() {
        CertificationCheck check = CertificationCheck.pass("ltp", "ltp=75000", Duration.ofMillis(45));

        assertEquals("ltp", check.name());
        assertEquals(CertificationStatus.PASS, check.status());
        assertEquals("ltp=75000", check.evidence());
        assertEquals(45, check.latencyMs());
        assertTrue(check.isPass());
        assertFalse(check.isFail());
        assertNull(check.errorMessage());
    }

    @Test
    void certificationCheckFailCreatesCorrectRecord() {
        CertificationCheck check = CertificationCheck.fail("depth", "Connection timeout", Duration.ofMillis(5000));

        assertEquals("depth", check.name());
        assertEquals(CertificationStatus.FAIL, check.status());
        assertEquals("Connection timeout", check.errorMessage());
        assertFalse(check.isPass());
        assertTrue(check.isFail());
    }

    @Test
    void certificationCheckSkipCreatesCorrectRecord() {
        CertificationCheck check = CertificationCheck.skip("websocket", "Not supported");

        assertEquals(CertificationStatus.SKIP, check.status());
        assertEquals("Not supported", check.evidence());
    }

    @Test
    void certificationReportComputesPassFailCounts() {
        List<CertificationCheck> checks = List.of(
                CertificationCheck.pass("ltp", "ltp=75000", Duration.ofMillis(45)),
                CertificationCheck.pass("quote", "ltp=75000", Duration.ofMillis(52)),
                CertificationCheck.fail("depth", "timeout", Duration.ofMillis(5000)),
                CertificationCheck.skip("websocket", "not supported")
        );

        CertificationReport report = new CertificationReport(
                BrokerSource.DHAN, checks, CertificationStatus.PARTIAL, Duration.ofMillis(5097));

        assertEquals(2, report.passed());
        assertEquals(1, report.failed());
        assertEquals(1, report.skipped());
        assertEquals(4, report.total());
        assertEquals(CertificationStatus.PARTIAL, report.overall());
        assertFalse(report.isPass());
    }

    @Test
    void certificationReportFormatsHumanReadableOutput() {
        List<CertificationCheck> checks = List.of(
                CertificationCheck.pass("ltp", "ltp=75000", Duration.ofMillis(45)),
                CertificationCheck.fail("depth", "timeout", Duration.ofMillis(5000))
        );

        CertificationReport report = new CertificationReport(
                BrokerSource.DHAN, checks, CertificationStatus.PARTIAL, Duration.ofMillis(5045));

        String formatted = report.formatReport();

        assertNotNull(formatted);
        assertTrue(formatted.contains("DHAN"));
        assertTrue(formatted.contains("ltp"));
        assertTrue(formatted.contains("depth"));
        assertTrue(formatted.contains("PARTIAL"));
        assertTrue(formatted.contains("1/2 passed"));
    }

    @Test
    void allPassReportIsPass() {
        List<CertificationCheck> checks = List.of(
                CertificationCheck.pass("ltp", "ltp=75000", Duration.ofMillis(45)),
                CertificationCheck.pass("quote", "ok", Duration.ofMillis(52))
        );

        CertificationReport report = new CertificationReport(
                BrokerSource.DHAN, checks, CertificationStatus.PASS, Duration.ofMillis(97));

        assertTrue(report.isPass());
        assertEquals(2, report.passed());
        assertEquals(0, report.failed());
    }
}
