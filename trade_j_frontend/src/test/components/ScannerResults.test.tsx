import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import ScannerResults from "../../components/ScannerResults";

const makeHit = (overrides: Partial<{ symbol: string; score: number; reasons: string[]; promoted: boolean; assetClass: string }> = {}) => ({
  symbol: "RELIANCE",
  exchangeSegment: "NSE",
  assetClass: "EQUITY",
  underlying: "RELIANCE",
  score: 85.5,
  reasons: ["Breakout", "Volume spike"],
  snapshot: {},
  promoted: false,
  ...overrides,
});

describe("ScannerResults", () => {
  it("renders without crashing", () => {
    const onProfileChange = vi.fn();
    const onRunScan = vi.fn();
    const { container } = render(
      <ScannerResults
        hits={[makeHit()]}
        loading={false}
        error=""
        profile="momentum"
        onProfileChange={onProfileChange}
        onRunScan={onRunScan}
        lastRun={null}
      />,
    );
    expect(container.firstChild).toBeTruthy();
  });

  it("renders hit symbols", () => {
    const onProfileChange = vi.fn();
    const onRunScan = vi.fn();
    render(
      <ScannerResults
        hits={[
          makeHit({ symbol: "RELIANCE" }),
          makeHit({ symbol: "TCS" }),
        ]}
        loading={false}
        error=""
        profile="momentum"
        onProfileChange={onProfileChange}
        onRunScan={onRunScan}
        lastRun={null}
      />,
    );
    // At least one hit's symbol should be visible.
    expect(screen.getByText("RELIANCE")).toBeTruthy();
  });

  it("handles empty state", () => {
    const onProfileChange = vi.fn();
    const onRunScan = vi.fn();
    render(
      <ScannerResults
        hits={[]}
        loading={false}
        error=""
        profile="momentum"
        onProfileChange={onProfileChange}
        onRunScan={onRunScan}
        lastRun={null}
      />,
    );
    expect(screen.getByText(/no scan results yet/i)).toBeTruthy();
  });
});
