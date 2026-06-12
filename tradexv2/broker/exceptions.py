class BrokerError(Exception):
    """Base exception for all TradeXV2 broker operations."""
    pass


class AuthenticationError(BrokerError):
    """Raised when authentication credentials fail, expire, or are missing."""
    pass


class NetworkError(BrokerError):
    """Raised when network calls, WebSockets, or HTTP requests timeout or disconnect."""
    pass


class OrderError(BrokerError):
    """Base exception for order lifecycle errors."""
    pass


class OrderRejectedError(OrderError):
    """Raised when an order placement or modification is rejected by the broker or risk systems."""
    pass


class OrderNotFoundError(OrderError):
    """Raised when attempting to modify, cancel, or query an order ID that does not exist."""
    pass


class InsufficientFundsError(BrokerError):
    """Raised when an order placement fails due to inadequate margin or funds."""
    pass


class InvalidSymbolError(BrokerError):
    """Raised when a symbol cannot be resolved or is not traded on the target segment."""
    pass


class NotSupportedError(BrokerError):
    """Raised when a specific feature (e.g. L2 Depth or Bracket Order) is not supported by the broker."""
    pass
