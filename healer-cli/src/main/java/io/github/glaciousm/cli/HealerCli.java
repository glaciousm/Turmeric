package io.github.glaciousm.cli;

import io.github.glaciousm.cli.commands.ApproveCommand;
import io.github.glaciousm.cli.commands.CacheCommand;
import io.github.glaciousm.cli.commands.ConfigCommand;
import io.github.glaciousm.cli.commands.ReportCommand;
import io.github.glaciousm.cli.commands.WatchCommand;
import io.github.glaciousm.cli.util.CliOutput;
import io.github.glaciousm.core.config.ConfigLoader;
import io.github.glaciousm.core.config.HealerConfig;
import io.github.glaciousm.report.ReportGenerator;

/**
 * Command-line interface for Intent Healer utilities.
 */
public class HealerCli {

    private static final ConfigCommand configCmd = new ConfigCommand();
    private static final CacheCommand cacheCmd = new CacheCommand();
    private static final WatchCommand watchCmd = new WatchCommand();
    private static final ApproveCommand approveCmd = new ApproveCommand();
    private static ReportCommand reportCmd;

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String command = args[0];
        String[] subArgs = args.length > 1 ? java.util.Arrays.copyOfRange(args, 1, args.length) : new String[0];

        try {
            switch (command) {
                case "config" -> handleConfigCommand(subArgs);
                case "cache" -> handleCacheCommand(subArgs);
                case "report" -> handleReportCommand(subArgs);
                case "watch" -> handleWatchCommand(subArgs);
                case "approve" -> handleApproveCommand(subArgs);
                case "version" -> printVersion();
                case "help", "-h", "--help" -> printUsage();
                default -> {
                    CliOutput.error("Unknown command: " + command);
                    printUsage();
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            CliOutput.error("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void handleConfigCommand(String[] args) throws Exception {
        if (args.length == 0) {
            configCmd.show();
            return;
        }

        switch (args[0]) {
            case "show" -> configCmd.show();
            case "validate" -> configCmd.validate();
            case "init" -> {
                String path = args.length > 1 ? args[1] : "healer-config.yml";
                configCmd.init(path);
            }
            case "where" -> configCmd.where();
            default -> {
                CliOutput.error("Unknown config subcommand: " + args[0]);
                printConfigUsage();
            }
        }
    }

    private static void handleCacheCommand(String[] args) throws Exception {
        if (args.length == 0) {
            cacheCmd.stats();
            return;
        }

        switch (args[0]) {
            case "stats" -> cacheCmd.stats();
            case "clear" -> {
                boolean force = args.length > 1 && args[1].equals("--force");
                cacheCmd.clear(force);
            }
            case "warmup" -> {
                String dir = args.length > 1 ? args[1] : "./healer-reports";
                cacheCmd.warmup(dir);
            }
            case "export" -> {
                String path = args.length > 1 ? args[1] : "./heal-cache-export.json";
                cacheCmd.export(path);
            }
            case "import" -> {
                if (args.length < 2) {
                    CliOutput.error("Usage: healer cache import <file>");
                    return;
                }
                cacheCmd.importCache(args[1]);
            }
            default -> {
                CliOutput.error("Unknown cache subcommand: " + args[0]);
                printCacheUsage();
            }
        }
    }

    private static void handleReportCommand(String[] args) throws Exception {
        HealerConfig config = new ConfigLoader().load();
        if (reportCmd == null) {
            reportCmd = new ReportCommand(new ReportGenerator(config.getReport()));
        }

        if (args.length == 0) {
            String dir = config.getReport() != null ? config.getReport().getOutputDir() : "./healer-reports";
            reportCmd.summary(dir);
            return;
        }

        switch (args[0]) {
            case "summary" -> {
                String dir = args.length > 1 ? args[1] : "./healer-reports";
                reportCmd.summary(dir);
            }
            case "list" -> {
                String dir = args.length > 1 ? args[1] : "./healer-reports";
                int limit = args.length > 2 ? Integer.parseInt(args[2]) : 20;
                reportCmd.list(dir, limit);
            }
            case "generate" -> {
                String inputDir = args.length > 1 ? args[1] : "./healer-reports";
                String outputPath = args.length > 2 ? args[2] : "./healer-report.html";
                String format = args.length > 3 ? args[3] : "html";
                reportCmd.generate(inputDir, outputPath, format);
            }
            case "export" -> handleReportExportCommand(args, config);
            case "help" -> printReportUsage();
            default -> {
                CliOutput.error("Unknown report subcommand: " + args[0]);
                printReportUsage();
            }
        }
    }

    private static void handleReportExportCommand(String[] args, HealerConfig config) throws Exception {
        if (args.length < 2) {
            CliOutput.error("Usage: healer report export <format> [options]");
            CliOutput.println("Formats: csv, junit-xml, summary, trends");
            return;
        }

        String defaultDir = config.getReport() != null ? config.getReport().getOutputDir() : "./healer-reports";
        String format = args[1];

        switch (format) {
            case "csv" -> {
                String dir = args.length > 2 ? args[2] : defaultDir;
                String output = args.length > 3 ? args[3] : "./healer-export.csv";
                reportCmd.exportCsv(dir, output);
            }
            case "junit-xml", "junit", "xml" -> {
                String dir = args.length > 2 ? args[2] : defaultDir;
                String output = args.length > 3 ? args[3] : "./healer-results.xml";
                reportCmd.exportJunitXml(dir, output);
            }
            case "summary", "txt", "text" -> {
                String dir = args.length > 2 ? args[2] : defaultDir;
                String output = args.length > 3 ? args[3] : "./healer-summary.txt";
                reportCmd.exportSummary(dir, output);
            }
            case "trends" -> {
                String dir = args.length > 2 ? args[2] : defaultDir;
                String output = args.length > 3 ? args[3] : "./healer-trends.csv";
                reportCmd.exportTrends(dir, output);
            }
            case "pdf" -> {
                String dir = args.length > 2 ? args[2] : defaultDir;
                String output = args.length > 3 ? args[3] : "./healer-report.pdf";
                reportCmd.exportPdf(dir, output);
            }
            default -> {
                CliOutput.error("Unknown export format: " + format);
                CliOutput.println("Supported formats: csv, junit-xml, summary, trends, pdf");
            }
        }
    }

    private static void handleWatchCommand(String[] args) throws Exception {
        if (args.length == 0) {
            watchCmd.watch();
            return;
        }

        switch (args[0]) {
            case "help" -> printWatchUsage();
            default -> watchCmd.watch(args[0]);
        }
    }

    private static void handleApproveCommand(String[] args) throws Exception {
        if (args.length == 0) {
            approveCmd.list();
            return;
        }

        switch (args[0]) {
            case "start" -> {
                int port = args.length > 1 ? Integer.parseInt(args[1]) : 7654;
                approveCmd.start(port);
            }
            case "list" -> approveCmd.list();
            case "yes", "y" -> {
                if (args.length > 1) {
                    approveCmd.approve(args[1]);
                } else {
                    CliOutput.error("Usage: healer approve yes <id>");
                }
            }
            case "no", "n" -> {
                if (args.length > 1) {
                    String reason = args.length > 2 ? args[2] : "Rejected by user";
                    approveCmd.reject(args[1], reason);
                } else {
                    CliOutput.error("Usage: healer approve no <id> [reason]");
                }
            }
            case "all" -> approveCmd.approveAll();
            case "help" -> printApproveUsage();
            default -> {
                CliOutput.error("Unknown approve subcommand: " + args[0]);
                printApproveUsage();
            }
        }
    }

    private static void printVersion() {
        CliOutput.println("Intent Healer v1.0.0-SNAPSHOT");
        CliOutput.println("LLM-powered semantic test recovery for Selenium + Cucumber");
    }

    private static void printUsage() {
        CliOutput.println("""
            ╔═══════════════════════════════════════════════════════════════╗
            ║                    INTENT HEALER CLI                          ║
            ║         LLM-Powered Semantic Test Recovery                    ║
            ╚═══════════════════════════════════════════════════════════════╝

            Usage: healer <command> [options]

            Commands:
              config    Manage healer configuration
              cache     Manage heal cache
              report    View and generate heal reports
              watch     Watch for heal events in real-time
              approve   Approve/reject heals in CONFIRM mode
              version   Show version information
              help      Show this help message

            Examples:
              healer config show          Show current configuration
              healer config init          Create default config file
              healer cache stats          Show cache statistics
              healer cache clear --force  Clear the cache
              healer report summary       Show heal summary
              healer report list          List recent heal events
              healer watch                Watch for heal events live
              healer approve start        Start approval server

            For command-specific help:
              healer <command> help

            Documentation: https://github.com/intenthealer/intent-healer
            """);
    }

    private static void printConfigUsage() {
        CliOutput.println("""
            Usage: healer config <subcommand>

            Subcommands:
              show      Show current configuration
              validate  Validate configuration file
              init      Create default configuration file
              where     Show config file locations
            """);
    }

    private static void printCacheUsage() {
        CliOutput.println("""
            Usage: healer cache <subcommand>

            Subcommands:
              stats         Show cache statistics
              clear         Clear the cache (use --force to confirm)
              warmup <dir>  Warm up cache from reports
              export <file> Export cache to file
              import <file> Import cache from file
            """);
    }

    private static void printReportUsage() {
        CliOutput.println("""
            Usage: healer report <subcommand>

            Subcommands:
              summary [dir]              Show report summary
              list [dir] [limit]         List recent heal events
              generate <in> <out> [fmt]  Generate HTML report
              export <format> [dir] [out] Export reports to various formats

            Export formats:
              csv                        Export to CSV for spreadsheet analysis
              junit-xml                  Export to JUnit XML for CI integration
              summary                    Export to text summary file
              trends                     Export trend data across multiple runs
              pdf                        Export to PDF for sharing and archiving

            Examples:
              healer report summary                     Show summary from default dir
              healer report export csv                  Export to CSV
              healer report export junit-xml ./reports ./results.xml
              healer report export trends ./reports ./trends.csv
              healer report export pdf ./reports ./healer-report.pdf
            """);
    }

    private static void printWatchUsage() {
        CliOutput.println("""
            Usage: healer watch [directory]

            Watch for heal events in real-time as tests execute.
            Monitors the specified directory (default: ./healer-reports) for
            new heal report files and displays events as they occur.

            Examples:
              healer watch                       Watch default directory
              healer watch ./custom-reports      Watch custom directory

            Press Ctrl+C to stop watching.
            """);
    }

    private static void printApproveUsage() {
        CliOutput.println("""
            Usage: healer approve <subcommand>

            Approve or reject heal proposals when using CONFIRM mode.

            Subcommands:
              start [port]      Start the approval server (default port: 7654)
              list              List pending approvals
              yes <id>          Approve a heal by ID (or prefix)
              no <id> [reason]  Reject a heal by ID with optional reason
              all               Approve all pending heals

            Examples:
              healer approve start         Start approval server
              healer approve list          List pending heals
              healer approve yes abc123    Approve heal abc123
              healer approve no abc123 "Locator looks unstable"
              healer approve all           Approve all pending
            """);
    }
}
