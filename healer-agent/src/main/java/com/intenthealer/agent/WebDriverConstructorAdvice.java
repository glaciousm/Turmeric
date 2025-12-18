package com.intenthealer.agent;

import net.bytebuddy.asm.Advice;
import org.openqa.selenium.WebDriver;

/**
 * ByteBuddy advice for intercepting WebDriver constructor calls.
 *
 * <p>This advice runs after a WebDriver constructor completes, registering
 * the new driver instance with the AutoConfigurator for healing support.</p>
 */
public class WebDriverConstructorAdvice {

    /**
     * Called after WebDriver constructor completes.
     *
     * @param driver the newly created WebDriver instance
     */
    @Advice.OnMethodExit
    public static void onConstructorExit(@Advice.This WebDriver driver) {
        try {
            AutoConfigurator.registerDriver(driver);
        } catch (Throwable t) {
            // Don't let registration failures break driver creation
            System.err.println("[Intent Healer] Failed to register driver: " + t.getMessage());
        }
    }
}
