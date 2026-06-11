import React from "react";

interface Props {
  children: React.ReactNode;
  fallback?: React.ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export default class ErrorBoundary extends React.Component {
  declare props: Props;
  declare state: State;

  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error("[ErrorBoundary]", error, info.componentStack);
  }

  render(): React.ReactNode {
    if (this.state.hasError) {
      if (this.props.fallback) return this.props.fallback;
      return (
        <div className="flex items-center justify-center h-full bg-[#0d1117] text-slate-400 font-mono text-[11px] p-4">
          <div className="text-center">
            <div className="text-[#ef5350] font-bold text-sm mb-2">Component Error</div>
            <div className="text-[10px] text-slate-500 max-w-xs">
              {this.state.error?.message || "An unexpected error occurred"}
            </div>
            <button
              onClick={() => (this as any).setState({ hasError: false, error: null })}
              className="mt-3 px-3 py-1 bg-[#21262d] text-slate-300 rounded text-[10px] font-bold hover:bg-[#30363d] cursor-pointer">
              Retry
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
