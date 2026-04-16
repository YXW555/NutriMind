package com.yxw.food.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.yxw.food.service.impl.FoodGraphNeo4jService;

@Configuration
@EnableConfigurationProperties(FoodGraphNeo4jProperties.class)
public class FoodGraphNeo4jConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "app.graph.neo4j.enabled", havingValue = "true")
    public Driver neo4jDriver(FoodGraphNeo4jProperties properties) {
        String uri = StringUtils.hasText(properties.getUri()) ? properties.getUri().trim() : "bolt://localhost:7687";
        String username = StringUtils.hasText(properties.getUsername()) ? properties.getUsername().trim() : "neo4j";
        String password = properties.getPassword() == null ? "" : properties.getPassword();
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password));
    }

    @Bean
    public FoodGraphNeo4jBootstrap foodGraphNeo4jBootstrap(ObjectProvider<FoodGraphNeo4jSyncService> syncServiceProvider,
                                                           FoodGraphNeo4jProperties properties) {
        return new FoodGraphNeo4jBootstrap(syncServiceProvider.getIfAvailable(), properties);
    }
}
