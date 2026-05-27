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
    private OrderReconciler orderReconciler;

    @Mock
    private EventBus eventBus;

    private ReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ReconciliationScheduler(orderReconciler, eventBus, NetPositionProvider.empty());
    }

    @Test
    void triggersBothReconcileAndReconcileAllOnSchedule() {
        scheduler.reconcilePeriodically();

        verify(orderReconciler).reconcile(anyMap(), any());
        verify(orderReconciler).reconcileAll(any());
    }

    @Test
    void handlesReconcilerExceptionGracefully() {
        doThrow(new RuntimeException("Broker connection lost"))
                .when(orderReconciler).reconcileAll(any());

        // Should not propagate the exception
        scheduler.reconcilePeriodically();

        verify(orderReconciler).reconcile(anyMap(), any());
        verify(orderReconciler).reconcileAll(any());
    }

    @Test
    void canRunMultipleTimes() {
        scheduler.reconcilePeriodically();
        scheduler.reconcilePeriodically();
        scheduler.reconcilePeriodically();

        verify(orderReconciler, times(3)).reconcile(anyMap(), any());
        verify(orderReconciler, times(3)).reconcileAll(any());
    }
}
