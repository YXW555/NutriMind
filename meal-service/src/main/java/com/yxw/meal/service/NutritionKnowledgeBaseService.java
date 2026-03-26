package com.yxw.meal.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NutritionKnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(NutritionKnowledgeBaseService.class);
    private static final Pattern ENGLISH_TOKEN_PATTERN = Pattern.compile("[a-z0-9]{2,}");
    private static final Set<String> STOP_WORDS = Set.of(
            "什么", "怎么", "可以", "需要", "一下", "一个", "这种", "那个", "这样", "那样",
            "今天", "最近", "我的", "我们", "你们", "已经", "还是", "还有", "因为", "所以",
            "一下子", "请问", "帮我", "建议", "安排", "饮食", "营养", "吃饭", "问题"
    );

    private final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
    private volatile List<KnowledgeChunk> chunks = List.of();

    @PostConstruct
    public void loadKnowledgeBase() {
        try {
            List<KnowledgeChunk> loadedChunks = new ArrayList<>();
            for (Resource resource : resourceResolver.getResources("classpath*:rag/*.md")) {
                loadedChunks.addAll(parseDocument(resource));
            }
            chunks = List.copyOf(loadedChunks);
            log.info("Loaded {} nutrition knowledge chunks for advisor RAG", chunks.size());
        } catch (IOException exception) {
            log.warn("Failed to load nutrition knowledge base", exception);
            chunks = List.of();
        }
    }

    public List<KnowledgeChunkDocument> listChunks() {
        return chunks.stream()
                .map(chunk -> new KnowledgeChunkDocument(
                        chunk.chunkId(),
                        chunk.title(),
                        chunk.section(),
                        chunk.content(),
                        buildExcerpt(chunk.content(), 92),
                        chunk.title() + "\n" + chunk.section() + "\n" + chunk.content()
                ))
                .toList();
    }

    public List<KnowledgeHit> search(String query, int limit) {
        if (!StringUtils.hasText(query) || limit <= 0 || chunks.isEmpty()) {
            return List.of();
        }

        String normalizedQuery = normalize(query);
        Set<String> queryTokens = tokenize(normalizedQuery);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        return chunks.stream()
                .map(chunk -> scoreChunk(chunk, normalizedQuery, queryTokens))
                .filter(match -> match.score() > 0)
                .sorted(Comparator.comparingDouble(ChunkMatch::score).reversed())
                .limit(limit)
                .map(match -> new KnowledgeHit(
                        match.chunk().chunkId(),
                        match.chunk().title(),
                        match.chunk().section(),
                        buildExcerpt(match.chunk().content(), 92),
                        firstSentence(match.chunk().content()),
                        match.score()))
                .toList();
    }

    private List<KnowledgeChunk> parseDocument(Resource resource) throws IOException {
        String content = resource.getContentAsString(StandardCharsets.UTF_8);
        if (!StringUtils.hasText(content)) {
            return List.of();
        }

        String title = titleFromFilename(resource.getFilename());
        String section = "";
        StringBuilder paragraph = new StringBuilder();
        List<KnowledgeChunk> parsedChunks = new ArrayList<>();

        for (String rawLine : content.split("\\R")) {
            String line = rawLine.trim();
            if (!StringUtils.hasText(line)) {
                flushChunk(parsedChunks, title, section, paragraph);
                continue;
            }
            if (line.startsWith("# ")) {
                flushChunk(parsedChunks, title, section, paragraph);
                title = line.substring(2).trim();
                continue;
            }
            if (line.startsWith("##")) {
                flushChunk(parsedChunks, title, section, paragraph);
                section = line.replaceFirst("^##+\\s*", "").trim();
                continue;
            }

            String normalizedLine = line.replaceFirst("^[-*]\\s*", "").trim();
            if (!StringUtils.hasText(normalizedLine)) {
                continue;
            }
            if (paragraph.length() > 0) {
                paragraph.append(' ');
            }
            paragraph.append(normalizedLine);
            if (paragraph.length() >= 180 && endsWithSentence(normalizedLine)) {
                flushChunk(parsedChunks, title, section, paragraph);
            }
        }
        flushChunk(parsedChunks, title, section, paragraph);
        return parsedChunks;
    }

    private void flushChunk(List<KnowledgeChunk> parsedChunks, String title, String section, StringBuilder paragraph) {
        if (!StringUtils.hasText(paragraph.toString())) {
            paragraph.setLength(0);
            return;
        }

        String content = paragraph.toString().trim();
        paragraph.setLength(0);
        if (content.length() < 12) {
            return;
        }

        String normalizedText = normalize(title + " " + section + " " + content);
        parsedChunks.add(new KnowledgeChunk(
                buildChunkId(title, section, content),
                title,
                StringUtils.hasText(section) ? section : "重点建议",
                content,
                normalizedText,
                tokenize(normalizedText)
        ));
    }

    private ChunkMatch scoreChunk(KnowledgeChunk chunk, String normalizedQuery, Set<String> queryTokens) {
        double score = 0;
        for (String token : queryTokens) {
            if (chunk.tokens().contains(token)) {
                score += token.length() >= 3 ? 2.6D : 1.8D;
            }
            if (chunk.normalizedText().contains(token)) {
                score += 0.6D;
            }
            if (chunk.title().contains(token) || chunk.section().contains(token)) {
                score += 1.4D;
            }
        }
        if (normalizedQuery.length() >= 4 && chunk.normalizedText().contains(normalizedQuery)) {
            score += 4.0D;
        }
        return new ChunkMatch(chunk, score);
    }

    private Set<String> tokenize(String text) {
        String normalized = normalize(text);
        LinkedHashSet<String> tokens = new LinkedHashSet<>();

        Matcher englishMatcher = ENGLISH_TOKEN_PATTERN.matcher(normalized);
        while (englishMatcher.find()) {
            String token = englishMatcher.group();
            if (!STOP_WORDS.contains(token)) {
                tokens.add(token);
            }
        }

        StringBuilder chineseSegment = new StringBuilder();
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (isChinese(current)) {
                chineseSegment.append(current);
                continue;
            }
            appendChineseTokens(chineseSegment, tokens);
        }
        appendChineseTokens(chineseSegment, tokens);
        return tokens;
    }

    private void appendChineseTokens(StringBuilder segment, Set<String> tokens) {
        if (segment.isEmpty()) {
            return;
        }

        String value = segment.toString();
        segment.setLength(0);
        if (value.length() <= 4 && !STOP_WORDS.contains(value)) {
            tokens.add(value);
        }
        for (int index = 0; index < value.length(); index++) {
            if (index + 2 <= value.length()) {
                addToken(tokens, value.substring(index, index + 2));
            }
            if (index + 3 <= value.length() && value.length() <= 12) {
                addToken(tokens, value.substring(index, index + 3));
            }
        }
    }

    private void addToken(Set<String> tokens, String token) {
        if (!STOP_WORDS.contains(token)) {
            tokens.add(token);
        }
    }

    private String normalize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replace('（', ' ')
                .replace('）', ' ')
                .replace('，', ' ')
                .replace('。', ' ')
                .replace('、', ' ')
                .replace('；', ' ')
                .replace('：', ' ')
                .replace('！', ' ')
                .replace('？', ' ')
                .replace(',', ' ')
                .replace('.', ' ')
                .replace(';', ' ')
                .replace(':', ' ')
                .replace('!', ' ')
                .replace('?', ' ')
                .trim();
    }

    private String firstSentence(String content) {
        for (String part : content.split("[。！？；]")) {
            String sentence = part.trim();
            if (StringUtils.hasText(sentence)) {
                return sentence + "。";
            }
        }
        return content;
    }

    private String buildExcerpt(String content, int maxLength) {
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }

    private boolean endsWithSentence(String line) {
        return line.endsWith("。") || line.endsWith("！") || line.endsWith("？") || line.endsWith("；");
    }

    private boolean isChinese(char value) {
        Character.UnicodeScript script = Character.UnicodeScript.of(value);
        return script == Character.UnicodeScript.HAN;
    }

    private String titleFromFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "营养知识";
        }
        int dotIndex = filename.lastIndexOf('.');
        String baseName = dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
        return baseName.replace('-', ' ');
    }

    private String buildChunkId(String title, String section, String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((title + "|" + section + "|" + content).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < 8; index++) {
                builder.append(String.format("%02x", hash[index]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record KnowledgeChunk(
            String chunkId,
            String title,
            String section,
            String content,
            String normalizedText,
            Set<String> tokens
    ) {
    }

    private record ChunkMatch(KnowledgeChunk chunk, double score) {
    }

    public record KnowledgeChunkDocument(
            String chunkId,
            String title,
            String section,
            String content,
            String excerpt,
            String embeddingText
    ) {
    }

    public record KnowledgeHit(
            String chunkId,
            String title,
            String section,
            String excerpt,
            String summary,
            double score
    ) {
    }
}
