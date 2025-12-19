package io.github.glaciousm.intellij.ui;

import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import io.github.glaciousm.intellij.services.HealerProjectService;
import io.github.glaciousm.intellij.services.HealerProjectService.LocatorStabilityEntry;
import io.github.glaciousm.intellij.services.HealerProjectService.StabilitySummary;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel showing locator stability scores.
 */
public class LocatorStabilityPanel extends JBPanel<LocatorStabilityPanel> {

    private final Project project;
    private final HealerProjectService service;
    private final JBTable stabilityTable;
    private final StabilityTableModel tableModel;
    private final JBLabel totalLabel;
    private final JBLabel stableLabel;
    private final JBLabel moderateLabel;
    private final JBLabel unstableLabel;

    public LocatorStabilityPanel(@NotNull Project project) {
        super(new BorderLayout());
        this.project = project;
        this.service = HealerProjectService.getInstance(project);
        this.tableModel = new StabilityTableModel();
        this.stabilityTable = new JBTable(tableModel);

        // Initialize summary labels
        this.totalLabel = createValueLabel("0", Color.BLUE);
        this.stableLabel = createValueLabel("0", new Color(0, 150, 0));
        this.moderateLabel = createValueLabel("0", Color.ORANGE);
        this.unstableLabel = createValueLabel("0", Color.RED);

        initComponents();
        refresh();
    }

