import math
from typing import Dict, List, Tuple


def normal_cdf(x: float) -> float:
    """Standard normal cumulative distribution function (N(x))."""
    return (1.0 + math.erf(x / math.sqrt(2.0))) / 2.0


def normal_pdf(x: float) -> float:
    """Standard normal probability density function (N'(x))."""
    return math.exp(-x**2 / 2.0) / math.sqrt(2.0 * math.pi)


def calculate_d1_d2(S: float, K: float, T: float, r: float, sigma: float) -> Tuple[float, float]:
    """Calculate d1 and d2 components of Black-Scholes formula."""
    if sigma <= 0.0 or T <= 0.0:
        return 0.0, 0.0
    d1 = (math.log(S / K) + (r + (sigma**2) / 2.0) * T) / (sigma * math.sqrt(T))
    d2 = d1 - sigma * math.sqrt(T)
    return d1, d2


def bs_price(S: float, K: float, T: float, r: float, sigma: float, option_type: str) -> float:
    """Calculate Black-Scholes option price."""
    if T <= 0.0:
        if option_type.upper() == "CE":
            return max(S - K, 0.0)
        else:
            return max(K - S, 0.0)

    if sigma <= 0.0:
        if option_type.upper() == "CE":
            return max(S - K * math.exp(-r * T), 0.0)
        else:
            return max(K * math.exp(-r * T) - S, 0.0)

    d1, d2 = calculate_d1_d2(S, K, T, r, sigma)
    if option_type.upper() == "CE":
        return S * normal_cdf(d1) - K * math.exp(-r * T) * normal_cdf(d2)
    else:
        return K * math.exp(-r * T) * normal_cdf(-d2) - S * normal_cdf(-d1)


def bs_delta(S: float, K: float, T: float, r: float, sigma: float, option_type: str) -> float:
    """Calculate option Delta (sensitivity to underlying price changes)."""
    if T <= 0.0:
        return 1.0 if option_type.upper() == "CE" and S >= K else (-1.0 if option_type.upper() == "PE" and S <= K else 0.0)
    d1, _ = calculate_d1_d2(S, K, T, r, sigma)
    if option_type.upper() == "CE":
        return normal_cdf(d1)
    else:
        return normal_cdf(d1) - 1.0


def bs_gamma(S: float, K: float, T: float, r: float, sigma: float) -> float:
    """Calculate option Gamma (sensitivity of Delta to underlying price changes)."""
    if T <= 0.0 or sigma <= 0.0:
        return 0.0
    d1, _ = calculate_d1_d2(S, K, T, r, sigma)
    return normal_pdf(d1) / (S * sigma * math.sqrt(T))


def bs_vega(S: float, K: float, T: float, r: float, sigma: float) -> float:
    """Calculate option Vega (sensitivity to 1% change in volatility)."""
    if T <= 0.0:
        return 0.0
    d1, _ = calculate_d1_d2(S, K, T, r, sigma)
    # Annualized vega is S * sqrt(T) * N'(d1). Divided by 100 for 1% volatility change.
    return (S * math.sqrt(T) * normal_pdf(d1)) / 100.0


def bs_theta(S: float, K: float, T: float, r: float, sigma: float, option_type: str) -> float:
    """Calculate option Theta (decay per day)."""
    if T <= 0.0:
        return 0.0
    d1, d2 = calculate_d1_d2(S, K, T, r, sigma)
    term1 = -(S * normal_pdf(d1) * sigma) / (2.0 * math.sqrt(T))
    if option_type.upper() == "CE":
        term2 = -r * K * math.exp(-r * T) * normal_cdf(d2)
        return (term1 + term2) / 365.0
    else:
        term2 = r * K * math.exp(-r * T) * normal_cdf(-d2)
        return (term1 - term2) / 365.0


def bs_rho(S: float, K: float, T: float, r: float, sigma: float, option_type: str) -> float:
    """Calculate option Rho (sensitivity to 1% change in risk-free interest rate)."""
    if T <= 0.0:
        return 0.0
    _, d2 = calculate_d1_d2(S, K, T, r, sigma)
    if option_type.upper() == "CE":
        return (K * T * math.exp(-r * T) * normal_cdf(d2)) / 100.0
    else:
        return (-K * T * math.exp(-r * T) * normal_cdf(-d2)) / 100.0


def implied_volatility(target_price: float, S: float, K: float, T: float, r: float, option_type: str) -> float:
    """Calculate Implied Volatility (IV) using a hybrid Newton-Raphson / Bisection algorithm."""
    if target_price <= 0.01 or T <= 0.0:
        return 0.0

    # 1. Newton-Raphson numerical search
    sigma = 0.3  # Reasonable starting guess for IV (30%)
    for _ in range(50):
        price = bs_price(S, K, T, r, sigma, option_type)
        diff = price - target_price
        if abs(diff) < 1e-5:
            return sigma
        # bs_vega returns change per 1%, so multiply by 100 to get absolute derivative
        vega_abs = bs_vega(S, K, T, r, sigma) * 100.0
        if vega_abs < 1e-4:
            break
        step = diff / vega_abs
        sigma -= step
        if sigma <= 0.001 or sigma > 4.0:
            break

    # 2. Bisection fallback
    low = 0.0001
    high = 4.0
    for _ in range(50):
        mid = (low + high) / 2.0
        price = bs_price(S, K, T, r, mid, option_type)
        if abs(price - target_price) < 1e-4:
            return mid
        if price > target_price:
            high = mid
        else:
            low = mid
    return (low + high) / 2.0


def calculate_pcr(chain: List[Dict]) -> Tuple[float, float]:
    """Calculate Volume PCR and Open Interest (OI) PCR for an option chain."""
    call_vol = 0
    put_vol = 0
    call_oi = 0
    put_oi = 0

    for item in chain:
        opt_type = item.get("option_type", "").upper()
        vol = item.get("volume", 0)
        oi = item.get("oi", 0)

        if opt_type == "CE":
            call_vol += vol
            call_oi += oi
        elif opt_type == "PE":
            put_vol += vol
            put_oi += oi

    pcr_volume = put_vol / call_vol if call_vol > 0 else 0.0
    pcr_oi = put_oi / call_oi if call_oi > 0 else 0.0
    return pcr_volume, pcr_oi


def calculate_max_pain(chain: List[Dict]) -> float:
    """Calculate the Max Pain strike price (where option sellers experience minimum loss)."""
    if not chain:
        return 0.0

    # Get unique strikes
    strikes = sorted(list(set(item["strike"] for item in chain)))
    if not strikes:
        return 0.0

    min_loss = float("inf")
    max_pain_strike = strikes[0]

    for test_strike in strikes:
        total_loss = 0.0
        for item in chain:
            strike = item["strike"]
            oi = item.get("oi", 0)
            opt_type = item.get("option_type", "").upper()

            if opt_type == "CE":
                loss = max(strike - test_strike, 0.0) * oi
            else:  # PE
                loss = max(test_strike - strike, 0.0) * oi
            total_loss += loss

        if total_loss < min_loss:
            min_loss = total_loss
            max_pain_strike = test_strike

    return max_pain_strike
