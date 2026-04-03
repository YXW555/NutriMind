package com.yxw.meal.service;

import com.yxw.meal.dto.AdminKnowledgeDocumentResponse;
import com.yxw.meal.dto.AdminKnowledgeUpsertRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AdminKnowledgeService {

    private static final Pattern FRONT_MATTER_PATTERN = Pattern.compile("^---\\s*\\R(.*?)\\R---\\s*\\R?", Pattern.DOTALL);
    private static final Pattern TITLE_PATTERN = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);

    private final NutritionKnowledgeBaseService nutritionKnowledgeBaseService;
    private final MilvusKnowledgeStoreService milvusKnowledgeStoreService;

    public AdminKnowledgeService(NutritionKnowledgeBaseService nutritionKnowledgeBaseService,
                                 MilvusKnowledgeStoreService milvusKnowledgeStoreService) {
        this.nutritionKnowledgeBaseService = nutritionKnowledgeBaseService;
        this.milvusKnowledgeStoreService = milvusKnowledgeStoreService;
    }

    public List<AdminKnowledgeDocumentResponse> listDocuments() {
        try {
            Path ragDirectory = resolveRagDirectory();
            if (!Files.exists(ragDirectory)) {
                return List.of();
            }

            List<AdminKnowledgeDocumentResponse> documents = new ArrayList<>();
            try (var paths = Files.list(ragDirectory)) {
                paths.filter(path -> path.getFileName().toString().endsWith(".md"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .forEach(path -> documents.add(readDocument(path)));
            }
            return documents;
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read knowledge files");
        }
    }

    public AdminKnowledgeDocumentResponse saveDocument(AdminKnowledgeUpsertRequest request) {
        try {
            Path ragDirectory = resolveRagDirectory();
            Files.createDirectories(ragDirectory);

            String fileName = resolveFileName(request);
            Path target = ragDirectory.resolve(fileName);
            String content = buildDocumentContent(request);
            Files.writeString(target, content, StandardCharsets.UTF_8);

            refreshKnowledgeBase();
            return readDocument(target);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to save knowledge file");
        }
    }

    public void refreshKnowledgeBase() {
        nutritionKnowledgeBaseService.loadKnowledgeBase();
        milvusKnowledgeStoreService.rebuildIndex();
    }

    private AdminKnowledgeDocumentResponse readDocument(Path path) {
        try {
            String rawContent = Files.readString(path, StandardCharsets.UTF_8);
            ParsedKnowledgeDocument parsed = parseDocument(path, rawContent);
            return AdminKnowledgeDocumentResponse.builder()
                    .id(parsed.id())
                    .fileName(parsed.fileName())
                    .title(parsed.title())
                    .authority(parsed.authority())
                    .sourceName(parsed.sourceName())
                    .sourceUrl(parsed.sourceUrl())
                    .tag(parsed.tag())
                    .status(parsed.status())
                    .excerpt(parsed.excerpt())
                    .content(parsed.content())
                    .build();
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read knowledge file");
        }
    }

    private ParsedKnowledgeDocument parseDocument(Path path, String rawContent) {
        Map<String, String> frontMatter = new LinkedHashMap<>();
        String content = rawContent;
        Matcher matcher = FRONT_MATTER_PATTERN.matcher(rawContent);
        if (matcher.find()) {
            frontMatter.putAll(parseFrontMatter(matcher.group(1)));
            content = rawContent.substring(matcher.end()).trim();
        }

        String title = firstNonBlank(frontMatter.get("title"), extractMarkdownTitle(content), baseName(path.getFileName().toString()));
        String excerpt = firstNonBlank(frontMatter.get("excerpt"), firstParagraph(content), "");
        return new ParsedKnowledgeDocument(
                baseName(path.getFileName().toString()),
                path.getFileName().toString(),
                title,
                frontMatter.get("authority"),
                frontMatter.get("source_name"),
                frontMatter.get("source_url"),
                frontMatter.get("tag"),
                firstNonBlank(frontMatter.get("status"), "已启用"),
                excerpt,
                content
        );
    }

    private Map<String, String> parseFrontMatter(String rawFrontMatter) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : rawFrontMatter.split("\\R")) {
            String trimmed = line.trim();
            if (!StringUtils.hasText(trimmed) || trimmed.startsWith("#")) {
                continue;
            }
            int separatorIndex = trimmed.indexOf(':');
            if (separatorIndex <= 0) {
                continue;
            }
            String key = trimmed.substring(0, separatorIndex).trim().toLowerCase(Locale.ROOT);
            String value = trimmed.substring(separatorIndex + 1).trim();
            values.put(key, stripQuotes(value));
        }
        return values;
    }

    private String buildDocumentContent(AdminKnowledgeUpsertRequest request) {
        String normalizedContent = normalizeMarkdownContent(request.getContent(), request.getTitle());
        return """
                ---
                title: \"%s\"
                authority: \"%s\"
                source_name: \"%s\"
                source_url: \"%s\"
                tag: \"%s\"
                status: \"%s\"
                excerpt: \"%s\"
                ---

                %s
                """.formatted(
                escapeYaml(request.getTitle()),
                escapeYaml(request.getAuthority()),
                escapeYaml(request.getSourceName()),
                escapeYaml(nullToEmpty(request.getSourceUrl())),
                escapeYaml(firstNonBlank(request.getTag(), "未分类")),
                escapeYaml(firstNonBlank(request.getStatus(), "已启用")),
                escapeYaml(request.getExcerpt()),
                normalizedContent
        );
    }

    private String normalizeMarkdownContent(String content, String title) {
        String normalized = StringUtils.hasText(content) ? content.trim() : "";
        if (!normalized.startsWith("#")) {
            normalized = "# " + title.trim() + System.lineSeparator() + System.lineSeparator() + normalized;
        }
        return normalized;
    }

    private String resolveFileName(AdminKnowledgeUpsertRequest request) {
        if (StringUtils.hasText(request.getFileName())) {
            String trimmed = request.getFileName().trim();
            return trimmed.endsWith(".md") ? trimmed : trimmed + ".md";
        }
        if (StringUtils.hasText(request.getId())) {
            return request.getId().trim() + ".md";
        }
        return slugify(request.getTitle()) + ".md";
    }

    private Path resolveRagDirectory() {
        Path root = Paths.get("").toAbsolutePath().normalize();
        return root.resolve("meal-service").resolve("src").resolve("main").resolve("resources").resolve("rag");
    }

    private String extractMarkdownTitle(String content) {
        Matcher matcher = TITLE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private String firstParagraph(String content) {
        for (String block : content.split("\\R\\s*\\R")) {
            String candidate = block.trim();
            if (!StringUtils.hasText(candidate) || candidate.startsWith("#")) {
                continue;
            }
            return candidate.replaceAll("\\s+", " ");
        }
        return "";
    }

    private String stripQuotes(String value) {
        if (!StringUtils.hasText(value) || value.length() < 2) {
            return value;
        }
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private String escapeYaml(String value) {
        return nullToEmpty(value).replace("\"", "\\\"");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String baseName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return StringUtils.hasText(normalized) ? normalized : "knowledge-" + System.currentTimeMillis();
    }

    private record ParsedKnowledgeDocument(
            String id,
            String fileName,
            String title,
            String authority,
            String sourceName,
            String sourceUrl,
            String tag,
            String status,
            String excerpt,
            String content
    ) {
    }
}
