import type { DashboardSpec, WidgetSpec } from "./types";

export class DashboardYamlError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "DashboardYamlError";
  }
}

/**
 * Minimal hand-rolled YAML reader scoped to dashboard.yaml files.
 * Supports:
 *   - top-level scalars: id: foo, title: "My Dashboard"
 *   - key: value pairs
 *   - list items: - value or - key: value
 *   - nested objects via indentation
 *   - quoted strings ("..." or '...')
 *
 * This is intentionally a tiny subset. A real implementation should
 * use a battle-tested YAML library; the platform's primary need is
 * "read dashboard.yaml, build a layout" which the minimal parser
 * supports.
 */
export function parseDashboardYaml(input: string): DashboardSpec {
  const lines = input.split(/\r?\n/).filter((l) => !/^\s*#/.test(l) && l.trim().length > 0);
  const root = parseBlock(lines, 0, -1);
  if (!root || typeof root !== "object") {
    throw new DashboardYamlError("Dashboard YAML must start with a top-level object");
  }
  return root as unknown as DashboardSpec;
}

function parseBlock(lines: string[], start: number, baseIndent: number): { value: unknown; next: number } {
  const obj: Record<string, unknown> = {};
  let i = start;
  while (i < lines.length) {
    const line = lines[i];
    const indent = line.match(/^\s*/)?.[0].length ?? 0;
    if (indent <= baseIndent) break;
    const stripped = line.trim();
    if (stripped.startsWith("- ")) {
      break;
    }
    const colonIdx = stripped.indexOf(":");
    if (colonIdx < 0) {
      throw new DashboardYamlError(`Line ${i + 1}: expected 'key: value'`);
    }
    const key = stripped.substring(0, colonIdx).trim();
    const rest = stripped.substring(colonIdx + 1).trim();
    if (rest === "") {
      const child = parseBlock(lines, i + 1, indent);
      obj[key] = child.value;
      i = child.next;
    } else {
      obj[key] = parseScalar(rest);
      i++;
    }
  }
  return { value: obj, next: i };
}

function parseScalar(raw: string): unknown {
  if (raw === "true") return true;
  if (raw === "false") return false;
  if (raw === "null" || raw === "~") return null;
  if (/^-?\d+$/.test(raw)) return parseInt(raw, 10);
  if (/^-?\d+\.\d+$/.test(raw)) return parseFloat(raw);
  if ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("'") && raw.endsWith("'"))) {
    return raw.substring(1, raw.length - 1);
  }
  return raw;
}

export function validateDashboard(spec: unknown): spec is DashboardSpec {
  if (!spec || typeof spec !== "object") return false;
  const s = spec as Record<string, unknown>;
  if (typeof s.id !== "string" || typeof s.title !== "string") return false;
  if (!s.layout || typeof s.layout !== "object") return false;
  if (!Array.isArray(s.widgets)) return false;
  return true;
}

export function defaultExecutionDashboard(): DashboardSpec {
  return {
    id: "execution",
    title: "Execution",
    description: "Live orders, fills, positions, P&L",
    layout: { kind: "grid", cols: 2, gap: 8 },
    widgets: [
      { id: "orders", type: "orders-table", title: "Active Orders", dataSource: { kind: "readmodel.orders" } },
      { id: "positions", type: "positions-table", title: "Positions", dataSource: { kind: "readmodel.positions" } },
      { id: "pnl", type: "pnl-summary", title: "P&L", dataSource: { kind: "readmodel.pnl" } },
      { id: "signals", type: "signals-table", title: "Recent Signals", dataSource: { kind: "readmodel.signals" } },
    ],
  };
}
