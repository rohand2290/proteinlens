"""pl-eval: score clusterings against reference complexes, run baselines, and gate CI on regressions."""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from proteinlens_ml.eval.complexes import (complex_metrics, load_reference, read_clusters,
                                           restrict_reference, split_reference)

GATED = ("f1", "mmr")


def _subset(ref, ids):
    return [ref[i] for i in ids]


def cmd_complexes(a: argparse.Namespace) -> int:
    clusters, nodes = read_clusters(a.clusters)
    ref = restrict_reference(load_reference(a.reference), nodes)
    _, test_ids = split_reference(ref, a.seed)
    m = complex_metrics(clusters, _subset(ref, test_ids))
    m["n_ref_total"] = len(ref)
    print(json.dumps(m, indent=2))
    if a.out:
        a.out.write_text(json.dumps(m, indent=2) + "\n")
    if a.write_baseline:
        a.write_baseline.write_text(json.dumps(m, indent=2) + "\n")
        print(f"wrote baseline {a.write_baseline}", file=sys.stderr)
    if a.baseline:
        base = json.loads(a.baseline.read_text())
        bad = [f"{k}: {m[k]:.4f} vs baseline {base[k]:.4f} (drop {base[k] - m[k]:.4f} > {a.max_drop})"
               for k in GATED if base[k] - m[k] > a.max_drop]
        if bad:
            print("EVAL GATE FAILED\n  " + "\n  ".join(bad), file=sys.stderr)
            return 1
        print("eval gate passed", file=sys.stderr)
    return 0


def cmd_baselines(a: argparse.Namespace) -> int:
    from proteinlens_ml.eval import baselines as bl

    nodes, adj = bl.read_graph(a.graph)
    ref = restrict_reference(load_reference(a.reference), set(nodes))
    tune_ids, test_ids = split_reference(ref, a.seed)
    tune, test = _subset(ref, tune_ids), _subset(ref, test_ids)
    rows = []

    def best(name, impl, param, candidates):
        scored = []
        for v, c in candidates:
            m = complex_metrics(c, tune)
            scored.append((m["f1"], m["mmr"], v, c))
        _, _, v, c = max(scored, key=lambda t: (t[0], t[1]))
        rows.append({"method": name, "impl": impl, "params": f"{param}={v}", **complex_metrics(c, test)})
        print(f"{name}: tuned {param}={v}", file=sys.stderr)

    for name, (_, param, _, impl) in bl.METHODS.items():
        best(name, impl, param, bl.sweep(name, nodes, adj))

    if a.spectral_auto:
        cl, _ = read_clusters(a.spectral_auto)
        rows.insert(0, {"method": "Spectral, eigengap k", "impl": "Java CLI", "params": "kMax=20",
                        **complex_metrics(cl, test)})
    if a.spectral_k:
        cands = [(int(Path(p).stem.rsplit("_k", 1)[1]), read_clusters(Path(p))[0]) for p in a.spectral_k]
        best("Spectral, k sweep", "Java CLI", "k", cands)
        rows.insert(1 if a.spectral_auto else 0, rows.pop())

    info = {"nodes": len(nodes), "n_ref_total": len(ref), "n_tune": len(tune), "n_test": len(test)}
    print(json.dumps({"info": info, "rows": rows}, indent=2))
    lines = ["| Method | Implementation | Params | Precision | Recall | F1 | MMR | Predicted clusters (≥3) |",
             "| --- | --- | --- | --- | --- | --- | --- | --- |"]
    for r in rows:
        lines.append(f"| {r['method']} | {r['impl']} | {r['params']} | {r['precision']:.3f} | {r['recall']:.3f} "
                     f"| {r['f1']:.3f} | {r['mmr']:.3f} | {r['n_pred']} |")
    table = "\n".join(lines)
    print("\n" + table, file=sys.stderr)
    if a.out:
        a.out.write_text(json.dumps({"info": info, "rows": rows}, indent=2) + "\n")
    if a.table:
        a.table.write_text(table + "\n")
    return 0


def main() -> None:
    p = argparse.ArgumentParser(prog="pl-eval")
    sub = p.add_subparsers(dest="cmd", required=True)

    c = sub.add_parser("complexes", help="score a cluster CSV against reference complexes")
    c.add_argument("--clusters", type=Path, required=True)
    c.add_argument("--reference", type=Path, required=True)
    c.add_argument("--baseline", type=Path)
    c.add_argument("--max-drop", type=float, default=0.02)
    c.add_argument("--write-baseline", type=Path)
    c.add_argument("--out", type=Path)
    c.add_argument("--seed", type=int, default=42)
    c.set_defaults(fn=cmd_complexes)

    b = sub.add_parser("baselines", help="run Louvain/Leiden/MCL and assemble the results table")
    b.add_argument("--graph", type=Path, required=True)
    b.add_argument("--reference", type=Path, required=True)
    b.add_argument("--spectral-auto", type=Path, help="Java CLI clusters CSV with eigengap-chosen k")
    b.add_argument("--spectral-k", type=Path, nargs="*", help="Java CLI clusters CSVs named *_k<K>.csv")
    b.add_argument("--out", type=Path)
    b.add_argument("--table", type=Path)
    b.add_argument("--seed", type=int, default=42)
    b.set_defaults(fn=cmd_baselines)

    a = p.parse_args()
    sys.exit(a.fn(a))


if __name__ == "__main__":
    main()
