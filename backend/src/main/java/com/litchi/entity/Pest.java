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
@Node("Pest")
public class Pest {

    @Id
    @GeneratedValue
    private Long id;

    @Property("name")
    private String name;

    @Property("damage")
    private String damage;

    @Property("controlMethod")
    private String controlMethod;

    @Property("description")
    private String description;
}
