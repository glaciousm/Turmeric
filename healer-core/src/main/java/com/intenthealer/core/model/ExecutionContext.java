package com.intenthealer.core.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Context available during outcome and invariant verification.
 * Provides access to driver state and snapshots without depending on Selenium types.
 */
public final class ExecutionContext {
    private final UiSnapshot beforeSnapshot;
    private final UiSnapshot afterSnapshot;
    private final Object driver;  // WebDriver reference
    private final Map<String, Object> attributes;

    public ExecutionContext(Object driver, UiSnapshot beforeSnapshot, UiSnapshot afterSnapshot) {
        this.driver = driver;
        this.beforeSnapshot = beforeSnapshot;
        this.afterSnapshot = afterSnapshot;
        this.attributes = new HashMap<>();
    }

    public ExecutionContext(Object driver, UiSnapshot snapshot) {
        this(driver, snapshot, snapshot);
    }

    /**
     * Gets the WebDriver instance (requires casting in consumer).
     */
    public Object getDriver() {
        return driver;
    }

    /**
     * Gets the typed driver instance.
     */
    @SuppressWarnings("unchecked")
    public <T> T getDriver(Class<T> driverClass) {
        return (T) driver;
    }

    /**
     * Gets the UI snapshot taken before the action.
     */
    public UiSnapshot getBeforeSnapshot() {
        return beforeSnapshot;
    }

    /**
     * Gets the UI snapshot taken after the action.
     */
    public UiSnapshot getAfterSnapshot() {
        return afterSnapshot;
    }

    /**
     * Gets the current URL from the after snapshot.
     */
    public String getCurrentUrl() {
        return afterSnapshot != null ? afterSnapshot.getUrl() : null;
    }

    /**
     * Gets the current page title from the after snapshot.
     */
    public String getCurrentTitle() {
        return afterSnapshot != null ? afterSnapshot.getTitle() : null;
    }

    /**
     * Gets the current page title from the after snapshot.
     * Alias for getCurrentTitle().
     */
    public String getPageTitle() {
        return getCurrentTitle();
    }

    /**
     * Checks if URL changed between before and after snapshots.
     */
    public boolean urlChanged() {
        if (beforeSnapshot == null || afterSnapshot == null) return false;
        return !Objects.equals(beforeSnapshot.getUrl(), afterSnapshot.getUrl());
    }

    /**
     * Checks if page title changed between before and after snapshots.
     */
    public boolean titleChanged() {
        if (beforeSnapshot == null || afterSnapshot == null) return false;
        return !Objects.equals(beforeSnapshot.getTitle(), afterSnapshot.getTitle());
    }

    /**
     * Sets a custom attribute on this context.
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * Gets a custom attribute from this context.
     */
    public Optional<Object> getAttribute(String key) {
        return Optional.ofNullable(attributes.get(key));
    }

    /**
     * Gets a typed custom attribute from this context.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getAttribute(String key, Class<T> type) {
        Object value = attributes.get(key);
        if (value != null && type.isInstance(value)) {
            return Optional.of((T) value);
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return "ExecutionContext{url='" + getCurrentUrl() + "', title='" + getCurrentTitle() + "'}";
    }
}
