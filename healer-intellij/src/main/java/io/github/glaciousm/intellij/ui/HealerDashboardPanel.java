package io.github.glaciousm.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import io.github.glaciousm.intellij.services.HealerProjectService;
import io.github.glaciousm.intellij.settings.HealerSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Dashboard panel showing healing statistics.
 */
public class HealerDashboardPanel extends JBPanel<HealerDashboardPanel> implements Disposable,
        HealerProjectService.HealHistoryListener {

    private final Project project;
    private final HealerProjectService service;
    private final Timer refreshTimer;

    // UI Components
    private JBLabel trustLevelLabel;
    private JProgressBar trustProgressBar;
    private JBLabel successRateLabel;
    private JBLabel totalHealsLabel;
    private JBLabel pendingHealsLabel;
    private JBLabel recentActivityLabel;

    public HealerDashboardPanel(@NotNull Project project) {
        super(new BorderLayout());
        this.project = project;
        this.service = HealerProjectService.getInstance(project);
        this.service.addListener(this);

        initComponents();
        updateDashboard();

        // Setup auto-refresh
        HealerSettings settings = HealerSettings.getInstance();
        if (settings.autoRefreshDashboard) {
            refreshTimer = new Timer("dashboard-refresh", true);
            refreshTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    SwingUtilities.invokeLater(() -> updateDashboard());
                }
            }, settings.refreshIntervalSeconds * 1000L, settings.refreshIntervalSeconds * 1000L);
        } else {
            refreshTimer = null;
        }
    }

    private void initComponents() {
        setBorder(JBUI.Borders.empty(10));

        // Trust Level Section
        JPanel trustPanel = createTrustPanel();

        // Statistics Section
        JPanel statsPanel = createStatsPanel();

        // Recent Activity Section
        JPanel activityPanel = createActivityPanel();

        // Layout
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.add(trustPanel);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(statsPanel);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(activityPanel);
        mainPanel.add(Box.createVerticalGlue());

        add(mainPanel, BorderLayout.NORTH);
    }

    private JPanel createTrustPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Trust Level"));

        trustLevelLabel = new JBLabel("L0_SHADOW");
        trustLevelLabel.setFont(trustLevelLabel.getFont().deriveFont(Font.BOLD, 18f));

        trustProgressBar = new JProgressBar(0, 4);
        trustProgressBar.setValue(0);
        trustProgressBar.setStringPainted(true);
        trustProgressBar.setString("Shadow Mode");

        JPanel content = new JPanel(new GridLayout(2, 1, 5, 5));
        content.add(trustLevelLabel);
        content.add(trustProgressBar);
        content.setBorder(JBUI.Borders.empty(5));

        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createStatsPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 2, 10, 10));
        panel.setBorder(BorderFactory.createTitledBorder("Statistics"));

        successRateLabel = createStatCard("Success Rate", "0%", JBColor.GREEN);
        totalHealsLabel = createStatCard("Total Heals", "0", JBColor.BLUE);
        pendingHealsLabel = createStatCard("Pending Review", "0", JBColor.ORANGE);
        JBLabel acceptedLabel = createStatCard("Accepted", "0", JBColor.GREEN);

        panel.add(createStatPanel(successRateLabel, "Success Rate"));
        panel.add(createStatPanel(totalHealsLabel, "Total Heals"));
        panel.add(createStatPanel(pendingHealsLabel, "Pending Review"));
        panel.add(createStatPanel(acceptedLabel, "Accepted"));

        return panel;
    }

    private JBLabel createStatCard(String label, String value, Color color) {
        JBLabel statLabel = new JBLabel(value);
        statLabel.setFont(statLabel.getFont().deriveFont(Font.BOLD, 24f));
        statLabel.setForeground(color);
        return statLabel;
    }

    private JPanel createStatPanel(JBLabel valueLabel, String title) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(10));

        JBLabel titleLabel = new JBLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 11f));

        panel.add(valueLabel, BorderLayout.CENTER);
        panel.add(titleLabel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createActivityPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Recent Activity"));

        recentActivityLabel = new JBLabel("<html><i>No recent activity</i></html>");
        recentActivityLabel.setBorder(JBUI.Borders.empty(5));

        panel.add(recentActivityLabel, BorderLayout.CENTER);
        return panel;
    }

    private void updateDashboard() {
        // Update trust level
        HealerProjectService.TrustLevelInfo trustInfo = service.getCurrentTrustLevel();
        trustLevelLabel.setText(trustInfo.level());

        int level = parseTrustLevel(trustInfo.level());
        trustProgressBar.setValue(level);
        trustProgressBar.setString(getTrustLevelDescription(level));

        // Update statistics
        var history = service.getHealHistory();
        int total = history.size();
        long pending = history.stream()
                .filter(h -> h.status() == HealerProjectService.HealStatus.PENDING)
                .count();
        long accepted = history.stream()
                .filter(h -> h.status() == HealerProjectService.HealStatus.ACCEPTED)
                .count();

        totalHealsLabel.setText(String.valueOf(total));
        pendingHealsLabel.setText(String.valueOf(pending));
        successRateLabel.setText(String.format("%.1f%%", trustInfo.successRate()));

        // Update recent activity
        if (!history.isEmpty()) {
            var recent = history.get(0);
            recentActivityLabel.setText(String.format(
                    "<html><b>%s</b><br/>%s → %s<br/><i>%.0f%% confidence</i></html>",
                    recent.stepText(),
                    truncate(recent.originalLocator(), 30),
                    truncate(recent.healedLocator(), 30),
                    recent.confidence() * 100
            ));
        }
    }

    private int parseTrustLevel(String level) {
        if (level == null) return 0;
        return switch (level) {
            case "L0_SHADOW" -> 0;
            case "L1_SUGGEST" -> 1;
            case "L2_PROMPT" -> 2;
            case "L3_AUTO" -> 3;
            case "L4_SILENT" -> 4;
            default -> 0;
        };
    }

    private String getTrustLevelDescription(int level) {
        return switch (level) {
            case 0 -> "Shadow Mode";
            case 1 -> "Suggest Mode";
            case 2 -> "Prompt Mode";
            case 3 -> "Auto Mode";
            case 4 -> "Silent Mode";
            default -> "Unknown";
        };
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen - 3) + "..." : text;
    }

    @Override
    public void onHealAdded(HealerProjectService.HealHistoryEntry entry) {
        SwingUtilities.invokeLater(this::updateDashboard);
    }

    @Override
    public void onTrustLevelChanged(HealerProjectService.TrustLevelInfo trustLevel) {
        SwingUtilities.invokeLater(this::updateDashboard);
    }

    @Override
    public void onHistoryRefreshed() {
        SwingUtilities.invokeLater(this::updateDashboard);
    }

    @Override
    public void dispose() {
        if (refreshTimer != null) {
            refreshTimer.cancel();
        }
        service.removeListener(this);
    }
}
