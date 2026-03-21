package com.proteinlens.ingestionservice.repository;

import com.proteinlens.ingestionservice.domain.Protein;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProteinRepository extends Neo4jRepository<Protein, String> {

    Optional<Protein> findByPreferredName(String preferredName);

    List<Protein> findByNcbiTaxonId(Integer ncbiTaxonId);

    /**
     * Finds all proteins that interact with the given protein
     * and have a combined score above the threshold.
     */
    @Query("""
            MATCH (p:Protein {stringId: $stringId})-[r:INTERACTS_WITH]->(partner:Protein)
            WHERE r.score >= $minScore
            RETURN partner, collect(r), collect(p)
            """)
    List<Protein> findInteractionPartners(String stringId, double minScore);

    /**
     * MERGE-based upsert: creates or updates a Protein node without
     * overwriting existing relationships managed elsewhere.
     */
    @Query("""
            MERGE (p:Protein {stringId: $stringId})
            SET p.preferredName = $preferredName,
                p.ncbiTaxonId   = $ncbiTaxonId
            RETURN p
            """)
    Protein mergeProtein(String stringId, String preferredName, Integer ncbiTaxonId);
}
