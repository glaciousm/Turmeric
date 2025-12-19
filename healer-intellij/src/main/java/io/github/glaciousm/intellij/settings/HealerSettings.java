package io.github.glaciousm.intellij.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Persistent settings for Intent Healer plugin.
 */
@State(
        name = "IntentHealerSettings",
        storages = @Storage("intentHealer.xml")
)
public class HealerSettings implements PersistentStateComponent<HealerSettings> {

    // General settings
    public boolean enableNotifications = true;
    public boolean autoRefreshDashboard = true;
    public int refreshIntervalSeconds = 30;

    // Heal cache settings
    public String healCacheDirectory = ".intent-healer/cache";
    public boolean persistHealHistory = true;
    public int maxHistoryEntries = 1000;

    // Real-time watching settings
    public String healReportsDirectory = "target/healer-reports";
    public boolean enableRealTimeWatching = true;
    public int watchPollingIntervalSeconds = 5;

    // Trust settings
    public boolean showTrustLevel = true;
    public boolean warnOnLowConfidence = true;
    public double confidenceWarningThreshold = 0.75;

    // LLM provider settings
    public String llmProvider = "openai";
    public String apiKeyEnvVar = "OPENAI_API_KEY";

    // UI settings
    public boolean showLineMarkers = true;
    public boolean highlightHealedLocators = true;

    public static HealerSettings getInstance() {
        return ApplicationManager.getApplication().getService(HealerSettings.class);
    }

    @Override
    public @Nullable HealerSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull HealerSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
