package com.tradej.execution.reconcile;

import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.port.NetPositionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ReconciliationSchedulerUnitTest {

    @Mock
    private ReconciliationUseCase reconciliationUseCase;

    private ReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ReconciliationScheduler(reconciliationUseCase);
    }

    @Test
    void triggersBothReconcileAndReconcileAllOnSchedule() {
        scheduler.reconcilePeriodically();

        verify(reconciliationUseCase).reconcileAllSources();
    }

    @Test
    void handlesReconcilerExceptionGracefully() {
        doThrow(new RuntimeException("Broker connection lost"))
                .when(reconciliationUseCase).reconcileAllSources();

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, scheduler::reconcilePeriodically);

        verify(reconciliationUseCase).reconcileAllSources();
    }

    @Test
    void canRunMultipleTimes() {
        scheduler.reconcilePeriodically();
        scheduler.reconcilePeriodically();
        scheduler.reconcilePeriodically();

        verify(reconciliationUseCase, times(3)).reconcileAllSources();
    }
}
