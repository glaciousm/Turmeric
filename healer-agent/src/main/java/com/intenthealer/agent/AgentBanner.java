package com.intenthealer.agent;

import com.intenthealer.core.config.HealerConfig;
import com.intenthealer.core.config.LlmConfig;

/**
 * Displays a startup banner when the Intent Healer agent is loaded.
 *
 * <p>The banner shows key configuration information to help users
 * verify that the agent is active and properly configured.</p>
 */
public class AgentBanner {

    private static final String BANNER_TOP    = "+===============================================================+";
    private static final String BANNER_SEP    = "+---------------------------------------------------------------+";
    private static final String BANNER_BOTTOM = "+===============================================================+";

    /**
     * Print the startup banner to System.out.
     */
    public static void print() {
        HealerConfig config = AutoConfigurator.getConfig();

        String mode = "AUTO_SAFE";
        String provider = "mock";
        String model = "heuristic";
        String healing = "ENABLED";

        if (config != null) {
            if (config.getMode() != null) {
                mode = config.getMode().name();
            }
            LlmConfig llm = config.getLlm();
            if (llm != null) {
                if (llm.getProvider() != null) {
                    provider = llm.getProvider();
                }
                if (llm.getModel() != null) {
                    model = llm.getModel();
                }
            }
        }

        System.out.println();
        System.out.println(BANNER_TOP);
        System.out.println("|           INTENT HEALER AGENT - ACTIVE                        |");
        System.out.println(BANNER_SEP);
        System.out.printf("|  Mode:       %-48s |%n", mode);
        System.out.printf("|  Provider:   %-48s |%n", provider);
        System.out.printf("|  Model:      %-48s |%n", model);
        System.out.printf("|  Healing:    %-48s |%n", healing);
        System.out.println(BANNER_BOTTOM);
        System.out.println();
        System.out.println("  Self-healing is active for all WebDriver instances.");
        System.out.println("  Broken locators will be automatically fixed at runtime.");
        System.out.println();
    }

    /**
     * Print a minimal banner (one line).
     */
    public static void printMinimal() {
        HealerConfig config = AutoConfigurator.getConfig();
        String provider = config != null && config.getLlm() != null
                ? config.getLlm().getProvider()
                : "mock";
        System.out.println("[Intent Healer] Agent active - provider: " + provider);
    }
}
