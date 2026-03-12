package com.litchi.entity;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

@Data
@Node("LitchiVariety")
public class LitchiVariety {

    @Id
    @GeneratedValue
    private Long id;

    @Property("name")
    private String name;

    @Property("origin")
    private String origin;

    @Property("taste")
    private String taste;

    @Property("ripeningSeason")
    private String ripeningSeason;

    @Property("yield")
    private String yield;

    @Property("description")
    private String description;
}
