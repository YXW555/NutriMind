package com.yxw.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.vision")
public class VisionProperties {

    /**
     * Available values: mock, python.
     */
    private String engine = "python";

    private int defaultTopK = 4;

    private int maxTopK = 5;

    private final Python python = new Python();

    @Data
    public static class Python {

        private String baseUrl = "http://localhost:8091";

        private String predictPath = "/predict";
    }
}
