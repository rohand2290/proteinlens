# Complex-detection results

Reproduce: `pl-benchmark` builds the files in this directory from STRING v12.0 and the EBI Complex Portal
(see `ml/proteinlens_ml/data/benchmark.py`); `pl-eval baselines` produces the table below. Checksums are in `SHA256SUMS`.

**Benchmark.** Human physical interactions from STRING v12.0 with combined score ≥ 700, induced on the 2,599 proteins that
belong to a Complex Portal human complex (dropped to the 2,500 that have at least one such edge). 32,155 edges, 44 connected
components (one of 2,317 nodes). No distractor proteins outside complexes are included; the dense eigensolver caps n at 3,000,
and adding 1-hop neighbors gives 6,068 nodes.

**Reference.** 1,169 Complex Portal complexes with ≥ 3 UniProt members that map to STRING (duplicates by member set removed).
Restricted to proteins in the graph, 1,130 complexes keep ≥ 3 members. Their ids are split 50/50 with a fixed seed: hyperparameters
are tuned on one half (565 complexes), and every number below is on the other half (565 complexes).

A predicted cluster matches a complex when the overlap score |P∩C|² / (|P||C|) ≥ 0.25. Precision is the share of predicted clusters
(≥ 3 members) that match a complex, recall is the share of complexes matched, and MMR is the one-to-one maximum matching ratio.

| Method | Implementation | Params (tuned on other half) | Precision | Recall | F1 | MMR | Predicted clusters (≥3) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Spectral, eigengap k | Java CLI | kMax=20 (selects k=44) | 0.538 | 0.064 | 0.114 | 0.030 | 39 |
| Spectral, k sweep | Java CLI | k=300 of {50,100,200,300,400,600,800} | 0.658 | 0.678 | 0.668 | 0.203 | 275 |
| Louvain | networkx | resolution=128 of 0.5…2048 | 0.651 | 0.473 | 0.547 | 0.175 | 229 |
| Leiden | leidenalg | resolution=128 of 0.5…2048 | 0.644 | 0.460 | 0.537 | 0.169 | 225 |
| MCL | markov_clustering | inflation=5.0 of 1.8…6.0 | 0.658 | 0.533 | 0.589 | 0.192 | 243 |

**Reading the table.**
- The eigengap rule fails here: with 44 connected components and kMax=20 its search window is empty, so it returns k=44 and
  recovers little beyond the small components. A handful of large clusters cannot match complexes of 3 to 20 members.
- With k tuned, spectral clustering is the best row, but the margin is not established: one seed, one benchmark, a 7-point k grid
  (finer than none, coarser than the baselines' grids), and 565 test complexes.
- The best parameter of Louvain, Leiden and MCL is interior to its grid, after extending the grids beyond the ranges first tried
  (MCL beyond the 1.8 to 3.0 first planned).
- The eigengap gate (`metrics_baseline.json`) has a tiny MMR baseline, so `metrics_baseline_k300.json` gates the tuned operating point too.
