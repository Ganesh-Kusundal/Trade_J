package com.tradej.execution.risk;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class RiskCheckChainTest {

    @Test
    void allChecksPass_returnsApproved() {
        var chain = new RiskCheckChain(List.of(
                new KillSwitchRiskCheck(),
                new DailyLossRiskCheck(),
                new PositionLimitRiskCheck()
        ));

        var context = new RiskContext("SBIN", 0, 0, 1, false, false, 100_000, 10);

        assertTrue(chain.isApproved(context));
        assertTrue(chain.findRejection(context).isEmpty());
    }

    @Test
    void killSwitchEngaged_rejectsFirst() {
        var chain = new RiskCheckChain(List.of(
                new KillSwitchRiskCheck(),
                new DailyLossRiskCheck(),
                new PositionLimitRiskCheck()
        ));

        var context = new RiskContext("SBIN", 0, 0, 1, true, false, 100_000, 10);

        Optional<RiskVerdict> rejection = chain.findRejection(context);
        assertTrue(rejection.isPresent());
        assertEquals("kill_switch", rejection.get().checkName());
    }

    @Test
    void reconciliationHalt_rejectsFirst() {
        var chain = new RiskCheckChain(List.of(
                new KillSwitchRiskCheck(),
                new DailyLossRiskCheck()
        ));

        var context = new RiskContext("SBIN", 0, 0, 0, false, true, 100_000, 10);

        Optional<RiskVerdict> rejection = chain.findRejection(context);
        assertTrue(rejection.isPresent());
        assertEquals("reconciliation_halt", rejection.get().checkName());
        assertTrue(rejection.get().reason().contains("Reconciliation"));
    }

    @Test
    void dailyLossExceeded_rejects() {
        var chain = new RiskCheckChain(List.of(
                new KillSwitchRiskCheck(),
                new DailyLossRiskCheck()
        ));

        var context = new RiskContext("SBIN", 60_000, 50_000, 1, false, false, 100_000, 10);

        Optional<RiskVerdict> rejection = chain.findRejection(context);
        assertTrue(rejection.isPresent());
        assertEquals("daily_loss", rejection.get().checkName());
    }

    @Test
    void positionLimitReached_rejects() {
        var chain = new RiskCheckChain(List.of(
                new KillSwitchRiskCheck(),
                new DailyLossRiskCheck(),
                new PositionLimitRiskCheck()
        ));

        var context = new RiskContext("SBIN", 0, 0, 10, false, false, 100_000, 10);

        Optional<RiskVerdict> rejection = chain.findRejection(context);
        assertTrue(rejection.isPresent());
        assertEquals("position_limit", rejection.get().checkName());
    }

    @Test
    void chainShortCircuits_firstRejectionWins() {
        var chain = new RiskCheckChain(List.of(
                new KillSwitchRiskCheck(),
                new DailyLossRiskCheck(),
                new PositionLimitRiskCheck()
        ));

        // Both kill switch AND loss exceeded — kill switch should win (first in chain)
        var context = new RiskContext("SBIN", 200_000, 0, 15, true, false, 100_000, 10);

        Optional<RiskVerdict> rejection = chain.findRejection(context);
        assertTrue(rejection.isPresent());
        assertEquals("kill_switch", rejection.get().checkName());
    }

    @Test
    void emptyChain_approvesEverything() {
        var chain = new RiskCheckChain(List.of());
        var context = new RiskContext("SBIN", 999_999, 999_999, 100, true, true, 0, 0);

        assertTrue(chain.isApproved(context));
        assertEquals(0, chain.size());
    }

    @Test
    void individualChecks_areStatelessAndReusable() {
        var killCheck = new KillSwitchRiskCheck();
        var lossCheck = new DailyLossRiskCheck();
        var posCheck = new PositionLimitRiskCheck();

        // Check with different contexts — no state leaks
        assertTrue(killCheck.check(new RiskContext("A", 0, 0, 0, false, false, 0, 0)).approved());
        assertFalse(killCheck.check(new RiskContext("B", 0, 0, 0, true, false, 0, 0)).approved());
        assertTrue(killCheck.check(new RiskContext("C", 0, 0, 0, false, false, 0, 0)).approved());

        assertTrue(lossCheck.check(new RiskContext("A", 0, 0, 0, false, false, 100, 0)).approved());
        assertFalse(lossCheck.check(new RiskContext("B", 100, 0, 0, false, false, 100, 0)).approved());

        assertTrue(posCheck.check(new RiskContext("A", 0, 0, 0, false, false, 0, 5)).approved());
        assertFalse(posCheck.check(new RiskContext("B", 0, 0, 5, false, false, 0, 5)).approved());
    }

    @Test
    void zeroLimits_disableChecks() {
        var chain = new RiskCheckChain(List.of(
                new DailyLossRiskCheck(),
                new PositionLimitRiskCheck()
        ));

        // maxDailyLossPaisa=0 disables loss check, maxOpenPositions=0 disables position check
        var context = new RiskContext("SBIN", 999_999, 999_999, 100, false, false, 0, 0);
        assertTrue(chain.isApproved(context));
    }
}
