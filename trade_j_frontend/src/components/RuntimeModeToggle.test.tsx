import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, waitFor, fireEvent, act } from "@testing-library/react";
import RuntimeModeToggle from "./RuntimeModeToggle";

const mockFetch = vi.fn();

beforeEach(() => {
  mockFetch.mockReset();
  vi.stubGlobal("fetch", mockFetch);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("RuntimeModeToggle", () => {
  it("rendersLiveByDefault_whenGetReturnsLive", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ mode: "LIVE", userToggleable: true }),
    });
    render(<RuntimeModeToggle />);
    const live = await screen.findByTestId("runtime-mode-live");
    expect(live).toBeInTheDocument();
    expect(live.className).toMatch(/ef5350/);
    expect(screen.getByTestId("runtime-mode-paper").className).not.toMatch(/3b82f6/);
  });

  it("rendersPaperByDefault_whenGetReturnsPaper", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ mode: "PAPER", userToggleable: true }),
    });
    render(<RuntimeModeToggle />);
    const paper = await screen.findByTestId("runtime-mode-paper");
    expect(paper).toBeInTheDocument();
    expect(paper.className).toMatch(/3b82f6/);
    expect(screen.getByTestId("runtime-mode-live").className).not.toMatch(/ef5350/);
  });

  it("clickingPaper_callsPutAndUpdatesState", async () => {
    mockFetch
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ mode: "LIVE", userToggleable: true }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ mode: "PAPER", userToggleable: true }),
      });
    const onToast = vi.fn();
    render(<RuntimeModeToggle onToast={onToast} />);
    const live = await screen.findByTestId("runtime-mode-live");
    const paper = screen.getByTestId("runtime-mode-paper");
    await act(async () => {
      fireEvent.click(paper);
    });
    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledTimes(2);
    });
    const [url, init] = mockFetch.mock.calls[1];
    expect(url).toBe("/api/v1/runtime/mode");
    expect(init.method).toBe("PUT");
    expect(JSON.parse(init.body)).toEqual({ mode: "PAPER" });
    await waitFor(() => {
      expect(paper.className).toMatch(/3b82f6/);
    });
    expect(onToast).toHaveBeenCalledWith("Switched to PAPER mode", "success");
  });

  it("clickingLive_callsPutAndUpdatesState", async () => {
    mockFetch
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ mode: "PAPER", userToggleable: true }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ mode: "LIVE", userToggleable: true }),
      });
    const onToast = vi.fn();
    render(<RuntimeModeToggle onToast={onToast} />);
    const live = await screen.findByTestId("runtime-mode-live");
    await waitFor(() => {
      expect(live.className).not.toMatch(/ef5350/);
    });
    await act(async () => {
      fireEvent.click(live);
    });
    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledTimes(2);
    });
    const [url, init] = mockFetch.mock.calls[1];
    expect(url).toBe("/api/v1/runtime/mode");
    expect(init.method).toBe("PUT");
    expect(JSON.parse(init.body)).toEqual({ mode: "LIVE" });
    await waitFor(() => {
      expect(live.className).toMatch(/ef5350/);
    });
    expect(onToast).toHaveBeenCalledWith("Switched to LIVE mode", "success");
  });

  it("failure_showsErrorToastAndRevertsState", async () => {
    mockFetch
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ mode: "LIVE", userToggleable: true }),
      })
      .mockResolvedValueOnce({
        ok: false,
        status: 500,
        json: async () => ({ error: "internal" }),
      });
    const onToast = vi.fn();
    render(<RuntimeModeToggle onToast={onToast} />);
    const live = await screen.findByTestId("runtime-mode-live");
    const paper = screen.getByTestId("runtime-mode-paper");
    // initial state: LIVE active
    expect(live.className).toMatch(/ef5350/);
    await act(async () => {
      fireEvent.click(paper);
    });
    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledTimes(2);
    });
    // After failure, the toggle should revert to LIVE
    await waitFor(() => {
      expect(live.className).toMatch(/ef5350/);
    });
    expect(paper.className).not.toMatch(/3b82f6/);
    expect(onToast).toHaveBeenCalledWith(
      expect.stringContaining("Failed to switch to PAPER"),
      "error"
    );
  });
});
