package com.intenthealer.core.engine.sharing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.intenthealer.core.model.LocatorInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Enables sharing of successful heal patterns across projects.
 *
 * Features:
 * - Export successful heal patterns
 * - Import patterns from other projects
 * - Pattern matching with similarity scoring
 * - Central pattern registry support
 * - Privacy-preserving pattern anonymization
 */
public class PatternSharingService {

    private static final Logger logger = LoggerFactory.getLogger(PatternSharingService.class);

    private final Map<String, SharedPattern> localPatterns;
    private final Map<String, SharedPattern> importedPatterns;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final SharingConfig config;

    public PatternSharingService() {
        this(SharingConfig.defaults());
    }

    public PatternSharingService(SharingConfig config) {
        this.config = config;
        this.localPatterns = new ConcurrentHashMap<>();
        this.importedPatterns = new ConcurrentHashMap<>();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Add a successful heal pattern for sharing.
     */
    public void addPattern(HealPatternData data) {
        SharedPattern pattern = createPattern(data);
        localPatterns.put(pattern.patternId(), pattern);
        logger.debug("Added pattern: {}", pattern.patternId());
    }

    /**
     * Find matching patterns for a given locator.
     */
    public List<PatternMatch> findMatchingPatterns(LocatorInfo failedLocator, String pageContext) {
        List<PatternMatch> matches = new ArrayList<>();

        // Search local patterns
        for (SharedPattern pattern : localPatterns.values()) {
            double similarity = calculateSimilarity(failedLocator, pageContext, pattern);
            if (similarity >= config.minMatchSimilarity()) {
                matches.add(new PatternMatch(pattern, similarity, PatternSource.LOCAL));
            }
        }

        // Search imported patterns
        for (SharedPattern pattern : importedPatterns.values()) {
            double similarity = calculateSimilarity(failedLocator, pageContext, pattern);
            if (similarity >= config.minMatchSimilarity()) {
                matches.add(new PatternMatch(pattern, similarity, PatternSource.IMPORTED));
            }
        }

        // Sort by similarity
        matches.sort(Comparator.comparingDouble(PatternMatch::similarity).reversed());

        return matches.stream().limit(10).toList();
    }

    /**
     * Export patterns to a file.
     */
    public ExportResult exportPatterns(Path outputPath) throws IOException {
        List<SharedPattern> exportable = localPatterns.values().stream()
                .filter(p -> p.successCount() >= config.minSuccessesForExport())
                .filter(p -> p.successRate() >= config.minSuccessRateForExport())
                .map(this::anonymizePattern)
                .toList();

        PatternExport export = new PatternExport(
                config.projectId(),
                Instant.now(),
                exportable,
                getExportMetadata()
        );

        Files.createDirectories(outputPath.getParent());
        objectMapper.writeValue(outputPath.toFile(), export);

        logger.info("Exported {} patterns to {}", exportable.size(), outputPath);

        return new ExportResult(exportable.size(), outputPath.toString());
    }

    /**
     * Import patterns from a file.
     */
    public ImportResult importPatterns(Path inputPath) throws IOException {
        PatternExport imported = objectMapper.readValue(inputPath.toFile(), PatternExport.class);

        int added = 0;
        int skipped = 0;

        for (SharedPattern pattern : imported.patterns()) {
            // Verify pattern quality
            if (pattern.successRate() < config.minSuccessRateForImport()) {
                skipped++;
                continue;
            }

            // Check for duplicates
            if (isDuplicate(pattern)) {
                skipped++;
                continue;
            }

            importedPatterns.put(pattern.patternId(), pattern);
            added++;
        }

        logger.info("Imported {} patterns from {} (skipped {})", added, inputPath, skipped);

        return new ImportResult(added, skipped, imported.sourceProject());
    }

    /**
     * Sync with central registry.
     */
    public SyncResult syncWithRegistry() throws IOException, InterruptedException {
        if (config.registryUrl() == null) {
            return SyncResult.noRegistry();
        }

        // Upload local patterns
        List<SharedPattern> toUpload = localPatterns.values().stream()
                .filter(p -> p.successCount() >= config.minSuccessesForExport())
                .filter(p -> !p.syncedToRegistry())
                .map(this::anonymizePattern)
                .toList();

        int uploaded = 0;
        if (!toUpload.isEmpty()) {
            uploaded = uploadToRegistry(toUpload);
        }

        // Download new patterns
        int downloaded = downloadFromRegistry();

        return new SyncResult(uploaded, downloaded, Instant.now());
    }

    /**
     * Get pattern statistics.
     */
    public PatternStats getStats() {
        int totalLocal = localPatterns.size();
        int totalImported = importedPatterns.size();

        double avgSuccessRate = localPatterns.values().stream()
                .mapToDouble(SharedPattern::successRate)
                .average().orElse(0);

        int highQuality = (int) localPatterns.values().stream()
                .filter(p -> p.successRate() >= 0.9)
                .count();

        Map<String, Integer> byCategory = localPatterns.values().stream()
                .collect(Collectors.groupingBy(
                        SharedPattern::category,
                        Collectors.summingInt(p -> 1)
                ));

        return new PatternStats(
                totalLocal,
                totalImported,
                avgSuccessRate,
                highQuality,
                byCategory
        );
    }

    /**
     * Clear all imported patterns.
     */
    public void clearImported() {
        importedPatterns.clear();
        logger.info("Cleared imported patterns");
    }

    // Private methods

    private SharedPattern createPattern(HealPatternData data) {
        String patternId = generatePatternId(data);
        String locatorSignature = createLocatorSignature(data.originalLocator());
        String healSignature = createLocatorSignature(data.healedLocator());

        return new SharedPattern(
                patternId,
                locatorSignature,
                healSignature,
                data.originalLocator().getStrategy().name(),
                data.healedLocator().getStrategy().name(),
                categorizePattern(data),
                data.pageUrlPattern(),
                data.intent(),
                1,
                data.success() ? 1 : 0,
                data.success() ? 1.0 : 0.0,
                data.confidence(),
                Instant.now(),
                false,
                data.tags()
        );
    }

    private SharedPattern anonymizePattern(SharedPattern pattern) {
        // Remove potentially identifying information
        return new SharedPattern(
                pattern.patternId(),
                pattern.originalSignature(),
                pattern.healedSignature(),
                pattern.originalStrategy(),
                pattern.healedStrategy(),
                pattern.category(),
                anonymizeUrl(pattern.pageUrlPattern()),
                pattern.intent(),
                pattern.totalAttempts(),
                pattern.successCount(),
                pattern.successRate(),
                pattern.avgConfidence(),
                pattern.lastUsed(),
                pattern.syncedToRegistry(),
                pattern.tags()
        );
    }

    private String anonymizeUrl(String url) {
        if (url == null) return null;
        // Keep only path structure, remove domain
        return url.replaceAll("https?://[^/]+", "")
                .replaceAll("[a-f0-9]{8,}", "{id}")
                .replaceAll("\\d{4,}", "{num}");
    }

    private String generatePatternId(HealPatternData data) {
        String input = data.originalLocator().getStrategy() +
                data.originalLocator().getValue() +
                data.healedLocator().getStrategy() +
                data.healedLocator().getValue();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString().substring(0, 16);
        }
    }

