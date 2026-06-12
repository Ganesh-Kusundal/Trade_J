import React from "react";

interface Props { children: React.ReactNode; widgetId: string; }
interface State { hasError: boolean; error: string; }

export default class WidgetErrorBoundary extends React.Component {
  declare props: Props;
  declare state: State;

  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: "" };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error: error.message };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error(`[WidgetErrorBoundary] Widget '${this.props.widgetId}' failed:`, error, info);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="bg-[#0d1117] border border-[#ef5350]/30 rounded flex items-center justify-center h-full">
          <div className="text-center p-3">
            <div className="text-[#ef5350] text-[10px] font-bold mb-1">Widget Error</div>
            <div className="text-slate-500 text-[9px]">{this.props.widgetId}</div>
            <div className="text-slate-600 text-[8px] mt-1">{this.state.error}</div>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
