package io.github.glaciousm.core.engine.approval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.concurrent.*;

/**
 * Console-based approval callback for interactive heal approval.
 * Useful for local development and debugging.
 */
public class ConsoleApprovalCallback implements ApprovalCallback {

    private static final Logger logger = LoggerFactory.getLogger(ConsoleApprovalCallback.class);

    private final Duration timeout;
    private final BufferedReader reader;
    private final ExecutorService executor;

    public ConsoleApprovalCallback() {
        this(Duration.ofMinutes(5));
    }

    public ConsoleApprovalCallback(Duration timeout) {
        this.timeout = timeout;
        this.reader = new BufferedReader(new InputStreamReader(System.in));
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "console-approval");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public ApprovalDecision requestApproval(HealProposal proposal) {
        printProposal(proposal);

        Future<ApprovalDecision> future = executor.submit(this::readDecision);

        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            System.out.println("\n⏰ Approval timed out. Skipping this heal.");
            return ApprovalDecision.timeout();
        } catch (InterruptedException | ExecutionException e) {
            logger.warn("Error reading approval decision", e);
            return ApprovalDecision.skip();
        }
    }

    private void printProposal(HealProposal proposal) {
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("                    🔧 HEAL PROPOSAL                           ");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println();
        System.out.printf("  Feature:    %s%n", proposal.getFeatureName());
        System.out.printf("  Scenario:   %s%n", proposal.getScenarioName());
        System.out.printf("  Step:       %s%n", proposal.getStepText());
        System.out.println();
        System.out.println("  Original Locator:");
        System.out.printf("    %s: %s%n",
                proposal.getOriginalLocator().getStrategy(),
                proposal.getOriginalLocator().getValue());
        System.out.println();
        System.out.println("  Proposed Heal:");
        System.out.printf("    %s: %s%n",
                proposal.getProposedLocator().getStrategy(),
                proposal.getProposedLocator().getValue());
        System.out.println();
        System.out.printf("  Confidence: %.1f%%%n", proposal.getConfidence() * 100);
        System.out.println();
        System.out.println("  LLM Reasoning:");
        System.out.printf("    %s%n", proposal.getReasoning());
        System.out.println();
        System.out.println("───────────────────────────────────────────────────────────────");
        System.out.println("  Options:");
        System.out.println("    [Y] Yes - Approve this heal");
        System.out.println("    [R] Remember - Approve and remember for future");
        System.out.println("    [N] No - Reject this heal");
        System.out.println("    [B] Blacklist - Reject and block future attempts");
        System.out.println("    [S] Skip - Skip without decision");
        System.out.println("───────────────────────────────────────────────────────────────");
        System.out.print("  Your choice: ");
        System.out.flush();
    }

    private ApprovalDecision readDecision() {
        try {
            String input = reader.readLine();
            if (input == null || input.trim().isEmpty()) {
                return ApprovalDecision.skip();
            }

            char choice = input.trim().toUpperCase().charAt(0);
            return switch (choice) {
                case 'Y' -> {
                    System.out.println("  ✅ Heal APPROVED");
                    yield ApprovalDecision.approve();
                }
                case 'R' -> {
                    System.out.println("  ✅ Heal APPROVED and will be remembered");
                    yield ApprovalDecision.approveAndRemember();
                }
                case 'N' -> {
                    System.out.print("  Reason (optional): ");
                    String reason = reader.readLine();
                    System.out.println("  ❌ Heal REJECTED");
                    yield ApprovalDecision.reject(reason != null && !reason.isEmpty() ? reason : "Rejected by reviewer");
                }
                case 'B' -> {
                    System.out.print("  Reason: ");
                    String reason = reader.readLine();
                    System.out.println("  🚫 Heal REJECTED and BLACKLISTED");
                    yield ApprovalDecision.rejectAndBlacklist(reason != null && !reason.isEmpty() ? reason : "Blacklisted by reviewer");
                }
                default -> {
                    System.out.println("  ⏭️ Heal SKIPPED");
                    yield ApprovalDecision.skip();
                }
            };
        } catch (IOException e) {
            logger.warn("Error reading console input", e);
            return ApprovalDecision.skip();
        }
    }

    @Override
    public void notifyAutoApplied(HealProposal proposal) {
        System.out.println();
        System.out.println("ℹ️  Auto-applied heal:");
        System.out.printf("   %s: %s -> %s: %s%n",
                proposal.getOriginalLocator().getStrategy(),
                proposal.getOriginalLocator().getValue(),
                proposal.getProposedLocator().getStrategy(),
                proposal.getProposedLocator().getValue());
        System.out.printf("   Confidence: %.1f%%%n", proposal.getConfidence() * 100);
    }

    @Override
    public void notifyRejected(HealProposal proposal, String reason) {
        System.out.println();
        System.out.println("⚠️  Heal rejected by guardrails:");
        System.out.printf("   %s%n", reason);
    }

    /**
     * Shutdown the executor.
     */
    public void shutdown() {
        executor.shutdown();
    }
}