    private String createLocatorSignature(LocatorInfo locator) {
        // Create a normalized signature that can match similar locators
        String value = locator.getValue();

        // Normalize common patterns
        value = value.replaceAll("[a-f0-9]{8,}", "{hash}");
        value = value.replaceAll("\\d+", "{n}");
        value = value.replaceAll("'[^']*'", "'{str}'");
        value = value.replaceAll("\"[^\"]*\"", "\"{str}\"");

        return locator.getStrategy() + ":" + value;
    }

    private String categorizePattern(HealPatternData data) {
        String intent = data.intent() != null ? data.intent().toLowerCase() : "";
        String locator = data.originalLocator().getValue().toLowerCase();

        if (intent.contains("button") || intent.contains("click") || locator.contains("btn") || locator.contains("button")) {
            return "button";
        }
        if (intent.contains("input") || intent.contains("field") || intent.contains("enter") || locator.contains("input")) {
            return "input";
        }
        if (intent.contains("link") || locator.contains("link") || locator.contains("href")) {
            return "link";
        }
        if (intent.contains("select") || intent.contains("dropdown") || locator.contains("select")) {
            return "select";
        }
        if (intent.contains("text") || intent.contains("label") || intent.contains("message")) {
            return "text";
        }
        if (intent.contains("image") || locator.contains("img") || locator.contains("icon")) {
            return "image";
        }

        return "general";
    }

