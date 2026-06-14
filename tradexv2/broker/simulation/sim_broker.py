from datetime import datetime, timedelta
import random
from typing import List, Dict, Callable, Optional, Any

from broker.interfaces import (
    Broker,
    MarketDataProvider,
    TradingProvider,
    AccountProvider,
    OptionsProvider,
    StreamingProvider,
    InstrumentProvider,
)
from broker.models import (
    Candle,
    Quote,
    MarketDepth,
    MarketDepthLevel,
    OptionChainItem,
    Order,
    Position,
    Holding,
    Funds,
    Instrument,
)
from broker.exceptions import InvalidSymbolError, OrderNotFoundError
from broker.analytics.black_scholes import bs_price, bs_delta, bs_gamma, bs_vega, bs_theta, bs_rho, implied_volatility


class SimMarketDataProvider(MarketDataProvider):
    """Generates simulated market data using base prices and random walks."""

    def __init__(self, broker: "SimulationBroker"):
        self.broker = broker
        # Set base prices for common symbols
        self.base_prices = {
            "TCS": 3500.0,
            "RELIANCE": 2500.0,
            "INFY": 1600.0,
            "NIFTY": 22000.0,
            "BANKNIFTY": 48000.0,
        }

    def _get_price(self, symbol: str) -> float:
        base = self.base_prices.get(symbol.upper(), 100.0)
        # Add a tiny random walk jitter
        jitter = random.uniform(-0.002, 0.002) * base
        return round(base + jitter, 2)

    def get_ltp(self, symbols: List[str]) -> Dict[str, float]:
        return {sym: self._get_price(sym) for sym in symbols}

    def get_quote(self, symbol: str) -> Quote:
        price = self._get_price(symbol)
        return Quote(
            symbol=symbol,
            ltp=price,
            open=round(price * 0.99, 2),
            high=round(price * 1.015, 2),
            low=round(price * 0.985, 2),
            close=round(price * 0.995, 2),
            volume=random.randint(50000, 1000000),
            oi=random.randint(1000, 50000),
            bid=round(price - 0.1, 2),
            ask=round(price + 0.1, 2),
        )

    def get_history(
        self, symbol: str, timeframe: str, start_time: datetime, end_time: datetime
    ) -> List[Candle]:
        candles = []
        current_time = start_time
        base_price = self.base_prices.get(symbol.upper(), 100.0)

        # Map timeframe to time delta
        delta_map = {
            "1m": timedelta(minutes=1),
            "5m": timedelta(minutes=5),
            "15m": timedelta(minutes=15),
            "1d": timedelta(days=1),
        }
        step = delta_map.get(timeframe, timedelta(minutes=1))

        price = base_price
        while current_time < end_time:
            # Random walk
            change = random.uniform(-0.005, 0.005) * price
            op = price
            cl = price + change
            hi = max(op, cl) + random.uniform(0, 0.003) * price
            lo = min(op, cl) - random.uniform(0, 0.003) * price
            vol = random.randint(1000, 50000)
            oi = random.randint(500, 10000)

            candles.append(
                Candle(
                    timestamp=current_time,
                    open=round(op, 2),
                    high=round(hi, 2),
                    low=round(lo, 2),
                    close=round(cl, 2),
                    volume=vol,
                    oi=oi,
                    symbol=symbol,
                    exchange="NSE",
                    timeframe=timeframe,
                )
            )
            price = cl
            current_time += step

        return candles

    def get_depth(self, symbol: str, levels: int = 5) -> MarketDepth:
        price = self._get_price(symbol)
        bids = []
        asks = []
        for i in range(levels):
            bids.append(MarketDepthLevel(price=round(price - (0.05 * (i + 1)), 2), qty=random.randint(100, 2000)))
            asks.append(MarketDepthLevel(price=round(price + (0.05 * (i + 1)), 2), qty=random.randint(100, 2000)))
        return MarketDepth(symbol=symbol, bids=bids, asks=asks)


