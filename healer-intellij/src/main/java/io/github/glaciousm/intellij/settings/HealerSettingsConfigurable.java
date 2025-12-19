package io.github.glaciousm.intellij.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Settings UI for Intent Healer plugin.
 */
public class HealerSettingsConfigurable implements Configurable {

    private JBCheckBox enableNotifications;
    private JBCheckBox autoRefreshDashboard;
    private JSpinner refreshInterval;
    private JBTextField healCacheDirectory;
    private JBCheckBox persistHealHistory;
    private JSpinner maxHistoryEntries;
    private JBCheckBox showTrustLevel;
    private JBCheckBox warnOnLowConfidence;
    private JSpinner confidenceThreshold;
    private JComboBox<String> llmProvider;
    private JBTextField apiKeyEnvVar;
    private JBCheckBox showLineMarkers;
    private JBCheckBox highlightHealedLocators;

    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
        return "Intent Healer";
    }

    @Override
    public @Nullable JComponent createComponent() {
        HealerSettings settings = HealerSettings.getInstance();

        // General settings
        enableNotifications = new JBCheckBox("Enable notifications", settings.enableNotifications);
        autoRefreshDashboard = new JBCheckBox("Auto-refresh dashboard", settings.autoRefreshDashboard);
        refreshInterval = new JSpinner(new SpinnerNumberModel(settings.refreshIntervalSeconds, 5, 300, 5));

        // Cache settings
        healCacheDirectory = new JBTextField(settings.healCacheDirectory);
        persistHealHistory = new JBCheckBox("Persist heal history", settings.persistHealHistory);
        maxHistoryEntries = new JSpinner(new SpinnerNumberModel(settings.maxHistoryEntries, 100, 10000, 100));

        // Trust settings
        showTrustLevel = new JBCheckBox("Show trust level in status bar", settings.showTrustLevel);
        warnOnLowConfidence = new JBCheckBox("Warn on low confidence heals", settings.warnOnLowConfidence);
        confidenceThreshold = new JSpinner(new SpinnerNumberModel(settings.confidenceWarningThreshold, 0.5, 1.0, 0.05));

        // LLM settings
        llmProvider = new JComboBox<>(new String[]{"openai", "anthropic", "azure", "bedrock", "ollama"});
        llmProvider.setSelectedItem(settings.llmProvider);
        apiKeyEnvVar = new JBTextField(settings.apiKeyEnvVar);

        // UI settings
        showLineMarkers = new JBCheckBox("Show line markers for @Intent", settings.showLineMarkers);
        highlightHealedLocators = new JBCheckBox("Highlight healed locators", settings.highlightHealedLocators);

        return FormBuilder.createFormBuilder()
                .addComponent(new JBLabel("<html><b>General Settings</b></html>"))
                .addComponent(enableNotifications)
                .addComponent(autoRefreshDashboard)
                .addLabeledComponent("Refresh interval (seconds):", refreshInterval)
                .addSeparator()
                .addComponent(new JBLabel("<html><b>Cache Settings</b></html>"))
                .addLabeledComponent("Cache directory:", healCacheDirectory)
                .addComponent(persistHealHistory)
                .addLabeledComponent("Max history entries:", maxHistoryEntries)
                .addSeparator()
                .addComponent(new JBLabel("<html><b>Trust Settings</b></html>"))
                .addComponent(showTrustLevel)
                .addComponent(warnOnLowConfidence)
                .addLabeledComponent("Confidence warning threshold:", confidenceThreshold)
                .addSeparator()
                .addComponent(new JBLabel("<html><b>LLM Provider Settings</b></html>"))
                .addLabeledComponent("LLM provider:", llmProvider)
                .addLabeledComponent("API key env variable:", apiKeyEnvVar)
                .addSeparator()
                .addComponent(new JBLabel("<html><b>Editor Settings</b></html>"))
                .addComponent(showLineMarkers)
                .addComponent(highlightHealedLocators)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    @Override
    public boolean isModified() {
        HealerSettings settings = HealerSettings.getInstance();
        return enableNotifications.isSelected() != settings.enableNotifications ||
               autoRefreshDashboard.isSelected() != settings.autoRefreshDashboard ||
               (int) refreshInterval.getValue() != settings.refreshIntervalSeconds ||
               !healCacheDirectory.getText().equals(settings.healCacheDirectory) ||
               persistHealHistory.isSelected() != settings.persistHealHistory ||
               (int) maxHistoryEntries.getValue() != settings.maxHistoryEntries ||
               showTrustLevel.isSelected() != settings.showTrustLevel ||
               warnOnLowConfidence.isSelected() != settings.warnOnLowConfidence ||
               (double) confidenceThreshold.getValue() != settings.confidenceWarningThreshold ||
               !llmProvider.getSelectedItem().equals(settings.llmProvider) ||
               !apiKeyEnvVar.getText().equals(settings.apiKeyEnvVar) ||
               showLineMarkers.isSelected() != settings.showLineMarkers ||
               highlightHealedLocators.isSelected() != settings.highlightHealedLocators;
    }

    @Override
    public void apply() throws ConfigurationException {
        HealerSettings settings = HealerSettings.getInstance();
        settings.enableNotifications = enableNotifications.isSelected();
        settings.autoRefreshDashboard = autoRefreshDashboard.isSelected();
        settings.refreshIntervalSeconds = (int) refreshInterval.getValue();
        settings.healCacheDirectory = healCacheDirectory.getText();
        settings.persistHealHistory = persistHealHistory.isSelected();
        settings.maxHistoryEntries = (int) maxHistoryEntries.getValue();
        settings.showTrustLevel = showTrustLevel.isSelected();
        settings.warnOnLowConfidence = warnOnLowConfidence.isSelected();
        settings.confidenceWarningThreshold = (double) confidenceThreshold.getValue();
        settings.llmProvider = (String) llmProvider.getSelectedItem();
        settings.apiKeyEnvVar = apiKeyEnvVar.getText();
        settings.showLineMarkers = showLineMarkers.isSelected();
        settings.highlightHealedLocators = highlightHealedLocators.isSelected();
    }

    @Override
    public void reset() {
        HealerSettings settings = HealerSettings.getInstance();
        enableNotifications.setSelected(settings.enableNotifications);
        autoRefreshDashboard.setSelected(settings.autoRefreshDashboard);
        refreshInterval.setValue(settings.refreshIntervalSeconds);
        healCacheDirectory.setText(settings.healCacheDirectory);
        persistHealHistory.setSelected(settings.persistHealHistory);
        maxHistoryEntries.setValue(settings.maxHistoryEntries);
        showTrustLevel.setSelected(settings.showTrustLevel);
        warnOnLowConfidence.setSelected(settings.warnOnLowConfidence);
        confidenceThreshold.setValue(settings.confidenceWarningThreshold);
        llmProvider.setSelectedItem(settings.llmProvider);
        apiKeyEnvVar.setText(settings.apiKeyEnvVar);
        showLineMarkers.setSelected(settings.showLineMarkers);
        highlightHealedLocators.setSelected(settings.highlightHealedLocators);
    }
}
