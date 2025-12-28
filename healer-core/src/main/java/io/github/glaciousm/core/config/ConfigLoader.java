package io.github.glaciousm.core.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.glaciousm.core.model.HealPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.glaciousm.core.exception.ConfigurationException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

/**
 * Loads healer configuration from multiple sources with proper precedence.
 *
 * Configuration sources (in order of precedence, highest first):
 * 1. System properties (-Dhealer.*)
 * 2. Environment variables (HEALER_*)
 * 3. Configuration file
 * 4. Defaults
 */
public class ConfigLoader {

    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);

    private static final String[] CONFIG_LOCATIONS = {
            "healer-config.yml",
            "healer-config.yaml",
            "src/test/resources/healer-config.yml",
            "src/test/resources/healer-config.yaml",
            ".healer/config.yml"
    };

    private final ObjectMapper yamlMapper;

    public ConfigLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Load configuration from all sources with proper precedence.
     */
    public HealerConfig load() {
        HealerConfig config = new HealerConfig();

        // 1. Apply defaults
        config.applyDefaults();

        // 2. Load from file (first found)
        loadFromFile(config);

        // 3. Override from environment variables
        mergeFromEnvironment(config, System.getenv());

        // 4. Override from system properties
        mergeFromSystemProperties(config, System.getProperties());

        // 5. Validate
        config.validate();

        logger.info("Loaded healer configuration: {}", config);
        return config;
    }

    /**
     * Load configuration from a specific file.
     */
    public HealerConfig loadFromFile(String path) {
        File file = new File(path);
        if (!file.exists()) {
            throw new IllegalArgumentException("Configuration file not found: " + path);
        }

        HealerConfig config = new HealerConfig();
        config.applyDefaults();

        try {
            HealerConfigWrapper wrapper = yamlMapper.readValue(file, HealerConfigWrapper.class);
            if (wrapper.healer != null) {
                mergeConfig(config, wrapper.healer);
            }
        } catch (IOException e) {
            throw ConfigurationException.loadFailed(path, e);
        }

        config.validate();
        return config;
    }

    /**
     * Load configuration from an input stream.
     */
    public HealerConfig loadFromStream(InputStream inputStream) {
        HealerConfig config = new HealerConfig();
        config.applyDefaults();

        try {
            HealerConfigWrapper wrapper = yamlMapper.readValue(inputStream, HealerConfigWrapper.class);
            if (wrapper.healer != null) {
                mergeConfig(config, wrapper.healer);
            }
        } catch (IOException e) {
            throw ConfigurationException.streamLoadFailed(e);
        }

        config.validate();
        return config;
    }

    private void loadFromFile(HealerConfig config) {
        // DIAGNOSTIC: Print current working directory
        System.err.println("[Intent Healer] ConfigLoader - Working directory: " + new File(".").getAbsolutePath());

        for (String location : CONFIG_LOCATIONS) {
            File file = new File(location);
            System.err.println("[Intent Healer] ConfigLoader - Checking: " + file.getAbsolutePath() + " exists=" + file.exists());
            if (file.exists() && file.isFile()) {
                logger.info("Loading configuration from: {}", file.getAbsolutePath());
                System.err.println("[Intent Healer] ConfigLoader - FOUND config at: " + file.getAbsolutePath());
                try {
                    HealerConfigWrapper wrapper = yamlMapper.readValue(file, HealerConfigWrapper.class);
                    if (wrapper.healer != null) {
                        System.err.println("[Intent Healer] ConfigLoader - Parsed enabled=" + wrapper.healer.isEnabled());
                        mergeConfig(config, wrapper.healer);
                    } else {
                        System.err.println("[Intent Healer] ConfigLoader - WARNING: wrapper.healer is NULL!");
                    }
                    return;
                } catch (IOException e) {
                    System.err.println("[Intent Healer] ConfigLoader - Failed to parse: " + e.getMessage());
                    logger.warn("Failed to load configuration from {}: {}", location, e.getMessage());
                }
            }
        }

        // Try classpath
        System.err.println("[Intent Healer] ConfigLoader - Trying classpath...");
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("healer-config.yml")) {
            if (is != null) {
                logger.info("Loading configuration from classpath: healer-config.yml");
                System.err.println("[Intent Healer] ConfigLoader - FOUND config on classpath");
                HealerConfigWrapper wrapper = yamlMapper.readValue(is, HealerConfigWrapper.class);
                if (wrapper.healer != null) {
                    System.err.println("[Intent Healer] ConfigLoader - Classpath config enabled=" + wrapper.healer.isEnabled());
                    mergeConfig(config, wrapper.healer);
                } else {
                    System.err.println("[Intent Healer] ConfigLoader - WARNING: classpath wrapper.healer is NULL!");
                }
            } else {
                System.err.println("[Intent Healer] ConfigLoader - NOT found on classpath");
            }
        } catch (IOException e) {
            System.err.println("[Intent Healer] ConfigLoader - Classpath load failed: " + e.getMessage());
            logger.debug("No classpath configuration found");
        }
    }

    private void mergeConfig(HealerConfig target, HealerConfig source) {
        if (source.getMode() != null) {
            target.setMode(source.getMode());
        }
        target.setEnabled(source.isEnabled());

        if (source.getLlm() != null) {
            LlmConfig llm = target.getLlm();
            LlmConfig srcLlm = source.getLlm();
            if (srcLlm.getProvider() != null) llm.setProvider(srcLlm.getProvider());
            if (srcLlm.getModel() != null) llm.setModel(srcLlm.getModel());
            if (srcLlm.getApiKeyEnv() != null) llm.setApiKeyEnv(srcLlm.getApiKeyEnv());
            if (srcLlm.getBaseUrl() != null) llm.setBaseUrl(srcLlm.getBaseUrl());
            llm.setTimeoutSeconds(srcLlm.getTimeoutSeconds());
            llm.setMaxRetries(srcLlm.getMaxRetries());
            llm.setTemperature(srcLlm.getTemperature());
            llm.setConfidenceThreshold(srcLlm.getConfidenceThreshold());
            llm.setMaxTokensPerRequest(srcLlm.getMaxTokensPerRequest());
            llm.setMaxRequestsPerTestRun(srcLlm.getMaxRequestsPerTestRun());
            llm.setMaxCostPerRunUsd(srcLlm.getMaxCostPerRunUsd());
            if (srcLlm.getFallback() != null && !srcLlm.getFallback().isEmpty()) {
                llm.setFallback(srcLlm.getFallback());
            }
        }

        if (source.getGuardrails() != null) {
            GuardrailConfig guard = target.getGuardrails();
            GuardrailConfig srcGuard = source.getGuardrails();
            guard.setMinConfidence(srcGuard.getMinConfidence());
            guard.setMaxHealAttemptsPerStep(srcGuard.getMaxHealAttemptsPerStep());
            guard.setMaxHealsPerScenario(srcGuard.getMaxHealsPerScenario());
            if (srcGuard.getForbiddenKeywords() != null && !srcGuard.getForbiddenKeywords().isEmpty()) {
                guard.setForbiddenKeywords(srcGuard.getForbiddenKeywords());
            }
            if (srcGuard.getErrorSelectors() != null && !srcGuard.getErrorSelectors().isEmpty()) {
                guard.setErrorSelectors(srcGuard.getErrorSelectors());
            }
        }

        if (source.getSnapshot() != null) {
            SnapshotConfig snap = target.getSnapshot();
            SnapshotConfig srcSnap = source.getSnapshot();
            snap.setMaxElements(srcSnap.getMaxElements());
            snap.setIncludeHidden(srcSnap.isIncludeHidden());
            snap.setIncludeDisabled(srcSnap.isIncludeDisabled());
            snap.setCaptureScreenshot(srcSnap.isCaptureScreenshot());
            snap.setCaptureDom(srcSnap.isCaptureDom());
        }

        if (source.getCache() != null) {
            CacheConfig cache = target.getCache();
            CacheConfig srcCache = source.getCache();
            cache.setEnabled(srcCache.isEnabled());
            cache.setTtlHours(srcCache.getTtlHours());
            cache.setMaxEntries(srcCache.getMaxEntries());
            if (srcCache.getStorage() != null) cache.setStorage(srcCache.getStorage());
            if (srcCache.getFilePath() != null) cache.setFilePath(srcCache.getFilePath());
        }

        if (source.getReport() != null) {
            ReportConfig report = target.getReport();
            ReportConfig srcReport = source.getReport();
            if (srcReport.getOutputDir() != null) report.setOutputDir(srcReport.getOutputDir());
            report.setJsonEnabled(srcReport.isJsonEnabled());
            report.setHtmlEnabled(srcReport.isHtmlEnabled());
            report.setIncludeScreenshots(srcReport.isIncludeScreenshots());
            report.setIncludeLlmPrompts(srcReport.isIncludeLlmPrompts());
        }
    }

    private void mergeFromEnvironment(HealerConfig config, Map<String, String> env) {
        // HEALER_MODE
        String mode = env.get("HEALER_MODE");
        if (mode != null && !mode.isEmpty()) {
            try {
                config.setMode(HealPolicy.valueOf(mode.toUpperCase()));
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid HEALER_MODE: {}", mode);
            }
        }

        // HEALER_ENABLED
        String enabled = env.get("HEALER_ENABLED");
        if (enabled != null && !enabled.isEmpty()) {
            config.setEnabled(Boolean.parseBoolean(enabled));
        }

        // LLM settings
        String provider = env.get("HEALER_LLM_PROVIDER");
        if (provider != null && !provider.isEmpty()) {
            config.getLlm().setProvider(provider);
        }

        String model = env.get("HEALER_LLM_MODEL");
        if (model != null && !model.isEmpty()) {
            config.getLlm().setModel(model);
        }

        String confidenceThreshold = env.get("HEALER_CONFIDENCE_THRESHOLD");
        if (confidenceThreshold != null && !confidenceThreshold.isEmpty()) {
            try {
                config.getLlm().setConfidenceThreshold(Double.parseDouble(confidenceThreshold));
            } catch (NumberFormatException e) {
                logger.warn("Invalid HEALER_CONFIDENCE_THRESHOLD: {}", confidenceThreshold);
            }
        }
    }

    private void mergeFromSystemProperties(HealerConfig config, Properties props) {
        // healer.mode
        String mode = props.getProperty("healer.mode");
        if (mode != null && !mode.isEmpty()) {
            try {
                config.setMode(HealPolicy.valueOf(mode.toUpperCase()));
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid healer.mode: {}", mode);
            }
        }

        // healer.enabled
        String enabled = props.getProperty("healer.enabled");
        if (enabled != null && !enabled.isEmpty()) {
            config.setEnabled(Boolean.parseBoolean(enabled));
        }

        // healer.llm.provider
        String provider = props.getProperty("healer.llm.provider");
        if (provider != null && !provider.isEmpty()) {
            config.getLlm().setProvider(provider);
        }

        // healer.llm.model
        String model = props.getProperty("healer.llm.model");
        if (model != null && !model.isEmpty()) {
            config.getLlm().setModel(model);
        }

        // healer.confidence.threshold
        String confidenceThreshold = props.getProperty("healer.confidence.threshold");
        if (confidenceThreshold != null && !confidenceThreshold.isEmpty()) {
            try {
                config.getLlm().setConfidenceThreshold(Double.parseDouble(confidenceThreshold));
            } catch (NumberFormatException e) {
                logger.warn("Invalid healer.confidence.threshold: {}", confidenceThreshold);
            }
        }
    }

    /**
     * Wrapper class for YAML structure where healer config is under 'healer' key.
     */
    private static class HealerConfigWrapper {
        public HealerConfig healer;
    }
}
