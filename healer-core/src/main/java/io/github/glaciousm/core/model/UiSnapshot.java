package io.github.glaciousm.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Complete snapshot of the current page state.
 * Contains all information needed for the LLM to make a healing decision.
 */
public final class UiSnapshot {
    private final String url;
    private final String title;
    private final String detectedLanguage;
    private final List<ElementSnapshot> interactiveElements;
    private final Instant timestamp;
    private final String screenshotBase64;
    private final String domSnapshot;

    @JsonCreator
    public UiSnapshot(
            @JsonProperty("url") String url,
            @JsonProperty("title") String title,
            @JsonProperty("language_detected") String detectedLanguage,
            @JsonProperty("interactive_elements") List<ElementSnapshot> interactiveElements,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("screenshot") String screenshotBase64,
            @JsonProperty("dom_snapshot") String domSnapshot) {
        this.url = url;
        this.title = title;
        this.detectedLanguage = detectedLanguage;
        this.interactiveElements = interactiveElements != null ? List.copyOf(interactiveElements) : List.of();
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.screenshotBase64 = screenshotBase64;
        this.domSnapshot = domSnapshot;
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public String getDetectedLanguage() {
        return detectedLanguage;
    }

    public List<ElementSnapshot> getInteractiveElements() {
        return interactiveElements;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Optional<String> getScreenshotBase64() {
        return Optional.ofNullable(screenshotBase64);
    }

    public Optional<String> getDomSnapshot() {
        return Optional.ofNullable(domSnapshot);
    }

    /**
     * Gets the HTML/DOM snapshot.
     * Alias for getDomSnapshot() that returns String (null if not present).
     */
    public String getHtml() {
        return domSnapshot;
    }

    /**
     * Gets an element by its index.
     */
    public Optional<ElementSnapshot> getElement(int index) {
        return interactiveElements.stream()
                .filter(e -> e.getIndex() == index)
                .findFirst();
    }

    /**
     * Gets an element by its index, throwing if not found.
     */
    public ElementSnapshot getElementRequired(int index) {
        return getElement(index)
                .orElseThrow(() -> new IllegalArgumentException("Element with index " + index + " not found"));
    }

    /**
     * Returns the number of interactive elements.
     */
    public int getElementCount() {
        return interactiveElements.size();
    }

    /**
     * Checks if the snapshot has any elements.
     */
    public boolean hasElements() {
        return !interactiveElements.isEmpty();
    }

    /**
     * Filters elements by tag name.
     */
    public List<ElementSnapshot> getElementsByTag(String tagName) {
        return interactiveElements.stream()
                .filter(e -> tagName.equalsIgnoreCase(e.getTagName()))
                .toList();
    }

    /**
     * Filters elements that are interactable (visible and enabled).
     */
    public List<ElementSnapshot> getInteractableElements() {
        return interactiveElements.stream()
                .filter(ElementSnapshot::isInteractable)
                .toList();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UiSnapshot that = (UiSnapshot) o;
        return Objects.equals(url, that.url) &&
               Objects.equals(title, that.title) &&
               Objects.equals(interactiveElements, that.interactiveElements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, title, interactiveElements);
    }

    @Override
    public String toString() {
        return "UiSnapshot{url='" + url + "', title='" + title +
               "', elements=" + interactiveElements.size() + "}";
    }

    public static final class Builder {
        private String url;
        private String title;
        private String detectedLanguage;
        private List<ElementSnapshot> interactiveElements;
        private Instant timestamp;
        private String screenshotBase64;
        private String domSnapshot;

        private Builder() {
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder detectedLanguage(String detectedLanguage) {
            this.detectedLanguage = detectedLanguage;
            return this;
        }

        public Builder interactiveElements(List<ElementSnapshot> interactiveElements) {
            this.interactiveElements = interactiveElements;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder screenshotBase64(String screenshotBase64) {
            this.screenshotBase64 = screenshotBase64;
            return this;
        }

        public Builder domSnapshot(String domSnapshot) {
            this.domSnapshot = domSnapshot;
            return this;
        }

        public UiSnapshot build() {
            return new UiSnapshot(url, title, detectedLanguage, interactiveElements,
                    timestamp, screenshotBase64, domSnapshot);
        }
    }
}
