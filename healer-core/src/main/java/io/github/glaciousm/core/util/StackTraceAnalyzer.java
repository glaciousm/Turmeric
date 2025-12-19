package io.github.glaciousm.core.util;

import io.github.glaciousm.core.model.SourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Analyzes exception stack traces to extract source code location information.
 * Used for identifying where a failed locator is defined in user test code.
 */
public class StackTraceAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(StackTraceAnalyzer.class);

    /**
     * Package prefixes to filter out when looking for user code.
     * These are framework packages that should be skipped.
     */
    private static final Set<String> FRAMEWORK_PACKAGES = Set.of(
            "org.openqa.selenium",
            "io.github.glaciousm",
            "org.junit",
            "org.testng",
            "io.cucumber",
            "java.",
            "javax.",
            "sun.",
            "com.sun.",
            "jdk.",
            "org.apache.maven",
            "org.gradle",
            "org.mockito",
            "org.assertj"
    );

    /**
     * Common source directory paths to search for source files.
     */
    private static final List<String> SOURCE_PATHS = List.of(
            "src/main/java",
            "src/test/java",
            "src/it/java"
    );

    private final List<String> projectRoots;

    /**
     * Creates a StackTraceAnalyzer with the specified project roots.
     *
     * @param projectRoots directories to search for source files
     */
    public StackTraceAnalyzer(List<String> projectRoots) {
        this.projectRoots = projectRoots != null ? projectRoots : List.of(System.getProperty("user.dir"));
    }

    /**
     * Creates a StackTraceAnalyzer using the current working directory.
     */
    public StackTraceAnalyzer() {
        this(List.of(System.getProperty("user.dir")));
    }

    /**
     * Analyzes an exception to extract the source location where the failed
     * element locator is defined.
     *
     * @param exception the exception thrown during element lookup
     * @return Optional containing the source location if found
     */
    public Optional<SourceLocation> extractSourceLocation(Throwable exception) {
        if (exception == null) {
            return Optional.empty();
        }

        StackTraceElement[] stackTrace = exception.getStackTrace();
        return findUserCodeFrame(stackTrace)
                .flatMap(this::buildSourceLocation);
    }

    /**
     * Analyzes an exception and current thread's stack trace to find the
     * best source location for the failed locator.
     *
     * @param exception the exception thrown during element lookup
     * @return Optional containing the source location if found
     */
    public Optional<SourceLocation> extractSourceLocationWithContext(Throwable exception) {
        // First try the exception's stack trace
        Optional<SourceLocation> fromException = extractSourceLocation(exception);
        if (fromException.isPresent()) {
            return fromException;
        }

        // Fall back to current thread's stack trace
        StackTraceElement[] currentTrace = Thread.currentThread().getStackTrace();
        return findUserCodeFrame(currentTrace)
                .flatMap(this::buildSourceLocation);
    }

    /**
     * Finds the first stack frame that belongs to user code (not framework code).
     */
    private Optional<StackTraceElement> findUserCodeFrame(StackTraceElement[] stackTrace) {
        return Arrays.stream(stackTrace)
                .filter(this::isUserCode)
                .findFirst();
    }

    /**
     * Determines if a stack frame belongs to user code vs framework code.
     */
    private boolean isUserCode(StackTraceElement element) {
        String className = element.getClassName();

        // Check if it starts with any framework package
        for (String prefix : FRAMEWORK_PACKAGES) {
            if (className.startsWith(prefix)) {
                return false;
            }
        }

        // Skip synthetic or generated classes
        if (className.contains("$") && className.contains("Lambda")) {
            return false;
        }

        // Skip classes without line number info (native methods, etc.)
        if (element.getLineNumber() < 0) {
            return false;
        }

        return true;
    }

    /**
     * Builds a SourceLocation from a stack trace element.
     */
    private Optional<SourceLocation> buildSourceLocation(StackTraceElement element) {
        String className = element.getClassName();
        String methodName = element.getMethodName();
        int lineNumber = element.getLineNumber();
        String fileName = element.getFileName();

        // Try to resolve the actual file path
        String filePath = resolveFilePath(className, fileName);
        String locatorCode = null;

        if (filePath != null) {
            locatorCode = readLineFromFile(filePath, lineNumber);
        }

        SourceLocation location = SourceLocation.builder()
                .filePath(filePath)
                .className(className)
                .methodName(methodName)
                .lineNumber(lineNumber)
                .locatorCode(locatorCode)
                .build();

        return Optional.of(location);
    }

    /**
     * Resolves the full file path from a class name and file name.
     */
    private String resolveFilePath(String className, String fileName) {
        // Convert class name to relative path
        String relativePath = className.replace('.', File.separatorChar);

        // Handle inner classes
        int dollarIndex = relativePath.indexOf('$');
        if (dollarIndex > 0) {
            relativePath = relativePath.substring(0, dollarIndex);
        }

        // Add .java extension
        relativePath = relativePath + ".java";

        // Search in project roots
        for (String root : projectRoots) {
            for (String srcPath : SOURCE_PATHS) {
                Path fullPath = Paths.get(root, srcPath, relativePath);
                if (Files.exists(fullPath)) {
                    try {
                        return fullPath.toRealPath().toString();
                    } catch (IOException e) {
                        return fullPath.toAbsolutePath().toString();
                    }
                }
            }

            // Also try direct relative path from root
            Path directPath = Paths.get(root, relativePath);
            if (Files.exists(directPath)) {
                try {
                    return directPath.toRealPath().toString();
                } catch (IOException e) {
                    return directPath.toAbsolutePath().toString();
                }
            }
        }

        // If file name is provided, try that as fallback
        if (fileName != null) {
            for (String root : projectRoots) {
                for (String srcPath : SOURCE_PATHS) {
                    // Extract package path from class name
                    int lastDot = className.lastIndexOf('.');
                    String packagePath = lastDot > 0
                            ? className.substring(0, lastDot).replace('.', File.separatorChar)
                            : "";
                    Path fullPath = Paths.get(root, srcPath, packagePath, fileName);
                    if (Files.exists(fullPath)) {
                        try {
                            return fullPath.toRealPath().toString();
                        } catch (IOException e) {
                            return fullPath.toAbsolutePath().toString();
                        }
                    }
                }
            }
        }

        logger.debug("Could not resolve file path for class: {}", className);
        return null;
    }

    /**
     * Reads a specific line from a file.
     */
    private String readLineFromFile(String filePath, int lineNumber) {
        if (filePath == null || lineNumber < 1) {
            return null;
        }

        try {
            Path path = Paths.get(filePath);
            List<String> lines = Files.readAllLines(path);
            if (lineNumber <= lines.size()) {
                return lines.get(lineNumber - 1); // Convert to 0-indexed
            }
        } catch (IOException e) {
            logger.debug("Could not read line {} from file {}: {}", lineNumber, filePath, e.getMessage());
        }

        return null;
    }

    /**
     * Checks if the specified package should be considered user code.
     */
    public static boolean isUserPackage(String packageName) {
        if (packageName == null) {
            return false;
        }
        for (String prefix : FRAMEWORK_PACKAGES) {
            if (packageName.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Adds additional package prefixes to filter out.
     */
    public static Set<String> getFrameworkPackages() {
        return FRAMEWORK_PACKAGES;
    }
}
