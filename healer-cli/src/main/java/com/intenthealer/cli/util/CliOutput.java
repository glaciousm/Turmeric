package com.intenthealer.cli.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;

/**
 * Utility class for CLI output that combines console output with logging.
 * This allows user interaction through console while maintaining audit logs.
 */
public final class CliOutput {

    private static final Logger logger = LoggerFactory.getLogger("healer-cli");

    private static PrintStream out = System.out;
    private static PrintStream err = System.err;
    private static boolean loggingEnabled = true;

    private CliOutput() {
        // Utility class
    }

    /**
     * Set custom output streams (useful for testing).
     */
    public static void setStreams(PrintStream out, PrintStream err) {
        CliOutput.out = out;
        CliOutput.err = err;
    }

    /**
     * Reset to default streams.
     */
    public static void resetStreams() {
        out = System.out;
        err = System.err;
    }

    /**
     * Enable or disable logging (useful for testing).
     */
    public static void setLoggingEnabled(boolean enabled) {
        loggingEnabled = enabled;
    }

    /**
     * Print a line to stdout and log at INFO level.
     */
    public static void println(String message) {
        out.println(message);
        if (loggingEnabled) {
            logger.info(message);
        }
    }

    /**
     * Print an empty line to stdout.
     */
    public static void println() {
        out.println();
    }

    /**
     * Print formatted output to stdout and log at INFO level.
     */
    public static void printf(String format, Object... args) {
        String message = String.format(format, args);
        out.print(message);
        if (loggingEnabled) {
            logger.info(message.stripTrailing());
        }
    }

    /**
     * Print a message to stdout without newline.
     */
    public static void print(String message) {
        out.print(message);
    }

    /**
     * Print an error message to stderr and log at ERROR level.
     */
    public static void error(String message) {
        err.println(message);
        if (loggingEnabled) {
            logger.error(message);
        }
    }

    /**
     * Print a formatted error message to stderr and log at ERROR level.
     */
    public static void error(String format, Object... args) {
        String message = String.format(format, args);
        err.println(message);
        if (loggingEnabled) {
            logger.error(message);
        }
    }

    /**
     * Print a warning message to stdout and log at WARN level.
     */
    public static void warn(String message) {
        out.println(message);
        if (loggingEnabled) {
            logger.warn(message);
        }
    }

    /**
     * Print a success message (with checkmark).
     */
    public static void success(String message) {
        println(message);
    }

    /**
     * Print a header/banner.
     */
    public static void header(String title) {
        println();
        println("===================================================================");
        printf("                   %s%n", title);
        println("===================================================================");
        println();
    }

    /**
     * Print a divider line.
     */
    public static void divider() {
        println("===================================================================");
    }

    /**
     * Log a debug message (no console output).
     */
    public static void debug(String message) {
        if (loggingEnabled) {
            logger.debug(message);
        }
    }

    /**
     * Log a debug message with format (no console output).
     */
    public static void debug(String format, Object... args) {
        if (loggingEnabled) {
            logger.debug(format, args);
        }
    }
}
