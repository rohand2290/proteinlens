# ProteinLens

A microservice platform for ingesting and exploring protein interaction networks. Pulls data from [STRING-DB](https://string-db.org), stores it as a graph in Neo4j, and publishes events to Kafka for downstream consumers.

## Architecture

```
Client
  │
  ▼
ingestion-service  ──►  STRING-DB API
  │
  ├──►  Neo4j  (protein interaction graph)
  │
  └──►  Kafka  (ingestion.events topic)
```

## Services

| Service | Port | Description |
|---|---|---|
| `ingestion-service` | `8081` | Spring Boot API — orchestrates the ingestion pipeline |
| Neo4j | `7474` (UI), `7687` (Bolt) | Graph database storing proteins and interactions |
| Kafka | `9092` | Event bus for downstream consumers |

## Running locally

**With Docker Compose (recommended):**
```bash
docker-compose up --build
```

All services start with health checks — `ingestion-service` waits for Neo4j and Kafka to be ready before starting.

**Without Docker:**

Start Neo4j and Kafka separately, then:
```bash
cd ingestion-service
NEO4J_PASSWORD=password mvn spring-boot:run
```

## Usage

Trigger an ingestion job:
```bash
curl -X POST http://localhost:8081/api/v1/ingest \
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

## Viewing the graph

Open Neo4j Browser at `http://localhost:7474` (credentials: `neo4j` / `password`):

```cypher
MATCH (a:Protein)-[r:INTERACTS_WITH]->(b:Protein) RETURN a, r, b LIMIT 100
```

## Graph model

```
(Protein {stringId, preferredName, ncbiTaxonId})
  -[:INTERACTS_WITH {score, nscore, fscore, pscore, ascore, escore, dscore, tscore}]->
(Protein)
```

Writes are idempotent — re-ingesting the same proteins updates scores without creating duplicates.

## Deployment

Kubernetes manifests are in [`k8s/`](k8s/) covering the namespace, Neo4j, Kafka, and ingestion-service deployments.
