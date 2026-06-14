import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import TradesList from "../../components/TradesList";

describe("TradesList", () => {
  it("renders without crashing", () => {
    const { container } = render(
      <TradesList
        trades={[
          { time: 1700000000, price: 100.5, quantity: 10, side: "BUY" },
        ]}
      />,
    );
    expect(container.firstChild).toBeTruthy();
  });

  it("renders trade tick prices", () => {
    render(
      <TradesList
        trades={[
          { time: 1700000000, price: 100.5, quantity: 10, side: "BUY" },
          { time: 1700000001, price: 100.75, quantity: 5, side: "SELL" },
          { time: 1700000002, price: 101.0, quantity: 3, side: "BUY" },
        ]}
      />,
    );
    // "100.50", "100.75", "101.00" should all appear.
    expect(screen.getByText(/100\.50/)).toBeTruthy();
    expect(screen.getByText(/100\.75/)).toBeTruthy();
    expect(screen.getByText(/101\.00/)).toBeTruthy();
  });

  it("handles empty state", () => {
    render(<TradesList trades={[]} />);
    expect(
      screen.getByText(/waiting for trades|market closed|no trades/i),
    ).toBeTruthy();
  });
});