class SimTradingProvider(TradingProvider):
    """Simulates trading matching and execution in memory."""

    def __init__(self, broker: "SimulationBroker"):
        self.broker = broker
        self.order_counter = 0

    def place_order(
        self,
        symbol: str,
        side: str,
        qty: int,
        order_type: str,
        price: float = 0.0,
        trigger_price: float = 0.0,
        product: str = "INTRADAY",
        stop_loss: float = 0.0,
        take_profit: float = 0.0,
    ) -> str:
        self.order_counter += 1
        order_id = f"SIM-ORD-{self.order_counter:05d}"
        
        # Resolve ltp for matching
        ltp = self.broker.market_data._get_price(symbol)
        exec_price = price if order_type == "LIMIT" else ltp
        
        # Verify funds
        required_margin = exec_price * qty
        if required_margin > self.broker.account.funds_state.available:
            from broker.exceptions import InsufficientFundsError
            raise InsufficientFundsError("Insufficient virtual balance to place order")

        # Create canonical order
        order = Order(
            order_id=order_id,
            symbol=symbol,
            qty=qty,
            side=side,
            order_type=order_type,
            price=exec_price,
            status="COMPLETED",  # Immediate execution in simulation mode
        )
        
        # Update inside broker memory
        self.broker.account.order_book_state.append(order)
        self.broker.account.trade_book_state.append({
            "trade_id": f"SIM-TRD-{self.order_counter:05d}",
            "order_id": order_id,
            "symbol": symbol,
            "qty": qty,
            "side": side,
            "price": exec_price,
            "timestamp": datetime.now().isoformat()
        })
        
        # Deduct margin
        self.broker.account.funds_state.available -= required_margin
        self.broker.account.funds_state.utilized += required_margin

        # Update position
        positions = self.broker.account.position_book_state
        found = False
        for p in positions:
            if p.symbol == symbol:
                # Update existing
                prev_qty = p.qty
                if side == "BUY":
                    new_qty = prev_qty + qty
                else:
                    new_qty = prev_qty - qty
                
                p.qty = new_qty
                # Re-calculate entry price using average cost
                p.entry_price = exec_price
                p.current_price = exec_price
                p.pnl = 0.0
                found = True
                break
        
        if not found:
            positions.append(
                Position(
                    symbol=symbol,
                    qty=qty if side == "BUY" else -qty,
                    entry_price=exec_price,
                    current_price=exec_price,
                    pnl=0.0
                )
            )

        return order_id

    def modify_order(
        self, order_id: str, qty: int, price: float = 0.0, trigger_price: float = 0.0
    ) -> bool:
        for order in self.broker.account.order_book_state:
            if order.order_id == order_id:
                order.qty = qty
                if price > 0:
                    order.price = price
                return True
        raise OrderNotFoundError(f"Order ID {order_id} not found in simulator")

    def cancel_order(self, order_id: str) -> bool:
        for order in self.broker.account.order_book_state:
            if order.order_id == order_id:
                order.status = "CANCELLED"
                return True
        raise OrderNotFoundError(f"Order ID {order_id} not found in simulator")

    def cancel_all_orders(self) -> bool:
        for order in self.broker.account.order_book_state:
            if order.status == "PENDING":
                order.status = "CANCELLED"
        return True


class SimAccountProvider(AccountProvider):
    """Retrieves simulated account state."""

    def __init__(self, broker: "SimulationBroker"):
        self.broker = broker
        self.funds_state = Funds(available=1000000.0, collateral=0.0, utilized=0.0)
        self.holdings_state = [
            Holding(symbol="TCS", qty=10, average_cost=3400.0, current_price=3500.0, value=35000.0),
            Holding(symbol="RELIANCE", qty=20, average_cost=2450.0, current_price=2500.0, value=50000.0),
        ]
        self.position_book_state: List[Position] = []
        self.order_book_state: List[Order] = []
        self.trade_book_state: List[Dict[str, Any]] = []

    def get_funds(self) -> Funds:
        return self.funds_state

    def get_holdings(self) -> List[Holding]:
        return self.holdings_state

    def get_positions(self) -> List[Position]:
        # Dynamically update positions with latest prices to calculate unrealized PnL
        for p in self.position_book_state:
            latest = self.broker.market_data._get_price(p.symbol)
            p.current_price = latest
            p.pnl = round(p.qty * (latest - p.entry_price), 2)
        return self.position_book_state

    def get_orders(self) -> List[Order]:
        return self.order_book_state

    def get_trades(self) -> List[Any]:
        return self.trade_book_state

    def close_position(self, symbol: str) -> bool:
        for p in self.position_book_state:
            if p.symbol == symbol and p.qty != 0:
                side = "SELL" if p.qty > 0 else "BUY"
                self.broker.trading.place_order(
                    symbol=symbol,
                    side=side,
                    qty=abs(p.qty),
                    order_type="MARKET"
                )
                return True
        return False

    def close_all_positions(self) -> bool:
        # Avoid concurrent modification issues by copying the list keys/symbols
        symbols = [p.symbol for p in self.position_book_state if p.qty != 0]
        for sym in symbols:
            self.close_position(sym)
        return True


