package io.github.glaciousm.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import io.github.glaciousm.intellij.services.HealerProjectService;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Panel showing heal history with actions.
 */
public class HealHistoryPanel extends JBPanel<HealHistoryPanel> implements Disposable,
        HealerProjectService.HealHistoryListener {

    private final Project project;
    private final HealerProjectService service;
    private final JBTable historyTable;
    private final HistoryTableModel tableModel;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public HealHistoryPanel(@NotNull Project project) {
        super(new BorderLayout());
        this.project = project;
        this.service = HealerProjectService.getInstance(project);
        this.tableModel = new HistoryTableModel();
        this.historyTable = new JBTable(tableModel);

        initComponents();
        service.addListener(this);
        refresh();
    }

    private void initComponents() {
        setBorder(JBUI.Borders.empty(5));

        // Configure table
        historyTable.setRowHeight(30);
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyTable.getColumnModel().getColumn(0).setPreferredWidth(150); // Time
        historyTable.getColumnModel().getColumn(1).setPreferredWidth(200); // Step
        historyTable.getColumnModel().getColumn(2).setPreferredWidth(150); // Original
        historyTable.getColumnModel().getColumn(3).setPreferredWidth(150); // Healed
        historyTable.getColumnModel().getColumn(4).setPreferredWidth(80);  // Confidence
        historyTable.getColumnModel().getColumn(5).setPreferredWidth(80);  // Status

        // Status column renderer
        historyTable.getColumnModel().getColumn(5).setCellRenderer(new StatusCellRenderer());

        // Double-click to view details
        historyTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    showHealDetails();
                }
            }
        });

        // Toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refresh());
        toolbar.add(refreshBtn);

        JButton acceptBtn = new JButton("Accept");
        acceptBtn.addActionListener(e -> acceptSelected());
        toolbar.add(acceptBtn);

        JButton rejectBtn = new JButton("Reject");
        rejectBtn.addActionListener(e -> rejectSelected());
        toolbar.add(rejectBtn);

        JButton blacklistBtn = new JButton("Blacklist");
        blacklistBtn.addActionListener(e -> blacklistSelected());
        toolbar.add(blacklistBtn);

        toolbar.addSeparator();

        JButton clearBtn = new JButton("Clear History");
        clearBtn.addActionListener(e -> clearHistory());
        toolbar.add(clearBtn);

        // Layout
        add(toolbar, BorderLayout.NORTH);
        add(new JBScrollPane(historyTable), BorderLayout.CENTER);
    }

    private void refresh() {
        tableModel.setData(service.getHealHistory());
    }

    private void acceptSelected() {
        int row = historyTable.getSelectedRow();
        if (row >= 0) {
            var entry = tableModel.getEntry(row);
            service.acceptHeal(entry.id());
            refresh();
        }
    }

    private void rejectSelected() {
        int row = historyTable.getSelectedRow();
        if (row >= 0) {
            var entry = tableModel.getEntry(row);
            service.rejectHeal(entry.id());
            refresh();
        }
    }

    private void blacklistSelected() {
        int row = historyTable.getSelectedRow();
        if (row >= 0) {
            var entry = tableModel.getEntry(row);

            // Show dialog to get blacklist reason
            String reason = JOptionPane.showInputDialog(
                    this,
                    "Enter reason for blacklisting this heal:\n" +
                    "Original: " + entry.originalLocator() + "\n" +
                    "Healed: " + entry.healedLocator(),
                    "Blacklist Heal",
                    JOptionPane.QUESTION_MESSAGE
            );

            if (reason != null && !reason.trim().isEmpty()) {
                service.blacklistHeal(entry.id(), reason.trim());
                refresh();
                JOptionPane.showMessageDialog(this,
                        "Heal has been blacklisted. This locator mapping will not be suggested again.",
                        "Blacklisted",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    private void clearHistory() {
        int result = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to clear all heal history?",
                "Clear History", JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            service.clearHistory();
            refresh();
        }
    }

    private void showHealDetails() {
        int row = historyTable.getSelectedRow();
        if (row >= 0) {
            var entry = tableModel.getEntry(row);
            String details = String.format("""
                    <html>
                    <h3>Heal Details</h3>
                    <p><b>Feature:</b> %s</p>
                    <p><b>Scenario:</b> %s</p>
                    <p><b>Step:</b> %s</p>
                    <p><b>Original Locator:</b> %s</p>
                    <p><b>Healed Locator:</b> %s</p>
                    <p><b>Confidence:</b> %.1f%%</p>
                    <p><b>Status:</b> %s</p>
                    <h4>Reasoning:</h4>
                    <p>%s</p>
                    </html>
                    """,
                    entry.featureName(),
                    entry.scenarioName(),
                    entry.stepText(),
                    entry.originalLocator(),
                    entry.healedLocator(),
                    entry.confidence() * 100,
                    entry.status(),
                    entry.reasoning()
            );

            JOptionPane.showMessageDialog(this, details, "Heal Details", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    @Override
    public void onHealAdded(HealerProjectService.HealHistoryEntry entry) {
        SwingUtilities.invokeLater(this::refresh);
    }

    @Override
    public void onHistoryRefreshed() {
        SwingUtilities.invokeLater(this::refresh);
    }

    @Override
    public void dispose() {
        service.removeListener(this);
    }

    /**
     * Table model for heal history.
     */
    private class HistoryTableModel extends AbstractTableModel {
        private final String[] columns = {"Time", "Step", "Original", "Healed", "Confidence", "Status"};
        private List<HealerProjectService.HealHistoryEntry> data = List.of();

        public void setData(List<HealerProjectService.HealHistoryEntry> data) {
            this.data = data;
            fireTableDataChanged();
        }

        public HealerProjectService.HealHistoryEntry getEntry(int row) {
            return data.get(row);
        }

        @Override
        public int getRowCount() {
            return data.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            var entry = data.get(row);
            return switch (column) {
                case 0 -> TIME_FORMAT.format(entry.timestamp());
                case 1 -> truncate(entry.stepText(), 40);
                case 2 -> truncate(entry.originalLocator(), 30);
                case 3 -> truncate(entry.healedLocator(), 30);
                case 4 -> String.format("%.0f%%", entry.confidence() * 100);
                case 5 -> entry.status();
                default -> "";
            };
        }

        private String truncate(String text, int maxLen) {
            if (text == null) return "";
            return text.length() > maxLen ? text.substring(0, maxLen - 3) + "..." : text;
        }
    }

    /**
     * Renderer for status column.
     */
    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (value instanceof HealerProjectService.HealStatus status) {
                switch (status) {
                    case PENDING -> setForeground(Color.ORANGE);
                    case ACCEPTED -> setForeground(new Color(0, 150, 0));
                    case REJECTED -> setForeground(Color.RED);
                    case BLACKLISTED -> setForeground(Color.GRAY);
                }
            }

            return c;
        }
    }
}
