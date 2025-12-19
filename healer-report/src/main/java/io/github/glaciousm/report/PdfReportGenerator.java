package io.github.glaciousm.report;

import io.github.glaciousm.report.model.HealEvent;
import io.github.glaciousm.report.model.HealReport;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Generates PDF reports from healing events.
 * Produces professional reports suitable for sharing and archiving.
 */
public class PdfReportGenerator {

    private static final Logger logger = LoggerFactory.getLogger(PdfReportGenerator.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Colors
    private static final Color HEADER_BG = new Color(33, 150, 243);
    private static final Color SUCCESS_COLOR = new Color(76, 175, 80);
    private static final Color REFUSED_COLOR = new Color(255, 152, 0);
    private static final Color FAILED_COLOR = new Color(244, 67, 54);
    private static final Color LIGHT_GRAY = new Color(245, 245, 245);
    private static final Color TEXT_GRAY = new Color(100, 100, 100);

    // Fonts
    private Font titleFont;
    private Font headerFont;
    private Font subHeaderFont;
    private Font normalFont;
    private Font boldFont;
    private Font smallFont;
    private Font codeFont;

    private final HealingAnalytics analytics;

    public PdfReportGenerator() {
        this.analytics = new HealingAnalytics();
        initializeFonts();
    }

    private void initializeFonts() {
        titleFont = new Font(Font.HELVETICA, 24, Font.BOLD, Color.WHITE);
        headerFont = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(33, 33, 33));
        subHeaderFont = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(66, 66, 66));
        normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
        boldFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);
        smallFont = new Font(Font.HELVETICA, 8, Font.NORMAL, TEXT_GRAY);
        codeFont = new Font(Font.COURIER, 9, Font.NORMAL, new Color(33, 33, 33));
    }

    /**
     * Generate a PDF report from a HealReport.
     *
     * @param report the heal report
     * @param outputPath the output file path
     */
    public void generateReport(HealReport report, String outputPath) throws IOException, DocumentException {
        Path path = Path.of(outputPath);
        Files.createDirectories(path.getParent() != null ? path.getParent() : Path.of("."));

        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(path.toFile()));

        // Add page event for headers/footers
        writer.setPageEvent(new HeaderFooterPageEvent(report.getTimestamp()));

        document.open();

        // Title section
        addTitleSection(document, report);

        // Summary section
        addSummarySection(document, report);

        // Analytics section
        addAnalyticsSection(document, report);

        // Events section
        addEventsSection(document, report);

        document.close();

        logger.info("PDF report generated: {}", outputPath);
    }

    private void addTitleSection(Document document, HealReport report) throws DocumentException {
        // Header table with blue background
        PdfPTable headerTable = new PdfPTable(1);
        headerTable.setWidthPercentage(100);

        PdfPCell headerCell = new PdfPCell();
        headerCell.setBackgroundColor(HEADER_BG);
        headerCell.setPadding(20);
        headerCell.setBorder(Rectangle.NO_BORDER);

        Paragraph title = new Paragraph("Intent Healer Report", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        headerCell.addElement(title);

        Font subtitleFont = new Font(Font.HELVETICA, 12, Font.NORMAL, new Color(200, 220, 255));
        Paragraph subtitle = new Paragraph("Self-Healing Test Automation Summary", subtitleFont);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingBefore(5);
        headerCell.addElement(subtitle);

        String timestamp = formatTimestamp(report.getTimestamp());
        Paragraph dateText = new Paragraph("Generated: " + timestamp, subtitleFont);
        dateText.setAlignment(Element.ALIGN_CENTER);
        dateText.setSpacingBefore(10);
        headerCell.addElement(dateText);

        headerTable.addCell(headerCell);
        document.add(headerTable);
        document.add(new Paragraph("\n"));
    }

    private void addSummarySection(Document document, HealReport report) throws DocumentException {
        document.add(new Paragraph("Summary", headerFont));
        document.add(new Paragraph("\n"));

        HealReport.ReportSummary summary = report.getSummary();

        // Summary table
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 1, 1, 1});

        // Stat cards
        addStatCard(table, String.valueOf(summary.getHealAttempts()), "Heal Attempts", HEADER_BG);
        addStatCard(table, String.valueOf(summary.getHealSuccesses()), "Successes", SUCCESS_COLOR);
        addStatCard(table, String.valueOf(summary.getHealRefusals()), "Refused", REFUSED_COLOR);
        addStatCard(table, String.valueOf(summary.getHealFailures()), "Failed", FAILED_COLOR);

        document.add(table);
        document.add(new Paragraph("\n"));

        // Additional metrics
        PdfPTable metricsTable = new PdfPTable(3);
        metricsTable.setWidthPercentage(100);

        double successRate = summary.getHealAttempts() > 0
                ? (summary.getHealSuccesses() * 100.0) / summary.getHealAttempts() : 0;

        addMetricCell(metricsTable, "Success Rate", String.format("%.1f%%", successRate));
        addMetricCell(metricsTable, "LLM Cost", String.format("$%.4f", summary.getTotalLlmCostUsd()));
        addMetricCell(metricsTable, "Duration", formatDuration(report.getDurationMs()));

        document.add(metricsTable);
        document.add(new Paragraph("\n"));
    }

    private void addStatCard(PdfPTable table, String value, String label, Color color) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(LIGHT_GRAY);
        cell.setPadding(15);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Font valueFont = new Font(Font.HELVETICA, 28, Font.BOLD, color);
        Paragraph valuePara = new Paragraph(value, valueFont);
        valuePara.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(valuePara);

        Paragraph labelPara = new Paragraph(label, smallFont);
        labelPara.setAlignment(Element.ALIGN_CENTER);
        labelPara.setSpacingBefore(5);
        cell.addElement(labelPara);

        table.addCell(cell);
    }

    private void addMetricCell(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(LIGHT_GRAY);
        cell.setBackgroundColor(LIGHT_GRAY);
        cell.setPadding(10);

        Paragraph labelPara = new Paragraph(label, smallFont);
        labelPara.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(labelPara);

        Paragraph valuePara = new Paragraph(value, boldFont);
        valuePara.setAlignment(Element.ALIGN_CENTER);
        valuePara.setSpacingBefore(3);
        cell.addElement(valuePara);

        table.addCell(cell);
    }

    private void addAnalyticsSection(Document document, HealReport report) throws DocumentException {
        if (report.getEvents() == null || report.getEvents().isEmpty()) {
            return;
        }

        document.add(new Paragraph("Analytics", headerFont));
        document.add(new Paragraph("\n"));

        HealingAnalytics.AnalyticsSummary analyticsSummary = analytics.analyzeReport(report);

        // Analytics metrics table
        PdfPTable analyticsTable = new PdfPTable(4);
        analyticsTable.setWidthPercentage(100);

        addMetricCell(analyticsTable, "Avg Confidence",
                String.format("%.0f%%", analyticsSummary.averageConfidence() * 100));
        addMetricCell(analyticsTable, "Time Saved",
                formatDuration(analyticsSummary.estimatedTimeSaved().toMillis()));
        addMetricCell(analyticsTable, "Est. Savings",
                String.format("$%.2f", analyticsSummary.getEstimatedCostSavings(75.0)));
        addMetricCell(analyticsTable, "ROI",
                String.format("%.0f%%", analyticsSummary.getROI(75.0)));

        document.add(analyticsTable);
        document.add(new Paragraph("\n"));

        // Heals by action type
        if (!analyticsSummary.healsByActionType().isEmpty()) {
            document.add(new Paragraph("Heals by Action Type", subHeaderFont));
            document.add(new Paragraph("\n"));

            PdfPTable actionTable = new PdfPTable(2);
            actionTable.setWidthPercentage(60);
            actionTable.setHorizontalAlignment(Element.ALIGN_LEFT);

            addTableHeader(actionTable, "Action Type", "Count");

            analyticsSummary.healsByActionType().entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .forEach(entry -> {
                        addTableRow(actionTable, entry.getKey(), String.valueOf(entry.getValue()));
                    });

            document.add(actionTable);
            document.add(new Paragraph("\n"));
        }

        // Most frequently healed locators
        if (!analyticsSummary.mostFrequentlyHealedLocators().isEmpty()) {
            document.add(new Paragraph("Frequently Healed Locators", subHeaderFont));
            document.add(new Paragraph("These locators break often and should be improved:", smallFont));
            document.add(new Paragraph("\n"));

            PdfPTable locatorTable = new PdfPTable(3);
            locatorTable.setWidthPercentage(100);
            locatorTable.setWidths(new float[]{3, 1, 1});

            addTableHeader(locatorTable, "Locator", "Count", "Avg Conf");

            for (HealingAnalytics.FrequentLocator loc : analyticsSummary.mostFrequentlyHealedLocators()) {
                addTableRow(locatorTable,
                        truncate(loc.locator(), 50),
                        String.valueOf(loc.healCount()),
                        String.format("%.0f%%", loc.averageConfidence() * 100));
            }

            document.add(locatorTable);
            document.add(new Paragraph("\n"));
        }
    }

    private void addEventsSection(Document document, HealReport report) throws DocumentException {
        if (report.getEvents() == null || report.getEvents().isEmpty()) {
            return;
        }

        document.newPage();
        document.add(new Paragraph("Healing Events", headerFont));
        document.add(new Paragraph("\n"));

        int index = 1;
        for (HealEvent event : report.getEvents()) {
            addEventCard(document, event, index++);
        }
    }

    private void addEventCard(Document document, HealEvent event, int index) throws DocumentException {
        String status = event.getResult() != null ? event.getResult().getStatus() : "UNKNOWN";
        Color statusColor = switch (status) {
            case "SUCCESS" -> SUCCESS_COLOR;
            case "REFUSED" -> REFUSED_COLOR;
            default -> FAILED_COLOR;
        };

        // Event card table
        PdfPTable cardTable = new PdfPTable(1);
        cardTable.setWidthPercentage(100);
        cardTable.setSpacingBefore(10);

        PdfPCell cardCell = new PdfPCell();
        cardCell.setBorder(Rectangle.BOX);
        cardCell.setBorderColor(statusColor);
        cardCell.setBorderWidth(2);
        cardCell.setPadding(12);

        // Header with step and status
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);

        PdfPCell stepCell = new PdfPCell(new Phrase(index + ". " + truncate(event.getStep(), 60), boldFont));
        stepCell.setBorder(Rectangle.NO_BORDER);
        headerTable.addCell(stepCell);

        Font statusFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
        PdfPCell statusCell = new PdfPCell(new Phrase(status, statusFont));
        statusCell.setBackgroundColor(statusColor);
        statusCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        statusCell.setPadding(5);
        statusCell.setBorder(Rectangle.NO_BORDER);
        headerTable.addCell(statusCell);

        cardCell.addElement(headerTable);

        // Details
        Paragraph featurePara = new Paragraph("Feature: " + event.getFeature(), smallFont);
        featurePara.setSpacingBefore(8);
        cardCell.addElement(featurePara);

        cardCell.addElement(new Paragraph("Scenario: " + event.getScenario(), smallFont));

        // Locator info
        String originalLocator = event.getOriginalLocator();
        String healedLocator = event.getHealedLocator();

        if (originalLocator != null && !originalLocator.isEmpty()) {
            Paragraph origPara = new Paragraph();
            origPara.setSpacingBefore(8);
            origPara.add(new Chunk("Original: ", smallFont));
            origPara.add(new Chunk(truncate(originalLocator, 60), codeFont));
            cardCell.addElement(origPara);
        }

        if (healedLocator != null && !healedLocator.isEmpty()) {
            Paragraph healedPara = new Paragraph();
            Font greenSmall = new Font(Font.HELVETICA, 8, Font.NORMAL, SUCCESS_COLOR);
            healedPara.add(new Chunk("Healed: ", greenSmall));
            healedPara.add(new Chunk(truncate(healedLocator, 60), codeFont));
            cardCell.addElement(healedPara);
        }

        // Confidence
        double confidence = event.getDecision() != null ? event.getDecision().getConfidence() * 100 : 0;
        Paragraph confPara = new Paragraph(String.format("Confidence: %.0f%%", confidence), smallFont);
        confPara.setSpacingBefore(5);
        cardCell.addElement(confPara);

        // Reasoning
        if (event.getDecision() != null && event.getDecision().getReasoning() != null) {
            Paragraph reasonPara = new Paragraph("Reasoning: " +
                    truncate(event.getDecision().getReasoning(), 150), smallFont);
            reasonPara.setSpacingBefore(5);
            cardCell.addElement(reasonPara);
        }

        cardTable.addCell(cardCell);
        document.add(cardTable);
    }

    private void addTableHeader(PdfPTable table, String... headers) {
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, boldFont));
            cell.setBackgroundColor(LIGHT_GRAY);
            cell.setPadding(8);
            table.addCell(cell);
        }
    }

    private void addTableRow(PdfPTable table, String... values) {
        for (String value : values) {
            PdfPCell cell = new PdfPCell(new Phrase(value, normalFont));
            cell.setPadding(6);
            table.addCell(cell);
        }
    }

    private String formatTimestamp(Instant timestamp) {
        if (timestamp == null) return "N/A";
        return LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault()).format(DATE_FORMAT);
    }

    private String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m " + (seconds % 60) + "s";
        }
        long hours = minutes / 60;
        return hours + "h " + (minutes % 60) + "m";
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen - 3) + "..." : text;
    }

    /**
     * Page event handler for headers and footers.
     */
    private static class HeaderFooterPageEvent extends PdfPageEventHelper {
        private final Instant reportTimestamp;
        private final Font footerFont = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(150, 150, 150));

        public HeaderFooterPageEvent(Instant reportTimestamp) {
            this.reportTimestamp = reportTimestamp;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();

            // Footer
            String footerText = String.format("Intent Healer Report | Page %d", writer.getPageNumber());

            Phrase footer = new Phrase(footerText, footerFont);
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, footer,
                    (document.right() - document.left()) / 2 + document.leftMargin(),
                    document.bottom() - 20, 0);
        }
    }
}