    private double calculateSimilarity(LocatorInfo locator, String pageContext, SharedPattern pattern) {
        double similarity = 0.0;

        // Strategy match
        if (locator.getStrategy().equals(pattern.originalStrategy())) {
            similarity += 0.2;
        }

        // Signature similarity
        String currentSignature = createLocatorSignature(locator);
        double signatureSim = calculateStringSimilarity(currentSignature, pattern.originalSignature());
        similarity += signatureSim * 0.4;

        // Page context match
        if (pageContext != null && pattern.pageUrlPattern() != null) {
            String normalizedContext = anonymizeUrl(pageContext);
            if (normalizedContext.equals(pattern.pageUrlPattern())) {
                similarity += 0.2;
            } else if (normalizedContext.contains(pattern.pageUrlPattern()) ||
                    pattern.pageUrlPattern().contains(normalizedContext)) {
                similarity += 0.1;
            }
        }

        // Category match from locator
        String category = categorizeFromLocator(locator);
        if (category.equals(pattern.category())) {
            similarity += 0.1;
        }

        // Quality bonus
        if (pattern.successRate() > 0.9) {
            similarity += 0.1;
        }

        return Math.min(1.0, similarity);
    }

    private String categorizeFromLocator(LocatorInfo locator) {
        String value = locator.getValue().toLowerCase();
        if (value.contains("btn") || value.contains("button")) return "button";
        if (value.contains("input") || value.contains("text") || value.contains("field")) return "input";
        if (value.contains("link") || value.contains("href")) return "link";
        if (value.contains("select") || value.contains("option")) return "select";
        return "general";
    }

    private double calculateStringSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0;
        if (s1.equals(s2)) return 1.0;

        // Levenshtein-based similarity
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;

