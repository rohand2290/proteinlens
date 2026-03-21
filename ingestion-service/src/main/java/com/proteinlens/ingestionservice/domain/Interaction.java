package com.proteinlens.ingestionservice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

/**
 * Relationship entity representing a protein–protein interaction edge.
 * Maps to the INTERACTS_WITH relationship type in Neo4j.
 *
 * Score fields mirror STRING-DB's scoring channels (0–1000 range).
 */
@RelationshipProperties
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Interaction {

    @RelationshipId
    private Long id;

    @TargetNode
    private Protein target;

    /** Combined interaction score */
    @Property("score")
    private Double score;

    /** Neighbourhood genomic score */
    @Property("nscore")
    private Double nscore;

    /** Gene fusion score */
    @Property("fscore")
    private Double fscore;

    /** Phylogenetic co-occurrence score */
    @Property("pscore")
    private Double pscore;

    /** Co-expression score */
    @Property("ascore")
    private Double ascore;

    /** Experimental score */
    @Property("escore")
    private Double escore;

    /** Database-curated score */
    @Property("dscore")
    private Double dscore;

    /** Text-mining score */
    @Property("tscore")
    private Double tscore;
}
