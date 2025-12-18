package com.intenthealer.agent;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * ByteBuddy advice for intercepting WebDriver.findElement() calls.
 *
 * <p>This advice wraps the findElement method to add self-healing capability.
 * When a NoSuchElementException or StaleElementReferenceException is thrown,
 * the healing engine attempts to find an alternative locator.</p>
 *
 * <p>The healing process:</p>
 * <ol>
 *   <li>Original findElement is called</li>
 *   <li>If exception is thrown, healing is triggered</li>
 *   <li>Healing engine captures DOM snapshot</li>
 *   <li>LLM/heuristic finds alternative element</li>
 *   <li>Healed element is returned to caller</li>
 * </ol>
 */
public class WebDriverInterceptor {

    /**
     * Called when findElement throws an exception.
     * Attempts to heal the locator and find the element.
     *
     * @param driver the WebDriver instance
     * @param by the locator that failed
     * @param thrown the exception that was thrown
     * @return the healed element, or null if healing failed
     */
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onFindElementExit(
            @Advice.This WebDriver driver,
            @Advice.Argument(0) By by,
            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) WebElement returned,
            @Advice.Thrown(readOnly = false, typing = Assigner.Typing.DYNAMIC) Throwable thrown) {

        // Only intercept NoSuchElementException and StaleElementReferenceException
        if (thrown == null) {
            return;
        }

        if (!(thrown instanceof NoSuchElementException) &&
            !(thrown instanceof StaleElementReferenceException)) {
            return;
        }

        // Check if healing is enabled
        if (!AutoConfigurator.isEnabled()) {
            return;
        }

        try {
            // Attempt to heal
            WebElement healedElement = AutoConfigurator.heal(driver, by, thrown);

            if (healedElement != null) {
                // Suppress the exception and return the healed element
                thrown = null;
                returned = healedElement;
            }
        } catch (Throwable healError) {
            // Healing failed, let the original exception propagate
            System.err.println("[Intent Healer] Healing failed: " + healError.getMessage());
        }
    }
}
