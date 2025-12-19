package io.github.glaciousm.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for report generation.
 */
public class ReportConfig {

    @JsonProperty("output_dir")
    private String outputDir = "build/healer-reports";

    @JsonProperty("json_enabled")
    private boolean jsonEnabled = true;

    @JsonProperty("html_enabled")
    private boolean htmlEnabled = true;

    @JsonProperty("include_screenshots")
    private boolean includeScreenshots = true;

    @JsonProperty("include_llm_prompts")
    private boolean includeLlmPrompts = false;

    @JsonProperty("include_dom_snapshots")
    private boolean includeDomSnapshots = false;

    @JsonProperty("max_artifacts_per_report")
    private int maxArtifactsPerReport = 100;

    public ReportConfig() {
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public boolean isJsonEnabled() {
        return jsonEnabled;
    }

    public void setJsonEnabled(boolean jsonEnabled) {
        this.jsonEnabled = jsonEnabled;
    }

    public boolean isHtmlEnabled() {
        return htmlEnabled;
    }

    public void setHtmlEnabled(boolean htmlEnabled) {
        this.htmlEnabled = htmlEnabled;
    }

    public boolean isIncludeScreenshots() {
        return includeScreenshots;
    }

    public void setIncludeScreenshots(boolean includeScreenshots) {
        this.includeScreenshots = includeScreenshots;
    }

    public boolean isIncludeLlmPrompts() {
        return includeLlmPrompts;
    }

    public void setIncludeLlmPrompts(boolean includeLlmPrompts) {
        this.includeLlmPrompts = includeLlmPrompts;
    }

    public boolean isIncludeDomSnapshots() {
        return includeDomSnapshots;
    }

    public void setIncludeDomSnapshots(boolean includeDomSnapshots) {
        this.includeDomSnapshots = includeDomSnapshots;
    }

    public int getMaxArtifactsPerReport() {
        return maxArtifactsPerReport;
    }

    public void setMaxArtifactsPerReport(int maxArtifactsPerReport) {
        this.maxArtifactsPerReport = maxArtifactsPerReport;
    }

    /**
     * Check if reporting is enabled (at least one format enabled).
     */
    public boolean isEnabled() {
        return jsonEnabled || htmlEnabled;
    }

    /**
     * Get the format string for display.
     */
    public String getFormat() {
        if (jsonEnabled && htmlEnabled) return "both";
        if (jsonEnabled) return "json";
        if (htmlEnabled) return "html";
        return "none";
    }

    @Override
    public String toString() {
        return "ReportConfig{outputDir='" + outputDir + "', jsonEnabled=" + jsonEnabled +
               ", htmlEnabled=" + htmlEnabled + "}";
    }
}
