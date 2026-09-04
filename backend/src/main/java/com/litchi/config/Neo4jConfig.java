package com.litchi.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Neo4jConfig {

    @Value("${spring.neo4j.uri}")
    private String uri;

    @Value("${spring.neo4j.authentication.username}")
    private String username;

    @Value("${spring.neo4j.authentication.password}")
    private String password;

    @Bean(destroyMethod = "close")
    public Driver neo4jDriver() {
        Config config = Config.builder()
                .withConnectionTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .withMaxConnectionPoolSize(10)
                .build();
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password), config);
    }
}
