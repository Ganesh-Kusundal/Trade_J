import {Component} from 'react';
import type {ErrorInfo, ReactNode} from 'react';

interface Props {children: ReactNode; fallback?: ReactNode}
interface State {hasError: boolean; error: Error | null}

export class ErrorBoundary extends Component<Props, State> {
  state: State = {hasError: false, error: null};

  static getDerivedStateFromError(error: Error) {
    return {hasError: true, error};
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('ErrorBoundary caught:', error, info);
  }

  render() {
    if (this.state.hasError) {
      return this.props.fallback || (
        <div className="flex items-center justify-center h-full bg-[#09090b] text-zinc-400 font-mono text-xs p-8">
          <div className="text-center">
            <div className="text-[#ef4444] text-lg mb-2">⚠</div>
            <div className="mb-1">Component crashed</div>
            <div className="text-zinc-600">{this.state.error?.message}</div>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
