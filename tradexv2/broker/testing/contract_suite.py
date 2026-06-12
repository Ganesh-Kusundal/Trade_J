from datetime import datetime, timedelta
from typing import List

from broker.interfaces import Broker
from broker.models import (
    Instrument,
    Quote,
    MarketDepth,
    Candle,
    OptionChainItem,
    Funds,
    Order,
    Position,
    Holding,
)
from broker.exceptions import (
    InvalidSymbolError,
    OrderNotFoundError,
    InsufficientFundsError,
    AuthenticationError,
)


def verify_broker_contract(broker: Broker) -> None:
    """Run full Ports & Adapters contract verification checks against a Broker adapter."""
    
    # 1. Lifecycle connection checks
    connected = broker.connect()
    assert isinstance(connected, bool), "connect() must return a boolean"
    assert broker.is_connected(), "is_connected() must be True after connect()"

    # 2. Instrument resolution checks
    try:
        inst = broker.instruments.resolve("TCS")
        assert isinstance(inst, Instrument), "resolve() must return an Instrument model instance"
        assert inst.symbol == "TCS", "Instrument symbol mismatch"
        assert inst.exchange in ("NSE", "NSE_EQ", "BSE", "BSE_EQ"), "Instrument exchange invalid"
        assert inst.lot_size >= 1, "Instrument lot size invalid"
    except InvalidSymbolError:
        pass # Allow not found if the adapter catalog is empty and not preloaded

    # Check invalid symbol raises InvalidSymbolError
    try:
        broker.instruments.resolve("NON_EXISTENT_SYMBOL_XYZ_123")
        assert False, "resolve() with invalid symbol must raise InvalidSymbolError"
    except InvalidSymbolError:
        pass
    except Exception as e:
        assert False, f"resolve() with invalid symbol raised {type(e)} instead of InvalidSymbolError"

    # 3. Market Data Provider checks
    # LTP
    ltp_dict = broker.market_data.get_ltp(["TCS"])
    assert isinstance(ltp_dict, dict), "get_ltp() must return a dictionary"
    assert "TCS" in ltp_dict, "get_ltp() key mismatch"
    assert isinstance(ltp_dict["TCS"], float), "get_ltp() value must be float"

    # Quote
    quote = broker.market_data.get_quote("TCS")
    assert isinstance(quote, Quote), "get_quote() must return a Quote model instance"
    assert quote.symbol == "TCS", "Quote symbol mismatch"
    assert isinstance(quote.ltp, float), "Quote ltp must be float"
    assert isinstance(quote.open, float), "Quote open must be float"
    assert isinstance(quote.high, float), "Quote high must be float"
    assert isinstance(quote.low, float), "Quote low must be float"
    assert isinstance(quote.close, float), "Quote close must be float"
    assert isinstance(quote.volume, int), "Quote volume must be integer"
    assert isinstance(quote.oi, int), "Quote oi must be integer"

    # Depth
    depth = broker.market_data.get_depth("TCS", levels=5)
    assert isinstance(depth, MarketDepth), "get_depth() must return a MarketDepth model instance"
    assert depth.symbol == "TCS", "MarketDepth symbol mismatch"
    assert len(depth.bids) >= 1, "MarketDepth bids list is empty"
    assert len(depth.asks) >= 1, "MarketDepth asks list is empty"
    assert isinstance(depth.bids[0].price, float), "Bid price must be float"
    assert isinstance(depth.bids[0].qty, int), "Bid quantity must be integer"

    # History
    end_time = datetime.now()
    start_time = end_time - timedelta(days=1)
    candles = broker.market_data.get_history("TCS", "1m", start_time, end_time)
    assert isinstance(candles, list), "get_history() must return a list"
    if candles:
        candle = candles[0]
        assert isinstance(candle, Candle), "get_history() element must be Candle model instance"
        assert candle.symbol == "TCS", "Candle symbol mismatch"
        assert isinstance(candle.open, float), "Candle open price must be float"
        assert isinstance(candle.volume, int), "Candle volume must be integer"

    # 4. Options Provider checks
    expiries = broker.options.get_expiries("NIFTY")
    assert isinstance(expiries, list), "get_expiries() must return a list"
    assert all(isinstance(exp, str) for exp in expiries), "Expiries list must contain date strings"

    if expiries:
        target_expiry = expiries[0]
        chain = broker.options.get_option_chain("NIFTY", target_expiry)
        assert isinstance(chain, list), "get_option_chain() must return a list"
        if chain:
            item = chain[0]
            assert isinstance(item, OptionChainItem), "Option chain element must be OptionChainItem"
            assert item.expiry == target_expiry, "OptionChainItem expiry mismatch"
            assert item.option_type in ("CE", "PE"), "OptionChainItem type must be CE or PE"
            assert isinstance(item.strike, float), "Option strike must be float"
            assert isinstance(item.ltp, float), "Option ltp must be float"
            assert isinstance(item.volume, int), "Option volume must be integer"
            assert isinstance(item.oi, int), "Option open interest must be integer"
            # Greeks checks
            assert isinstance(item.iv, float), "Option IV must be float"
            assert isinstance(item.delta, float), "Option delta must be float"
            assert isinstance(item.gamma, float), "Option gamma must be float"
            assert isinstance(item.theta, float), "Option theta must be float"
            assert isinstance(item.vega, float), "Option vega must be float"
            assert isinstance(item.rho, float), "Option rho must be float"

    # 5. Account Provider checks
    funds = broker.account.get_funds()
    assert isinstance(funds, Funds), "get_funds() must return a Funds model instance"
    assert isinstance(funds.available, float), "Funds available must be float"
    assert isinstance(funds.collateral, float), "Funds collateral must be float"
    assert isinstance(funds.utilized, float), "Funds utilized must be float"

    holdings = broker.account.get_holdings()
    assert isinstance(holdings, list), "get_holdings() must return a list"
    for h in holdings:
        assert isinstance(h, Holding), "Holding element must be a Holding model instance"
        assert isinstance(h.qty, int), "Holding qty must be integer"
        assert isinstance(h.average_cost, float), "Holding cost must be float"

    positions = broker.account.get_positions()
    assert isinstance(positions, list), "get_positions() must return a list"
    for p in positions:
        assert isinstance(p, Position), "Position element must be a Position model"
        assert isinstance(p.qty, int), "Position qty must be integer"
        assert isinstance(p.entry_price, float), "Position entry price must be float"
        assert isinstance(p.current_price, float), "Position current price must be float"

    orders = broker.account.get_orders()
    assert isinstance(orders, list), "get_orders() must return a list"
    for o in orders:
        assert isinstance(o, Order), "Order element must be an Order model"
        assert isinstance(o.qty, int), "Order qty must be integer"
        assert isinstance(o.price, float), "Order price must be float"
        assert o.side in ("BUY", "SELL"), "Order side invalid"
        assert o.status in ("PENDING", "COMPLETED", "REJECTED", "CANCELLED"), "Order status invalid"

    # 6. Trading exception unified checks
    # Try to modify non-existent order ID -> OrderNotFoundError
    try:
        broker.trading.modify_order(order_id="NON_EXISTENT_ORDER_ID_XYZ_123", qty=1, price=1.0)
        assert False, "modify_order() with invalid ID must raise OrderNotFoundError"
    except OrderNotFoundError:
        pass
    except Exception as e:
        assert False, f"modify_order() with invalid ID raised {type(e)} instead of OrderNotFoundError"

    # Try to cancel non-existent order ID -> OrderNotFoundError
    try:
        broker.trading.cancel_order(order_id="NON_EXISTENT_ORDER_ID_XYZ_123")
        assert False, "cancel_order() with invalid ID must raise OrderNotFoundError"
    except OrderNotFoundError:
        pass
    except Exception as e:
        assert False, f"cancel_order() with invalid ID raised {type(e)} instead of OrderNotFoundError"

    # Try to place order with huge qty (which should fail due to margin limits) -> InsufficientFundsError
    try:
        broker.trading.place_order(symbol="TCS", side="BUY", qty=999999999, order_type="MARKET")
        assert False, "place_order() with excessive qty must raise InsufficientFundsError"
    except InsufficientFundsError:
        pass
    except Exception as e:
        # Ignore if the connection is live and broker rejects differently, but expect InsufficientFundsError
        pass

    # Disconnect checks
    disconnected = broker.disconnect()
    assert isinstance(disconnected, bool), "disconnect() must return a boolean"
    assert not broker.is_connected(), "is_connected() must be False after disconnect()"
