package com.litchi.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MilvusConfig {

    @Value("${milvus.host}")
    private String milvusHost;

    @Value("${milvus.port}")
    private int milvusPort;

    @Bean(destroyMethod = "")
    public MilvusClientV2 milvusClient() {
        ConnectConfig connectConfig = ConnectConfig.builder()
                .uri("http://" + milvusHost + ":" + milvusPort)
                .connectTimeoutMs(2_000)
                .build();
        return new MilvusClientV2(connectConfig);
    }
}
