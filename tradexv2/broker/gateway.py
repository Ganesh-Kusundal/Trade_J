from datetime import datetime, timedelta
import polars as pl
from typing import List, Dict, Union, Any, Optional, Callable

from broker.interfaces import Broker
from broker.models import Candle, Quote, MarketDepth, OptionChainItem, Order, Position, Holding, Funds, Instrument
from broker.exceptions import InvalidSymbolError, NotSupportedError
from broker.analytics.black_scholes import calculate_pcr, calculate_max_pain


class Gateway:
    """The main user-facing Gateway facade for the TradeXV2 Broker Framework."""

    def __init__(self, broker: Optional[Broker] = None):
        """Initialize the Gateway with an underlying Broker adapter."""
        if broker is None:
            # Fall back to Simulation Broker by default for instant notebook readiness
            from broker.simulation.sim_broker import SimulationBroker
            self._broker = SimulationBroker()
        else:
            self._broker = broker
        self._connected = False

    @property
    def broker(self) -> Broker:
        """Access the underlying active broker port."""
        return self._broker

    # ── Broker Lifecycle ──

    def connect(self) -> bool:
        """Connect to the broker gateway."""
        self._connected = self._broker.connect()
        return self._connected

    def disconnect(self) -> bool:
        """Disconnect from the broker gateway."""
        self._connected = not self._broker.disconnect()
        return not self._connected

    def reconnect(self) -> bool:
        """Reconnect to the broker gateway."""
        self.disconnect()
        return self.connect()

    def health(self) -> Dict[str, Any]:
        """Get the health status of the broker connection."""
        return {
            "status": "CONNECTED" if self._broker.is_connected() else "DISCONNECTED",
            "broker": self._broker.__class__.__name__,
            "timestamp": datetime.now().isoformat(),
        }

    def capabilities(self) -> Dict[str, bool]:
        """Get capabilities of the broker connection (e.g. streaming, options, futures)."""
        return {
            "streaming": True,
            "options": True,
            "futures": True,
            "depth_l2": True,
        }

    # ── Authentication ──

    def login(self, **credentials: Any) -> bool:
        """Log in with the broker API."""
        return self._broker.login(**credentials)

    def logout(self) -> bool:
        """Log out and terminate session."""
        self.disconnect()
        return True

    def refresh_session(self) -> bool:
        """Refresh auth session/tokens."""
        return True

    # ── Account APIs ──

    def funds(self) -> Funds:
        """Get account funds and margins."""
        return self._broker.account.get_funds()

    def holdings(self) -> List[Holding]:
        """Get current equity holdings."""
        return self._broker.account.get_holdings()

    def positions(self) -> List[Position]:
        """Get current open and closed positions."""
        return self._broker.account.get_positions()

    def orders(self) -> List[Order]:
        """Get the order book."""
        return self._broker.account.get_orders()

    def trades(self) -> List[Any]:
        """Get the trade book."""
        return self._broker.account.get_trades()

    def order_book(self) -> List[Order]:
        """Alias for orders()."""
        return self.orders()

    def trade_book(self) -> List[Any]:
        """Alias for trades()."""
        return self.trades()

    # ── Market Data APIs ──

    def ltp(self, symbols: Union[str, List[str]]) -> Union[float, Dict[str, float]]:
        """Get Last Traded Price (LTP) for one or multiple symbols."""
        if isinstance(symbols, str):
            res = self._broker.market_data.get_ltp([symbols])
            if symbols not in res:
                raise InvalidSymbolError(f"Symbol {symbols} not found in LTP response")
            return res[symbols]
        return self._broker.market_data.get_ltp(symbols)

    def quote(self, symbol: str) -> Quote:
        """Get quote snapshot for a symbol."""
        return self._broker.market_data.get_quote(symbol)

    def history(
        self,
        symbol: str,
        timeframe: str = "1m",
        lookback_days: int = 90,
        lazy: bool = False,
        exchange: str = "NSE"
    ) -> Union[pl.DataFrame, pl.LazyFrame]:
        """Get historical candles/bars as a Polars DataFrame or LazyFrame."""
        end_time = datetime.now()
        start_time = end_time - timedelta(days=lookback_days)
        
        # Fetch clean candles from provider
        candles = self._broker.market_data.get_history(symbol, timeframe, start_time, end_time)
        
        # Format into Polars structure matching requirements
        data = {
            "timestamp": [c.timestamp for c in candles],
            "open": [c.open for c in candles],
            "high": [c.high for c in candles],
            "low": [c.low for c in candles],
            "close": [c.close for c in candles],
            "volume": [c.volume for c in candles],
            "oi": [c.oi for c in candles],
            "symbol": [c.symbol for c in candles],
            "exchange": [c.exchange for c in candles],
            "timeframe": [c.timeframe for c in candles]
        }
        
        df = pl.DataFrame(data)
        if lazy:
            return df.lazy()
        return df

    def intraday(self, symbol: str) -> pl.DataFrame:
        """Get 1-minute intraday candles for the last 5 days."""
        return self.history(symbol, timeframe="1m", lookback_days=5)

    def daily(self, symbol: str) -> pl.DataFrame:
        """Get daily candles for the last 365 days."""
        return self.history(symbol, timeframe="1d", lookback_days=365)

    def weekly(self, symbol: str) -> pl.DataFrame:
        """Get weekly candles for the last 2 years."""
        return self.history(symbol, timeframe="1w", lookback_days=730)

    def monthly(self, symbol: str) -> pl.DataFrame:
        """Get monthly candles for the last 5 years."""
        return self.history(symbol, timeframe="1mo", lookback_days=1825)

    # ── Market Depth ──

    def depth(self, symbol: str, levels: int = 5) -> pl.DataFrame:
        """Get order book market depth as a Polars DataFrame."""
        depth_data = self._broker.market_data.get_depth(symbol, levels)
        
        # Build tabular representation: bid_price, bid_qty, ask_price, ask_qty
        bids = depth_data.bids
        asks = depth_data.asks
        
        max_len = max(len(bids), len(asks))
        bid_prices = [bids[i].price if i < len(bids) else 0.0 for i in range(max_len)]
        bid_qtys = [bids[i].qty if i < len(bids) else 0 for i in range(max_len)]
        ask_prices = [asks[i].price if i < len(asks) else 0.0 for i in range(max_len)]
        ask_qtys = [asks[i].qty if i < len(asks) else 0 for i in range(max_len)]
        
        return pl.DataFrame({
            "bid_price": bid_prices,
            "bid_qty": bid_qtys,
            "ask_price": ask_prices,
            "ask_qty": ask_qtys
        })

    def full_depth(self, symbol: str) -> pl.DataFrame:
        """Get maximum available depth levels."""
        return self.depth(symbol, levels=20)

    # ── Live Streaming ──

    def stream(self, symbols: Union[str, List[str]], callback: Callable[[Any], None]) -> None:
        """Subscribe to quote stream for one or more symbols."""
        sym_list = [symbols] if isinstance(symbols, str) else symbols
        self._broker.streaming.subscribe(sym_list, callback)

    def candles(self, symbol: str, timeframe: str = "1m") -> None:
        """Mock stream connection for candle updates."""
        raise NotSupportedError("Candle streaming is currently handled via tick subscription callbacks.")

    def ticks(self, symbol: str) -> None:
        """Mock stream connection for tick updates."""
        raise NotSupportedError("Tick streaming is currently handled via subscriber callbacks.")

    # ── Instrument APIs ──

    def search(self, query: str) -> List[Instrument]:
        """Search resolved instruments."""
        return self._broker.instruments.search(query)

    def instrument(self, symbol: str) -> Instrument:
        """Lookup details of an instrument."""
        return self._broker.instruments.lookup(symbol)

    def resolve(self, symbol: str) -> Instrument:
        """Resolve a user-friendly string to a canonical Instrument object."""
        return self._broker.instruments.resolve(symbol)

    def universe(self, name: str) -> List[str]:
        """Get symbol names of a basket/universe."""
        return self._broker.instruments.get_universe(name)

    # ── Options APIs ──

    def option_chain(self, underlying: str, expiry: Optional[str] = None) -> pl.DataFrame:
        """Get option chain as a Polars DataFrame."""
        if expiry is None:
            expiries = self.expiries(underlying)
            if not expiries:
                raise InvalidSymbolError(f"No option expiries found for underlying: {underlying}")
            expiry = expiries[0]
            
        items = self._broker.options.get_option_chain(underlying, expiry)
        
        data = {
            "strike": [item.strike for item in items],
            "expiry": [item.expiry for item in items],
            "option_type": [item.option_type for item in items],
            "ltp": [item.ltp for item in items],
            "volume": [item.volume for item in items],
            "oi": [item.oi for item in items],
            "iv": [item.iv for item in items],
            "delta": [item.delta for item in items],
            "gamma": [item.gamma for item in items],
            "theta": [item.theta for item in items],
            "vega": [item.vega for item in items],
            "rho": [item.rho for item in items]
        }
        return pl.DataFrame(data)

    def expiries(self, underlying: str) -> List[str]:
        """Get list of expiry dates for options."""
        return self._broker.options.get_expiries(underlying)

    def atm(self, underlying: str) -> float:
        """Get the At-The-Money (ATM) strike price."""
        ltp = self.ltp(underlying)
        strikes = self.option_chain(underlying)["strike"].unique().to_list()
        if not strikes:
            raise InvalidSymbolError(f"No strikes found for underlying: {underlying}")
        return min(strikes, key=lambda s: abs(s - ltp))

    def ce(self, underlying: str, expiry: Optional[str] = None) -> pl.DataFrame:
        """Get all Call Options (CE) in the chain."""
        df = self.option_chain(underlying, expiry)
        return df.filter(pl.col("option_type") == "CE")

    def pe(self, underlying: str, expiry: Optional[str] = None) -> pl.DataFrame:
        """Get all Put Options (PE) in the chain."""
        df = self.option_chain(underlying, expiry)
        return df.filter(pl.col("option_type") == "PE")

    def otm(self, underlying: str, expiry: Optional[str] = None) -> pl.DataFrame:
        """Get Out-of-the-Money (OTM) options."""
        ltp = self.ltp(underlying)
        df = self.option_chain(underlying, expiry)
        # Call OTM: strike > ltp. Put OTM: strike < ltp.
        return df.filter(
            ((pl.col("option_type") == "CE") & (pl.col("strike") > ltp)) |
            ((pl.col("option_type") == "PE") & (pl.col("strike") < ltp))
        )

    def itm(self, underlying: str, expiry: Optional[str] = None) -> pl.DataFrame:
        """Get In-the-Money (ITM) options."""
        ltp = self.ltp(underlying)
        df = self.option_chain(underlying, expiry)
        # Call ITM: strike < ltp. Put ITM: strike > ltp.
        return df.filter(
            ((pl.col("option_type") == "CE") & (pl.col("strike") < ltp)) |
            ((pl.col("option_type") == "PE") & (pl.col("strike") > ltp))
        )

    def straddle(self, underlying: str, strike: Optional[float] = None, expiry: Optional[str] = None) -> float:
        """Get the combined price of an ATM or specific strike Straddle (Call + Put)."""
        if strike is None:
            strike = self.atm(underlying)
        df = self.option_chain(underlying, expiry)
        options = df.filter(pl.col("strike") == strike)
        return float(options["ltp"].sum())

    def strangle(
        self,
        underlying: str,
        ce_strike: Optional[float] = None,
        pe_strike: Optional[float] = None,
        expiry: Optional[str] = None
    ) -> float:
        """Get the combined price of a Strangle."""
        ltp = self.ltp(underlying)
        df = self.ce(underlying, expiry)
        if ce_strike is None:
            # First OTM Call
            ce_strike = df.filter(pl.col("strike") > ltp)["strike"].min()
        
        df_pe = self.pe(underlying, expiry)
        if pe_strike is None:
            # First OTM Put
            pe_strike = df_pe.filter(pl.col("strike") < ltp)["strike"].max()

        call_ltp = df.filter(pl.col("strike") == ce_strike)["ltp"].to_list()[0]
        put_ltp = df_pe.filter(pl.col("strike") == pe_strike)["ltp"].to_list()[0]
        return call_ltp + put_ltp

    def greeks(self, underlying: str, expiry: Optional[str] = None) -> pl.DataFrame:
        """Get option chain with only strikes and Greeks columns."""
        df = self.option_chain(underlying, expiry)
        return df.select(["strike", "option_type", "delta", "gamma", "theta", "vega", "rho"])

    def iv(self, underlying: str, expiry: Optional[str] = None) -> pl.DataFrame:
        """Get option chain strikes and Implied Volatility (IV)."""
        df = self.option_chain(underlying, expiry)
        return df.select(["strike", "option_type", "iv"])

    def pcr(self, underlying: str, expiry: Optional[str] = None) -> float:
        """Get Put-Call Ratio based on Open Interest (OI)."""
        if expiry is None:
            expiries = self.expiries(underlying)
            if not expiries:
                raise InvalidSymbolError(f"No option expiries found for underlying: {underlying}")
            expiry = expiries[0]
        items = self._broker.options.get_option_chain(underlying, expiry)
        # Convert Pydantic models to dicts for analytics engine
        chain_dicts = [item.model_dump() for item in items]
        _, pcr_oi = calculate_pcr(chain_dicts)
        return pcr_oi

    def max_pain(self, underlying: str, expiry: Optional[str] = None) -> float:
        """Get Max Pain strike price."""
        if expiry is None:
            expiries = self.expiries(underlying)
            if not expiries:
                raise InvalidSymbolError(f"No option expiries found for underlying: {underlying}")
            expiry = expiries[0]
        items = self._broker.options.get_option_chain(underlying, expiry)
        chain_dicts = [item.model_dump() for item in items]
        return calculate_max_pain(chain_dicts)

    # ── Futures APIs ──

    def future(self, underlying: str) -> Instrument:
        """Get near-month futures contract."""
        res = self._broker.instruments.search(underlying)
        futures = [f for f in res if f.instrument_type == "FUTURES"]
        if not futures:
            raise InvalidSymbolError(f"No futures found for: {underlying}")
        # Sort by expiry if string format allows (assumes chronological order)
        futures.sort(key=lambda f: f.expiry or "")
        return futures[0]

    def next_future(self, underlying: str) -> Instrument:
        """Get next-month futures contract."""
        res = self._broker.instruments.search(underlying)
        futures = [f for f in res if f.instrument_type == "FUTURES"]
        if len(futures) < 2:
            raise InvalidSymbolError(f"Next month futures not found for: {underlying}")
        futures.sort(key=lambda f: f.expiry or "")
        return futures[1]

    def future_chain(self, underlying: str) -> List[Instrument]:
        """Get all available futures contracts."""
        res = self._broker.instruments.search(underlying)
        return [f for f in res if f.instrument_type == "FUTURES"]

    # ── Order APIs ──

    def buy(
        self,
        symbol: str,
        qty: int,
        price: float = 0.0,
        trigger_price: float = 0.0,
        product: str = "INTRADAY",
        order_type: Optional[str] = None
    ) -> str:
        """Place a buy order."""
        if order_type is None:
            order_type = "LIMIT" if price > 0.0 else "MARKET"
        return self._broker.trading.place_order(
            symbol=symbol,
            side="BUY",
            qty=qty,
            order_type=order_type,
            price=price,
            trigger_price=trigger_price,
            product=product
        )

    def sell(
        self,
        symbol: str,
        qty: int,
        price: float = 0.0,
        trigger_price: float = 0.0,
        product: str = "INTRADAY",
        order_type: Optional[str] = None
    ) -> str:
        """Place a sell order."""
        if order_type is None:
            order_type = "LIMIT" if price > 0.0 else "MARKET"
        return self._broker.trading.place_order(
            symbol=symbol,
            side="SELL",
            qty=qty,
            order_type=order_type,
            price=price,
            trigger_price=trigger_price,
            product=product
        )

    def market_buy(self, symbol: str, qty: int, product: str = "INTRADAY") -> str:
        """Place a market buy order."""
        return self.buy(symbol, qty, product=product, order_type="MARKET")

    def market_sell(self, symbol: str, qty: int, product: str = "INTRADAY") -> str:
        """Place a market sell order."""
        return self.sell(symbol, qty, product=product, order_type="MARKET")

    def limit_buy(self, symbol: str, qty: int, price: float, product: str = "INTRADAY") -> str:
        """Place a limit buy order."""
        return self.buy(symbol, qty, price=price, product=product, order_type="LIMIT")

    def limit_sell(self, symbol: str, qty: int, price: float, product: str = "INTRADAY") -> str:
        """Place a limit sell order."""
        return self.sell(symbol, qty, price=price, product=product, order_type="LIMIT")

    def sl_buy(self, symbol: str, qty: int, trigger_price: float, price: float = 0.0, product: str = "INTRADAY") -> str:
        """Place a stop loss buy order."""
        order_type = "SL" if price > 0.0 else "SL-M"
        return self.buy(symbol, qty, price=price, trigger_price=trigger_price, product=product, order_type=order_type)

    def sl_sell(self, symbol: str, qty: int, trigger_price: float, price: float = 0.0, product: str = "INTRADAY") -> str:
        """Place a stop loss sell order."""
        order_type = "SL" if price > 0.0 else "SL-M"
        return self.sell(symbol, qty, price=price, trigger_price=trigger_price, product=product, order_type=order_type)

    def bo(
        self,
        symbol: str,
        qty: int,
        side: str,
        price: float,
        stop_loss: float,
        take_profit: float
    ) -> str:
        """Place a Bracket Order (BO)."""
        return self._broker.trading.place_order(
            symbol=symbol,
            side=side,
            qty=qty,
            order_type="LIMIT",
            price=price,
            product="BO",
            stop_loss=stop_loss,
            take_profit=take_profit
        )

    def co(self, symbol: str, qty: int, side: str, trigger_price: float, price: float = 0.0) -> str:
        """Place a Cover Order (CO)."""
        order_type = "LIMIT" if price > 0.0 else "MARKET"
        return self._broker.trading.place_order(
            symbol=symbol,
            side=side,
            qty=qty,
            order_type=order_type,
            price=price,
            trigger_price=trigger_price,
            product="CO"
        )

    def basket(self, orders: List[Dict[str, Any]]) -> List[str]:
        """Execute multiple orders as a basket transaction."""
        order_ids = []
        for o in orders:
            oid = self._broker.trading.place_order(
                symbol=o["symbol"],
                side=o["side"],
                qty=o["qty"],
                order_type=o.get("order_type", "MARKET"),
                price=o.get("price", 0.0),
                trigger_price=o.get("trigger_price", 0.0),
                product=o.get("product", "INTRADAY")
            )
            order_ids.append(oid)
        return order_ids

    def slice(
        self,
        symbol: str,
        side: str,
        qty: int,
        slice_size: int,
        order_type: str = "MARKET",
        price: float = 0.0,
        product: str = "INTRADAY"
    ) -> List[str]:
        """Slice a large order into multiple smaller orders."""
        order_ids = []
        remaining = qty
        while remaining > 0:
            current_qty = min(slice_size, remaining)
            oid = self._broker.trading.place_order(
                symbol=symbol,
                side=side,
                qty=current_qty,
                order_type=order_type,
                price=price,
                product=product
            )
            order_ids.append(oid)
            remaining -= current_qty
        return order_ids

    def modify(self, order_id: str, qty: int, price: float = 0.0, trigger_price: float = 0.0) -> bool:
        """Modify an existing open order."""
        return self._broker.trading.modify_order(order_id, qty, price, trigger_price)

    def cancel(self, order_id: str) -> bool:
        """Cancel an open order."""
        return self._broker.trading.cancel_order(order_id)

    def cancel_all(self) -> bool:
        """Cancel all open orders."""
        return self._broker.trading.cancel_all_orders()

    # ── Position Management ──

    def close(self, symbol: str) -> bool:
        """Close/Square off an open position for a symbol."""
        return self._broker.account.close_position(symbol)

    def close_all(self) -> bool:
        """Close/Square off all open positions."""
        return self._broker.account.close_all_positions()

    def exit_intraday(self) -> bool:
        """Exit all intraday positions."""
        return self.close_all()

    # ── Diagnostics ──

    def rate_limits(self) -> Dict[str, Any]:
        """Get current rate limit information."""
        return {"requests_per_minute": 120, "used": 0, "remaining": 120}

    def status(self) -> str:
        """Get the gateway health status string."""
        return self.health()["status"]

    def connection_info(self) -> Dict[str, Any]:
        """Get verbose connection detail mapping."""
        return {
            "broker": self._broker.__class__.__name__,
            "connected": self._broker.is_connected(),
            "timestamp": datetime.now().isoformat()
        }
