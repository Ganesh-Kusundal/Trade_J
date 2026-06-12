import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import OrderBook from "../../components/OrderBook";

describe("OrderBook", () => {
  it("renders without crashing", () => {
    const { container } = render(
      <OrderBook
        bids={[{ price: 100, quantity: 10, orders: 1 }]}
        asks={[{ price: 101, quantity: 8, orders: 1 }]}
        lastPrice={100.5}
        priceChange={0.5}
        symbol="RELIANCE"
      />,
    );
    expect(container.firstChild).toBeTruthy();
  });

  it("renders top-of-book prices", () => {
    render(
      <OrderBook
        bids={[
          { price: 100, quantity: 10, orders: 1 },
          { price: 99.95, quantity: 15, orders: 2 },
        ]}
        asks={[
          { price: 101, quantity: 8, orders: 1 },
          { price: 101.05, quantity: 12, orders: 3 },
        ]}
        lastPrice={100.5}
        priceChange={0.5}
        symbol="RELIANCE"
      />,
    );
    // 101.00 should appear in the asks section (formatted as "101.00").
    expect(screen.getByText(/101\.00/)).toBeTruthy();
  });

  it("handles empty state", () => {
    render(
      <OrderBook
        bids={[]}
        asks={[]}
        lastPrice={0}
        priceChange={0}
        symbol="RELIANCE"
      />,
    );
    // Empty-state: "No asks" and "No bids" both render — verify at least one is present.
    const all = screen.getAllByText(/no asks|no bids|market closed/i);
    expect(all.length).toBeGreaterThan(0);
  });
});
