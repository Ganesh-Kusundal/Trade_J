import { useState, useEffect } from "react";
import { fetchOptionChain, type OptionStrike } from "../api/options";

export function useOptionChain(underlying: string, exchangeSegment: string) {
  const [strikes, setStrikes] = useState<OptionStrike[]>([]);
  const [spotPrice, setSpotPrice] = useState(0);
  const [expiry, setExpiry] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    const loadOptionChain = async () => {
      try {
        setLoading(true);
        setError("");

        // Get nearest Friday expiry
        const today = new Date();
        const daysUntilFriday = (5 - today.getDay() + 7) % 7 || 7;
        const nearestExpiry = new Date(today);
        nearestExpiry.setDate(today.getDate() + daysUntilFriday);
        const expiryStr = nearestExpiry.toISOString().split("T")[0];

        const response = await fetchOptionChain(underlying, exchangeSegment, expiryStr);
        setStrikes(response.strikes);
        setSpotPrice(response.spotPrice);
        setExpiry(response.expiry);
      } catch (err: any) {
        setError(err.message || "Failed to load option chain");
      } finally {
        setLoading(false);
      }
    };

    loadOptionChain();
  }, [underlying, exchangeSegment]);

  return { strikes, spotPrice, expiry, loading, error };
}
