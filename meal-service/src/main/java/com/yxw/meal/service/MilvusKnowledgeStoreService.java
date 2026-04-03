package com.yxw.meal.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.yxw.meal.config.RagProperties;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MilvusKnowledgeStoreService {

    private static final Logger log = LoggerFactory.getLogger(MilvusKnowledgeStoreService.class);

    private final RagProperties ragProperties;
    private final NutritionKnowledgeBaseService nutritionKnowledgeBaseService;
    private final QwenModelStudioService qwenModelStudioService;
    private final Gson gson = new Gson();

    private volatile MilvusClientV2 client;
    private volatile boolean indexReady;

    public MilvusKnowledgeStoreService(RagProperties ragProperties,
                                       NutritionKnowledgeBaseService nutritionKnowledgeBaseService,
                                       QwenModelStudioService qwenModelStudioService) {
        this.ragProperties = ragProperties;
        this.nutritionKnowledgeBaseService = nutritionKnowledgeBaseService;
        this.qwenModelStudioService = qwenModelStudioService;
    }

    public boolean isReady() {
        return ragProperties.isEnabled()
                && ragProperties.getMilvus().isEnabled()
                && qwenModelStudioService.isReady();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapOnStartup() {
        if (!ragProperties.isBootstrapOnStartup()) {
            return;
        }
        ensureIndexReady();
    }

    public List<NutritionKnowledgeBaseService.KnowledgeHit> search(String query, int limit) {
        if (!ensureIndexReady()) {
            return List.of();
        }

        try {
            List<Float> queryEmbedding = qwenModelStudioService.embedQuery(query);
            SearchReq request = SearchReq.builder()
                    .collectionName(ragProperties.getMilvus().getCollectionName())
                    .annsField(ragProperties.getMilvus().getVectorField())
                    .metricType(resolveMetricType())
                    .topK(Math.max(1, limit))
                    .outputFields(List.of("title", "section", "content", "excerpt", "authority", "source_name", "source_url"))
                    .data(List.of(new FloatVec(queryEmbedding)))
                    .build();

            SearchResp response = getClient().search(request);
            if (response.getSearchResults().isEmpty()) {
                return List.of();
            }

            List<NutritionKnowledgeBaseService.KnowledgeHit> hits = new ArrayList<>();
            for (SearchResp.SearchResult result : response.getSearchResults().get(0)) {
                Map<String, Object> entity = result.getEntity();
                String title = stringValue(entity.get("title"));
                String section = stringValue(entity.get("section"));
                String content = stringValue(entity.get("content"));
                String excerpt = stringValue(entity.get("excerpt"));
                String authority = stringValue(entity.get("authority"));
                String sourceName = stringValue(entity.get("source_name"));
                String sourceUrl = stringValue(entity.get("source_url"));
                hits.add(new NutritionKnowledgeBaseService.KnowledgeHit(
                        result.getId() == null ? null : String.valueOf(result.getId()),
                        title,
                        section,
                        StringUtils.hasText(excerpt) ? excerpt : truncate(content, 92),
                        firstSentence(content),
                        authority,
                        sourceName,
                        sourceUrl,
                        result.getScore() == null ? 0D : result.getScore()
                ));
            }
            return hits;
        } catch (Exception exception) {
            log.warn("Milvus vector search failed, falling back to local retrieval", exception);
            return List.of();
        }
    }

    public synchronized boolean ensureIndexReady() {
        if (indexReady) {
            return true;
        }
        if (!isReady()) {
            log.info("Skip Milvus bootstrap because external RAG is not ready yet.");
            return false;
        }

        try {
            MilvusClientV2 milvusClient = getClient();
            boolean exists = milvusClient.hasCollection(HasCollectionReq.builder()
                    .collectionName(ragProperties.getMilvus().getCollectionName())
                    .build());

            if (exists && ragProperties.getMilvus().isRecreateOnStartup()) {
                milvusClient.dropCollection(DropCollectionReq.builder()
                        .collectionName(ragProperties.getMilvus().getCollectionName())
                        .build());
                exists = false;
            }

            if (!exists) {
                createCollection(milvusClient);
                indexKnowledgeChunks(milvusClient);
            } else {
                milvusClient.loadCollection(LoadCollectionReq.builder()
                        .collectionName(ragProperties.getMilvus().getCollectionName())
                        .build());
                if (!hasData(milvusClient)) {
                    indexKnowledgeChunks(milvusClient);
                }
            }

            indexReady = true;
            log.info("Milvus knowledge store is ready for advisor RAG.");
            return true;
        } catch (Exception exception) {
            indexReady = false;
            log.warn("Failed to bootstrap Milvus knowledge store.", exception);
            return false;
        }
    }

    public synchronized boolean rebuildIndex() {
        indexReady = false;
        if (!isReady()) {
            log.info("Skip Milvus rebuild because external RAG is not ready yet.");
            return false;
        }

        try {
            MilvusClientV2 milvusClient = getClient();
            boolean exists = milvusClient.hasCollection(HasCollectionReq.builder()
                    .collectionName(ragProperties.getMilvus().getCollectionName())
                    .build());
            if (exists) {
                milvusClient.dropCollection(DropCollectionReq.builder()
                        .collectionName(ragProperties.getMilvus().getCollectionName())
                        .build());
            }
            createCollection(milvusClient);
            indexKnowledgeChunks(milvusClient);
            indexReady = true;
            log.info("Milvus knowledge store rebuilt from latest knowledge documents.");
            return true;
        } catch (Exception exception) {
            log.warn("Failed to rebuild Milvus knowledge store.", exception);
            return false;
        }
    }

    @PreDestroy
    public void close() {
        if (client == null) {
            return;
        }
        try {
            client.close(3);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while closing Milvus client.", exception);
        }
    }

    private void createCollection(MilvusClientV2 milvusClient) {
        IndexParam vectorIndex = IndexParam.builder()
                .fieldName(ragProperties.getMilvus().getVectorField())
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .metricType(resolveMetricType())
                .build();

        CreateCollectionReq request = CreateCollectionReq.builder()
                .collectionName(ragProperties.getMilvus().getCollectionName())
                .primaryFieldName(ragProperties.getMilvus().getPrimaryField())
                .idType(DataType.VarChar)
                .maxLength(128)
                .vectorFieldName(ragProperties.getMilvus().getVectorField())
                .dimension(ragProperties.getQwen().getEmbeddingDimension())
                .enableDynamicField(true)
                .metricType(resolveMetricType().name())
                .indexParams(List.of(vectorIndex))
                .build();
        milvusClient.createCollection(request);
        milvusClient.loadCollection(LoadCollectionReq.builder()
                .collectionName(ragProperties.getMilvus().getCollectionName())
                .build());
    }

    private void indexKnowledgeChunks(MilvusClientV2 milvusClient) {
        List<NutritionKnowledgeBaseService.KnowledgeChunkDocument> chunks = nutritionKnowledgeBaseService.listChunks();
        List<List<Float>> embeddings = qwenModelStudioService.embedTexts(chunks.stream()
                .map(NutritionKnowledgeBaseService.KnowledgeChunkDocument::embeddingText)
                .toList());

        List<JsonObject> rows = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            NutritionKnowledgeBaseService.KnowledgeChunkDocument chunk = chunks.get(index);
            JsonObject row = new JsonObject();
            row.addProperty(ragProperties.getMilvus().getPrimaryField(), chunk.chunkId());
            row.add(ragProperties.getMilvus().getVectorField(), gson.toJsonTree(embeddings.get(index)));
            row.addProperty("title", chunk.title());
            row.addProperty("section", chunk.section());
            row.addProperty("excerpt", chunk.excerpt());
            row.addProperty("content", chunk.content());
            row.addProperty("authority", chunk.authority());
            row.addProperty("source_name", chunk.sourceName());
            row.addProperty("source_url", chunk.sourceUrl());
            rows.add(row);
        }

        milvusClient.insert(InsertReq.builder()
                .collectionName(ragProperties.getMilvus().getCollectionName())
                .data(rows)
                .build());
        milvusClient.loadCollection(LoadCollectionReq.builder()
                .collectionName(ragProperties.getMilvus().getCollectionName())
                .refresh(true)
                .build());
    }

    private boolean hasData(MilvusClientV2 milvusClient) {
        return !milvusClient.query(QueryReq.builder()
                        .collectionName(ragProperties.getMilvus().getCollectionName())
                        .filter(ragProperties.getMilvus().getPrimaryField() + " != \"\"")
                        .limit(1)
                        .outputFields(List.of(ragProperties.getMilvus().getPrimaryField()))
                        .build())
                .getQueryResults()
                .isEmpty();
    }

    private MilvusClientV2 getClient() {
        if (client != null) {
            return client;
        }

        synchronized (this) {
            if (client == null) {
                String uri = normalizeMilvusUri(ragProperties.getMilvus().getUri());
                ConnectConfig config = ConnectConfig.builder()
                        .uri(uri)
                        .build();
                log.info("Connecting advisor RAG to Milvus at {}", uri);
                client = new MilvusClientV2(config);
            }
        }
        return client;
    }

    private String normalizeMilvusUri(String configuredUri) {
        String uri = StringUtils.hasText(configuredUri)
                ? configuredUri.trim()
                : "http://localhost:19530";
        uri = stripWrappingQuotes(uri);
        while (StringUtils.hasText(uri) && endsWithPunctuation(uri)) {
            uri = uri.substring(0, uri.length() - 1).trim();
        }
        if (!uri.startsWith("http://") && !uri.startsWith("https://")) {
            uri = "http://" + uri;
        }
        return uri;
    }

    private String stripWrappingQuotes(String value) {
        if (!StringUtils.hasText(value) || value.length() < 2) {
            return value;
        }
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private boolean endsWithPunctuation(String value) {
        char last = value.charAt(value.length() - 1);
        return last == ','
                || last == ';'
                || last == '，'
                || last == '；';
    }

    private IndexParam.MetricType resolveMetricType() {
        String configured = ragProperties.getMilvus().getMetricType();
        if (!StringUtils.hasText(configured)) {
            return IndexParam.MetricType.COSINE;
        }
        try {
            return IndexParam.MetricType.valueOf(configured.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            log.warn("Unknown Milvus metric type '{}', fallback to COSINE.", configured);
            return IndexParam.MetricType.COSINE;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String firstSentence(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        for (String part : content.split("[。！？；]")) {
            if (StringUtils.hasText(part)) {
                return part.trim() + "。";
            }
        }
        return content;
    }

    private String truncate(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
