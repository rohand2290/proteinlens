"""Complex-detection metrics: predicted clusters scored against curated protein complexes."""
from __future__ import annotations

import csv
import json
from collections import defaultdict
from pathlib import Path

import numpy as np
from scipy.optimize import linear_sum_assignment

MIN_SIZE = 3


def overlap(p: frozenset[str], c: frozenset[str]) -> float:
    """Bader & Hogue (2003) overlap score |P ∩ C|^2 / (|P| |C|)."""
    i = len(p & c)
    return i * i / (len(p) * len(c))


def complex_metrics(pred: list[frozenset[str]], ref: list[frozenset[str]], omega: float = 0.25) -> dict[str, float]:
    """Precision/recall/F1 at overlap threshold omega, plus maximum matching ratio (Nepusz et al., 2012).

    precision: share of predicted clusters (size >= 3) matching some reference complex.
    recall:    share of reference complexes matched by some predicted cluster.
    mmr:       best one-to-one assignment of overlap scores, divided by the number of reference complexes.
    """
    pred = [p for p in pred if len(p) >= MIN_SIZE]
    if not pred or not ref:
        return {"precision": 0.0, "recall": 0.0, "f1": 0.0, "mmr": 0.0, "n_pred": len(pred), "n_ref": len(ref)}
    w = np.array([[overlap(p, c) for p in pred] for c in ref])  # ref x pred
    hit = w >= omega
    precision = float(hit.any(axis=0).mean())
    recall = float(hit.any(axis=1).mean())
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
    rows, cols = linear_sum_assignment(w, maximize=True)
    mmr = float(w[rows, cols].sum() / len(ref))
    return {"precision": precision, "recall": recall, "f1": f1, "mmr": mmr, "n_pred": len(pred), "n_ref": len(ref)}


def load_reference(path: Path) -> dict[str, frozenset[str]]:
    return {k: frozenset(v) for k, v in json.loads(Path(path).read_text())["complexes"].items()}


def restrict_reference(ref: dict[str, frozenset[str]], nodes: set[str]) -> dict[str, frozenset[str]]:
    """Keep members present in the graph; drop complexes left with fewer than 3 members."""
    out = {k: v & nodes for k, v in ref.items()}
    return {k: v for k, v in out.items() if len(v) >= MIN_SIZE}


def split_reference(ref: dict[str, frozenset[str]], seed: int = 42) -> tuple[list[str], list[str]]:
    """Deterministic tune/test halves of the reference complex ids. Tune on the first, report on the second."""
    ids = sorted(ref)
    perm = np.random.default_rng(seed).permutation(len(ids))
    half = len(ids) // 2
    return [ids[i] for i in perm[:half]], [ids[i] for i in perm[half:]]


def read_clusters(path: Path) -> tuple[list[frozenset[str]], set[str]]:
    """Read the Java CLI's stringId,clusterId,centrality CSV. Returns (clusters, all nodes)."""
    groups: dict[str, set[str]] = defaultdict(set)
    nodes: set[str] = set()
    with open(path, newline="") as f:
        for row in csv.DictReader(f):
            groups[row["clusterId"]].add(row["stringId"])
            nodes.add(row["stringId"])
    return [frozenset(g) for g in groups.values()], nodes
