package com.intenthealer.selenium.integration;

import com.intenthealer.core.config.HealerConfig;
import com.intenthealer.core.engine.HealingEngine;
import com.intenthealer.core.model.*;
import com.intenthealer.selenium.driver.HealingWebDriver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for HealingWebDriver.
 * Tests the complete healing flow with mocked WebDriver and LLM.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HealingWebDriver Integration")
class HealingWebDriverIntegrationTest {

    @Mock
    private WebDriver mockDelegate;

    @Mock
    private JavascriptExecutor mockJsExecutor;

    @Mock
    private WebElement mockElement;

    private HealerConfig config;
    private HealingEngine healingEngine;
    private HealingWebDriver healingWebDriver;

    @BeforeEach
    void setUp() {
        config = new HealerConfig();
        config.setEnabled(true);
        config.applyDefaults();
        healingEngine = new HealingEngine(config);

        // Create healing web driver with mock delegate
        healingWebDriver = new HealingWebDriver(mockDelegate, healingEngine, config);
    }

    @Nested
    @DisplayName("Basic WebDriver Operations")
    class BasicOperationsTests {

        @Test
        @DisplayName("should delegate get() call")
        void delegateGet() {
            healingWebDriver.get("https://example.com");

            verify(mockDelegate).get("https://example.com");
        }

        @Test
        @DisplayName("should delegate getCurrentUrl()")
        void delegateGetCurrentUrl() {
            when(mockDelegate.getCurrentUrl()).thenReturn("https://example.com/page");

            String url = healingWebDriver.getCurrentUrl();

            assertThat(url).isEqualTo("https://example.com/page");
            verify(mockDelegate).getCurrentUrl();
        }

        @Test
        @DisplayName("should delegate getTitle()")
        void delegateGetTitle() {
            when(mockDelegate.getTitle()).thenReturn("Example Page");

            String title = healingWebDriver.getTitle();

            assertThat(title).isEqualTo("Example Page");
            verify(mockDelegate).getTitle();
        }

        @Test
        @DisplayName("should delegate close()")
        void delegateClose() {
            healingWebDriver.close();

            verify(mockDelegate).close();
        }

        @Test
        @DisplayName("should delegate quit()")
        void delegateQuit() {
            healingWebDriver.quit();

            verify(mockDelegate).quit();
        }
    }

    @Nested
    @DisplayName("Element Finding - No Healing Needed")
    class ElementFindingNoHealingTests {

        @Test
        @DisplayName("should find element without healing when element exists")
        void findElementNoHealing() {
            By locator = By.id("test-element");
            when(mockDelegate.findElement(locator)).thenReturn(mockElement);

            WebElement result = healingWebDriver.findElement(locator);

            assertThat(result).isNotNull();
            verify(mockDelegate).findElement(locator);
        }

        @Test
        @DisplayName("should find elements without healing when elements exist")
        void findElementsNoHealing() {
            By locator = By.cssSelector(".test-class");
            when(mockDelegate.findElements(locator)).thenReturn(List.of(mockElement));

            List<WebElement> results = healingWebDriver.findElements(locator);

            assertThat(results).hasSize(1);
            verify(mockDelegate).findElements(locator);
        }
    }

    @Nested
    @DisplayName("Intent Context Management")
    class IntentContextTests {

        @Test
        @DisplayName("should set and clear intent context")
        void setAndClearIntent() {
            IntentContract intent = IntentContract.builder()
                .action("click")
                .description("Click login button")
                .policy(HealPolicy.AUTO_SAFE)
                .build();

            healingWebDriver.setCurrentIntent(intent, "I click the login button");
            healingWebDriver.clearCurrentIntent();

            // No exception means success
        }

        @Test
        @DisplayName("should allow multiple intent context changes")
        void multipleIntentChanges() {
            IntentContract intent1 = IntentContract.builder()
                .action("click")
                .description("Click button 1")
                .build();
            IntentContract intent2 = IntentContract.builder()
                .action("type")
                .description("Type in input")
                .build();

            healingWebDriver.setCurrentIntent(intent1, "Step 1");
            healingWebDriver.setCurrentIntent(intent2, "Step 2");
            healingWebDriver.clearCurrentIntent();

            // No exception means success
        }
    }

    @Nested
    @DisplayName("Healing Flow Tests")
    class HealingFlowTests {

        @Test
        @DisplayName("should propagate NoSuchElementException when healing not configured")
        void propagateExceptionWhenHealingNotConfigured() {
            By locator = By.id("missing-element");
            when(mockDelegate.findElement(locator)).thenThrow(new NoSuchElementException("Not found"));

            // Without snapshot capture configured, healing will fail
            assertThatThrownBy(() -> healingWebDriver.findElement(locator))
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("should propagate StaleElementReferenceException when healing not configured")
        void propagateStaleException() {
            By locator = By.cssSelector(".stale-element");
            when(mockDelegate.findElement(locator))
                .thenThrow(new StaleElementReferenceException("Element is stale"));

            assertThatThrownBy(() -> healingWebDriver.findElement(locator))
                .isInstanceOf(StaleElementReferenceException.class);
        }
    }

    @Nested
    @DisplayName("Healing Disabled Tests")
    class HealingDisabledTests {