    private JBLabel createValueLabel(String text, Color color) {
        JBLabel label = new JBLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 20f));
        label.setForeground(color);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        return label;
    }

    private void initComponents() {
        setBorder(JBUI.Borders.empty(5));

        // Header with summary
        JPanel headerPanel = new JPanel(new GridLayout(1, 4, 10, 0));
        headerPanel.setBorder(JBUI.Borders.empty(5, 0, 10, 0));

        headerPanel.add(createSummaryCard("Total Locators", totalLabel, Color.BLUE));
        headerPanel.add(createSummaryCard("Stable", stableLabel, new Color(0, 150, 0)));
        headerPanel.add(createSummaryCard("Moderate", moderateLabel, Color.ORANGE));
        headerPanel.add(createSummaryCard("Unstable", unstableLabel, Color.RED));

        // Configure table
        stabilityTable.setRowHeight(25);
        stabilityTable.getColumnModel().getColumn(0).setPreferredWidth(250); // Locator
        stabilityTable.getColumnModel().getColumn(1).setPreferredWidth(80);  // Score
        stabilityTable.getColumnModel().getColumn(2).setPreferredWidth(100); // Level
        stabilityTable.getColumnModel().getColumn(3).setPreferredWidth(80);  // Successes
        stabilityTable.getColumnModel().getColumn(4).setPreferredWidth(80);  // Failures
        stabilityTable.getColumnModel().getColumn(5).setPreferredWidth(80);  // Heals

        // Score column renderer
        stabilityTable.getColumnModel().getColumn(1).setCellRenderer(new ScoreCellRenderer());
        stabilityTable.getColumnModel().getColumn(2).setCellRenderer(new LevelCellRenderer());

        // Toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refresh());
        toolbar.add(refreshBtn);

        JButton exportBtn = new JButton("Export Report");
        exportBtn.addActionListener(e -> exportReport());
        toolbar.add(exportBtn);

        // Info label
        JBLabel infoLabel = new JBLabel(
                "<html><i>Locator stability is tracked automatically. " +
                "Lower scores indicate locators that frequently need healing.</i></html>"
        );
        infoLabel.setBorder(JBUI.Borders.empty(10, 0));

        // Layout
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(headerPanel, BorderLayout.NORTH);
        topPanel.add(toolbar, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);
        add(new JBScrollPane(stabilityTable), BorderLayout.CENTER);
        add(infoLabel, BorderLayout.SOUTH);
    }

    private JPanel createSummaryCard(String label, JBLabel valueLabel, Color color) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color, 1),
                JBUI.Borders.empty(5)
        ));

        JBLabel titleLabel = new JBLabel(label);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 10f));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);

        panel.add(valueLabel, BorderLayout.CENTER);
        panel.add(titleLabel, BorderLayout.SOUTH);

        return panel;
    }

    private void refresh() {
        // Load stability data from service
        List<LocatorStabilityEntry> stabilities = service.getLocatorStabilities();
        tableModel.setData(stabilities);

        // Update summary
        StabilitySummary summary = service.getStabilitySummary();
        totalLabel.setText(String.valueOf(summary.total()));
        stableLabel.setText(String.valueOf(summary.stable()));
        moderateLabel.setText(String.valueOf(summary.moderate()));
        unstableLabel.setText(String.valueOf(summary.unstable()));
    }

    private void exportReport() {
        FileSaverDescriptor descriptor = new FileSaverDescriptor(
                "Export Stability Report",
                "Export locator stability data to CSV or JSON",
                "csv", "json"
        );

        FileSaverDialog dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project);
        VirtualFileWrapper wrapper = dialog.save((com.intellij.openapi.vfs.VirtualFile) null, "stability-report");

        if (wrapper != null) {
            try {
                String path = wrapper.getFile().getAbsolutePath();
                if (path.endsWith(".json")) {
                    exportAsJson(path);
                } else {
                    exportAsCsv(path);
                }
                JOptionPane.showMessageDialog(this,
                        "Report exported successfully to:\n" + path,
                        "Export Complete",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Failed to export report: " + ex.getMessage(),
                        "Export Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportAsCsv(String path) throws IOException {
        try (FileWriter writer = new FileWriter(path)) {
            writer.write("Locator,Score,Level,Successes,Failures,Heals\n");
            for (LocatorStabilityEntry entry : service.getLocatorStabilities()) {
                writer.write(String.format("\"%s\",%.1f,%s,%d,%d,%d\n",
                        entry.locator().replace("\"", "\"\""),
                        entry.score(),
                        entry.level(),
                        entry.successes(),
                        entry.failures(),
                        entry.heals()
                ));
            }
        }
    }

    private void exportAsJson(String path) throws IOException {
        try (FileWriter writer = new FileWriter(path)) {
            writer.write("{\n  \"stabilityData\": [\n");
            List<LocatorStabilityEntry> entries = service.getLocatorStabilities();
            for (int i = 0; i < entries.size(); i++) {
                LocatorStabilityEntry entry = entries.get(i);
                writer.write(String.format(
                        "    {\"locator\": \"%s\", \"score\": %.1f, \"level\": \"%s\", " +
                        "\"successes\": %d, \"failures\": %d, \"heals\": %d}%s\n",
                        entry.locator().replace("\"", "\\\""),
                        entry.score(),
                        entry.level(),
                        entry.successes(),
                        entry.failures(),
                        entry.heals(),
                        i < entries.size() - 1 ? "," : ""
                ));
            }
            writer.write("  ]\n}");
        }
    }

    /**
     * Table model for stability data.
     */
    private static class StabilityTableModel extends AbstractTableModel {
        private final String[] columns = {"Locator", "Score", "Level", "Successes", "Failures", "Heals"};
        private List<LocatorStabilityEntry> data = new ArrayList<>();

        public void setData(List<LocatorStabilityEntry> data) {
            this.data = new ArrayList<>(data);
            // Sort by score (lowest first - most unstable at top)
            this.data.sort((a, b) -> Double.compare(a.score(), b.score()));
            fireTableDataChanged();
        }

        public LocatorStabilityEntry getEntry(int row) {
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
            LocatorStabilityEntry entry = data.get(row);
            return switch (column) {
                case 0 -> truncate(entry.locator(), 50);
                case 1 -> entry.score();
                case 2 -> entry.level();
                case 3 -> entry.successes();
                case 4 -> entry.failures();
                case 5 -> entry.heals();
                default -> "";
            };
        }

        private String truncate(String text, int maxLen) {
            if (text == null) return "";
            return text.length() > maxLen ? text.substring(0, maxLen - 3) + "..." : text;
        }
    }

    /**
     * Renderer for score column.
     */
    private static class ScoreCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (value instanceof Number score) {
                double scoreVal = score.doubleValue();
                setText(String.format("%.1f", scoreVal));
                if (!isSelected) {
                    if (scoreVal >= 75) {
                        setForeground(new Color(0, 150, 0));
                    } else if (scoreVal >= 50) {
                        setForeground(Color.ORANGE);
                    } else {
                        setForeground(Color.RED);
                    }
                }
            }

            return c;
        }
    }

    /**
     * Renderer for level column.
     */
    private static class LevelCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (value instanceof String level && !isSelected) {
                switch (level) {
                    case "VERY_STABLE", "STABLE" -> setForeground(new Color(0, 150, 0));
                    case "MODERATE" -> setForeground(Color.ORANGE);
                    case "UNSTABLE", "VERY_UNSTABLE" -> setForeground(Color.RED);
                }
            }

            return c;
        }
    }
}
