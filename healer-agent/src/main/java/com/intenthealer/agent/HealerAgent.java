package com.intenthealer.agent;

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

    private static volatile boolean initialized = false;

    /**
     * Agent entry point called before the application's main method.
     *
     * @param args agent arguments (optional)
     * @param inst instrumentation instance for bytecode manipulation
     */
    public static void premain(String args, Instrumentation inst) {
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
        if (initialized) {
            logger.debug("Agent already initialized, skipping");
            return;
        }
        initialized = true;

        try {
            // Initialize configuration and healing engine
            AutoConfigurator.initialize();

            if (!AutoConfigurator.isEnabled()) {
                System.out.println("[Intent Healer] Agent loaded but DISABLED (healer.enabled=false)");
                return;
            }

            // Print startup banner
            AgentBanner.print();

            // Install ByteBuddy transformer
            installTransformer(inst);

            logger.info("Intent Healer Agent initialized successfully via {}", mode);

        } catch (Exception e) {
            System.err.println("[Intent Healer] Agent initialization failed: " + e.getMessage());
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
