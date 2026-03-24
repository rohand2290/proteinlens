# ProteinLens

A platform for ingesting, analyzing, and visualizing protein–protein interaction networks. Pulls interaction data from [STRING-DB](https://string-db.org), stores it as a graph in Neo4j, runs spectral graph analysis as a background job, and surfaces everything through a web dashboard.

---

## System Architecture

```
Browser
  │
  ▼
dashboard-service (:8080)          Next.js UI + Spring Boot API gateway
  │
  ├── POST /api/ingest  ─────────────────────────────► ingestion-service (:8081)
  │                                                           │
  │                                                           ├──► STRING-DB API
  │                                                           │      (protein mappings + interactions)
  │                                                           │
  │                                                           ├──► Neo4j
  │                                                           │      MERGE Protein nodes (idempotent)
  │                                                           │      MERGE INTERACTS_WITH edges
  │                                                           │
  │                                                           └──► Kafka  topic: ingestion.events
  │                                                                        │
  │                                                                        ▼
  │                                                           graph-compute-service
  │                                                           (no HTTP port — Kafka consumer only)
  │                                                                  │
  │                                                                  ├── load subgraph from Neo4j
  │                                                                  ├── build L_sym (normalized Laplacian)
  │                                                                  ├── eigendecomposition via EJML
  │                                                                  ├── spectral gap → kStar
  │                                                                  ├── spectral embedding + K-means++
  │                                                                  ├── power-iteration eigenvector centrality
  │                                                                  └──► Neo4j
  │                                                                         MERGE SpectralResult node
  │                                                                         MERGE COVERS edges
  │
  ├── GET /api/graph/{jobId}    ──► Neo4j  (protein nodes + edges)
  └── GET /api/spectral/{jobId} ──► Neo4j  (eigenvalues, kStar, centrality)
```

### Services

| Service | Port | Stack | Role |
|---|---|---|---|
| `dashboard-service` | `8080` | Spring Boot + embedded Next.js | UI, job submission, graph + spectral visualization |
| `ingestion-service` | `8081` | Spring Boot, WebFlux, Spring Data Neo4j | Orchestrates STRING-DB fetch → Neo4j write → Kafka publish |
| `graph-compute-service` | — | Spring Boot, Kafka consumer, EJML, Commons Math | Event-driven spectral analysis worker |
| Neo4j | `7474` (UI) / `7687` (Bolt) | Neo4j 5 | Stores protein graph and spectral results |
| Kafka | `9092` | KRaft mode (no Zookeeper) | Decouples ingestion from compute |

### Data flow in detail

1. **Ingestion** — the user submits a list of protein identifiers. `ingestion-service` resolves them against STRING-DB (identifier mapping endpoint), fetches all the pairwise interaction scores above the confidence threshold, upserts `Protein` nodes and `INTERACTS_WITH` edges into Neo4j, then publishes an `IngestionEventDto` (jobId + list of STRING IDs) to the `ingestion.events` Kafka topic.

2. **Compute** — `graph-compute-service` consumes the event, loads the relevant subgraph from Neo4j (nodes ordered by `stringId` lexicographically for deterministic matrix indexing), and runs the full spectral pipeline (see below). Results are written back to Neo4j as a `SpectralResult` node linked to each covered `Protein` via `COVERS` edges.

3. **Visualization** — the dashboard polls `GET /api/spectral/{jobId}` until the result is available, then renders the interaction network (with nodes coloured by cluster and sized by centrality) and the eigenvalue spectrum chart.

### Graph model

```
(Protein {stringId, preferredName, ncbiTaxonId, clusterId, centralityScore})
  -[:INTERACTS_WITH {score, nscore, fscore, pscore, ascore, escore, dscore, tscore}]->
(Protein)

(SpectralResult {jobId, eigenvalues, kStar, maxGap, convergedIn})
  -[:COVERS]->
(Protein)
```

`INTERACTS_WITH` scores are raw STRING-DB combined scores (0–1000). Protein writes are MERGE-based, so re-ingesting the same proteins updates scores without creating duplicates.

---

## Mathematics

### 1. Weighted adjacency matrix

Let the protein interaction graph have $n$ nodes. Define the symmetric weighted adjacency matrix $A \in \mathbb{R}^{n \times n}$ where

$$A_{ij} = A_{ji} = w_{ij}$$

and $w_{ij}$ is the STRING-DB combined interaction score (normalized to $[0, 1]$) for proteins $i$ and $j$. Self-loops are excluded. Because STRING-DB returns both the $A \to B$ and $B \to A$ directed edges, the ingestion layer stores both, and `NormalizedLaplacianService` processes only canonical pairs ($i < j$) to avoid double-counting.

### 2. Normalized symmetric Laplacian

Define the diagonal degree matrix $D$ with

$$D_{ii} = \sum_{j} A_{ij}$$

For isolated nodes ($D_{ii} = 0$) the inverse square root is set to zero. The normalized symmetric Laplacian is

$$L_\text{sym} = I - D^{-1/2} A D^{-1/2}$$

This is a positive semi-definite matrix with eigenvalues in $[0, 2]$. The smallest eigenvalue is always 0 (with multiplicity equal to the number of connected components). Nodes with similar interaction profiles produce similar rows in $D^{-1/2} A D^{-1/2}$, so they appear close together in the spectral embedding.

`NormalizedLaplacianService` builds $A$ in sparse CSC format, computes the degree vector by iterating over CSC internals in $O(\text{nnz})$, scales each non-zero entry of $A$ by $d^{-1/2}_i \cdot d^{-1/2}_j$ in a single pass, then computes $L_\text{sym} = I - A_\text{norm}$ using EJML's sparse `CommonOps_DSCC.add`.

### 3. Eigendecomposition

`SpectralAnalyzerService` performs a full symmetric eigendecomposition of (the dense form of) $L_\text{sym}$ using EJML's `DecompositionFactory_DDRM.eig`. This yields $n$ real eigenvalue–eigenvector pairs

$$L_\text{sym} \, u_k = \lambda_k \, u_k, \quad 0 = \lambda_1 \le \lambda_2 \le \cdots \le \lambda_n \le 2$$

sorted in ascending order.

### 4. Spectral gap and natural cluster count $k^*$

The number of natural clusters in the graph is estimated by finding the largest gap between consecutive eigenvalues:

$$i^* = \underset{i \in \{1,\ldots,n-1\}}{\arg\max} \; (\lambda_{i+1} - \lambda_i)$$

$$k^* = i^* + 1, \quad \Delta^* = \lambda_{i^*+1} - \lambda_{i^*}$$

A large gap after $\lambda_{k^*}$ indicates that the first $k^*$ eigenvectors capture the dominant cluster structure while the remaining eigenvectors describe within-cluster variation. $k^*$ and $\Delta^*$ are stored as `kStar` and `maxGap` in the `SpectralResult`.

### 5. Spectral embedding and K-means++ clustering

Build the embedding matrix $U \in \mathbb{R}^{n \times k^*}$ whose columns are the first $k^*$ eigenvectors:

$$U = \begin{bmatrix} u_1 & u_2 & \cdots & u_{k^*} \end{bmatrix}$$

Each row $U_{i,:}$ is then $\ell^{2}$-normalized to the unit sphere. This row-normalization (Ng–Jordan–Weiss trick) reduces sensitivity to eigenvalue magnitude and makes the cluster structure more robust when there are outlier proteins with very high or very low degree.

K-means++ is then run on the rows of $U$ with $k = k^*$ clusters (Apache Commons Math `KMeansPlusPlusClusterer`, seeded deterministically). The resulting cluster assignments map each protein to a cluster in $\{0, \ldots, k^* - 1\}$ and are written back to each `Protein` node's `clusterId` property.

### 6. Eigenvector centrality via power iteration

Classical eigenvector centrality is defined on $A_\text{norm} = I - L_\text{sym} = D^{-1/2} A D^{-1/2}$, the normalized adjacency matrix. Its dominant eigenvector is the fixed point of the iteration

$$v^{(t+1)} = \frac{A_\text{norm} \, v^{(t)}}{\| A_\text{norm} \, v^{(t)} \|_2}$$

initialized with $v^{(0)} = \frac{1}{\sqrt{n}} \mathbf{1}$. The iteration runs up to 1000 steps and stops when $\| v^{(t+1)} - v^{(t)} \|_2 < 10^{-9}$. The sign convention is fixed so that the component with the largest absolute value is positive. Centrality scores are then normalized to $[0, 1]$ by dividing by the maximum component.

A high centrality score indicates a protein that is highly connected to other well-connected proteins in the weighted interaction network — functionally important hub proteins like TP53 consistently score near 1.

---

## Running locally

**With Docker Compose (recommended):**
```bash
docker-compose up --build
```

Services start with health checks. `ingestion-service` and `dashboard-service` wait for Neo4j and Kafka to be healthy before starting. The dashboard is available at `http://localhost:8080` once all services are up.

**Without Docker:**

Start Neo4j and Kafka separately, then run each service:
```bash
# ingestion-service
cd ingestion-service
NEO4J_PASSWORD=password \
  SPRING_NEO4J_URI=bolt://localhost:7687 \
  KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
  mvn spring-boot:run

# graph-compute-service
cd graph-compute-service
NEO4J_PASSWORD=password \
  SPRING_NEO4J_URI=bolt://localhost:7687 \
  KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
  mvn spring-boot:run

# dashboard-service
cd dashboard-service
NEO4J_PASSWORD=password \
  SPRING_NEO4J_URI=bolt://localhost:7687 \
  INGESTION_SERVICE_URL=http://localhost:8081 \
  mvn spring-boot:run
```

---

## Usage

### Via the dashboard

Open `http://localhost:8080`, enter protein identifiers (gene names, UniProt IDs, or STRING IDs), and submit the form. The UI shows the interaction network and eigenvalue spectrum once the job completes.

### Via the API directly

Trigger an ingestion job against `ingestion-service`:
```bash
curl -X POST http://localhost:8081/api/v1/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "identifiers": ["TP53", "MDM2", "BRCA1", "EGFR", "KRAS"],
    "speciesTaxonId": 9606,
    "requiredScore": 400
  }'
```

Or proxy through `dashboard-service`:
```bash
curl -X POST http://localhost:8080/api/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "identifiers": ["TP53", "MDM2", "BRCA1", "EGFR", "KRAS"],
    "speciesTaxonId": 9606,
    "requiredScore": 400
  }'
```

| Field | Required | Description |
|---|---|---|
| `identifiers` | Yes | Gene names, UniProt IDs, or STRING IDs |
| `speciesTaxonId` | No | NCBI taxon ID (default: `9606` — Homo sapiens) |
| `requiredScore` | No | Confidence threshold 0–1000 (default: `400`) |

The response includes a `jobId`. Pass it to the query endpoints below.

---

## API reference

### ingestion-service (`localhost:8081`)

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/ingest` | Start an ingestion job |

### dashboard-service (`localhost:8080`)

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/ingest` | Proxy to ingestion-service |
| `GET` | `/api/graph/{jobId}` | Protein nodes and interaction edges for a job |
| `GET` | `/api/spectral/{jobId}` | Eigenvalue spectrum, kStar, centrality, and spectral gap |

**`GET /api/graph/{jobId}` response:**
```json
{
  "nodes": [
    { "id": "9606.ENSP00000269305", "label": "TP53", "clusterId": 0, "centrality": 0.85 }
  ],
  "edges": [
    { "source": "9606.ENSP00000269305", "target": "9606.ENSP00000367207", "score": 0.999 }
  ]
}
```

**`GET /api/spectral/{jobId}` response:**
```json
{
  "eigenvalues": [0.0, 0.41, 0.87, 1.23],
  "kStar": 3,
  "spectralGapIndex": 2,
  "maxGap": 0.46,
  "convergedIn": 12
}
```

---

## Viewing the graph in Neo4j Browser

Open `http://localhost:7474` (credentials: `neo4j` / `password`):

```cypher
-- All interactions
MATCH (a:Protein)-[r:INTERACTS_WITH]->(b:Protein) RETURN a, r, b LIMIT 100

-- Proteins covered by a specific job
MATCH (sr:SpectralResult {jobId: '<jobId>'})-[:COVERS]->(p:Protein) RETURN p

-- Spectral result for a job
MATCH (sr:SpectralResult {jobId: '<jobId>'}) RETURN sr
```

---

## Deployment

A Helm chart is in [`helm/proteinlens/`](helm/proteinlens/) covering all five services (Neo4j, Kafka, ingestion-service, graph-compute-service, dashboard-service) plus an nginx Ingress.

```bash
# Install into a 'proteinlens' namespace
helm install proteinlens ./helm/proteinlens \
  --namespace proteinlens --create-namespace \
  --set neo4j.password=<secret>
```

Key values (override with `--set` or a custom `values.yaml`):

| Value | Default | Description |
|---|---|---|
| `neo4j.password` | `password` | Neo4j auth password — always override in production |
| `ingress.host` | `proteinlens.local` | Hostname for the nginx Ingress |
| `ingress.className` | `nginx` | Ingress class |
| `graphComputeService.keda.enabled` | `false` | Enable KEDA autoscaling on Kafka consumer lag |
| `graphComputeService.keda.maxReplicaCount` | `5` | Max replicas under KEDA autoscaling |

For local clusters, add `127.0.0.1 proteinlens.local` to `/etc/hosts` to reach the Ingress.

---

## Known issues

**STRING-DB overload:** The public STRING-DB API occasionally returns HTTP 400 with body `"Service Unavailable / system overloaded"` during high-traffic periods. This is transient — wait a few minutes and retry.
