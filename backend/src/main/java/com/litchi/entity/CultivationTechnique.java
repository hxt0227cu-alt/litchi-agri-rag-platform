package com.litchi.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

@Getter
@Setter
@NoArgsConstructor
@Node("CultivationTechnique")
public class CultivationTechnique {

    @Id
    @GeneratedValue
    private Long id;

    @Property("name")
    private String name;

    @Property("description")
    private String description;

    @Property("bestSeason")
    private String bestSeason;
}
