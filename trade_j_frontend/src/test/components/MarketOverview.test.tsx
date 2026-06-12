import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import MarketOverview from "../../components/MarketOverview";

describe("MarketOverview", () => {
  it("renders without crashing", () => {
    const { container } = render(
      <MarketOverview
        indices={[
          {
            name: "NIFTY 50",
            value: 18000,
            change: 0.5,
            exchange: "NSE",
            isApprox: false,
            isAvailable: true,
          },
        ]}
      />,
    );
    expect(container.firstChild).toBeTruthy();
  });

  it("renders index names", () => {
    render(
      <MarketOverview
        indices={[
          {
            name: "NIFTY 50",
            value: 18000,
            change: 0.5,
            exchange: "NSE",
            isApprox: false,
            isAvailable: true,
          },
          {
            name: "SENSEX",
            value: 60000,
            change: -0.3,
            exchange: "BSE",
            isApprox: false,
            isAvailable: true,
          },
        ]}
      />,
    );
    // Both index names should be visible.
    expect(screen.getByText("NIFTY 50")).toBeTruthy();
    expect(screen.getByText("SENSEX")).toBeTruthy();
  });

  it("handles empty state", () => {
    const { container } = render(<MarketOverview indices={[]} />);
    // With no indices, the panel renders only the time indicator.
    expect(container.firstChild).toBeTruthy();
    expect(screen.getByText(/IST/)).toBeTruthy();
  });
});
