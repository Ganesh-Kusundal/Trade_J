package com.tradej.app.service;

import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.runtime.RuntimeMode;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.execution.command.CommandHandler;
import com.tradej.execution.command.CommandResult;
import com.tradej.execution.command.TradingCommand;
import com.tradej.execution.risk.PositionRiskHandler;
import org.springframework.stereotype.Service;

/**
 * Application service for order operations.
 * Encapsulates runtime mode checking and kill switch validation
 * that was previously in the controller.
 */
@Service
public class OrderApplicationService {

    private final CommandHandler commandHandler;
    private final RuntimeModeHolder runtimeModeHolder;
    private final PositionRiskHandler positionRiskHandler;

    public OrderApplicationService(
            CommandHandler commandHandler,
            RuntimeModeHolder runtimeModeHolder,
            PositionRiskHandler positionRiskHandler
    ) {
        this.commandHandler = commandHandler;
        this.runtimeModeHolder = runtimeModeHolder;
        this.positionRiskHandler = positionRiskHandler;
    }

    public CommandResult placeOrder(OrderRequest request) {
        if (runtimeModeHolder.mode() != RuntimeMode.LIVE) {
            return new CommandResult.Rejected("Order placement only allowed in LIVE mode (current: " + runtimeModeHolder.mode() + ")");
        }
        if (positionRiskHandler.isKillSwitchActive()) {
            return new CommandResult.Rejected("Kill switch active");
        }
        return commandHandler.execute(new TradingCommand.PlaceOrder(request));
    }

    public CommandResult modifyOrder(ModifyOrderRequest request) {
        if (runtimeModeHolder.mode() != RuntimeMode.LIVE) {
            return new CommandResult.Rejected("Order modify only allowed in LIVE mode");
        }
        return commandHandler.execute(new TradingCommand.ModifyOrder(request));
    }

    public CommandResult cancelOrder(String orderId) {
        if (runtimeModeHolder.mode() != RuntimeMode.LIVE) {
            return new CommandResult.Rejected("Order cancel only allowed in LIVE mode");
        }
        return commandHandler.execute(new TradingCommand.CancelOrder(orderId));
    }
}
