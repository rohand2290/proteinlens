package com.proteinlens.ingestionservice.repository;

import com.proteinlens.ingestionservice.domain.Interaction;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface InteractionRepository extends Neo4jRepository<Interaction, Long> {

    /**
     * MERGE-based upsert for an interaction edge between two proteins.
     * Uses MERGE on the relationship so re-ingesting the same pair is idempotent.
     */
    @Query("""
            MATCH (a:Protein {stringId: $stringIdA}), (b:Protein {stringId: $stringIdB})
            MERGE (a)-[r:INTERACTS_WITH]->(b)
            SET r.score  = $score,
                r.nscore = $nscore,
                r.fscore = $fscore,
                r.pscore = $pscore,
                r.ascore = $ascore,
                r.escore = $escore,
                r.dscore = $dscore,
                r.tscore = $tscore
            """)
    void mergeInteraction(
            String stringIdA, String stringIdB,
            double score,
            double nscore, double fscore, double pscore,
            double ascore, double escore, double dscore, double tscore
    );
}
