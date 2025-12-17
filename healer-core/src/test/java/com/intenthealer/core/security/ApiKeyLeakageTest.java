package com.intenthealer.core.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security audit tests to detect potential API key leakage.
 */
@DisplayName("API Key Leakage Security Tests")
class ApiKeyLeakageTest {

    // Patterns that indicate potential API key leakage
    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
            // Hardcoded OpenAI keys
            Pattern.compile("sk-[a-zA-Z0-9]{40,}"),
            // Hardcoded Anthropic keys
            Pattern.compile("sk-ant-[a-zA-Z0-9-]{40,}"),
            // AWS access keys
            Pattern.compile("AKIA[A-Z0-9]{16}"),
            // AWS secret keys (with context)
            Pattern.compile("(?i)secret[_-]?key[\"']?\\s*[:=]\\s*[\"']?[A-Za-z0-9/+=]{30,}"),
            // Generic hardcoded API keys with value assignment
            Pattern.compile("(?i)api[_-]?key\\s*=\\s*\"[a-zA-Z0-9-_]{20,}\""),
            // Bearer tokens hardcoded
            Pattern.compile("(?i)Bearer\\s+[a-zA-Z0-9-_.]{50,}")
    );

    // Patterns that are exceptions (test data, examples, masked values)
    private static final List<Pattern> EXCEPTION_PATTERNS = List.of(
            Pattern.compile("\\*\\*\\*REDACTED\\*\\*\\*"),
            Pattern.compile("\\$\\{[^}]+\\}"),  // Environment variable references
            Pattern.compile("test[-_]?key"),
            Pattern.compile("your[-_]?api[-_]?key"),
            Pattern.compile("example[-_]?key"),
            Pattern.compile("placeholder"),
            Pattern.compile("sk-\\.\\.\\."),  // Masked key example
            Pattern.compile("sk-ant-\\.\\.\\."),
            Pattern.compile("getenv\\("),  // Reading from environment
            Pattern.compile("System\\.getenv"),
            Pattern.compile("@Test"),  // Test annotations
            Pattern.compile("mock|fake|dummy", Pattern.CASE_INSENSITIVE)
    );

    @Nested
    @DisplayName("Source Code Scanning")
    class SourceCodeScanning {

        @Test
        @DisplayName("main source should not contain hardcoded API keys")
        void mainSourceShouldNotContainApiKeys() throws IOException {
            Path srcMain = findSrcMain();
            if (srcMain == null || !Files.exists(srcMain)) {
                System.out.println("Skipping: src/main not found");
                return;
            }

            List<String> violations = scanDirectory(srcMain, ".java");

            if (!violations.isEmpty()) {
                violations.forEach(System.out::println);
            }
            assertTrue(violations.isEmpty(),
                    "Found " + violations.size() + " potential API key leakage(s) in source");
        }

        @Test
        @DisplayName("configuration files should not contain hardcoded API keys")
        void configFilesShouldNotContainApiKeys() throws IOException {
            Path root = findProjectRoot();
            if (root == null) {
                System.out.println("Skipping: project root not found");
                return;
            }

            List<String> violations = new ArrayList<>();

            // Check YAML and properties files
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String fileName = file.getFileName().toString().toLowerCase();
                    if ((fileName.endsWith(".yml") || fileName.endsWith(".yaml") || fileName.endsWith(".properties"))
                            && !fileName.contains("test")
                            && !file.toString().contains("target")) {
                        violations.addAll(scanFile(file));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            if (!violations.isEmpty()) {
                violations.forEach(System.out::println);
            }
            assertTrue(violations.isEmpty(),
                    "Found " + violations.size() + " potential API key(s) in config files");
        }
    }

    @Nested
    @DisplayName("Logging Patterns")
    class LoggingPatterns {

        @Test
        @DisplayName("should not log API keys directly")
        void shouldNotLogApiKeysDirectly() throws IOException {
            Path srcMain = findSrcMain();
            if (srcMain == null || !Files.exists(srcMain)) {
                System.out.println("Skipping: src/main not found");
                return;
            }

            List<String> unsafeLogging = new ArrayList<>();

            Files.walkFileTree(srcMain, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toString().endsWith(".java")) {
                        String content = Files.readString(file);
                        checkUnsafeLogging(file, content, unsafeLogging);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            if (!unsafeLogging.isEmpty()) {
                unsafeLogging.forEach(System.out::println);
            }
            assertTrue(unsafeLogging.isEmpty(),
                    "Found " + unsafeLogging.size() + " potentially unsafe logging pattern(s)");
        }

        private void checkUnsafeLogging(Path file, String content, List<String> violations) {
            // Check for logging calls that include apiKey, secret, or password without masking
            String[] lines = content.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].toLowerCase();

                // Check if it's a logging statement
                boolean isLogging = line.contains("logger.") ||
                        line.contains("log.") ||
                        line.contains("system.out") ||
                        line.contains("system.err");

                if (isLogging) {
                    // Check for sensitive variable names being logged
                    boolean hasSensitive = line.contains("apikey") ||
                            line.contains("api_key") ||
                            line.contains("secretkey") ||
                            line.contains("secret_key") ||
                            (line.contains("password") && !line.contains("password=***"));

                    // Check for masking
                    boolean hasMasking = line.contains("mask") ||
                            line.contains("sanitize") ||
                            line.contains("redact") ||
                            line.contains("***");

                    if (hasSensitive && !hasMasking) {
                        // Check if it's actually logging the value (not just a message about it)
                        if (line.contains("+") || line.matches(".*\\{.*\\}.*")) {
                            violations.add(String.format("%s:%d - Potentially unsafe logging: %s",
                                    file.getFileName(), i + 1, lines[i].trim()));
                        }
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("Report Generation")
    class ReportGeneration {

        @Test
        @DisplayName("report templates should not expose sensitive data")
        void reportTemplatesShouldNotExposeSensitiveData() throws IOException {
            Path root = findProjectRoot();
            if (root == null) {
                System.out.println("Skipping: project root not found");
                return;
            }

            List<String> violations = new ArrayList<>();

            // Check HTML/template files in report module
            Path reportModule = root.resolve("healer-report");
            if (Files.exists(reportModule)) {
                Files.walkFileTree(reportModule, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String fileName = file.getFileName().toString().toLowerCase();
                        if (fileName.endsWith(".html") || fileName.endsWith(".ftl") || fileName.endsWith(".vm")) {
                            String content = Files.readString(file);

                            // Check for sensitive field names being displayed
                            if (content.toLowerCase().contains("apikey") ||
                                    content.toLowerCase().contains("secretkey") ||
                                    content.toLowerCase().contains("password")) {
                                violations.add(file + " - Contains references to sensitive fields");
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }

            assertTrue(violations.isEmpty(),
                    "Found sensitive data in report templates: " + violations);
        }
    }

    // Helper methods

    private List<String> scanDirectory(Path directory, String extension) throws IOException {
        List<String> violations = new ArrayList<>();

        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(extension) && !file.toString().contains("target")) {
                    violations.addAll(scanFile(file));
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return violations;
    }

    private List<String> scanFile(Path file) throws IOException {
        List<String> violations = new ArrayList<>();
        String content = Files.readString(file);
        String[] lines = content.split("\n");

        for (int lineNum = 0; lineNum < lines.length; lineNum++) {
            String line = lines[lineNum];

            // Skip if line matches exception patterns
            boolean isException = false;
            for (Pattern exceptionPattern : EXCEPTION_PATTERNS) {
                if (exceptionPattern.matcher(line).find()) {
                    isException = true;
                    break;
                }
            }
            if (isException) continue;

            // Check for sensitive patterns
            for (Pattern sensitivePattern : SENSITIVE_PATTERNS) {
                Matcher matcher = sensitivePattern.matcher(line);
                if (matcher.find()) {
                    violations.add(String.format("%s:%d - Potential secret: %s",
                            file.getFileName(), lineNum + 1, truncate(line.trim(), 80)));
                }
            }
        }

        return violations;
    }

    private String truncate(String str, int maxLen) {
        if (str == null || str.length() <= maxLen) return str;
        return str.substring(0, maxLen - 3) + "...";
    }

    private Path findProjectRoot() {
        Path current = Paths.get(System.getProperty("user.dir"));
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) &&
                    Files.exists(current.resolve("healer-core"))) {
                return current;
            }
            current = current.getParent();
        }
        return Paths.get(System.getProperty("user.dir"));
    }

    private Path findSrcMain() {
        Path root = findProjectRoot();
        if (root == null) return null;

        // Try to find any src/main/java directory
        try {
            List<Path> srcMains = new ArrayList<>();
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (dir.toString().contains("target") || dir.toString().contains("node_modules")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (dir.endsWith("src/main/java")) {
                        srcMains.add(dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return srcMains.isEmpty() ? null : srcMains.get(0).getParent().getParent().getParent();
        } catch (IOException e) {
            return null;
        }
    }
}
