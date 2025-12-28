package io.github.glaciousm.agent;

import io.github.glaciousm.core.config.HealerConfig;
import io.github.glaciousm.core.engine.HealingReportGenerator;
import io.github.glaciousm.core.engine.HealingSummary;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/**
 * Java Agent for zero-code Intent Healer integration.
 *
 * <p>This agent automatically intercepts all WebDriver instantiations and wraps them
 * with self-healing capability. Users only need to add the agent to their JVM arguments
 * and provide a healer-config.yml file - no code changes required.</p>
 *
 * <h2>Usage</h2>
 * <pre>
 * # Maven Surefire
 * mvn test -DargLine="-javaagent:healer-agent-1.0.0-SNAPSHOT.jar"
 *
 * # Direct JVM
 * java -javaagent:healer-agent-1.0.0-SNAPSHOT.jar -cp ... org.junit.platform.console.ConsoleLauncher
 * </pre>
 *
 * <h2>Behavior</h2>
 * <ul>
 *   <li>On JVM start: Loads healer-config.yml and prints startup banner</li>
 *   <li>On WebDriver creation: Registers driver for healing</li>
 *   <li>On findElement: Intercepts NoSuchElementException and triggers healing</li>
 * </ul>
 *
 * @see AutoConfigurator
 * @see WebDriverInterceptor
 */
public class HealerAgent {

    private static final Logger logger = LoggerFactory.getLogger(HealerAgent.class);

    // ANSI color codes for console output
    private static final String RESET = "\u001B[0m";
    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";

    private static volatile boolean initialized = false;

    /**
     * Agent entry point called before the application's main method.
     *
     * @param args agent arguments (optional)
     * @param inst instrumentation instance for bytecode manipulation
     */
    public static void premain(String args, Instrumentation inst) {
        // DIAGNOSTIC: Print immediately to verify agent is loading
        System.out.println(CYAN + "[Intent Healer] >>> premain() called <<<" + RESET);
        System.out.flush();

        initialize(args, inst, "premain");
    }

    /**
     * Agent entry point for dynamic attachment.
     *
     * @param args agent arguments (optional)
     * @param inst instrumentation instance for bytecode manipulation
     */
    public static void agentmain(String args, Instrumentation inst) {
        initialize(args, inst, "agentmain");
    }

    private static synchronized void initialize(String args, Instrumentation inst, String mode) {
        // DIAGNOSTIC: Print before any initialization
        System.out.println(CYAN + "[Intent Healer] Starting initialization via " + mode + RESET);
        System.out.flush();

        if (initialized) {
            System.out.println(YELLOW + "[Intent Healer] Already initialized, skipping" + RESET);
            logger.debug("Agent already initialized, skipping");
            return;
        }
        initialized = true;

        try {
            // Initialize configuration and healing engine
            System.out.println(CYAN + "[Intent Healer] Calling AutoConfigurator.initialize()..." + RESET);
            System.out.flush();
            AutoConfigurator.initialize();
            System.out.println(GREEN + "[Intent Healer] AutoConfigurator initialized successfully" + RESET);
            System.out.flush();

            if (!AutoConfigurator.isEnabled()) {
                System.out.println(YELLOW + "[Intent Healer] Agent loaded but DISABLED. Reason: " + AutoConfigurator.getDisabledReason() + RESET);
                return;
            }

            // Print startup banner
            System.out.println(CYAN + "[Intent Healer] Printing banner..." + RESET);
            AgentBanner.print();

            // Install ByteBuddy transformer
            System.out.println(CYAN + "[Intent Healer] Installing ByteBuddy transformer..." + RESET);
            installTransformer(inst);
            System.out.println(GREEN + "[Intent Healer] ByteBuddy transformer installed" + RESET);

            // Pre-load classes to avoid ClassNotFoundException in shutdown hook
            final HealingSummary healingSummary = HealingSummary.getInstance();
            final HealingReportGenerator reportGenerator;
            HealerConfig cfg = AutoConfigurator.getConfig();
            if (cfg != null && cfg.getReport() != null && cfg.getReport().isEnabled()) {
                reportGenerator = new HealingReportGenerator(cfg.getReport());
            } else {
                reportGenerator = null;
            }

            // Register shutdown hook to print healing summary and generate reports
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    // Print console summary (use pre-loaded instance)
                    healingSummary.printSummary();

                    // Generate HTML/JSON reports
                    if (reportGenerator != null) {
                        System.out.println(CYAN + "[Intent Healer] Generating reports..." + RESET);
                        reportGenerator.generateReports();
                        System.out.println(GREEN + "[Intent Healer] Reports generated" + RESET);
                    }
                } catch (Exception e) {
                    System.out.println(YELLOW + "[Intent Healer] Failed to generate healing reports: " + e.getMessage() + RESET);
                    e.printStackTrace(System.out);
                    logger.error("Failed to generate healing reports", e);
                }
            }, "intent-healer-summary"));

            logger.info("Intent Healer Agent initialized successfully via {}", mode);
            System.out.println(GREEN + "[Intent Healer] Agent initialized successfully via " + mode + RESET);

        } catch (Exception e) {
            System.out.println(YELLOW + "[Intent Healer] Agent initialization failed: " + e.getMessage() + RESET);
            e.printStackTrace(System.out);
            logger.error("Agent initialization failed", e);
        }
    }

    private static void installTransformer(Instrumentation inst) {
        new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(new AgentBuilder.Listener.Adapter() {
                    @Override
                    public void onTransformation(TypeDescription typeDescription,
                                                  ClassLoader classLoader,
                                                  JavaModule module,
                                                  boolean loaded,
                                                  DynamicType dynamicType) {
                        logger.debug("Transformed: {}", typeDescription.getName());
                    }

                    @Override
                    public void onError(String typeName,
                                        ClassLoader classLoader,
                                        JavaModule module,
                                        boolean loaded,
                                        Throwable throwable) {
                        logger.warn("Failed to transform {}: {}", typeName, throwable.getMessage());
                    }
                })
                // Match all WebDriver implementations (subclasses of RemoteWebDriver)
                .type(ElementMatchers.isSubTypeOf(org.openqa.selenium.remote.RemoteWebDriver.class)
                        .and(ElementMatchers.not(ElementMatchers.nameContains("Healing"))))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder,
                                                             TypeDescription typeDescription,
                                                             ClassLoader classLoader,
                                                             JavaModule module,
                                                             ProtectionDomain protectionDomain) {
                        return builder
                                // Intercept constructors to register drivers
                                .visit(Advice.to(WebDriverConstructorAdvice.class)
                                        .on(ElementMatchers.isConstructor()))
                                // Intercept findElement to add healing
                                .visit(Advice.to(WebDriverInterceptor.class)
                                        .on(ElementMatchers.named("findElement")
                                                .and(ElementMatchers.takesArgument(0, org.openqa.selenium.By.class))));
                    }
                })
                .installOn(inst);

        logger.debug("ByteBuddy transformer installed for WebDriver interception");
    }

    /**
     * Check if the agent has been initialized.
     */
    public static boolean isInitialized() {
        return initialized;
    }
}
