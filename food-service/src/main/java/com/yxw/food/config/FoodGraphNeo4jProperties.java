package com.yxw.food.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.graph.neo4j")
public class FoodGraphNeo4jProperties {

    private boolean enabled;

    private String uri;

    private String username;

    private String password;

    private String database = "neo4j";

    private boolean syncOnStartup = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public boolean isSyncOnStartup() {
        return syncOnStartup;
    }

    public void setSyncOnStartup(boolean syncOnStartup) {
        this.syncOnStartup = syncOnStartup;
    }
}
