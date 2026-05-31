#!/usr/bin/env python3
"""Regenerate docs/CODEBASE_LEAF_INDEX.md from src/main/java and src/test/java."""
from __future__ import annotations

from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "CODEBASE_LEAF_INDEX.md"

MODULES = [
    ("core", "core"),
    ("broker-api", "broker/api"),
    ("broker-core", "broker/core"),
    ("broker-dhan", "broker/dhan"),
    ("broker-upstox", "broker/upstox"),
    ("runtime-disruptor", "runtime/disruptor"),
    ("runtime-hotpath", "runtime/hotpath"),
    ("trading-strategy", "trading/strategy"),
    ("trading-execution", "trading/execution"),
    ("trading-scanner", "trading/scanner"),
    ("trading-institutional-scanner", "trading/institutional-scanner"),
    ("trading-indicators", "trading/indicators"),
    ("trading-simulation", "trading/simulation"),
    ("data-persistence", "data/persistence"),
    ("data-feature-store", "data/feature-store"),
    ("data-historical-ingest", "data/historical-ingest"),
    ("data-analytics", "data/analytics"),
    ("app", "app"),
    ("gateway", "gateway"),
    ("cli", "cli"),
    ("trade-pipeline-platform", "pipeline/platform/trade-pipeline-platform"),
    ("trade-analytics", "pipeline/analytics/trade-analytics"),
    ("trade-node-library", "nodes/trade-node-library"),
    ("trade-experiments", "research/experiment/trade-experiments"),
    ("trade-optimization", "research/optimizer/trade-optimization"),
]


def list_java(base: Path, kind: str) -> list[str]:
    root = base / "src" / kind / "java"
    if not root.exists():
        return []
    return sorted(p.relative_to(root).as_posix() for p in root.rglob("*.java"))


def format_module(gradle: str, path: str, main: list[str], test: list[str]) -> str:
    lines = [f"\n### `:{gradle}` — `{path}/` ({len(main)} main · {len(test)} test)\n"]
    if main:
        pkgs: dict[str, list[str]] = defaultdict(list)
        for f in main:
            parts = f.split("/")
            pkg = "/".join(parts[:-1]) if len(parts) > 1 else "(root)"
            pkgs[pkg].append(parts[-1].replace(".java", ""))
        for pkg in sorted(pkgs):
            classes = ", ".join(sorted(pkgs[pkg]))
            lines.append(f"- **`{pkg}`** ({len(pkgs[pkg])}): {classes}\n")
    else:
        lines.append("- *(no main sources)*\n")
    if test:
        if len(test) <= 30:
            lines.append(
                "- **tests:** "
                + ", ".join(t.replace(".java", "") for t in test)
                + "\n"
            )
        else:
            lines.append(f"- **tests:** {len(test)} files under `src/test/java`\n")
    return "".join(lines)


def main() -> None:
    total_main = total_test = 0
    body: list[str] = []
    for gradle, rel in MODULES:
        base = ROOT / rel
        main = list_java(base, "main")
        test = list_java(base, "test")
        total_main += len(main)
        total_test += len(test)
        body.append(format_module(gradle, rel, main, test))

    arch_test = list_java(ROOT / "architecture-test", "test")
    body.append(
        f"\n### `:architecture-test` — `architecture-test/` (0 main · {len(arch_test)} test)\n"
    )
    for f in arch_test:
        body.append(f"- `{f}`\n")

    fe_base = ROOT / "frontend"
    fe_files: list[str] = []
    if fe_base.exists():
        for ext in ("*.ts", "*.tsx", "*.css"):
            fe_files.extend(
                sorted(
                    p.relative_to(fe_base).as_posix()
                    for p in fe_base.rglob(ext)
                    if "node_modules" not in p.parts
                )
            )
    if fe_files:
        body.append(f"\n### `frontend/` — React console ({len(fe_files)} files, synced into `:app` via `syncFrontend`)\n")
        for f in fe_files[:50]:
            body.append(f"- `{f}`\n")
        if len(fe_files) > 50:
            body.append(f"- *… and {len(fe_files) - 50} more*\n")

    header = f"""# Trade-J — Leaf File Index (generated from repo)

> **Audit date:** auto-generated on write  
> **Totals:** {total_main} main Java · {total_test} test Java (+ {len(arch_test)} architecture-test)  
> **Canonical architecture:** [ARCHITECTURE_REPORT.md](ARCHITECTURE_REPORT.md)

Per-module listing of every `src/main/java` compilation unit, grouped by package.

Regenerate:

```bash
python3 scripts/generate-codebase-leaf-index.py
```

"""
    OUT.write_text(header + "".join(body))
    print(f"Wrote {OUT} ({total_main} main, {total_test} test)")


if __name__ == "__main__":
    main()
