package com.yxw.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.vision")
public class VisionProperties {

    /**
     * Available values: mock, python, qwen.
     */
    private String engine = "python";

    private int defaultTopK = 4;

    private int maxTopK = 5;

    private final Python python = new Python();

    private final Qwen qwen = new Qwen();

    @Data
    public static class Python {

        private String baseUrl = "http://localhost:8091";

        private String predictPath = "/predict";
    }

    @Data
    public static class Qwen {

        private boolean enabled = true;

        /**
         * Whether to use the local Python classifier/retrieval service as
         * a first-stage visual prior before asking Qwen to make the final decision.
         */
        private boolean preclassifierEnabled = true;

        /**
         * Number of local-model candidates passed to Qwen as prior hints.
         */
        private int preclassifierTopK = 3;

        private String apiKey = "";

        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

        private String model = "qwen3-vl-plus";

        private double temperature = 0.1D;

        private int maxTokens = 1200;
    }
}
