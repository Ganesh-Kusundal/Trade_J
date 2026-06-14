from abc import ABC, abstractmethod
from datetime import datetime
from typing import List, Dict, Callable, Optional, Any
from broker.models import Candle, Quote, MarketDepth, OptionChainItem, Order, Position, Holding, Funds, Instrument


class MarketDataProvider(ABC):
    """Port for querying market data (LTP, Quotes, History, and Depth)."""

    @abstractmethod
    def get_ltp(self, symbols: List[str]) -> Dict[str, float]:
        """Get Last Traded Price for multiple symbols."""
        pass

    @abstractmethod
    def get_quote(self, symbol: str) -> Quote:
        """Get complete quote snapshot for a symbol."""
        pass

    @abstractmethod
    def get_history(
        self, symbol: str, timeframe: str, start_time: datetime, end_time: datetime
    ) -> List[Candle]:
        """Get historical candles/bars for a symbol within a timeframe."""
        pass

    @abstractmethod
    def get_depth(self, symbol: str, levels: int = 5) -> MarketDepth:
        """Get order book market depth."""
        pass


class TradingProvider(ABC):
    """Port for place, modify, and cancel order execution."""

    @abstractmethod
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
        """Place a new order and return the order_id."""
        pass

    @abstractmethod
    def modify_order(
        self, order_id: str, qty: int, price: float = 0.0, trigger_price: float = 0.0
    ) -> bool:
        """Modify an existing open order."""
        pass

    @abstractmethod
    def cancel_order(self, order_id: str) -> bool:
        """Cancel an open order."""
        pass

    @abstractmethod
    def cancel_all_orders(self) -> bool:
        """Cancel all open orders in the account."""
        pass


class AccountProvider(ABC):
    """Port for retrieving account portfolio state and books."""

    @abstractmethod
    def get_funds(self) -> Funds:
        """Get available and collateral funds."""
        pass

    @abstractmethod
    def get_holdings(self) -> List[Holding]:
        """Get current equity holdings portfolio."""
        pass

    @abstractmethod
    def get_positions(self) -> List[Position]:
        """Get all open and closed positions."""
        pass

    @abstractmethod
    def get_orders(self) -> List[Order]:
        """Get all orders placed in the current session (Order Book)."""
        pass

    @abstractmethod
    def get_trades(self) -> List[Any]:
        """Get all executed trades in the current session (Trade Book)."""
        pass

    @abstractmethod
    def close_position(self, symbol: str) -> bool:
        """Close/Square off an open position for a symbol."""
        pass

    @abstractmethod
    def close_all_positions(self) -> bool:
        """Close/Square off all open positions."""
        pass


class OptionsProvider(ABC):
    """Port for options chain and derivatives calculations."""

    @abstractmethod
    def get_option_chain(self, underlying: str, expiry: str) -> List[OptionChainItem]:
        """Get option chain with Greeks and IV for the underlying symbol."""
        pass

    @abstractmethod
    def get_expiries(self, underlying: str) -> List[str]:
        """Get all available expiry dates for an underlying."""
        pass


class StreamingProvider(ABC):
    """Port for streaming WebSockets real-time data."""

    @abstractmethod
    def subscribe(self, symbols: List[str], callback: Callable[[Any], None]) -> None:
        """Subscribe to live quotes/ticks for symbols and register callback."""
        pass

    @abstractmethod
    def unsubscribe(self, symbols: List[str]) -> None:
        """Unsubscribe from live feeds for symbols."""
        pass

    @abstractmethod
    def connect_stream(self) -> None:
        """Open streaming connection."""
        pass

    @abstractmethod
    def disconnect_stream(self) -> None:
        """Close streaming connection."""
        pass


class InstrumentProvider(ABC):
    """Port for normalized symbol search and resolution."""

    @abstractmethod
    def search(self, query: str) -> List[Instrument]:
        """Search instruments matching query string."""
        pass

    @abstractmethod
    def lookup(self, symbol: str) -> Instrument:
        """Lookup instrument details by symbol name."""
        pass

    @abstractmethod
    def resolve(self, symbol: str) -> Instrument:
        """Resolve symbol string to canonical Instrument object."""
        pass

    @abstractmethod
    def get_universe(self, name: str) -> List[str]:
        """Get symbols belonging to an index/universe (e.g. NIFTY50)."""
        pass


class Broker(ABC):
    """Unified port representing complete broker integration."""

    @abstractmethod
    def connect(self) -> bool:
        """Connect to broker gateway/API."""
        pass

    @abstractmethod
    def disconnect(self) -> bool:
        """Disconnect and clean up resources."""
        pass

    @abstractmethod
    def is_connected(self) -> bool:
        """Check gateway health/connectivity status."""
        pass

    @abstractmethod
    def login(self, **credentials: Any) -> bool:
        """Authenticate with the broker API."""
        pass

    @property
    @abstractmethod
    def market_data(self) -> MarketDataProvider:
        """Access Market Data Provider."""
        pass

    @property
    @abstractmethod
    def trading(self) -> TradingProvider:
        """Access Trading Provider."""
        pass

    @property
    @abstractmethod
    def account(self) -> AccountProvider:
        """Access Account Provider."""
        pass

    @property
    @abstractmethod
    def options(self) -> OptionsProvider:
        """Access Options Provider."""
        pass

    @property
    @abstractmethod
    def streaming(self) -> StreamingProvider:
        """Access Streaming Provider."""
        pass

    @property
    @abstractmethod
    def instruments(self) -> InstrumentProvider:
        """Access Instrument Provider."""
        pass
