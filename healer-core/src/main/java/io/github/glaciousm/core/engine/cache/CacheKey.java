package io.github.glaciousm.core.engine.cache;

import io.github.glaciousm.core.model.ActionType;
import io.github.glaciousm.core.model.LocatorInfo;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Cache key for storing and retrieving healed locators.
 * Key is built from: page URL (normalized), original locator, action type, and intent hint.
 */
public class CacheKey {

    private final String pageUrlPattern;
    private final LocatorInfo originalLocator;
    private final ActionType actionType;
    private final String intentHint;
    private final String hash;

    private CacheKey(String pageUrlPattern, LocatorInfo originalLocator,
                     ActionType actionType, String intentHint) {
        this.pageUrlPattern = pageUrlPattern;
        this.originalLocator = originalLocator;
        this.actionType = actionType;
        this.intentHint = intentHint;
        this.hash = computeHash();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Normalize URL by removing query parameters and fragments for better cache hits.
     */
    public static String normalizeUrl(String url) {
        if (url == null) {
            return "";
        }
        // Remove query string and fragment
        int queryIndex = url.indexOf('?');
        int fragmentIndex = url.indexOf('#');

        int endIndex = url.length();
        if (queryIndex > 0) {
            endIndex = queryIndex;
        }
        if (fragmentIndex > 0 && fragmentIndex < endIndex) {
            endIndex = fragmentIndex;
        }

        return url.substring(0, endIndex);
    }

    /**
     * Extract page pattern from URL (removes dynamic IDs).
     */
    public static String extractPagePattern(String url) {
        String normalized = normalizeUrl(url);
        // Replace common dynamic path segments (UUIDs, numeric IDs)
        return normalized
                .replaceAll("/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "/{uuid}")
                .replaceAll("/\\d+(?=/|$)", "/{id}");
    }

    private String computeHash() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String content = String.join("|",
                    pageUrlPattern != null ? pageUrlPattern : "",
                    originalLocator != null ? originalLocator.getStrategy().name() : "",
                    originalLocator != null ? originalLocator.getValue() : "",
                    actionType != null ? actionType.name() : "",
                    intentHint != null ? intentHint : ""
            );
            byte[] hashBytes = md.digest(content.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().substring(0, 16); // Use first 16 chars
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple hash
            return String.valueOf(Objects.hash(pageUrlPattern, originalLocator, actionType, intentHint));
        }
    }

    public String getPageUrlPattern() {
        return pageUrlPattern;
    }

    public LocatorInfo getOriginalLocator() {
        return originalLocator;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public String getIntentHint() {
        return intentHint;
    }

    public String getHash() {
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CacheKey cacheKey = (CacheKey) o;
        return Objects.equals(hash, cacheKey.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash);
    }

    @Override
    public String toString() {
        return "CacheKey{" +
                "pageUrlPattern='" + pageUrlPattern + '\'' +
                ", originalLocator=" + originalLocator +
                ", actionType=" + actionType +
                ", hash='" + hash + '\'' +
                '}';
    }

    public static class Builder {
        private String pageUrl;
        private LocatorInfo originalLocator;
        private ActionType actionType;
        private String intentHint;

        public Builder pageUrl(String pageUrl) {
            this.pageUrl = pageUrl;
            return this;
        }

        public Builder originalLocator(LocatorInfo originalLocator) {
            this.originalLocator = originalLocator;
            return this;
        }

        public Builder actionType(ActionType actionType) {
            this.actionType = actionType;
            return this;
        }

        public Builder intentHint(String intentHint) {
            this.intentHint = intentHint;
            return this;
        }

        public CacheKey build() {
            String pattern = extractPagePattern(pageUrl);
            return new CacheKey(pattern, originalLocator, actionType, intentHint);
        }
    }
}
