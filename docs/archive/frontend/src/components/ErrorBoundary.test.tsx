import {describe, expect, it, vi, beforeEach} from 'vitest';
import {render, screen} from '@testing-library/react';
import {ErrorBoundary} from './ErrorBoundary';

vi.spyOn(console, 'error').mockImplementation(() => {});

function Bomb(): never {
  throw new Error('💥');
}

function Stable(): React.ReactNode {
  return <div data-testid="stable">All good</div>;
}

describe('ErrorBoundary', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders children when no error is thrown', () => {
    render(
      <ErrorBoundary>
        <Stable />
      </ErrorBoundary>,
    );
    expect(screen.getByTestId('stable')).toHaveTextContent('All good');
  });

  it('renders default fallback when a child throws', () => {
    render(
      <ErrorBoundary>
        <Bomb />
      </ErrorBoundary>,
    );
    expect(screen.getByText('Component crashed')).toBeInTheDocument();
    expect(screen.getByText('💥')).toBeInTheDocument();
  });

  it('renders custom fallback when provided', () => {
    render(
      <ErrorBoundary fallback={<div data-testid="custom">Custom error UI</div>}>
        <Bomb />
      </ErrorBoundary>,
    );
    expect(screen.getByTestId('custom')).toHaveTextContent('Custom error UI');
    expect(screen.queryByText('Component crashed')).not.toBeInTheDocument();
  });

  it('logs the error to console.error', () => {
    render(
      <ErrorBoundary>
        <Bomb />
      </ErrorBoundary>,
    );
    expect(console.error).toHaveBeenCalled();
  });

  it('handles error without a message', () => {
    function SilentBomb(): never {
      throw new Error();
    }
    render(
      <ErrorBoundary>
        <SilentBomb />
      </ErrorBoundary>,
    );
    expect(screen.getByText('Component crashed')).toBeInTheDocument();
  });
});
