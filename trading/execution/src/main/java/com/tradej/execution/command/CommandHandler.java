package com.tradej.execution.command;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.core.domain.port.HistoricalImportPort;
import com.tradej.execution.service.OrderManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Dispatches {@link TradingCommand} objects to the appropriate service.
 *
 * <p>Decouples callers from the service implementation. Callers construct a command
 * and pass it to the handler; the handler translates it into service method calls.
 *
 * <p>Usage:
 * <pre>
 *   var handler = new CommandHandler(orderManagementService, brokerConnection);
 *   CommandResult result = handler.execute(new TradingCommand.PlaceOrder(request));
 * </pre>
 */
public final class CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(CommandHandler.class);

    private final OrderManagementService oms;
    private final IBrokerConnection brokerConnection;
    private final HistoricalImportPort historicalImportPort;

    public CommandHandler(OrderManagementService oms, IBrokerConnection brokerConnection) {
        this(oms, brokerConnection, null);
    }

    public CommandHandler(OrderManagementService oms, IBrokerConnection brokerConnection,
                          HistoricalImportPort historicalImportPort) {
        this.oms = oms;
        this.brokerConnection = brokerConnection;
        this.historicalImportPort = historicalImportPort;
    }

    public CommandHandler(OrderManagementService oms) {
        this(oms, null, null);
    }

    public CommandResult execute(TradingCommand command) {
        try {
            return switch (command) {
                case TradingCommand.PlaceOrder cmd -> executePlace(cmd);
                case TradingCommand.CancelOrder cmd -> executeCancel(cmd);
                case TradingCommand.ModifyOrder cmd -> executeModify(cmd);
                case TradingCommand.CancelAllOpenOrders ignored -> executeCancelAll();
                case TradingCommand.SetKillSwitch cmd -> executeKillSwitch(cmd);
                case TradingCommand.RefreshInstrumentCatalog cmd -> executeRefreshCatalog(cmd);
                case TradingCommand.ImportHistoricalData cmd -> executeImport(cmd);
                case TradingCommand.ReconcilePositions cmd -> executeReconcile(cmd);
            };
        } catch (Exception e) {
            log.error("Command execution failed: {}", command, e);
            return new CommandResult.Error("Command execution failed: " + e.getMessage(), e);
        }
    }

    private CommandResult executePlace(TradingCommand.PlaceOrder cmd) {
        var order = oms.placeOrder(cmd.request());
        if (order == null) {
            return new CommandResult.Rejected("Order rejected by broker");
        }
        return new CommandResult.Success(order);
    }

    private CommandResult executeCancel(TradingCommand.CancelOrder cmd) {
        boolean cancelled = oms.cancelOrder(cmd.orderId());
        if (cancelled) {
            return new CommandResult.Success(null);
        }
        return new CommandResult.Rejected("Cancel rejected — order may already be terminal");
    }

    private CommandResult executeModify(TradingCommand.ModifyOrder cmd) {
        var order = oms.modifyOrder(cmd.request());
        if (order == null) {
            return new CommandResult.Rejected("Modify rejected — order may not be modifiable");
        }
        return new CommandResult.Success(order);
    }

    private CommandResult executeCancelAll() {
        if (brokerConnection == null) {
            return new CommandResult.Error("Broker connection not available for cancel-all");
        }
        var orderIds = brokerConnection.orders().cancelAllOpenOrders();
        return new CommandResult.BulkSuccess(orderIds);
    }

    private CommandResult executeKillSwitch(TradingCommand.SetKillSwitch cmd) {
        if (cmd.enabled()) {
            oms.activateKillSwitch();
        } else {
            oms.deactivateKillSwitch();
        }
        return new CommandResult.KillSwitchResult(cmd.enabled());
    }

    private CommandResult executeRefreshCatalog(TradingCommand.RefreshInstrumentCatalog cmd) {
        if (brokerConnection == null) {
            return new CommandResult.Error("Broker connection not available for catalog refresh");
        }
        Path catalogDir = Path.of("runtime/instruments");
        brokerConnection.loadInstrumentCatalog(catalogDir);
        int count = brokerConnection.instruments().catalogSize();
        log.info("Instrument catalog refreshed: {} instruments", count);
        return new CommandResult.CatalogRefreshed(count);
    }

    private CommandResult executeImport(TradingCommand.ImportHistoricalData cmd) {
        if (historicalImportPort == null) {
            return new CommandResult.Error("Historical import port not available");
        }
        var request = new HistoricalImportPort.ImportRequest(
                "default", cmd.symbol(), cmd.segment(), cmd.interval(), cmd.fromMs(), cmd.toMs());
        String jobId = historicalImportPort.startImport(request);
        log.info("Historical import started: jobId={} symbol={} interval={}", jobId, cmd.symbol(), cmd.interval());
        return new CommandResult.ImportStarted(jobId);
    }

    private CommandResult executeReconcile(TradingCommand.ReconcilePositions cmd) {
        log.info("Position reconciliation requested with payload length={}",
                cmd.jsonPayload() != null ? cmd.jsonPayload().length() : 0);
        return new CommandResult.ReconciliationResult(0);
    }
}
