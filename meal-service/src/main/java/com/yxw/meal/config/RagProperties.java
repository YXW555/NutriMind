package com.yxw.meal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    private boolean enabled = true;

    private int topK = 4;

    private boolean bootstrapOnStartup = true;

    private Milvus milvus = new Milvus();

    private Qwen qwen = new Qwen();

    @Data
    public static class Milvus {

        private boolean enabled = true;

        private String uri = "http://localhost:19530";

        private String collectionName = "nutrimind_advisor_knowledge";

        private String primaryField = "chunk_id";

        private String vectorField = "embedding";

        private String metricType = "COSINE";

        private boolean recreateOnStartup = false;
    }

    @Data
    public static class Qwen {

        private boolean enabled = true;

        private String apiKey = "";

        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

        private String chatModel = "qwen-plus";

        private String embeddingModel = "text-embedding-v4";

        private int embeddingDimension = 1024;

        private int embeddingBatchSize = 8;

        private double temperature = 0.3D;
    }
}
