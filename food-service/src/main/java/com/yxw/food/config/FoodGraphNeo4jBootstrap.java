package com.yxw.food.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

public class FoodGraphNeo4jBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FoodGraphNeo4jBootstrap.class);

    private final FoodGraphNeo4jSyncService syncService;
    private final FoodGraphNeo4jProperties properties;

    public FoodGraphNeo4jBootstrap(FoodGraphNeo4jSyncService syncService,
                                   FoodGraphNeo4jProperties properties) {
        this.syncService = syncService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.info("Neo4j graph sync skipped because app.graph.neo4j.enabled=false.");
            return;
        }
        if (!properties.isSyncOnStartup()) {
            log.info("Neo4j graph sync skipped because app.graph.neo4j.sync-on-startup=false.");
            return;
        }
        if (syncService == null) {
            log.warn("Neo4j graph sync skipped because sync service is unavailable.");
            return;
        }
        syncService.syncFromRelationalGraph();
    }
}
