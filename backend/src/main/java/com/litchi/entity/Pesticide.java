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
@Node("Pesticide")
public class Pesticide {

    @Id
    @GeneratedValue
    private Long id;

    @Property("name")
    private String name;

    @Property("type")
    private String type;

    @Property("usage")
    private String usage;

    @Property("safetyInterval")
    private Integer safetyInterval;

    @Property("description")
    private String description;
}
