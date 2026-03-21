package com.proteinlens.ingestionservice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;

@Node("Protein")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Protein {

    // STRING-DB stable identifier, e.g. "9606.ENSP00000269305"
    @Id
    private String stringId;

    @Property("preferredName")
    private String preferredName;

    @Property("ncbiTaxonId")
    private Integer ncbiTaxonId;

    // Outgoing INTERACTS_WITH relationships owned by this node
    @Relationship(type = "INTERACTS_WITH", direction = Relationship.Direction.OUTGOING)
    @Builder.Default
    private List<Interaction> interactions = new ArrayList<>();
}
