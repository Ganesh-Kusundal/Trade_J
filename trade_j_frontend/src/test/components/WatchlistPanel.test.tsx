import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import WatchlistPanel from "../../components/WatchlistPanel";

describe("WatchlistPanel", () => {
  it("renders without crashing", () => {
    const onSelect = vi.fn();
    const onRemove = vi.fn();
    const onAdd = vi.fn();
    const { container } = render(
      <WatchlistPanel
        items={[
          { symbol: "RELIANCE", exchange: "NSE", ltp: 2500, change: 0.5 },
        ]}
        onSelectSymbol={onSelect}
        onRemove={onRemove}
        onAdd={onAdd}
        currentSymbol=""
      />,
    );
    expect(container.firstChild).toBeTruthy();
  });

  it("renders watchlist item symbols", () => {
    const onSelect = vi.fn();
    const onRemove = vi.fn();
    const onAdd = vi.fn();
    render(
      <WatchlistPanel
        items={[
          { symbol: "RELIANCE", exchange: "NSE", ltp: 2500, change: 0.5 },
          { symbol: "TCS", exchange: "NSE", ltp: 3800, change: -1.2 },
          { symbol: "GOLDBEES", exchange: "MCX", ltp: 50, change: 0.1 },
        ]}
        onSelectSymbol={onSelect}
        onRemove={onRemove}
        onAdd={onAdd}
        currentSymbol=""
      />,
    );
    // At least one symbol should be visible.
    expect(screen.getByText("RELIANCE")).toBeTruthy();
  });

  it("handles empty state", () => {
    const onSelect = vi.fn();
    const onRemove = vi.fn();
    const onAdd = vi.fn();
    const { container } = render(
      <WatchlistPanel
        items={[]}
        onSelectSymbol={onSelect}
        onRemove={onRemove}
        onAdd={onAdd}
        currentSymbol=""
      />,
    );
    // With no items, the panel still renders the title bar.
    expect(screen.getByText(/watchlist/i)).toBeTruthy();
    expect(container.firstChild).toBeTruthy();
  });
});