        int distance = levenshteinDistance(s1, s2);
        return 1.0 - ((double) distance / maxLen);
    }

    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= s2.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(
                        dp[i - 1][j] + 1,
                        dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
            }
        }

        return dp[s1.length()][s2.length()];
    }

    private boolean isDuplicate(SharedPattern pattern) {
        return localPatterns.containsKey(pattern.patternId()) ||
                importedPatterns.containsKey(pattern.patternId());
    }

    private int uploadToRegistry(List<SharedPattern> patterns) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(Map.of(
                "projectId", config.projectId(),
                "patterns", patterns
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.registryUrl() + "/api/patterns"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.registryToken())
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200 || response.statusCode() == 201) {
            // Mark as synced
            for (SharedPattern pattern : patterns) {
                localPatterns.computeIfPresent(pattern.patternId(),
                        (k, v) -> new SharedPattern(
                                v.patternId(), v.originalSignature(), v.healedSignature(),
                                v.originalStrategy(), v.healedStrategy(), v.category(),
                                v.pageUrlPattern(), v.intent(), v.totalAttempts(),
                                v.successCount(), v.successRate(), v.avgConfidence(),
                                v.lastUsed(), true, v.tags()
                        ));
            }
            return patterns.size();
        }

        logger.warn("Failed to upload patterns: {}", response.statusCode());
        return 0;
    }

    private int downloadFromRegistry() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.registryUrl() + "/api/patterns?projectId=" + config.projectId()))
                .header("Authorization", "Bearer " + config.registryToken())
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            logger.warn("Failed to download patterns: {}", response.statusCode());
            return 0;
        }

        List<SharedPattern> downloaded = objectMapper.readValue(
                response.body(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, SharedPattern.class)
        );

        int added = 0;
        for (SharedPattern pattern : downloaded) {
            if (!isDuplicate(pattern)) {
                importedPatterns.put(pattern.patternId(), pattern);
                added++;
            }
        }

        return added;
    }

    private Map<String, Object> getExportMetadata() {
        return Map.of(
                "exportVersion", "1.0",
                "healerVersion", "1.0.0",
                "totalPatterns", localPatterns.size()
        );
    }

    // Records

    public record HealPatternData(
            LocatorInfo originalLocator,
            LocatorInfo healedLocator,
            String pageUrlPattern,
            String intent,
            double confidence,
            boolean success,
            List<String> tags
    ) {}

    public record SharedPattern(
            String patternId,
            String originalSignature,
            String healedSignature,
            String originalStrategy,
            String healedStrategy,
            String category,
            String pageUrlPattern,
            String intent,
            int totalAttempts,
            int successCount,
            double successRate,
            double avgConfidence,
            Instant lastUsed,
            boolean syncedToRegistry,
            List<String> tags
    ) {}

    public record PatternMatch(
            SharedPattern pattern,
            double similarity,
            PatternSource source
    ) {}

    public enum PatternSource { LOCAL, IMPORTED, REGISTRY }

    public record PatternExport(
            String sourceProject,
            Instant exportTime,
            List<SharedPattern> patterns,
            Map<String, Object> metadata
    ) {}

    public record ExportResult(int patternCount, String outputPath) {}

    public record ImportResult(int added, int skipped, String sourceProject) {}

    public record SyncResult(int uploaded, int downloaded, Instant syncTime) {
        public static SyncResult noRegistry() {
            return new SyncResult(0, 0, null);
        }
    }

    public record PatternStats(
            int totalLocal,
            int totalImported,
            double avgSuccessRate,
            int highQualityCount,
            Map<String, Integer> byCategory
    ) {}

    public record SharingConfig(
            String projectId,
            String registryUrl,
            String registryToken,
            double minMatchSimilarity,
            int minSuccessesForExport,
            double minSuccessRateForExport,
            double minSuccessRateForImport
    ) {
        public static SharingConfig defaults() {
            return new SharingConfig(
                    "default",
                    null,
                    null,
                    0.7,
                    5,
                    0.8,
                    0.7
            );
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String projectId = "default";
            private String registryUrl;
            private String registryToken;
            private double minMatchSimilarity = 0.7;
            private int minSuccessesForExport = 5;
            private double minSuccessRateForExport = 0.8;
            private double minSuccessRateForImport = 0.7;

            public Builder projectId(String projectId) {
                this.projectId = projectId;
                return this;
            }

            public Builder registryUrl(String url) {
                this.registryUrl = url;
                return this;
            }

            public Builder registryToken(String token) {
                this.registryToken = token;
                return this;
            }

            public Builder minMatchSimilarity(double similarity) {
                this.minMatchSimilarity = similarity;
                return this;
            }

            public Builder minSuccessesForExport(int count) {
                this.minSuccessesForExport = count;
                return this;
            }

            public Builder minSuccessRateForExport(double rate) {
                this.minSuccessRateForExport = rate;
                return this;
            }

            public Builder minSuccessRateForImport(double rate) {
                this.minSuccessRateForImport = rate;
                return this;
            }

            public SharingConfig build() {
                return new SharingConfig(
                        projectId, registryUrl, registryToken,
                        minMatchSimilarity, minSuccessesForExport,
                        minSuccessRateForExport, minSuccessRateForImport
                );
            }
        }
    }
}
