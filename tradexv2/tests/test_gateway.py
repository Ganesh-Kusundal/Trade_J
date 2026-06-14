from datetime import datetime, timedelta
import polars as pl
import pytest
from broker import Gateway
from broker.exceptions import InvalidSymbolError, InsufficientFundsError


def test_gateway_default_init():
    g = Gateway()
    assert g.broker.__class__.__name__ == "SimulationBroker"
    # Test connect/disconnect lifecycles
    assert g.connect() is True
    assert g.status() == "CONNECTED"
    assert g.disconnect() is True
    assert g.status() == "DISCONNECTED"


def test_gateway_market_data():
    g = Gateway()
    g.connect()
    
    # Test LTP
    ltp = g.ltp("TCS")
    assert isinstance(ltp, float)
    assert ltp > 0
    
    # Test multiple LTPs
    ltps = g.ltp(["TCS", "RELIANCE"])
    assert isinstance(ltps, dict)
    assert "TCS" in ltps and "RELIANCE" in ltps
    
    # Test quote
    quote = g.quote("TCS")
    assert quote.symbol == "TCS"
    assert isinstance(quote.ltp, float)
    assert abs(quote.ltp - ltp) < 50.0
    
    # Test depth
    depth_df = g.depth("TCS", levels=5)
    assert isinstance(depth_df, pl.DataFrame)
    assert "bid_price" in depth_df.columns
    assert "ask_price" in depth_df.columns
    assert len(depth_df) == 5


def test_gateway_history_polars():
    g = Gateway()
    g.connect()
    
    # Test default history (DataFrame)
    df = g.history("TCS")
    assert isinstance(df, pl.DataFrame)
    assert len(df) > 0
    assert "open" in df.columns
    assert "close" in df.columns
    
    # Test lazy history (LazyFrame)
    lf = g.history("TCS", lazy=True)
    assert isinstance(lf, pl.LazyFrame)
    # Perform a lazy pipe / computation
    df_piped = lf.filter(pl.col("open") > 0).collect()
    assert len(df_piped) == len(df)
    
    # Test shortcut candle helpers
    assert len(g.intraday("TCS")) > 0
    assert len(g.daily("TCS")) > 0


def test_gateway_options_analytics():
    g = Gateway()
    g.connect()
    
    # Option chain retrieval
    df = g.option_chain("NIFTY")
    assert isinstance(df, pl.DataFrame)
    # Check that required columns are exact
    required_cols = ["strike", "expiry", "option_type", "ltp", "volume", "oi", "iv", "delta", "gamma", "theta", "vega", "rho"]
    for col in required_cols:
        assert col in df.columns
        
    # ATM strike
    atm = g.atm("NIFTY")
    assert isinstance(atm, float)
    
    # PE/CE filters
    assert len(g.ce("NIFTY")) > 0
    assert len(g.pe("NIFTY")) > 0
    
    # ITM/OTM filters
    assert len(g.itm("NIFTY")) > 0
    assert len(g.otm("NIFTY")) > 0
    
    # PCR and Max Pain
    pcr = g.pcr("NIFTY")
    assert isinstance(pcr, float)
    
    max_pain = g.max_pain("NIFTY")
    assert isinstance(max_pain, float)
    
    # Straddle & Strangle pricing
    straddle_price = g.straddle("NIFTY")
    assert straddle_price > 0
    
    strangle_price = g.strangle("NIFTY")
    assert strangle_price > 0


def test_gateway_trading_lifecycle():
    g = Gateway()
    g.connect()
    
    # Check initial funds
    funds = g.funds()
    assert funds.available == 1000000.0
    
    # Place a limit buy order
    order_id = g.limit_buy("TCS", qty=10, price=3000.0)
    assert order_id.startswith("SIM-ORD-")
    
    # Confirm funds updated and position created
    assert g.funds().available < 1000000.0
    positions = g.positions()
    assert len(positions) == 1
    assert positions[0].symbol == "TCS"
    assert positions[0].qty == 10
    
    # Place a sell order to square off
    order_id2 = g.limit_sell("TCS", qty=10, price=3100.0)
    assert order_id2 is not None
    assert g.positions()[0].qty == 0
    
    # Modify an order
    order_id_open = g.buy("RELIANCE", qty=5, price=2000.0)
    success = g.modify(order_id_open, qty=10, price=2100.0)
    assert success is True
    
    # Cancel an order
    cancel_success = g.cancel(order_id_open)
    assert cancel_success is True


def test_gateway_advanced_orders():
    g = Gateway()
    g.connect()
    
    # Bracket order
    bo_id = g.bo("TCS", qty=5, side="BUY", price=3200.0, stop_loss=50.0, take_profit=100.0)
    assert bo_id.startswith("SIM-ORD-")
    
    # Cover order
    co_id = g.co("RELIANCE", qty=2, side="BUY", trigger_price=2400.0, price=2410.0)
    assert co_id.startswith("SIM-ORD-")
    
    # Basket execution
    basket_orders = [
        {"symbol": "TCS", "side": "BUY", "qty": 1, "price": 3500.0},
        {"symbol": "RELIANCE", "side": "SELL", "qty": 2, "price": 2500.0}
    ]
    basket_ids = g.basket(basket_orders)
    assert len(basket_ids) == 2
    
    # Sliced execution
    slice_ids = g.slice("TCS", side="BUY", qty=25, slice_size=10, price=3400.0)
    assert len(slice_ids) == 3 # 10, 10, 5
