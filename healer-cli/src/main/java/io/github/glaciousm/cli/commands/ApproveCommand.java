package io.github.glaciousm.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.glaciousm.cli.util.CliOutput;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CLI command for approving heal proposals in CONFIRM mode.
 * Provides an interactive approval workflow for pending heals.
 */
public class ApproveCommand {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int DEFAULT_PORT = 7654;
    private static final String PENDING_DIR = ".healer/pending";

    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Map<String, PendingApproval> pendingApprovals = new ConcurrentHashMap<>();

    public ApproveCommand() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Start the approval server and interactive prompt.
     */
    public void start(int port) throws IOException {
        CliOutput.println("""
            ╔═══════════════════════════════════════════════════════════════╗
            ║           INTENT HEALER - APPROVAL WORKFLOW                   ║
            ╚═══════════════════════════════════════════════════════════════╝
            """);
        CliOutput.println("  Approval server starting on port " + port);
        CliOutput.println("  Press Ctrl+C to stop");
        CliOutput.println("");

        // Ensure pending directory exists
        Path pendingPath = Path.of(PENDING_DIR);
        Files.createDirectories(pendingPath);

        // Load any existing pending approvals
        loadPendingApprovals(pendingPath);

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running.set(false);
            CliOutput.println("\n  Shutting down approval server...");
        }));

        // Start server in background thread
        ExecutorService executor = Executors.newCachedThreadPool();
        executor.submit(() -> runServer(port));

        // Interactive prompt
        runInteractivePrompt();

        executor.shutdownNow();
    }

    /**
     * Start with default port.
     */
    public void start() throws IOException {
        start(DEFAULT_PORT);
    }

    /**
     * List all pending approvals.
     */
    public void list() {
        Path pendingPath = Path.of(PENDING_DIR);
        if (!Files.exists(pendingPath)) {
            CliOutput.println("  No pending approvals.");
            return;
        }

        loadPendingApprovals(pendingPath);

        if (pendingApprovals.isEmpty()) {
            CliOutput.println("  No pending approvals.");
            return;
        }

        CliOutput.println("""
            ╔═══════════════════════════════════════════════════════════════╗
            ║              PENDING HEAL APPROVALS                           ║
            ╚═══════════════════════════════════════════════════════════════╝
            """);

        int index = 1;
        for (PendingApproval approval : pendingApprovals.values()) {
            displayPendingApproval(index++, approval);
        }
    }

    /**
     * Approve a specific heal by ID.
     */
    public void approve(String id) throws IOException {
        PendingApproval approval = findApproval(id);
        if (approval == null) {
            CliOutput.error("Approval not found: " + id);
            return;
        }

        approval.status = "APPROVED";
        approval.decidedAt = Instant.now();
        saveApprovalDecision(approval);
        CliOutput.success("Approved: " + approval.id);
    }

    /**
     * Reject a specific heal by ID.
     */
    public void reject(String id, String reason) throws IOException {
        PendingApproval approval = findApproval(id);
        if (approval == null) {
            CliOutput.error("Approval not found: " + id);
            return;
        }

        approval.status = "REJECTED";
        approval.rejectionReason = reason;
        approval.decidedAt = Instant.now();
        saveApprovalDecision(approval);
        CliOutput.warn("Rejected: " + approval.id + " - " + reason);
    }

    /**
     * Approve all pending heals.
     */
    public void approveAll() throws IOException {
        if (pendingApprovals.isEmpty()) {
            CliOutput.println("  No pending approvals.");
            return;
        }

        int count = 0;
        for (PendingApproval approval : pendingApprovals.values()) {
            if ("PENDING".equals(approval.status)) {
                approval.status = "APPROVED";
                approval.decidedAt = Instant.now();
                saveApprovalDecision(approval);
                count++;
            }
        }
        CliOutput.success("Approved " + count + " pending heals.");
    }

    private void runServer(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (running.get()) {
                try {
                    serverSocket.setSoTimeout(1000);
                    Socket clientSocket = serverSocket.accept();
                    handleClient(clientSocket);
                } catch (java.net.SocketTimeoutException e) {
                    // Expected, continue loop
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                CliOutput.error("Server error: " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String line = in.readLine();
            if (line == null) return;

            // Simple protocol: SUBMIT|<json> or CHECK|<id>
            if (line.startsWith("SUBMIT|")) {
                String json = line.substring(7);
                PendingApproval approval = objectMapper.readValue(json, PendingApproval.class);
                approval.submittedAt = Instant.now();
                approval.status = "PENDING";
                pendingApprovals.put(approval.id, approval);
                savePendingApproval(approval);
                displayNewApproval(approval);
                out.println("RECEIVED|" + approval.id);
            } else if (line.startsWith("CHECK|")) {
                String id = line.substring(6);
                PendingApproval approval = pendingApprovals.get(id);
                if (approval != null) {
                    out.println(approval.status + "|" + (approval.rejectionReason != null ? approval.rejectionReason : ""));
                } else {
                    out.println("UNKNOWN|");
                }
            }
        } catch (IOException e) {
            // Client disconnected
        }
    }

    private void runInteractivePrompt() {
        Scanner scanner = new Scanner(System.in);

        while (running.get()) {
            CliOutput.print("\n  healer> ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) continue;

            String[] parts = input.split("\\s+", 2);
            String cmd = parts[0].toLowerCase();

            try {
                switch (cmd) {
                    case "list", "ls" -> list();
                    case "approve", "a" -> {
                        if (parts.length > 1) {
                            approve(parts[1]);
                        } else {
                            CliOutput.error("Usage: approve <id>");
                        }
                    }
                    case "reject", "r" -> {
                        if (parts.length > 1) {
                            String[] rejectParts = parts[1].split("\\s+", 2);
                            String id = rejectParts[0];
                            String reason = rejectParts.length > 1 ? rejectParts[1] : "Rejected by user";
                            reject(id, reason);
                        } else {
                            CliOutput.error("Usage: reject <id> [reason]");
                        }
                    }
                    case "approveall", "aa" -> approveAll();
                    case "help", "h" -> printInteractiveHelp();
                    case "quit", "exit", "q" -> {
                        running.set(false);
                        CliOutput.println("  Goodbye!");
                    }
                    default -> CliOutput.error("Unknown command: " + cmd + " (type 'help' for commands)");
                }
            } catch (Exception e) {
                CliOutput.error("Error: " + e.getMessage());
            }
        }
    }

    private void printInteractiveHelp() {
        CliOutput.println("""
              Commands:
                list (ls)           - List pending approvals
                approve (a) <id>    - Approve a heal
                reject (r) <id>     - Reject a heal
                approveall (aa)     - Approve all pending heals
                help (h)            - Show this help
                quit (q)            - Exit
            """);
    }

    private void loadPendingApprovals(Path pendingPath) {
        try (var stream = Files.list(pendingPath)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                  .forEach(p -> {
                      try {
                          PendingApproval approval = objectMapper.readValue(p.toFile(), PendingApproval.class);
                          if ("PENDING".equals(approval.status)) {
                              pendingApprovals.put(approval.id, approval);
                          }
                      } catch (IOException e) {
                          // Skip invalid files
                      }
                  });
        } catch (IOException e) {
            // Directory may not exist yet
        }
    }

    private void savePendingApproval(PendingApproval approval) throws IOException {
        Path filePath = Path.of(PENDING_DIR, approval.id + ".json");
        objectMapper.writeValue(filePath.toFile(), approval);
    }

    private void saveApprovalDecision(PendingApproval approval) throws IOException {
        Path filePath = Path.of(PENDING_DIR, approval.id + ".json");
        objectMapper.writeValue(filePath.toFile(), approval);
        pendingApprovals.remove(approval.id);
    }

    private PendingApproval findApproval(String idOrPrefix) {
        // Try exact match first
        PendingApproval exact = pendingApprovals.get(idOrPrefix);
        if (exact != null) return exact;

        // Try prefix match
        for (String key : pendingApprovals.keySet()) {
            if (key.startsWith(idOrPrefix)) {
                return pendingApprovals.get(key);
            }
        }
        return null;
    }

    private void displayNewApproval(PendingApproval approval) {
        CliOutput.println("\n  ═══════════════════════════════════════════════════════════════");
        CliOutput.println("  🔔 NEW APPROVAL REQUEST");
        displayPendingApproval(0, approval);
    }

    private void displayPendingApproval(int index, PendingApproval approval) {
        String timestamp = approval.submittedAt != null
            ? LocalDateTime.ofInstant(approval.submittedAt, ZoneId.systemDefault()).format(TIME_FORMAT)
            : "N/A";

        String shortId = approval.id.length() > 8 ? approval.id.substring(0, 8) : approval.id;

        CliOutput.println("");
        if (index > 0) {
            CliOutput.println("  [" + index + "] ID: " + shortId + " (" + timestamp + ")");
        } else {
            CliOutput.println("  ID: " + shortId + " (" + timestamp + ")");
        }
        CliOutput.println("     Step:       " + truncate(approval.stepText, 50));
        CliOutput.println("     Original:   " + truncate(approval.originalLocator, 50));
        CliOutput.println("     Proposed:   " + truncate(approval.proposedLocator, 50));
        CliOutput.println("     Confidence: " + String.format("%.0f%%", approval.confidence * 100));
        CliOutput.println("     Reasoning:  " + truncate(approval.reasoning, 50));
        CliOutput.println("  ───────────────────────────────────────────────────────────────");
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Data class for pending approvals.
     */
    public static class PendingApproval {
        public String id;
        public String featureName;
        public String scenarioName;
        public String stepText;
        public String originalLocator;
        public String proposedLocator;
        public double confidence;
        public String reasoning;
        public String pageUrl;
        public Instant submittedAt;
        public Instant decidedAt;
        public String status;  // PENDING, APPROVED, REJECTED
        public String rejectionReason;
    }
}