        @Test
        @DisplayName("should propagate exception when healing is globally disabled")
        void propagateWhenHealingDisabled() {
            config.setEnabled(false);
            healingEngine = new HealingEngine(config);
            healingWebDriver = new HealingWebDriver(mockDelegate, healingEngine, config);

            By locator = By.id("test");
            when(mockDelegate.findElement(locator)).thenThrow(new NoSuchElementException("Not found"));

            assertThatThrownBy(() -> healingWebDriver.findElement(locator))
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("should propagate exception when intent policy is OFF")
        void propagateWhenPolicyOff() {
            IntentContract intent = IntentContract.builder()
                .action("click")
                .description("Click button")
                .policy(HealPolicy.OFF)
                .build();

            healingWebDriver.setCurrentIntent(intent, "Click button");

            By locator = By.id("test");
            when(mockDelegate.findElement(locator)).thenThrow(new NoSuchElementException("Not found"));

            assertThatThrownBy(() -> healingWebDriver.findElement(locator))
                .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    @DisplayName("Navigation Tests")
    class NavigationTests {

        @Mock
        private WebDriver.Navigation mockNavigation;

        @Test
        @DisplayName("should delegate navigate()")
        void delegateNavigate() {
            when(mockDelegate.navigate()).thenReturn(mockNavigation);

            WebDriver.Navigation nav = healingWebDriver.navigate();

            assertThat(nav).isEqualTo(mockNavigation);
            verify(mockDelegate).navigate();
        }
    }

    @Nested
    @DisplayName("Window Management Tests")
    class WindowManagementTests {

        @Mock
        private WebDriver.Options mockOptions;

        @Mock
        private WebDriver.TargetLocator mockTargetLocator;

        @Test
        @DisplayName("should delegate manage()")
        void delegateManage() {
            when(mockDelegate.manage()).thenReturn(mockOptions);

            WebDriver.Options options = healingWebDriver.manage();

            assertThat(options).isEqualTo(mockOptions);
            verify(mockDelegate).manage();
        }

        @Test
        @DisplayName("should delegate switchTo()")
        void delegateSwitchTo() {
            when(mockDelegate.switchTo()).thenReturn(mockTargetLocator);

            WebDriver.TargetLocator locator = healingWebDriver.switchTo();

            assertThat(locator).isEqualTo(mockTargetLocator);
            verify(mockDelegate).switchTo();
        }

        @Test
        @DisplayName("should delegate getWindowHandle()")
        void delegateGetWindowHandle() {
            when(mockDelegate.getWindowHandle()).thenReturn("window-123");

            String handle = healingWebDriver.getWindowHandle();

            assertThat(handle).isEqualTo("window-123");
            verify(mockDelegate).getWindowHandle();
        }

        @Test
        @DisplayName("should delegate getWindowHandles()")
        void delegateGetWindowHandles() {
            when(mockDelegate.getWindowHandles()).thenReturn(java.util.Set.of("win1", "win2"));

            java.util.Set<String> handles = healingWebDriver.getWindowHandles();

            assertThat(handles).containsExactlyInAnyOrder("win1", "win2");
            verify(mockDelegate).getWindowHandles();
        }
    }

    @Nested
    @DisplayName("Page Source Tests")
    class PageSourceTests {

        @Test
        @DisplayName("should delegate getPageSource()")
        void delegateGetPageSource() {
            String html = "<html><body>Test</body></html>";
            when(mockDelegate.getPageSource()).thenReturn(html);

            String source = healingWebDriver.getPageSource();

            assertThat(source).isEqualTo(html);
            verify(mockDelegate).getPageSource();
        }
    }

    @Nested
    @DisplayName("JavaScript Executor Tests")
    class JavaScriptExecutorTests {

        private HealingWebDriver jsHealingDriver;

        @BeforeEach
        void setUpJsDriver() {
            // Create a mock that implements both WebDriver and JavascriptExecutor
            WebDriver jsDelegate = mock(WebDriver.class, withSettings().extraInterfaces(JavascriptExecutor.class));
            jsHealingDriver = new HealingWebDriver(jsDelegate, healingEngine, config);
        }

        @Test
        @DisplayName("should delegate executeScript()")
        void delegateExecuteScript() {
            WebDriver jsDelegate = mock(WebDriver.class, withSettings().extraInterfaces(JavascriptExecutor.class));
            HealingWebDriver driver = new HealingWebDriver(jsDelegate, healingEngine, config);

            when(((JavascriptExecutor) jsDelegate).executeScript("return document.title"))
                .thenReturn("Test Title");

            Object result = driver.executeScript("return document.title");

            assertThat(result).isEqualTo("Test Title");
        }

        @Test
        @DisplayName("should throw when delegate does not support JavascriptExecutor")
        void throwWhenNoJsSupport() {
            // mockDelegate doesn't implement JavascriptExecutor
            assertThatThrownBy(() -> healingWebDriver.executeScript("return 1"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("JavascriptExecutor");
        }
    }

    @Nested
    @DisplayName("Concurrency Safety Tests")
    class ConcurrencySafetyTests {

        @Test
        @DisplayName("should handle concurrent intent changes safely")
        void concurrentIntentChanges() throws InterruptedException {
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    IntentContract intent = IntentContract.builder()
                        .action("action" + index)
                        .description("Description " + index)
                        .build();
                    healingWebDriver.setCurrentIntent(intent, "Step " + index);
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    healingWebDriver.clearCurrentIntent();
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // No exception means thread-safe operations
        }
    }
}
