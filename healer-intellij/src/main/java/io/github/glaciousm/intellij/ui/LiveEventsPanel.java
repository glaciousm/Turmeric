package io.github.glaciousm.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import io.github.glaciousm.intellij.services.HealEventWatcher;
import io.github.glaciousm.intellij.services.HealerProjectService;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;

/**
 * Panel showing real-time heal events as they occur.
 * Displays a live feed of healing activity from the test runner.
 */
public class LiveEventsPanel extends JBPanel<LiveEventsPanel> implements Disposable,
        HealEventWatcher.HealEventListener {

    private static final int MAX_EVENTS = 50;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Project project;
    private final HealEventWatcher watcher;
    private final LinkedList<EventEntry> events;

    private JBLabel statusLabel;
    private JPanel eventsContainer;
    private JButton toggleButton;
    private JButton clearButton;

    public LiveEventsPanel(@NotNull Project project) {
        super(new BorderLayout());
        this.project = project;
        this.watcher = new HealEventWatcher(project);
        this.events = new LinkedList<>();

        initComponents();
        watcher.addListener(this);
    }

    private void initComponents() {
        setBorder(JBUI.Borders.empty(5));

        // Toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        toggleButton = new JButton("Start Watching");
        toggleButton.addActionListener(e -> toggleWatching());
        toolbar.add(toggleButton);

        toolbar.addSeparator();

        clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> clearEvents());
        toolbar.add(clearButton);

        toolbar.add(Box.createHorizontalGlue());

        statusLabel = new JBLabel("Not watching");
        statusLabel.setForeground(JBColor.GRAY);
        toolbar.add(statusLabel);

        add(toolbar, BorderLayout.NORTH);

        // Events container
        eventsContainer = new JPanel();
        eventsContainer.setLayout(new BoxLayout(eventsContainer, BoxLayout.Y_AXIS));
        eventsContainer.setBorder(JBUI.Borders.empty(5));

        // Empty state
        JBLabel emptyLabel = new JBLabel("<html><center><i>No heal events yet.<br/>Click 'Start Watching' to begin monitoring.</i></center></html>");
        emptyLabel.setForeground(JBColor.GRAY);
        emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
        eventsContainer.add(emptyLabel);

        JBScrollPane scrollPane = new JBScrollPane(eventsContainer);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void toggleWatching() {
        if (watcher.isWatching()) {
            watcher.stopWatching();
        } else {
            watcher.startWatching();
        }
    }

    private void clearEvents() {
        events.clear();
        updateEventsDisplay();
    }

    private void updateEventsDisplay() {
        SwingUtilities.invokeLater(() -> {
            eventsContainer.removeAll();

            if (events.isEmpty()) {
                JBLabel emptyLabel = new JBLabel("<html><center><i>No heal events yet.</i></center></html>");
                emptyLabel.setForeground(JBColor.GRAY);
                emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
                eventsContainer.add(emptyLabel);
            } else {
                for (EventEntry entry : events) {
                    eventsContainer.add(createEventCard(entry));
                    eventsContainer.add(Box.createVerticalStrut(5));
                }
            }

            eventsContainer.revalidate();
            eventsContainer.repaint();
        });
    }

    private JPanel createEventCard(EventEntry entry) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(getStatusColor(entry), 1),
                JBUI.Borders.empty(8)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        card.setBackground(JBColor.background());

        // Header with timestamp and status
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JBLabel timeLabel = new JBLabel(TIME_FORMAT.format(entry.timestamp()));
        timeLabel.setFont(timeLabel.getFont().deriveFont(Font.PLAIN, 10f));
        timeLabel.setForeground(JBColor.GRAY);
        header.add(timeLabel, BorderLayout.WEST);

        JBLabel statusBadge = new JBLabel(entry.isSuccess() ? "SUCCESS" : "HEALED");
        statusBadge.setFont(statusBadge.getFont().deriveFont(Font.BOLD, 10f));
        statusBadge.setForeground(getStatusColor(entry));
        header.add(statusBadge, BorderLayout.EAST);

        card.add(header, BorderLayout.NORTH);

        // Body with step text
        JBLabel stepLabel = new JBLabel("<html><b>" + escapeHtml(truncate(entry.stepText(), 60)) + "</b></html>");
        card.add(stepLabel, BorderLayout.CENTER);

        // Footer with locator info
        JPanel footer = new JPanel(new GridLayout(2, 1, 0, 2));
        footer.setOpaque(false);
        footer.setBorder(JBUI.Borders.emptyTop(5));

        JBLabel origLabel = new JBLabel("<html><font color='gray'>From:</font> <code>" +
                escapeHtml(truncate(entry.originalLocator(), 50)) + "</code></html>");
        origLabel.setFont(origLabel.getFont().deriveFont(10f));

        JBLabel healedLabel = new JBLabel("<html><font color='green'>To:</font> <code>" +
                escapeHtml(truncate(entry.healedLocator(), 50)) + "</code></html>");
        healedLabel.setFont(healedLabel.getFont().deriveFont(10f));

        footer.add(origLabel);
        footer.add(healedLabel);
        card.add(footer, BorderLayout.SOUTH);

        return card;
    }

    private Color getStatusColor(EventEntry entry) {
        if (entry.confidence() >= 0.9) {
            return new Color(76, 175, 80); // Green
        } else if (entry.confidence() >= 0.75) {
            return new Color(255, 193, 7); // Yellow
        } else {
            return new Color(255, 152, 0); // Orange
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen - 3) + "..." : text;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public void onWatchingStarted(Path directory) {
        SwingUtilities.invokeLater(() -> {
            toggleButton.setText("Stop Watching");
            statusLabel.setText("Watching: " + directory.getFileName());
            statusLabel.setForeground(new Color(76, 175, 80));
        });
    }

    @Override
    public void onWatchingStopped() {
        SwingUtilities.invokeLater(() -> {
            toggleButton.setText("Start Watching");
            statusLabel.setText("Not watching");
            statusLabel.setForeground(JBColor.GRAY);
        });
    }

    @Override
    public void onHealEventReceived(HealerProjectService.HealHistoryEntry entry) {
        EventEntry eventEntry = new EventEntry(
                entry.timestamp(),
                entry.stepText(),
                entry.originalLocator(),
                entry.healedLocator(),
                entry.confidence(),
                entry.status() == HealerProjectService.HealStatus.ACCEPTED
        );

        // Add to front, keep limited size
        events.addFirst(eventEntry);
        while (events.size() > MAX_EVENTS) {
            events.removeLast();
        }

        updateEventsDisplay();

        // Scroll to top
        SwingUtilities.invokeLater(() -> {
            JScrollPane scrollPane = (JScrollPane) eventsContainer.getParent().getParent();
            scrollPane.getVerticalScrollBar().setValue(0);
        });
    }

    @Override
    public void dispose() {
        watcher.dispose();
    }

    /**
     * Internal event entry record.
     */
    private record EventEntry(
            java.time.Instant timestamp,
            String stepText,
            String originalLocator,
            String healedLocator,
            double confidence,
            boolean isSuccess
    ) {}
}
