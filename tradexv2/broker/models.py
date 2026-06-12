from datetime import datetime
from typing import List, Optional
from pydantic import BaseModel, Field


class Candle(BaseModel):
    """Canonical representation of a historical bar or candle."""
    timestamp: datetime
    open: float
    high: float
    low: float
    close: float
    volume: int
    oi: int
    symbol: str
    exchange: str
    timeframe: str


class Quote(BaseModel):
    """Canonical representation of a real-time symbol snapshot (Quote)."""
    symbol: str
    ltp: float
    open: float
    high: float
    low: float
    close: float
    volume: int
    oi: int
    bid: float
    ask: float


class MarketDepthLevel(BaseModel):
    """A single price level in the order book."""
    price: float
    qty: int


class MarketDepth(BaseModel):
    """Canonical representation of L2 order book depth."""
    symbol: str
    bids: List[MarketDepthLevel]
    asks: List[MarketDepthLevel]


class OptionChainItem(BaseModel):
    """Canonical representation of an option contract's metrics and Greeks."""
    strike: float
    expiry: str  # Expiry date formatted as YYYY-MM-DD
    option_type: str  # "CE" or "PE"
    ltp: float
    volume: int
    oi: int
    iv: float = 0.0
    delta: float = 0.0
    gamma: float = 0.0
    theta: float = 0.0
    vega: float = 0.0
    rho: float = 0.0


class Order(BaseModel):
    """Canonical representation of a broker transaction order."""
    order_id: str
    symbol: str
    qty: int
    side: str  # "BUY" or "SELL"
    order_type: str  # "MARKET", "LIMIT", "SL", "SL-M"
    price: float
    status: str  # "PENDING", "COMPLETED", "REJECTED", "CANCELLED"
    message: str = ""


class Position(BaseModel):
    """Canonical representation of an open trading position."""
    symbol: str
    qty: int
    entry_price: float
    current_price: float
    pnl: float


class Holding(BaseModel):
    """Canonical representation of equity portfolio holdings."""
    symbol: str
    qty: int
    average_cost: float
    current_price: float
    value: float


class Funds(BaseModel):
    """Canonical representation of account balance and margins."""
    available: float
    collateral: float
    utilized: float


class Instrument(BaseModel):
    """Canonical representation of a resolved tradable instrument."""
    symbol: str
    exchange: str
    security_id: str
    instrument_type: str  # "EQUITY", "FUTURES", "OPTION", "INDEX"
    lot_size: int
    expiry: Optional[str] = None
    strike: Optional[float] = None
    option_type: Optional[str] = None  # "CE", "PE"
