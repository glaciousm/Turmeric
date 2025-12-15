package com.intenthealer.cli;

import com.intenthealer.cli.commands.CacheCommand;
import com.intenthealer.cli.commands.ConfigCommand;
import com.intenthealer.cli.commands.ReportCommand;
import com.intenthealer.core.config.ConfigLoader;
import com.intenthealer.core.config.HealerConfig;
import com.intenthealer.report.ReportGenerator;

/**
 * Command-line interface for Intent Healer utilities.
 */
public class HealerCli {

    private static final ConfigCommand configCmd = new ConfigCommand();
    private static final CacheCommand cacheCmd = new CacheCommand();
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
                case "version" -> printVersion();
                case "help", "-h", "--help" -> printUsage();
                default -> {
                    System.err.println("Unknown command: " + command);
                    printUsage();
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
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
                System.err.println("Unknown config subcommand: " + args[0]);
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
                    System.err.println("Usage: healer cache import <file>");
                    return;
                }
                cacheCmd.importCache(args[1]);
            }
            default -> {
                System.err.println("Unknown cache subcommand: " + args[0]);
                printCacheUsage();
            }
        }
    }

    private static void handleReportCommand(String[] args) throws Exception {
        HealerConfig config = ConfigLoader.load();
        if (reportCmd == null) {
            reportCmd = new ReportCommand(new ReportGenerator(config.getReports()));
        }

        if (args.length == 0) {
            String dir = config.getReports() != null ? config.getReports().getOutputDir() : "./healer-reports";
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
            default -> {
                System.err.println("Unknown report subcommand: " + args[0]);
                printReportUsage();
            }
        }
    }

    private static void printVersion() {
        System.out.println("Intent Healer v1.0.0-SNAPSHOT");
        System.out.println("LLM-powered semantic test recovery for Selenium + Cucumber");
    }

    private static void printUsage() {
        System.out.println("""
            ╔═══════════════════════════════════════════════════════════════╗
            ║                    INTENT HEALER CLI                          ║
            ║         LLM-Powered Semantic Test Recovery                    ║
            ╚═══════════════════════════════════════════════════════════════╝

            Usage: healer <command> [options]

            Commands:
              config    Manage healer configuration
              cache     Manage heal cache
              report    View and generate heal reports
              version   Show version information
              help      Show this help message

            Examples:
              healer config show          Show current configuration
              healer config init          Create default config file
              healer cache stats          Show cache statistics
              healer cache clear --force  Clear the cache
              healer report summary       Show heal summary
              healer report list          List recent heal events

            For command-specific help:
              healer <command> help

            Documentation: https://github.com/intenthealer/intent-healer
            """);
    }

    private static void printConfigUsage() {
        System.out.println("""
            Usage: healer config <subcommand>

            Subcommands:
              show      Show current configuration
              validate  Validate configuration file
              init      Create default configuration file
              where     Show config file locations
            """);
    }

    private static void printCacheUsage() {
        System.out.println("""
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
        System.out.println("""
            Usage: healer report <subcommand>

            Subcommands:
              summary [dir]              Show report summary
              list [dir] [limit]         List recent heal events
              generate <in> <out> [fmt]  Generate HTML report
            """);
    }
}
