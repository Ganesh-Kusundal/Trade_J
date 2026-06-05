import {describe, expect, it, vi, beforeEach, afterEach} from 'vitest';
import {render, screen, fireEvent, act} from '@testing-library/react';
import {CommandPalette} from './CommandPalette';
import {useStudioStore} from '@/store/useStudioStore';

vi.mock('@/api/client', () => ({
  studioApi: {
    startupCandidates: vi.fn().mockResolvedValue({
      scanDate: '2026-05-29',
      scanTime: '10:00:00',
      requestedScanTime: '09:45:00',
      selectionMode: 'baseline',
      provenance: {},
      candidates: [],
    }),
  },
  scanApi: {latest: vi.fn().mockResolvedValue({run: {}, hits: []})},
}));

function resetStore() {
  useStudioStore.setState({
    symbols: [
      {symbol: 'SBIN', exchangeSegment: 'NSE_EQ', name: 'SBIN'},
      {symbol: 'RELIANCE', exchangeSegment: 'NSE_EQ', name: 'RELIANCE'},
    ],
  });
}

function dispatchKeyDown(code: string, metaKey = true) {
  fireEvent.keyDown(window, {code, metaKey, preventDefault: vi.fn()});
}

describe('CommandPalette', () => {
  beforeEach(() => resetStore());
  afterEach(() => {
    useStudioStore.setState({
      symbols: [],
      activeView: 'chart',
    });
  });

  it('is not visible by default', () => {
    const {container} = render(<CommandPalette />);
    expect(container.innerHTML).toBe('');
  });

  it('opens on Cmd+K', () => {
    render(<CommandPalette />);
    act(() => { dispatchKeyDown('KeyK'); });
    expect(screen.getByPlaceholderText('Type a command or symbol...')).toBeInTheDocument();
  });

  it('opens on Ctrl+K', () => {
    render(<CommandPalette />);
    act(() => { fireEvent.keyDown(window, {code: 'KeyK', ctrlKey: true, preventDefault: vi.fn()}); });
    expect(screen.getByPlaceholderText('Type a command or symbol...')).toBeInTheDocument();
  });

  it('closes when already open and Cmd+K pressed again', () => {
    render(<CommandPalette />);
    act(() => { dispatchKeyDown('KeyK'); });
    expect(screen.getByPlaceholderText('Type a command or symbol...')).toBeInTheDocument();
    act(() => { dispatchKeyDown('KeyK'); });
    expect(screen.queryByPlaceholderText('Type a command or symbol...')).not.toBeInTheDocument();
  });

  it('shows commands and symbols', () => {
    render(<CommandPalette />);
    act(() => { dispatchKeyDown('KeyK'); });
    expect(screen.getByText('/symbol SBIN')).toBeInTheDocument();
    expect(screen.getByText('/symbol RELIANCE')).toBeInTheDocument();
    expect(screen.getByText('/toggle sidebar')).toBeInTheDocument();
    expect(screen.getByText('/view chart')).toBeInTheDocument();
    expect(screen.getByText('/view scanner')).toBeInTheDocument();
    expect(screen.getByText('/view pipeline')).toBeInTheDocument();
    expect(screen.getByText('/view admin')).toBeInTheDocument();
  });

  it('filters commands by query', () => {
    render(<CommandPalette />);
    act(() => { dispatchKeyDown('KeyK'); });
    const input = screen.getByPlaceholderText('Type a command or symbol...');
    fireEvent.change(input, {target: {value: 'scan'}});
    expect(screen.getByText('/view scanner')).toBeInTheDocument();
    expect(screen.queryByText('/view chart')).not.toBeInTheDocument();
    expect(screen.queryByText('/toggle sidebar')).not.toBeInTheDocument();
  });

  it('filters symbols by query', () => {
    render(<CommandPalette />);
    act(() => { dispatchKeyDown('KeyK'); });
    const input = screen.getByPlaceholderText('Type a command or symbol...');
    fireEvent.change(input, {target: {value: 'SBIN'}});
    expect(screen.getByText('/symbol SBIN')).toBeInTheDocument();
    expect(screen.queryByText('/symbol RELIANCE')).not.toBeInTheDocument();
  });

  it('executes a command on click and closes', () => {
    const setActiveView = vi.fn();
    useStudioStore.setState({setActiveView});
    render(<CommandPalette />);
    act(() => { dispatchKeyDown('KeyK'); });
    fireEvent.click(screen.getByText('/view scanner'));
    expect(setActiveView).toHaveBeenCalledWith('scanner');
    expect(screen.queryByPlaceholderText('Type a command or symbol...')).not.toBeInTheDocument();
  });

  it('shows "No matching commands" when no results', () => {
    render(<CommandPalette />);
    act(() => { dispatchKeyDown('KeyK'); });
    const input = screen.getByPlaceholderText('Type a command or symbol...');
    fireEvent.change(input, {target: {value: 'zzzzz'}});
    expect(screen.getByText('No matching commands')).toBeInTheDocument();
  });

  it('closes on Escape', () => {
    render(<CommandPalette />);
    act(() => { dispatchKeyDown('KeyK'); });
    act(() => { fireEvent.keyDown(window, {code: 'Escape'}); });
    expect(screen.queryByPlaceholderText('Type a command or symbol...')).not.toBeInTheDocument();
  });

  it('closes on backdrop click', () => {
    const {container} = render(<CommandPalette />);
    act(() => { dispatchKeyDown('KeyK'); });
    const backdrop = container.firstChild as HTMLElement;
    act(() => { fireEvent.click(backdrop); });
    expect(screen.queryByPlaceholderText('Type a command or symbol...')).not.toBeInTheDocument();
  });

  it('navigates with arrow keys and selects with Enter', () => {
    render(<CommandPalette />);
    act(() => { dispatchKeyDown('KeyK'); });
    const input = screen.getByPlaceholderText('Type a command or symbol...');
    // Arrow down should select the second item
    fireEvent.keyDown(input, {code: 'ArrowDown'});
    // Press enter should execute the selected command
    fireEvent.keyDown(input, {code: 'Enter'});
    expect(screen.queryByPlaceholderText('Type a command or symbol...')).not.toBeInTheDocument();
  });
});
