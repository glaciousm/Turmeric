package io.github.glaciousm.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Represents the source code location where a locator is defined.
 * Used for auto-updating source code after validated heals.
 */
public final class SourceLocation {

    private final String filePath;       // Full path to source file
    private final String className;      // Fully qualified class name
    private final String methodName;     // Method containing locator
    private final int lineNumber;        // Line number in source file
    private final String locatorCode;    // The actual code line containing the locator

    @JsonCreator
    public SourceLocation(
            @JsonProperty("filePath") String filePath,
            @JsonProperty("className") String className,
            @JsonProperty("methodName") String methodName,
            @JsonProperty("lineNumber") int lineNumber,
            @JsonProperty("locatorCode") String locatorCode) {
        this.filePath = filePath;
        this.className = className;
        this.methodName = methodName;
        this.lineNumber = lineNumber;
        this.locatorCode = locatorCode;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getLocatorCode() {
        return locatorCode;
    }

    /**
     * Returns true if this source location has enough information
     * to perform a source code update.
     */
    public boolean isUpdatable() {
        return filePath != null && !filePath.isEmpty()
            && lineNumber > 0;
    }

    /**
     * Returns a short description of this location for logging.
     */
    public String toShortString() {
        String fileName = filePath != null && filePath.contains("/")
            ? filePath.substring(filePath.lastIndexOf('/') + 1)
            : (filePath != null && filePath.contains("\\")
                ? filePath.substring(filePath.lastIndexOf('\\') + 1)
                : filePath);
        return String.format("%s:%d", fileName != null ? fileName : "unknown", lineNumber);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SourceLocation that = (SourceLocation) o;
        return lineNumber == that.lineNumber
            && Objects.equals(filePath, that.filePath)
            && Objects.equals(className, that.className)
            && Objects.equals(methodName, that.methodName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath, className, methodName, lineNumber);
    }

    @Override
    public String toString() {
        return "SourceLocation{" +
                "filePath='" + filePath + '\'' +
                ", className='" + className + '\'' +
                ", methodName='" + methodName + '\'' +
                ", lineNumber=" + lineNumber +
                '}';
    }

    /**
     * Creates a builder for SourceLocation.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String filePath;
        private String className;
        private String methodName;
        private int lineNumber;
        private String locatorCode;

        private Builder() {}

        public Builder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        public Builder className(String className) {
            this.className = className;
            return this;
        }

        public Builder methodName(String methodName) {
            this.methodName = methodName;
            return this;
        }

        public Builder lineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
            return this;
        }

        public Builder locatorCode(String locatorCode) {
            this.locatorCode = locatorCode;
            return this;
        }

        public SourceLocation build() {
            return new SourceLocation(filePath, className, methodName, lineNumber, locatorCode);
        }
    }
}
