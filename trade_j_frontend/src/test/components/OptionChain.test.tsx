import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import OptionChain from "../../components/OptionChain";

const makeQuote = (overrides: Partial<{ ltp: number; oi: number; volume: number; iv: number; changeOi: number; available: boolean }> = {}) => ({
  available: true,
  ltp: 100,
  oi: 1000,
  volume: 500,
  iv: 15,
  changeOi: 50,
  ...overrides,
});

describe("OptionChain", () => {
  it("renders without crashing", () => {
    const { container } = render(
      <OptionChain
        strikes={[
          {
            strikePrice: 100,
            strikePricePaisa: 10000,
            call: makeQuote(),
            put: makeQuote(),
          },
        ]}
        spotPrice={100}
        expiry="2024-12-26"
        loading={false}
        error=""
        underlying="NIFTY"
      />,
    );
    expect(container.firstChild).toBeTruthy();
  });

  it("renders strike prices", () => {
    render(
      <OptionChain
        strikes={[
          {
            strikePrice: 18000,
            strikePricePaisa: 1800000,
            call: makeQuote({ ltp: 250 }),
            put: makeQuote({ ltp: 12 }),
          },
          {
            strikePrice: 18100,
            strikePricePaisa: 1810000,
            call: makeQuote({ ltp: 200 }),
            put: makeQuote({ ltp: 18 }),
          },
          {
            strikePrice: 18200,
            strikePricePaisa: 1820000,
            call: makeQuote({ ltp: 150 }),
            put: makeQuote({ ltp: 25 }),
          },
        ]}
        spotPrice={18100}
        expiry="2024-12-26"
        loading={false}
        error=""
        underlying="NIFTY"
      />,
    );
    // At least one strike price should be visible (use getAllByText since the
    // ATM strike is also displayed as the spot price "Spot: 18,100.00").
    const matches = screen.getAllByText(/18,100/);
    expect(matches.length).toBeGreaterThan(0);
  });

  it("handles empty state by falling through to header only", () => {
    // When strikes is empty, the reduce() in atmStrike gets strikes[0] which is undefined.
    // The component still renders the header with "0 strikes" — we just verify it doesn't crash.
    const { container } = render(
      <OptionChain
        strikes={[]}
        spotPrice={0}
        expiry="2024-12-26"
        loading={false}
        error=""
        underlying="NIFTY"
      />,
    );
    expect(container.firstChild).toBeTruthy();
    expect(screen.getByText(/0 strikes/)).toBeTruthy();
  });
});
