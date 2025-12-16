package com.intenthealer.llm.util;

import okhttp3.OkHttpClient;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Factory for creating and caching OkHttpClient instances.
 *
 * OkHttpClient should be reused across requests because:
 * - Creating new clients creates new connection pools and thread pools
 * - Reusing clients enables HTTP/2 connection multiplexing
 * - Connection keep-alive and pooling dramatically improve performance
 *
 * This factory provides thread-safe access to shared client instances
 * with configurable timeouts.
 */
public class HttpClientFactory {

    private static final ConcurrentHashMap<String, OkHttpClient> clientCache = new ConcurrentHashMap<>();

    // Default shared client for most use cases
    private static final OkHttpClient DEFAULT_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    private HttpClientFactory() {
        // Utility class
    }

    /**
     * Get the default shared OkHttpClient.
     * This client has standard timeouts suitable for most LLM API calls.
     */
    public static OkHttpClient getDefault() {
        return DEFAULT_CLIENT;
    }

    /**
     * Get a client with custom timeouts.
     * Clients are cached by timeout configuration for reuse.
     *
     * @param connectTimeoutSeconds connection timeout in seconds
     * @param readTimeoutSeconds    read timeout in seconds
     * @param writeTimeoutSeconds   write timeout in seconds
     * @return a configured OkHttpClient instance
     */
    public static OkHttpClient getClient(int connectTimeoutSeconds, int readTimeoutSeconds, int writeTimeoutSeconds) {
        String cacheKey = String.format("%d-%d-%d", connectTimeoutSeconds, readTimeoutSeconds, writeTimeoutSeconds);

        return clientCache.computeIfAbsent(cacheKey, key ->
                DEFAULT_CLIENT.newBuilder()
                        .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                        .readTimeout(Duration.ofSeconds(readTimeoutSeconds))
                        .writeTimeout(Duration.ofSeconds(writeTimeoutSeconds))
                        .build()
        );
    }

    /**
     * Get a client with custom read timeout.
     * Useful for LLM calls that may take longer than the default 60 seconds.
     *
     * @param readTimeoutSeconds read timeout in seconds
     * @return a configured OkHttpClient instance
     */
    public static OkHttpClient getClientWithReadTimeout(int readTimeoutSeconds) {
        return getClient(30, readTimeoutSeconds, 30);
    }

    /**
     * Clear all cached clients.
     * Should only be used in tests or when shutting down.
     */
    public static void clearCache() {
        clientCache.clear();
    }

    /**
     * Get statistics about cached clients.
     *
     * @return number of cached client configurations
     */
    public static int getCacheSize() {
        return clientCache.size();
    }
}
