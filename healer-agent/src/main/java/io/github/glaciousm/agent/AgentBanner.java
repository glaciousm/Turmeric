package io.github.glaciousm.agent;

import io.github.glaciousm.core.config.HealerConfig;
import io.github.glaciousm.core.config.LlmConfig;

/**
 * Displays a startup banner when the Intent Healer agent is loaded.
 *
 * <p>The banner shows key configuration information to help users
 * verify that the agent is active and properly configured.</p>
 */
public class AgentBanner {

    // ANSI color codes
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";

    private static final String BANNER_TOP    = "+===============================================================+";
    private static final String BANNER_SEP    = "+---------------------------------------------------------------+";
    private static final String BANNER_BOTTOM = "+===============================================================+";

    /**
     * Print the startup banner with ANSI colors.
     *
     * @param providerAvailable whether the configured provider is available (has API key, etc.)
     */
    public static void print(boolean providerAvailable) {
        HealerConfig config = AutoConfigurator.getConfig();

        String mode = "AUTO_SAFE";
        String provider = "mock";
        String model = "heuristic";
        String healing = providerAvailable ? "ENABLED" : "DISABLED (missing API key)";
        String healingColor = providerAvailable ? GREEN : YELLOW;

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

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(CYAN).append(BOLD).append(BANNER_TOP).append(RESET).append("\n");
        if (providerAvailable) {
            sb.append(CYAN).append(BOLD).append("|           INTENT HEALER AGENT - ACTIVE                        |").append(RESET).append("\n");
        } else {
            sb.append(YELLOW).append(BOLD).append("|           INTENT HEALER AGENT - INACTIVE                      |").append(RESET).append("\n");
        }
        sb.append(CYAN).append(BANNER_SEP).append(RESET).append("\n");
        sb.append(CYAN).append(String.format("|  Mode:       %-48s |", mode)).append(RESET).append("\n");
        sb.append(CYAN).append(String.format("|  Provider:   %-48s |", provider)).append(RESET).append("\n");
        sb.append(CYAN).append(String.format("|  Model:      %-48s |", model)).append(RESET).append("\n");
        sb.append(healingColor).append(String.format("|  Healing:    %-48s |", healing)).append(RESET).append("\n");
        sb.append(CYAN).append(BOLD).append(BANNER_BOTTOM).append(RESET).append("\n");
        sb.append("\n");
        if (providerAvailable) {
            sb.append(GREEN).append("  Self-healing is active for all WebDriver instances.").append(RESET).append("\n");
            sb.append(GREEN).append("  Broken locators will be automatically fixed at runtime.").append(RESET).append("\n");
        } else {
            sb.append(YELLOW).append("  WARNING: Healing is DISABLED because the API key is not configured.").append(RESET).append("\n");
            sb.append(YELLOW).append("  Set the required environment variable (e.g., AZURE_OPENAI_API_KEY)").append(RESET).append("\n");
            sb.append(YELLOW).append("  or switch to 'mock' provider in healer-config.yml").append(RESET).append("\n");
        }
        sb.append("\n");

        System.out.print(sb.toString());
        System.out.flush();
    }

    /**
     * Print the startup banner (default - checks provider availability).
     */
    public static void print() {
        print(AutoConfigurator.isEnabled());
    }

    /**
     * Print a minimal banner (one line).
     */
    public static void printMinimal() {
        HealerConfig config = AutoConfigurator.getConfig();
        String provider = config != null && config.getLlm() != null
                ? config.getLlm().getProvider()
                : "mock";
        System.out.println(CYAN + "[Intent Healer] Agent active - provider: " + provider + RESET);
        System.out.flush();
    }
}
