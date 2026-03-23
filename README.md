# ProteinLens

A microservice platform for ingesting, analyzing, and visualizing protein–protein interaction networks. Pulls interaction data from [STRING-DB](https://string-db.org), stores it as a graph in Neo4j, runs spectral graph analysis as a background job, and surfaces everything through a web dashboard.

## Architecture

```
Browser
  │
  ▼
dashboard-service (:8080)
  │  POST /api/ingest  ──────────────────►  ingestion-service (:8081)
  │                                                │
  │                                                ├──►  STRING-DB API
  │                                                ├──►  Neo4j  (Protein nodes + INTERACTS_WITH edges)
  │                                                └──►  Kafka  (ingestion.events)
  │                                                              │
  │                                                              ▼
  │                                                  graph-compute-service
  │                                                      │  (no HTTP port — background worker)
  │                                                      └──►  Neo4j  (SpectralResult nodes + COVERS edges)
  │
  ├──►  GET /api/graph/{jobId}    ──►  Neo4j
  └──►  GET /api/spectral/{jobId} ──►  Neo4j
```

## Services

| Service | Port | Description |
|---|---|---|
| `dashboard-service` | `8080` | Spring Boot + embedded Next.js UI — job submission and graph visualization |
| `ingestion-service` | `8081` | Spring Boot API — orchestrates the ingestion pipeline |
| `graph-compute-service` | — | Background worker — consumes Kafka events, runs spectral analysis, writes results to Neo4j |
| Neo4j | `7474` (UI), `7687` (Bolt) | Graph database — protein interaction graph and spectral results |
| Kafka | `9092` | Event bus (KRaft mode, no Zookeeper) |

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
| `GET` | `/api/spectral/{jobId}` | Eigenvalue spectrum, kStar, and spectral gap for a job |

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

## Graph model

```
(Protein {stringId, preferredName, ncbiTaxonId, clusterId, centralityScore})
  -[:INTERACTS_WITH {score, nscore, fscore, pscore, ascore, escore, dscore, tscore}]->
(Protein)

(SpectralResult {jobId, eigenvalues, kStar, maxGap, convergedIn})
  -[:COVERS]->
(Protein)
```

Protein writes are idempotent — re-ingesting the same proteins updates scores without creating duplicates (MERGE-based upserts).

## Spectral analysis

After a successful ingestion event is published to Kafka, `graph-compute-service` automatically:

1. Loads the protein subgraph from Neo4j using the STRING IDs from the event.
2. Builds the normalized Laplacian matrix of the interaction graph.
3. Computes eigenvalues via EJML.
4. Determines `kStar` (optimal cluster count) from the largest spectral gap.
5. Persists a `SpectralResult` node linked to all covered proteins via `COVERS` edges.

## Viewing the graph

Open Neo4j Browser at `http://localhost:7474` (credentials: `neo4j` / `password`):

```cypher
-- All interactions
MATCH (a:Protein)-[r:INTERACTS_WITH]->(b:Protein) RETURN a, r, b LIMIT 100

-- Proteins covered by a specific job
MATCH (sr:SpectralResult {jobId: '<jobId>'})-[:COVERS]->(p:Protein) RETURN p

-- Spectral result for a job
MATCH (sr:SpectralResult {jobId: '<jobId>'}) RETURN sr
```

## Known issues

**STRING-DB overload:** The public STRING-DB API occasionally returns HTTP 400 with body `"Service Unavailable / system overloaded"` during high-traffic periods. This is transient — wait a few minutes and retry.

## Deployment

Kubernetes manifests are in [`k8s/`](k8s/) covering:

| File | Contents |
|---|---|
| `namespace.yml` | `proteinlens` namespace |
| `neo4j.yml` | Neo4j StatefulSet + Service |
| `kafka.yml` | Kafka Deployment + Service |
| `ingestion-service.yml` | Deployment + ClusterIP Service |

Services use a `neo4j-secret` Kubernetes Secret for the Neo4j password and communicate via in-cluster DNS (e.g. `neo4j.proteinlens.svc.cluster.local`).
