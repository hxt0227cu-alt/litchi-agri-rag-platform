package com.litchi.entity;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

@Data
@Node("Disease")
public class Disease {

    @Id
    @GeneratedValue
    private Long id;

    @Property("name")
    private String name;

    @Property("symptom")
    private String symptom;

    @Property("cause")
    private String cause;

    @Property("highSeason")
    private String highSeason;

    @Property("description")
    private String description;
}
