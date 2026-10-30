"""Graph-clustering baselines (Louvain, Leiden, MCL) run on the same frozen benchmark graph as the Java spectral code."""
from __future__ import annotations

import gzip
from pathlib import Path

import igraph as ig
import leidenalg
import markov_clustering as mc
import networkx as nx
import scipy.sparse as sp


def read_graph(path: Path) -> tuple[list[str], sp.csr_matrix]:
    """Read a source/target/score edge list into a sorted node list and a symmetric weighted adjacency."""
    edges: list[tuple[str, str, float]] = []
    with gzip.open(path, "rt") as f:
        for line in f:
            a, b, s = line.rstrip("\n").split("\t")
            edges.append((a, b, float(s)))
    nodes = sorted({x for a, b, _ in edges for x in (a, b)})
    idx = {n: i for i, n in enumerate(nodes)}
    r = [idx[a] for a, _, _ in edges] + [idx[b] for _, b, _ in edges]
    c = [idx[b] for _, b, _ in edges] + [idx[a] for a, _, _ in edges]
    w = [s for _, _, s in edges] * 2
    return nodes, sp.csr_matrix((w, (r, c)), shape=(len(nodes), len(nodes)))


def _groups(nodes: list[str], labels) -> list[frozenset[str]]:
    out: dict[int, set[str]] = {}
    for n, lab in zip(nodes, labels):
        out.setdefault(int(lab), set()).add(n)
    return [frozenset(g) for g in out.values()]


def louvain(nodes: list[str], a: sp.csr_matrix, resolution: float, seed: int = 42) -> list[frozenset[str]]:
    g = nx.from_scipy_sparse_array(a)
    comms = nx.community.louvain_communities(g, weight="weight", resolution=resolution, seed=seed)
    return [frozenset(nodes[i] for i in c) for c in comms]


def leiden(nodes: list[str], a: sp.csr_matrix, resolution: float, seed: int = 42) -> list[frozenset[str]]:
    coo = sp.triu(a, k=1).tocoo()
    g = ig.Graph(n=len(nodes), edges=list(zip(coo.row.tolist(), coo.col.tolist())))
    g.es["weight"] = coo.data.tolist()
    part = leidenalg.find_partition(g, leidenalg.RBConfigurationVertexPartition, weights="weight",
                                    resolution_parameter=resolution, seed=seed)
    return _groups(nodes, part.membership)


def mcl(nodes: list[str], a: sp.csr_matrix, inflation: float) -> list[frozenset[str]]:
    result = mc.run_mcl(a.copy(), inflation=inflation)  # adds self-loops by default
    clusters = mc.get_clusters(result)
    return [frozenset(nodes[i] for i in c) for c in clusters]


RESOLUTIONS = [0.5, 1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048]
INFLATIONS = [1.8, 2.0, 2.2, 2.4, 2.6, 2.8, 3.0, 3.5, 4.0, 5.0, 6.0]

METHODS = {
    "Louvain": (louvain, "resolution", RESOLUTIONS, "networkx"),
    "Leiden": (leiden, "resolution", RESOLUTIONS, "leidenalg"),
    "MCL": (mcl, "inflation", INFLATIONS, "markov_clustering"),
}


def sweep(name: str, nodes: list[str], a: sp.csr_matrix):
    """Yield (param_value, clusters) over the method's grid."""
    fn, _, grid, _ = METHODS[name]
    for v in grid:
        yield v, fn(nodes, a, v)
