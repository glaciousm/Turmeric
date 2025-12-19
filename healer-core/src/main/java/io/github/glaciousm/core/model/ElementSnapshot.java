package io.github.glaciousm.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Snapshot of a single interactive element on the page.
 * Contains all attributes needed for LLM to understand the element's purpose.
 */
public final class ElementSnapshot {
    private final int index;
    private final String tagName;
    private final String type;
    private final String id;
    private final String name;
    private final List<String> classes;
    private final String text;
    private final String value;
    private final String placeholder;
    private final String ariaLabel;
    private final String ariaLabelledBy;
    private final String ariaDescribedBy;
    private final String ariaRole;
    private final String title;
    private final boolean visible;
    private final boolean enabled;
    private final boolean selected;
    private final ElementRect rect;
    private final String container;
    private final List<String> nearbyLabels;
    private final Map<String, String> dataAttributes;

    @JsonCreator
    public ElementSnapshot(
            @JsonProperty("index") int index,
            @JsonProperty("tag") String tagName,
            @JsonProperty("type") String type,
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("classes") List<String> classes,
            @JsonProperty("text") String text,
            @JsonProperty("value") String value,
            @JsonProperty("placeholder") String placeholder,
            @JsonProperty("aria_label") String ariaLabel,
            @JsonProperty("aria_labelledby") String ariaLabelledBy,
            @JsonProperty("aria_describedby") String ariaDescribedBy,
            @JsonProperty("aria_role") String ariaRole,
            @JsonProperty("title") String title,
            @JsonProperty("visible") boolean visible,
            @JsonProperty("enabled") boolean enabled,
            @JsonProperty("selected") boolean selected,
            @JsonProperty("rect") ElementRect rect,
            @JsonProperty("container") String container,
            @JsonProperty("nearby_labels") List<String> nearbyLabels,
            @JsonProperty("data_attributes") Map<String, String> dataAttributes) {
        this.index = index;
        this.tagName = tagName;
        this.type = type;
        this.id = id;
        this.name = name;
        this.classes = classes != null ? List.copyOf(classes) : List.of();
        this.text = text;
        this.value = value;
        this.placeholder = placeholder;
        this.ariaLabel = ariaLabel;
        this.ariaLabelledBy = ariaLabelledBy;
        this.ariaDescribedBy = ariaDescribedBy;
        this.ariaRole = ariaRole;
        this.title = title;
        this.visible = visible;
        this.enabled = enabled;
        this.selected = selected;
        this.rect = rect;
        this.container = container;
        this.nearbyLabels = nearbyLabels != null ? List.copyOf(nearbyLabels) : List.of();
        this.dataAttributes = dataAttributes != null ? Map.copyOf(dataAttributes) : Map.of();
    }

    public int getIndex() {
        return index;
    }

    public String getTagName() {
        return tagName;
    }

    public String getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<String> getClasses() {
        return classes;
    }

    public String getText() {
        return text;
    }

    public String getValue() {
        return value;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public String getAriaLabel() {
        return ariaLabel;
    }

    public String getAriaLabelledBy() {
        return ariaLabelledBy;
    }

    public String getAriaDescribedBy() {
        return ariaDescribedBy;
    }

    public String getAriaRole() {
        return ariaRole;
    }

    public String getTitle() {
        return title;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isSelected() {
        return selected;
    }

    public ElementRect getRect() {
        return rect;
    }

    public String getContainer() {
        return container;
    }

    public List<String> getNearbyLabels() {
        return nearbyLabels;
    }

    public Map<String, String> getDataAttributes() {
        return dataAttributes;
    }

    /**
     * Gets the data-testid attribute if present.
     */
    public String getDataTestId() {
        return dataAttributes.get("testid");
    }

    /**
     * Returns the normalized text content, trimmed and with whitespace normalized.
     */
    public String getNormalizedText() {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.trim().replaceAll("\\s+", " ");
    }

    /**
     * Checks if this element is interactable (visible and enabled).
     */
    public boolean isInteractable() {
        return visible && enabled;
    }

    /**
     * Returns the best available label for this element.
     */
    public String getBestLabel() {
        if (ariaLabel != null && !ariaLabel.isEmpty()) return ariaLabel;
        if (text != null && !text.isEmpty()) return text;
        if (title != null && !title.isEmpty()) return title;
        if (placeholder != null && !placeholder.isEmpty()) return placeholder;
        if (value != null && !value.isEmpty()) return value;
        if (!nearbyLabels.isEmpty()) return nearbyLabels.get(0);
        return "";
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ElementSnapshot that = (ElementSnapshot) o;
        return index == that.index &&
               visible == that.visible &&
               enabled == that.enabled &&
               Objects.equals(tagName, that.tagName) &&
               Objects.equals(id, that.id) &&
               Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, tagName, id, text, visible, enabled);
    }

    @Override
    public String toString() {
        return "ElementSnapshot{index=" + index + ", tag='" + tagName + "', id='" + id +
               "', text='" + getNormalizedText() + "'}";
    }

    public static final class Builder {
        private int index;
        private String tagName;
        private String type;
        private String id;
        private String name;
        private List<String> classes;
        private String text;
        private String value;
        private String placeholder;
        private String ariaLabel;
        private String ariaLabelledBy;
        private String ariaDescribedBy;
        private String ariaRole;
        private String title;
        private boolean visible = true;
        private boolean enabled = true;
        private boolean selected;
        private ElementRect rect;
        private String container;
        private List<String> nearbyLabels;
        private Map<String, String> dataAttributes;

        private Builder() {
        }

        public Builder index(int index) {
            this.index = index;
            return this;
        }

        public Builder tagName(String tagName) {
            this.tagName = tagName;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder classes(List<String> classes) {
            this.classes = classes;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder value(String value) {
            this.value = value;
            return this;
        }

        public Builder placeholder(String placeholder) {
            this.placeholder = placeholder;
            return this;
        }

        public Builder ariaLabel(String ariaLabel) {
            this.ariaLabel = ariaLabel;
            return this;
        }

        public Builder ariaLabelledBy(String ariaLabelledBy) {
            this.ariaLabelledBy = ariaLabelledBy;
            return this;
        }

        public Builder ariaDescribedBy(String ariaDescribedBy) {
            this.ariaDescribedBy = ariaDescribedBy;
            return this;
        }

        public Builder ariaRole(String ariaRole) {
            this.ariaRole = ariaRole;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder selected(boolean selected) {
            this.selected = selected;
            return this;
        }

        public Builder rect(ElementRect rect) {
            this.rect = rect;
            return this;
        }

        public Builder container(String container) {
            this.container = container;
            return this;
        }

        public Builder nearbyLabels(List<String> nearbyLabels) {
            this.nearbyLabels = nearbyLabels;
            return this;
        }

        public Builder dataAttributes(Map<String, String> dataAttributes) {
            this.dataAttributes = dataAttributes;
            return this;
        }

        public ElementSnapshot build() {
            return new ElementSnapshot(index, tagName, type, id, name, classes, text, value,
                    placeholder, ariaLabel, ariaLabelledBy, ariaDescribedBy, ariaRole, title,
                    visible, enabled, selected, rect, container, nearbyLabels, dataAttributes);
        }
    }
}