class SimOptionsProvider(OptionsProvider):
    """Simulates option chain using Black-Scholes analytics for Greeks."""

    def __init__(self, broker: "SimulationBroker"):
        self.broker = broker

    def get_option_chain(self, underlying: str, expiry: str) -> List[OptionChainItem]:
        underlying_price = self.broker.market_data._get_price(underlying)
        
        # Calculate time to expiry in years
        exp_date = datetime.strptime(expiry, "%Y-%m-%d")
        days_to_exp = max((exp_date - datetime.now()).days, 0.5)  # minimum 0.5 days for pricing
        T = days_to_exp / 365.0
        
        r = 0.07  # Risk-free rate (7%)
        sigma = 0.15  # Base IV (15%)
        
        # Generate strikes spaced by 100 around underlying LTP
        base_strike = round(underlying_price / 100) * 100
        strikes = [base_strike + (i * 100) for i in range(-5, 6)]
        
        chain = []
        for strike in strikes:
            for opt_type in ["CE", "PE"]:
                # Compute Black-Scholes option price
                price = bs_price(underlying_price, strike, T, r, sigma, opt_type)
                price = max(price, 0.05)  # tick minimum
                
                # Compute Greeks
                delta = bs_delta(underlying_price, strike, T, r, sigma, opt_type)
                gamma = bs_gamma(underlying_price, strike, T, r, sigma)
                vega = bs_vega(underlying_price, strike, T, r, sigma)
                theta = bs_theta(underlying_price, strike, T, r, sigma, opt_type)
                rho = bs_rho(underlying_price, strike, T, r, sigma, opt_type)
                
                # Compute mock volume and open interest decaying from ATM
                distance = abs(strike - underlying_price) / 100
                vol = max(100000 - int(distance * 15000), 500)
                oi = max(50000 - int(distance * 7500), 200)

                chain.append(
                    OptionChainItem(
                        strike=float(strike),
                        expiry=expiry,
                        option_type=opt_type,
                        ltp=round(price, 2),
                        volume=vol,
                        oi=oi,
                        iv=round(sigma * 100, 2),
                        delta=round(delta, 4),
                        gamma=round(gamma, 5),
                        theta=round(theta, 4),
                        vega=round(vega, 4),
                        rho=round(rho, 4),
                    )
                )
        return chain

    def get_expiries(self, underlying: str) -> List[str]:
        # Return next 3 Thursdays as simulated expiries
        expiries = []
        today = datetime.now()
        for i in range(1, 4):
            # Calculate next Thursday
            days_ahead = (3 - today.weekday() + 7) % 7
            if days_ahead == 0:
                days_ahead = 7
            thursday = today + timedelta(days=days_ahead + (i - 1) * 7)
            expiries.append(thursday.strftime("%Y-%m-%d"))
        return expiries


class SimStreamingProvider(StreamingProvider):
    """Mock streaming connection provider."""

    def __init__(self, broker: "SimulationBroker"):
        self.broker = broker
        self.subscriptions = set()

    def subscribe(self, symbols: List[str], callback: Callable[[Any], None]) -> None:
        self.subscriptions.update(symbols)

    def unsubscribe(self, symbols: List[str]) -> None:
        self.subscriptions.difference_update(symbols)

    def connect_stream(self) -> None:
        pass

    def disconnect_stream(self) -> None:
        pass


class SimInstrumentProvider(InstrumentProvider):
    """Resolves standard instrument symbols to canonical symbols."""

    def __init__(self, broker: "SimulationBroker"):
        self.broker = broker

    def search(self, query: str) -> List[Instrument]:
        query_upper = query.upper()
        # Mock search matches standard test symbols
        matches = []
        if "TCS" in query_upper:
            matches.append(Instrument(symbol="TCS", exchange="NSE", security_id="11536", instrument_type="EQUITY", lot_size=1))
        if "RELIANCE" in query_upper:
            matches.append(Instrument(symbol="RELIANCE", exchange="NSE", security_id="2885", instrument_type="EQUITY", lot_size=1))
        if "NIFTY" in query_upper:
            matches.append(Instrument(symbol="NIFTY", exchange="NSE", security_id="13", instrument_type="INDEX", lot_size=50))
            # Futures mock
            matches.append(Instrument(symbol="NIFTY-FUT", exchange="NSE", security_id="NIFTY_F1", instrument_type="FUTURES", lot_size=50, expiry="2026-06-25"))
        return matches

    def lookup(self, symbol: str) -> Instrument:
        matches = self.search(symbol)
        if not matches:
            raise InvalidSymbolError(f"Symbol {symbol} not found in instrument catalog")
        return matches[0]

    def resolve(self, symbol: str) -> Instrument:
        return self.lookup(symbol)

    def get_universe(self, name: str) -> List[str]:
        if "NIFTY50" in name.upper():
            return ["TCS", "RELIANCE", "INFY"]
        return ["TCS"]


class SimulationBroker(Broker):
    """Consolidated Simulation Broker providing an in-memory paper trading workspace."""

    def __init__(self):
        self._market_data = SimMarketDataProvider(self)
        self._trading = SimTradingProvider(self)
        self._account = SimAccountProvider(self)
        self._options = SimOptionsProvider(self)
        self._streaming = SimStreamingProvider(self)
        self._instruments = SimInstrumentProvider(self)
        self._connected = False

    def connect(self) -> bool:
        self._connected = True
        return True

    def disconnect(self) -> bool:
        self._connected = False
        return True

    def is_connected(self) -> bool:
        return self._connected

    def login(self, **credentials: Any) -> bool:
        return True

    @property
    def market_data(self) -> MarketDataProvider:
        return self._market_data

    @property
    def trading(self) -> TradingProvider:
        return self._trading

    @property
    def account(self) -> AccountProvider:
        return self._account

    @property
    def options(self) -> OptionsProvider:
        return self._options

    @property
    def streaming(self) -> StreamingProvider:
        return self._streaming

    @property
    def instruments(self) -> InstrumentProvider:
        return self._instruments
