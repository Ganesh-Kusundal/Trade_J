package com.tradej.broker.icici.unsupported;

import com.tradej.broker.api.port.BracketOrderProvider;
import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.broker.api.port.SessionRiskProvider;
import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.core.domain.model.ConditionalAlert;
import com.tradej.core.domain.model.ConditionalAlertRequest;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.PnlExitPolicy;
import com.tradej.core.domain.model.PnlExitResult;
import com.tradej.core.domain.model.SliceOrderRequest;

import java.util.List;

public final class IciciUnsupportedPorts {
    public static final SliceOrderCommand SLICE_ORDERS = new UnsupportedSliceOrders();
    public static final BracketOrderProvider BRACKET_ORDERS = new UnsupportedBracketOrders();
    public static final GttOrderProvider GTT_ORDERS = new UnsupportedGttOrders();
    public static final SessionRiskProvider SESSION_RISK = new UnsupportedSessionRisk();
    public static final ConditionalAlertProvider ALERTS = new UnsupportedAlerts();

    private IciciUnsupportedPorts() {
    }

    private static UnsupportedOperationException unsupported(String port) {
        return new UnsupportedOperationException("ICICI Breeze does not support " + port);
    }

    private static final class UnsupportedSliceOrders implements SliceOrderCommand {
        @Override
        public List<Order> placeSliceOrder(SliceOrderRequest request) {
            throw unsupported("slice orders");
        }
    }

    private static final class UnsupportedBracketOrders implements BracketOrderProvider {
        @Override
        public Order placeSuperOrder(
                OrderRequest request,
                long targetPricePaisa,
                long stopLossPricePaisa,
                long trailingJumpPaisa
        ) {
            throw unsupported("bracket orders");
        }

        @Override
        public Order modifySuperOrder(
                String orderId,
                String legName,
                long quantity,
                long pricePaisa,
                long triggerPricePaisa
        ) {
            throw unsupported("bracket orders");
        }

        @Override
        public boolean cancelSuperOrder(String orderId, String legName) {
            throw unsupported("bracket orders");
        }

        @Override
        public List<Order> getSuperOrders() {
            throw unsupported("bracket orders");
        }
    }

    private static final class UnsupportedGttOrders implements GttOrderProvider {
        @Override
        public Order placeForeverOrder(
                OrderRequest request,
                String orderFlag,
                Long quantity2,
                Long price2Paisa,
                Long trigger2Paisa
        ) {
            throw unsupported("GTT orders");
        }

        @Override
        public Order modifyForeverOrder(
                String orderId,
                String orderFlag,
                String legName,
                long quantity,
                long pricePaisa,
                long triggerPricePaisa
        ) {
            throw unsupported("GTT orders");
        }

        @Override
        public boolean cancelForeverOrder(String orderId) {
            throw unsupported("GTT orders");
        }

        @Override
        public List<Order> getForeverOrders() {
            throw unsupported("GTT orders");
        }
    }

    private static final class UnsupportedSessionRisk implements SessionRiskProvider {
        @Override
        public PnlExitResult enablePnlExit(PnlExitPolicy policy) {
            throw unsupported("session risk");
        }
    }

    private static final class UnsupportedAlerts implements ConditionalAlertProvider {
        @Override
        public String placeAlert(ConditionalAlertRequest request) {
            throw unsupported("conditional alerts");
        }

        @Override
        public ConditionalAlert getAlert(String alertId) {
            throw unsupported("conditional alerts");
        }

        @Override
        public List<ConditionalAlert> listAlerts() {
            throw unsupported("conditional alerts");
        }

        @Override
        public boolean deleteAlert(String alertId) {
            throw unsupported("conditional alerts");
        }
    }
}
