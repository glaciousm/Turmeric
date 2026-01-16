package io.github.glaciousm.core.context;

/**
 * Thread-local holder for test context information.
 *
 * <p>This allows test frameworks (like Cucumber) to set context information
 * that the healing code can use when recording heals.</p>
 *
 * <p>Usage in Cucumber hooks:</p>
 * <pre>
 * @Before
 * public void beforeScenario(Scenario scenario) {
 *     TestContext.setFeatureName(scenario.getUri().toString());
 *     TestContext.setScenarioName(scenario.getName());
 * }
 *
 * @After
 * public void afterScenario() {
 *     TestContext.clear();
 * }
 * </pre>
 */
public final class TestContext {

    private static final ThreadLocal<String> featureName = new ThreadLocal<>();
    private static final ThreadLocal<String> scenarioName = new ThreadLocal<>();

    private TestContext() {
        // Utility class
    }

    /**
     * Set the current feature name for this thread.
     */
    public static void setFeatureName(String name) {
        featureName.set(name);
    }

    /**
     * Get the current feature name for this thread.
     */
    public static String getFeatureName() {
        return featureName.get();
    }

    /**
     * Set the current scenario name for this thread.
     */
    public static void setScenarioName(String name) {
        scenarioName.set(name);
    }

    /**
     * Get the current scenario name for this thread.
     */
    public static String getScenarioName() {
        return scenarioName.get();
    }

    /**
     * Clear all context for this thread.
     * Should be called after each test/scenario.
     */
    public static void clear() {
        featureName.remove();
        scenarioName.remove();
    }

    /**
     * Check if any context is set.
     */
    public static boolean hasContext() {
        return featureName.get() != null || scenarioName.get() != null;
    }
}
