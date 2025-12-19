/*
 * Intent Healer - Self-Healing Test Automation
 * Copyright (C) 2025 Menelaos Mamouzellos
 *
 * This program is dual-licensed under AGPL-3.0 and Commercial License.
 * See LICENSE and LICENSE-COMMERCIAL.md for details.
 */
package io.github.glaciousm.benchmark;

import io.github.glaciousm.core.model.ElementSnapshot;
import io.github.glaciousm.core.model.UiSnapshot;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.Instant;
import java.util.*;

/**
 * Parses static HTML content into UiSnapshot with ElementSnapshot objects.
 * This enables benchmarking without a real browser.
 */
public class HtmlSnapshotParser {

    private static final Set<String> INTERACTIVE_TAGS = Set.of(
        "a", "button", "input", "select", "textarea", "label",
        "option", "details", "summary"
    );

    private static final Set<String> INTERACTIVE_ROLES = Set.of(
        "button", "link", "checkbox", "radio", "textbox", "combobox",
        "listbox", "menu", "menuitem", "tab", "switch", "slider"
    );

    /**
     * Parse HTML content into a UiSnapshot.
     *
     * @param html The HTML content to parse
     * @param url  The URL to associate with this snapshot (can be synthetic)
     * @return UiSnapshot containing all interactive elements
     */
    public UiSnapshot parse(String html, String url) {
        Document doc = Jsoup.parse(html);
        String title = doc.title();

        List<ElementSnapshot> elements = extractInteractiveElements(doc);

        return UiSnapshot.builder()
            .url(url)
            .title(title)
            .interactiveElements(elements)
            .domSnapshot(html)
            .timestamp(Instant.now())
            .build();
    }

    /**
     * Extract all interactive elements from the document.
     */
    private List<ElementSnapshot> extractInteractiveElements(Document doc) {
        List<ElementSnapshot> snapshots = new ArrayList<>();
        int index = 0;

        // Select all potentially interactive elements
        Elements allElements = doc.select("*");

        for (Element element : allElements) {
            if (isInteractive(element)) {
                ElementSnapshot snapshot = createElementSnapshot(element, index++);
                snapshots.add(snapshot);
            }
        }

        return snapshots;
    }

    /**
     * Determine if an element is interactive.
     */
    private boolean isInteractive(Element element) {
        String tagName = element.tagName().toLowerCase();

        // Check tag name
        if (INTERACTIVE_TAGS.contains(tagName)) {
            return true;
        }

        // Check for clickable attributes
        if (element.hasAttr("onclick") || element.hasAttr("href")) {
            return true;
        }

        // Check role attribute
        String role = element.attr("role").toLowerCase();
        if (INTERACTIVE_ROLES.contains(role)) {
            return true;
        }

        // Check tabindex (makes element focusable/interactive)
        if (element.hasAttr("tabindex")) {
            String tabindex = element.attr("tabindex");
            if (!"-1".equals(tabindex)) {
                return true;
            }
        }

        // Check for data-testid (commonly used for test automation)
        if (element.hasAttr("data-testid")) {
            return true;
        }

        return false;
    }

    /**
     * Create an ElementSnapshot from a Jsoup Element.
     */
    private ElementSnapshot createElementSnapshot(Element element, int index) {
        String tagName = element.tagName().toLowerCase();

        // Extract classes
        List<String> classes = new ArrayList<>();
        for (String cls : element.classNames()) {
            if (!cls.isEmpty()) {
                classes.add(cls);
            }
        }

        // Extract data attributes
        Map<String, String> dataAttributes = new HashMap<>();
        for (var attr : element.attributes()) {
            if (attr.getKey().startsWith("data-")) {
                String key = attr.getKey().substring(5); // Remove "data-" prefix
                dataAttributes.put(key, attr.getValue());
            }
        }

        // Extract text content
        String text = element.ownText().trim();
        if (text.isEmpty()) {
            text = element.text().trim();
        }

        // Determine visibility (check for hidden attributes and styles)
        boolean visible = isVisible(element);

        // Determine enabled state
        boolean enabled = !element.hasAttr("disabled");

        // Find nearby labels
        List<String> nearbyLabels = findNearbyLabels(element);

        return ElementSnapshot.builder()
            .index(index)
            .tagName(tagName)
            .type(element.attr("type"))
            .id(element.id().isEmpty() ? null : element.id())
            .name(element.attr("name").isEmpty() ? null : element.attr("name"))
            .classes(classes.isEmpty() ? null : classes)
            .text(text.isEmpty() ? null : text)
            .value(element.val().isEmpty() ? null : element.val())
            .placeholder(element.attr("placeholder").isEmpty() ? null : element.attr("placeholder"))
            .ariaLabel(element.attr("aria-label").isEmpty() ? null : element.attr("aria-label"))
            .ariaLabelledBy(element.attr("aria-labelledby").isEmpty() ? null : element.attr("aria-labelledby"))
            .ariaDescribedBy(element.attr("aria-describedby").isEmpty() ? null : element.attr("aria-describedby"))
            .ariaRole(element.attr("role").isEmpty() ? null : element.attr("role"))
            .title(element.attr("title").isEmpty() ? null : element.attr("title"))
            .visible(visible)
            .enabled(enabled)
            .dataAttributes(dataAttributes.isEmpty() ? null : dataAttributes)
            .nearbyLabels(nearbyLabels.isEmpty() ? null : nearbyLabels)
            .build();
    }

    /**
     * Find labels associated with or near an element.
     */
    private List<String> findNearbyLabels(Element element) {
        List<String> labels = new ArrayList<>();

        // Check for label with 'for' attribute matching element id
        String id = element.id();
        if (!id.isEmpty()) {
            Elements labelElements = element.root().select("label[for=" + id + "]");
            for (Element label : labelElements) {
                String labelText = label.text().trim();
                if (!labelText.isEmpty()) {
                    labels.add(labelText);
                }
            }
        }

        // Check for parent label
        Element parent = element.parent();
        while (parent != null && labels.isEmpty()) {
            if ("label".equals(parent.tagName())) {
                String labelText = parent.ownText().trim();
                if (!labelText.isEmpty()) {
                    labels.add(labelText);
                }
                break;
            }
            parent = parent.parent();
        }

        // Check for preceding sibling label
        Element prev = element.previousElementSibling();
        if (prev != null && "label".equals(prev.tagName())) {
            String labelText = prev.text().trim();
            if (!labelText.isEmpty() && !labels.contains(labelText)) {
                labels.add(labelText);
            }
        }

        return labels;
    }

    /**
     * Determine if an element is visible based on its attributes and styles.
     */
    private boolean isVisible(Element element) {
        // Check hidden attribute
        if (element.hasAttr("hidden")) {
            return false;
        }

        // Check type="hidden" for inputs
        if ("hidden".equals(element.attr("type"))) {
            return false;
        }

        // Check inline style for display:none or visibility:hidden
        String style = element.attr("style");
        if (style != null && !style.isEmpty()) {
            String normalizedStyle = style.toLowerCase().replaceAll("\\s+", "");
            if (normalizedStyle.contains("display:none")) {
                return false;
            }
            if (normalizedStyle.contains("visibility:hidden")) {
                return false;
            }
        }

        return true;
    }
}
