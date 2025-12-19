package io.github.glaciousm.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for UI snapshot capture.
 */
public class SnapshotConfig {

    @JsonProperty("max_elements")
    private int maxElements = 500;

    @JsonProperty("include_hidden")
    private boolean includeHidden = false;

    @JsonProperty("include_disabled")
    private boolean includeDisabled = true;

    @JsonProperty("capture_screenshot")
    private boolean captureScreenshot = true;

    @JsonProperty("capture_dom")
    private boolean captureDom = false;

    @JsonProperty("max_text_length")
    private int maxTextLength = 200;

    @JsonProperty("timeout_ms")
    private int timeoutMs = 5000;

    public SnapshotConfig() {
    }

    public int getMaxElements() {
        return maxElements;
    }

    public void setMaxElements(int maxElements) {
        this.maxElements = maxElements;
    }

    public boolean isIncludeHidden() {
        return includeHidden;
    }

    public void setIncludeHidden(boolean includeHidden) {
        this.includeHidden = includeHidden;
    }

    public boolean isIncludeDisabled() {
        return includeDisabled;
    }

    public void setIncludeDisabled(boolean includeDisabled) {
        this.includeDisabled = includeDisabled;
    }

    public boolean isCaptureScreenshot() {
        return captureScreenshot;
    }

    public void setCaptureScreenshot(boolean captureScreenshot) {
        this.captureScreenshot = captureScreenshot;
    }

    public boolean isCaptureDom() {
        return captureDom;
    }

    public void setCaptureDom(boolean captureDom) {
        this.captureDom = captureDom;
    }

    public int getMaxTextLength() {
        return maxTextLength;
    }

    public void setMaxTextLength(int maxTextLength) {
        this.maxTextLength = maxTextLength;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    @Override
    public String toString() {
        return "SnapshotConfig{maxElements=" + maxElements +
               ", captureScreenshot=" + captureScreenshot + "}";
    }
}
