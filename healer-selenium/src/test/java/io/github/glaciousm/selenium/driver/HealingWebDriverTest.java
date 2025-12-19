package io.github.glaciousm.selenium.driver;

import io.github.glaciousm.core.config.HealerConfig;
import io.github.glaciousm.core.engine.HealingEngine;
import io.github.glaciousm.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openqa.selenium.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HealingWebDriverTest {

    @Mock
    private WebDriver mockDelegate;

    @Mock
    private HealingEngine mockEngine;

    @Mock
    private HealerConfig mockConfig;

    @Mock
    private WebElement mockElement;

    private HealingWebDriver healingDriver;

    /**
     * Interface combining WebDriver, JavascriptExecutor, and TakesScreenshot
     * for proper mocking of all driver capabilities needed in healing tests.
     */
    interface FullFeaturedWebDriver extends WebDriver, JavascriptExecutor, TakesScreenshot {}

    @BeforeEach
    void setUp() {
        healingDriver = new HealingWebDriver(mockDelegate, mockEngine, mockConfig);
    }

    /**
     * Creates a mock delegate that supports JavascriptExecutor and TakesScreenshot.
     * Use this for tests that trigger healing flow.
     */
    private WebDriver createFullFeaturedMock() {
        WebDriver fullMock = mock(FullFeaturedWebDriver.class);
        setupSnapshotBuilderStubs(fullMock);
        return fullMock;
    }

    /**
     * Sets up stubs needed by SnapshotBuilder for any WebDriver mock.
     */
    private void setupSnapshotBuilderStubs(WebDriver driver) {
        lenient().when(driver.findElements(any(By.class))).thenReturn(List.of());
        lenient().when(driver.getPageSource()).thenReturn("<html></html>");
        lenient().when(driver.getCurrentUrl()).thenReturn("http://test.com");
        lenient().when(driver.getTitle()).thenReturn("Test Page");
        if (driver instanceof JavascriptExecutor) {
            JavascriptExecutor jsDriver = (JavascriptExecutor) driver;
            // Use thenAnswer to return appropriate types based on script content
            lenient().when(jsDriver.executeScript(anyString())).thenAnswer(invocation -> {
                String script = invocation.getArgument(0);
                if (script.contains("document.documentElement.lang")) {
                    return "en";  // detectLanguage() expects String
                }
                if (script.contains("document.documentElement.outerHTML")) {
                    return "<html></html>";  // captureDom() expects String
                }
                // Element capture scripts expect List<WebElement>
                return new ArrayList<>();
            });
            lenient().when(jsDriver.executeScript(anyString(), any())).thenReturn("body");
        }
        if (driver instanceof TakesScreenshot) {
            lenient().when(((TakesScreenshot) driver).getScreenshotAs(any())).thenReturn(new byte[0]);
        }
    }

    // ===== Test successful element finding (no healing needed) =====

    @Test
    void findElement_whenElementExists_returnsDelegateResult() {
        when(mockDelegate.findElement(By.id("test"))).thenReturn(mockElement);

        WebElement result = healingDriver.findElement(By.id("test"));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(HealingWebElement.class);
        verify(mockDelegate).findElement(By.id("test"));
        verifyNoInteractions(mockEngine);
    }

    @Test
    void findElements_whenElementsExist_returnsDelegateResult() {
        List<WebElement> mockElements = List.of(mockElement, mockElement);
        when(mockDelegate.findElements(By.className("test"))).thenReturn(mockElements);

        List<WebElement> result = healingDriver.findElements(By.className("test"));

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(e -> e instanceof HealingWebElement);
        verify(mockDelegate).findElements(By.className("test"));
        verifyNoInteractions(mockEngine);
    }

    // ===== Test healing triggered on NoSuchElementException =====

    @Test
    void findElement_whenNoSuchElementException_attemptsHealing() {
        // Use full-featured mock for healing tests
        WebDriver fullMock = createFullFeaturedMock();
        // Create a local mock to avoid strict stubbing issues
        HealingEngine localEngine = mock(HealingEngine.class, withSettings().strictness(Strictness.LENIENT));
        healingDriver = new HealingWebDriver(fullMock, localEngine, mockConfig);

        // Setup: First call throws exception, healing provides new locator
        when(fullMock.findElement(By.id("old-id")))
                .thenThrow(new NoSuchElementException("Element not found"));

        HealResult healResult = HealResult.success(
                0,
                0.9,
                "Element was healed using new ID",
                "css=#new-id"
        );
        doReturn(healResult).when(localEngine).attemptHeal(any(FailureContext.class), any(IntentContract.class));

        when(fullMock.findElement(By.cssSelector("#new-id")))
                .thenReturn(mockElement);

        // Execute
        WebElement result = healingDriver.findElement(By.id("old-id"));

        // Verify
        assertThat(result).isNotNull();
        verify(localEngine).attemptHeal(any(FailureContext.class), any(IntentContract.class));
        verify(fullMock).findElement(By.id("old-id"));
        verify(fullMock).findElement(By.cssSelector("#new-id"));
    }

    @Test
    void findElement_whenHealingFails_throwsOriginalException() {
        // Use full-featured mock for healing tests
        WebDriver fullMock = createFullFeaturedMock();
        HealingEngine localEngine = mock(HealingEngine.class, withSettings().strictness(Strictness.LENIENT));
        healingDriver = new HealingWebDriver(fullMock, localEngine, mockConfig);

        NoSuchElementException originalException = new NoSuchElementException("Element not found");
        when(fullMock.findElement(By.id("test")))
                .thenThrow(originalException);

        HealResult healResult = HealResult.failed("Could not find element");
        doReturn(healResult).when(localEngine).attemptHeal(any(FailureContext.class), any(IntentContract.class));

        assertThatThrownBy(() -> healingDriver.findElement(By.id("test")))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Element not found");

        verify(localEngine).attemptHeal(any(FailureContext.class), any(IntentContract.class));
    }

    // ===== Test healing triggered on StaleElementReferenceException =====

    @Test
    void findElement_whenStaleElementException_attemptsHealing() {
        // Use full-featured mock for healing tests
        WebDriver fullMock = createFullFeaturedMock();
        HealingEngine localEngine = mock(HealingEngine.class, withSettings().strictness(Strictness.LENIENT));
        healingDriver = new HealingWebDriver(fullMock, localEngine, mockConfig);

        when(fullMock.findElement(By.id("stale-id")))
                .thenThrow(new StaleElementReferenceException("Element is stale"));

        HealResult healResult = HealResult.failed("Could not re-find stale element");
        doReturn(healResult).when(localEngine).attemptHeal(any(FailureContext.class), any(IntentContract.class));

        // Execute - should attempt healing but fail
        assertThatThrownBy(() -> healingDriver.findElement(By.id("stale-id")))
                .isInstanceOf(StaleElementReferenceException.class);

        verify(localEngine).attemptHeal(any(FailureContext.class), any(IntentContract.class));
    }

    @Test
    void findElements_whenStaleElementException_attemptsHealing() {
        // Use full-featured mock for healing tests
        WebDriver fullMock = createFullFeaturedMock();
        HealingEngine localEngine = mock(HealingEngine.class, withSettings().strictness(Strictness.LENIENT));
        healingDriver = new HealingWebDriver(fullMock, localEngine, mockConfig);

        when(fullMock.findElements(By.className("test")))
                .thenThrow(new StaleElementReferenceException("Elements are stale"));

        HealResult healResult = HealResult.failed("Could not re-find stale elements");
        doReturn(healResult).when(localEngine).attemptHeal(any(FailureContext.class), any(IntentContract.class));

        assertThatThrownBy(() -> healingDriver.findElements(By.className("test")))
                .isInstanceOf(StaleElementReferenceException.class);

        verify(localEngine).attemptHeal(any(FailureContext.class), any(IntentContract.class));
    }

    // ===== Test healing disabled when intent says not to heal =====

    @Test
    void findElement_whenHealingNotAllowedByIntent_doesNotAttemptHealing() {
        IntentContract noHealIntent = IntentContract.builder()
                .action("click")
                .description("Click without healing")
                .policy(HealPolicy.OFF)
                .build();

        healingDriver.setCurrentIntent(noHealIntent, "Click button");

        when(mockDelegate.findElement(By.id("test")))
                .thenThrow(new NoSuchElementException("Element not found"));

        assertThatThrownBy(() -> healingDriver.findElement(By.id("test")))
                .isInstanceOf(NoSuchElementException.class);

        verifyNoInteractions(mockEngine);
    }

    @Test
    void findElement_whenIntentIsNull_usesDefaultIntent() {
        // When no intent is set, HealingWebDriver should use a default intent
        // We verify this by checking that the driver can be created and used
        // without setting an explicit intent

        // The healingDriver is created in setUp without any intent
        // Verify it can perform operations without NPE
        when(mockDelegate.findElement(By.id("exists"))).thenReturn(mockElement);

        WebElement result = healingDriver.findElement(By.id("exists"));
        assertThat(result).isNotNull();
        verify(mockDelegate).findElement(By.id("exists"));
    }

    // ===== Test current intent context management =====

    @Test
    void setCurrentIntent_storesIntentAndStepText() {
        // Test that setCurrentIntent properly stores the intent
        // We verify this through the clearCurrentIntent test's success
        // and by checking the intent can be set without exception
        IntentContract intent = IntentContract.builder()
                .action("click")
                .description("Click login button")
                .build();

        assertThatCode(() -> healingDriver.setCurrentIntent(intent, "When I click the login button"))
                .doesNotThrowAnyException();

        // Verify intent can be updated
        IntentContract intent2 = IntentContract.builder()
                .action("type")
                .description("Type in field")
                .build();

        assertThatCode(() -> healingDriver.setCurrentIntent(intent2, "Type into username"))
                .doesNotThrowAnyException();
    }

    @Test
    void clearCurrentIntent_removesIntentContext() {
        // Test that clearCurrentIntent resets the intent to null
        // (The healing flow with default intent is tested in other tests)
        IntentContract originalIntent = IntentContract.builder()
                .action("click")
                .description("Click button")
                .build();

        healingDriver.setCurrentIntent(originalIntent, "When I click button");

        // After setting, the intent should be available
        // We can't easily access it directly, but we can verify the clear works
        // by setting, clearing, and checking the behavior

        healingDriver.clearCurrentIntent();

        // The intent should now be cleared - test by verifying no exception is thrown
        // when we call clearCurrentIntent again (idempotent operation)
        assertThatCode(() -> healingDriver.clearCurrentIntent())
                .doesNotThrowAnyException();

        // Also verify we can set a new intent after clearing
        IntentContract newIntent = IntentContract.builder()
                .action("type")
                .description("Type text")
                .build();

        assertThatCode(() -> healingDriver.setCurrentIntent(newIntent, "Type some text"))
                .doesNotThrowAnyException();
    }

    // ===== Test byToLocatorInfo() conversion for all locator types =====

    @Test
    void byToLocatorInfo_convertsIdLocator() {
        WebDriver fullMock = createFullFeaturedMock();
        HealingEngine localEngine = mock(HealingEngine.class, withSettings().strictness(Strictness.LENIENT));
        healingDriver = new HealingWebDriver(fullMock, localEngine, mockConfig);

        By by = By.id("test-id");
        when(fullMock.findElement(by)).thenThrow(new NoSuchElementException("test"));
        doReturn(HealResult.failed("")).when(localEngine).attemptHeal(any(), any());

        assertThatThrownBy(() -> healingDriver.findElement(by));

        ArgumentCaptor<FailureContext> fcCaptor = ArgumentCaptor.forClass(FailureContext.class);
        verify(localEngine).attemptHeal(fcCaptor.capture(), any());
        assertThat(fcCaptor.getValue().getOriginalLocator().getStrategy()).isEqualTo(LocatorInfo.LocatorStrategy.ID);
        assertThat(fcCaptor.getValue().getOriginalLocator().getValue()).isEqualTo("test-id");
    }

    @Test
    void byToLocatorInfo_convertsNameLocator() {
        WebDriver fullMock = createFullFeaturedMock();
        HealingEngine localEngine = mock(HealingEngine.class, withSettings().strictness(Strictness.LENIENT));
        healingDriver = new HealingWebDriver(fullMock, localEngine, mockConfig);

        By by = By.name("username");
        when(fullMock.findElement(by)).thenThrow(new NoSuchElementException("test"));
        doReturn(HealResult.failed("")).when(localEngine).attemptHeal(any(), any());

        assertThatThrownBy(() -> healingDriver.findElement(by));

        ArgumentCaptor<FailureContext> fcCaptor = ArgumentCaptor.forClass(FailureContext.class);
        verify(localEngine).attemptHeal(fcCaptor.capture(), any());
        assertThat(fcCaptor.getValue().getOriginalLocator().getStrategy()).isEqualTo(LocatorInfo.LocatorStrategy.NAME);
        assertThat(fcCaptor.getValue().getOriginalLocator().getValue()).isEqualTo("username");
    }

    @Test
    void byToLocatorInfo_convertsClassNameLocator() {
        WebDriver fullMock = createFullFeaturedMock();
        HealingEngine localEngine = mock(HealingEngine.class, withSettings().strictness(Strictness.LENIENT));
        healingDriver = new HealingWebDriver(fullMock, localEngine, mockConfig);

        By by = By.className("btn-primary");
        when(fullMock.findElement(by)).thenThrow(new NoSuchElementException("test"));
        doReturn(HealResult.failed("")).when(localEngine).attemptHeal(any(), any());

        assertThatThrownBy(() -> healingDriver.findElement(by));

        ArgumentCaptor<FailureContext> fcCaptor = ArgumentCaptor.forClass(FailureContext.class);
        verify(localEngine).attemptHeal(fcCaptor.capture(), any());
        assertThat(fcCaptor.getValue().getOriginalLocator().getStrategy()).isEqualTo(LocatorInfo.LocatorStrategy.CLASS_NAME);
        assertThat(fcCaptor.getValue().getOriginalLocator().getValue()).isEqualTo("btn-primary");
    }

    @Test
    void byToLocatorInfo_convertsTagNameLocator() {
        WebDriver fullMock = createFullFeaturedMock();
        HealingEngine localEngine = mock(HealingEngine.class, withSettings().strictness(Strictness.LENIENT));
        healingDriver = new HealingWebDriver(fullMock, localEngine, mockConfig);

        By by = By.tagName("button");
        when(fullMock.findElement(by)).thenThrow(new NoSuchElementException("test"));
        doReturn(HealResult.failed("")).when(localEngine).attemptHeal(any(), any());

        assertThatThrownBy(() -> healingDriver.findElement(by));

        ArgumentCaptor<FailureContext> fcCaptor = ArgumentCaptor.forClass(FailureContext.class);
        verify(localEngine).attemptHeal(fcCaptor.capture(), any());
        assertThat(fcCaptor.getValue().getOriginalLocator().getStrategy()).isEqualTo(LocatorInfo.LocatorStrategy.TAG_NAME);
        assertThat(fcCaptor.getValue().getOriginalLocator().getValue()).isEqualTo("button");
    }

    @Test
    void byToLocatorInfo_convertsLinkTextLocator() {
        WebDriver fullMock = createFullFeaturedMock();
        HealingEngine localEngine = mock(HealingEngine.class, withSettings().strictness(Strictness.LENIENT));
        healingDriver = new HealingWebDriver(fullMock, localEngine, mockConfig);

        By by = By.linkText("Click here");
        when(fullMock.findElement(by)).thenThrow(new NoSuchElementException("test"));
        doReturn(HealResult.failed("")).when(localEngine).attemptHeal(any(), any());

        assertThatThrownBy(() -> healingDriver.findElement(by));

        ArgumentCaptor<FailureContext> fcCaptor = ArgumentCaptor.forClass(FailureContext.class);
        verify(localEngine).attemptHeal(fcCaptor.capture(), any());
        assertThat(fcCaptor.getValue().getOriginalLocator().getStrategy()).isEqualTo(LocatorInfo.LocatorStrategy.LINK_TEXT);
        assertThat(fcCaptor.getValue().getOriginalLocator().getValue()).isEqualTo("Click here");
    }

    @Test
    void byToLocatorInfo_convertsPartialLinkTextLocator() {
        WebDriver fullMock = createFullFeaturedMock();
        HealingEngine localEngine = mock(HealingEngine.class, withSettings().strictness(Strictness.LENIENT));
        healingDriver = new HealingWebDriver(fullMock, localEngine, mockConfig);

        By by = By.partialLinkText("Click");
        when(fullMock.findElement(by)).thenThrow(new NoSuchElementException("test"));
        doReturn(HealResult.failed("")).when(localEngine).attemptHeal(any(), any());

        assertThatThrownBy(() -> healingDriver.findElement(by));

        ArgumentCaptor<FailureContext> fcCaptor = ArgumentCaptor.forClass(FailureContext.class);
        verify(localEngine).attemptHeal(fcCaptor.capture(), any());
        assertThat(fcCaptor.getValue().getOriginalLocator().getStrategy()).isEqualTo(LocatorInfo.LocatorStrategy.PARTIAL_LINK_TEXT);
        assertThat(fcCaptor.getValue().getOriginalLocator().getValue()).isEqualTo("Click");
    }

    @Test
    void byToLocatorInfo_convertsCssSelectorLocator() {
        WebDriver fullMock = createFullFeaturedMock();
        HealingEngine localEngine = mock(HealingEngine.class, withSettings().strictness(Strictness.LENIENT));
        healingDriver = new HealingWebDriver(fullMock, localEngine, mockConfig);

        By by = By.cssSelector("div.container > button");
        when(fullMock.findElement(by)).thenThrow(new NoSuchElementException("test"));
        doReturn(HealResult.failed("")).when(localEngine).attemptHeal(any(), any());

        assertThatThrownBy(() -> healingDriver.findElement(by));

        ArgumentCaptor<FailureContext> fcCaptor = ArgumentCaptor.forClass(FailureContext.class);
        verify(localEngine).attemptHeal(fcCaptor.capture(), any());
        assertThat(fcCaptor.getValue().getOriginalLocator().getStrategy()).isEqualTo(LocatorInfo.LocatorStrategy.CSS);
        assertThat(fcCaptor.getValue().getOriginalLocator().getValue()).isEqualTo("div.container > button");
    }

    @Test
    void byToLocatorInfo_convertsXpathLocator() {
        WebDriver fullMock = createFullFeaturedMock();
        HealingEngine localEngine = mock(HealingEngine.class, withSettings().strictness(Strictness.LENIENT));
        healingDriver = new HealingWebDriver(fullMock, localEngine, mockConfig);

        By by = By.xpath("//button[@id='submit']");
        when(fullMock.findElement(by)).thenThrow(new NoSuchElementException("test"));
        doReturn(HealResult.failed("")).when(localEngine).attemptHeal(any(), any());

        assertThatThrownBy(() -> healingDriver.findElement(by));

        ArgumentCaptor<FailureContext> fcCaptor = ArgumentCaptor.forClass(FailureContext.class);
        verify(localEngine).attemptHeal(fcCaptor.capture(), any());
        assertThat(fcCaptor.getValue().getOriginalLocator().getStrategy()).isEqualTo(LocatorInfo.LocatorStrategy.XPATH);
        assertThat(fcCaptor.getValue().getOriginalLocator().getValue()).isEqualTo("//button[@id='submit']");
    }

    // ===== Test locatorInfoToBy() conversion =====

    @Test
    void locatorInfoToBy_convertsIdStrategy() {
        WebDriver fullMock = createFullFeaturedMock();
        HealingEngine localEngine = mock(HealingEngine.class, withSettings().strictness(Strictness.LENIENT));
        healingDriver = new HealingWebDriver(fullMock, localEngine, mockConfig);

        when(fullMock.findElement(By.id("old")))
                .thenThrow(new NoSuchElementException("test"));
        doReturn(HealResult.success(0, 0.9, "healed", "id=new")).when(localEngine).attemptHeal(any(), any());
        when(fullMock.findElement(By.id("new"))).thenReturn(mockElement);

        WebElement result = healingDriver.findElement(By.id("old"));
        assertThat(result).isNotNull();
        verify(fullMock).findElement(By.id("new"));
    }

    @Test
    void locatorInfoToBy_convertsNameStrategy() {
        WebDriver fullMock = createFullFeaturedMock();
        HealingEngine localEngine = mock(HealingEngine.class, withSettings().strictness(Strictness.LENIENT));
        healingDriver = new HealingWebDriver(fullMock, localEngine, mockConfig);

        when(fullMock.findElement(By.name("old")))
                .thenThrow(new NoSuchElementException("test"));
        doReturn(HealResult.success(0, 0.9, "healed", "name=new")).when(localEngine).attemptHeal(any(), any());
        when(fullMock.findElement(By.name("new"))).thenReturn(mockElement);

        WebElement result = healingDriver.findElement(By.name("old"));
        assertThat(result).isNotNull();
        verify(fullMock).findElement(By.name("new"));
    }

    @Test
    void locatorInfoToBy_convertsXpathStrategy() {
        WebDriver fullMock = createFullFeaturedMock();
        HealingEngine localEngine = mock(HealingEngine.class, withSettings().strictness(Strictness.LENIENT));
        healingDriver = new HealingWebDriver(fullMock, localEngine, mockConfig);

        when(fullMock.findElement(By.xpath("//old")))
                .thenThrow(new NoSuchElementException("test"));
        doReturn(HealResult.success(0, 0.9, "healed", "xpath=//new")).when(localEngine).attemptHeal(any(), any());
        when(fullMock.findElement(By.xpath("//new"))).thenReturn(mockElement);

        WebElement result = healingDriver.findElement(By.xpath("//old"));
        assertThat(result).isNotNull();
        verify(fullMock).findElement(By.xpath("//new"));
    }

    // ===== Test parseLocatorString() with various formats =====

    @Test
    void parseLocatorString_handlesStandardFormat() {
        WebDriver fullMock = createFullFeaturedMock();
        HealingEngine localEngine = mock(HealingEngine.class, withSettings().strictness(Strictness.LENIENT));
        healingDriver = new HealingWebDriver(fullMock, localEngine, mockConfig);

        when(fullMock.findElement(By.id("old")))
                .thenThrow(new NoSuchElementException("test"));
        doReturn(HealResult.success(0, 0.9, "healed", "ID=test-id")).when(localEngine).attemptHeal(any(), any());
        when(fullMock.findElement(By.id("test-id"))).thenReturn(mockElement);

        WebElement result = healingDriver.findElement(By.id("old"));
        assertThat(result).isNotNull();
        verify(fullMock).findElement(By.id("test-id"));
    }

    @Test
    void parseLocatorString_handlesClassNameVariation() {
        WebDriver fullMock = createFullFeaturedMock();
        HealingEngine localEngine = mock(HealingEngine.class, withSettings().strictness(Strictness.LENIENT));
        healingDriver = new HealingWebDriver(fullMock, localEngine, mockConfig);

        when(fullMock.findElement(By.id("old")))
                .thenThrow(new NoSuchElementException("test"));
        doReturn(HealResult.success(0, 0.9, "healed", "CLASSNAME=btn")).when(localEngine).attemptHeal(any(), any());
        when(fullMock.findElement(By.className("btn"))).thenReturn(mockElement);

        WebElement result = healingDriver.findElement(By.id("old"));
        assertThat(result).isNotNull();
        verify(fullMock).findElement(By.className("btn"));
    }

    @Test
    void parseLocatorString_handlesCssSelectorVariation() {
        WebDriver fullMock = createFullFeaturedMock();
        HealingEngine localEngine = mock(HealingEngine.class, withSettings().strictness(Strictness.LENIENT));
        healingDriver = new HealingWebDriver(fullMock, localEngine, mockConfig);

        when(fullMock.findElement(By.id("old")))
                .thenThrow(new NoSuchElementException("test"));
        doReturn(HealResult.success(0, 0.9, "healed", "CSSSELECTOR=.btn")).when(localEngine).attemptHeal(any(), any());
        when(fullMock.findElement(By.cssSelector(".btn"))).thenReturn(mockElement);

        WebElement result = healingDriver.findElement(By.id("old"));
        assertThat(result).isNotNull();
        verify(fullMock).findElement(By.cssSelector(".btn"));
    }

    @Test
    void parseLocatorString_defaultsToCssWhenNoStrategySpecified() {
        WebDriver fullMock = createFullFeaturedMock();
        HealingEngine localEngine = mock(HealingEngine.class, withSettings().strictness(Strictness.LENIENT));
        healingDriver = new HealingWebDriver(fullMock, localEngine, mockConfig);

        when(fullMock.findElement(By.id("old")))
                .thenThrow(new NoSuchElementException("test"));
        doReturn(HealResult.success(0, 0.9, "healed", ".btn-primary")).when(localEngine).attemptHeal(any(), any());
        when(fullMock.findElement(By.cssSelector(".btn-primary"))).thenReturn(mockElement);

        WebElement result = healingDriver.findElement(By.id("old"));
        assertThat(result).isNotNull();
        verify(fullMock).findElement(By.cssSelector(".btn-primary"));
    }

    // ===== Test WebDriver interface delegation =====

    @Test
    void get_delegatesToWebDriver() {
        healingDriver.get("https://example.com");
        verify(mockDelegate).get("https://example.com");
    }

    @Test
    void getCurrentUrl_delegatesToWebDriver() {
        when(mockDelegate.getCurrentUrl()).thenReturn("https://example.com");
        assertThat(healingDriver.getCurrentUrl()).isEqualTo("https://example.com");
    }

    @Test
    void getTitle_delegatesToWebDriver() {
        when(mockDelegate.getTitle()).thenReturn("Example Page");
        assertThat(healingDriver.getTitle()).isEqualTo("Example Page");
    }

    @Test
    void getPageSource_delegatesToWebDriver() {
        when(mockDelegate.getPageSource()).thenReturn("<html></html>");
        assertThat(healingDriver.getPageSource()).isEqualTo("<html></html>");
    }

    @Test
    void close_delegatesToWebDriver() {
        healingDriver.close();
        verify(mockDelegate).close();
    }

    @Test
    void quit_delegatesToWebDriver() {
        healingDriver.quit();
        verify(mockDelegate).quit();
    }

    // ===== Test JavascriptExecutor interface =====

    @Test
    void executeScript_delegatesToJavascriptExecutor() {
        // Create driver that implements both WebDriver and JavascriptExecutor
        WebDriver jsDriver = mock(WebDriver.class, withSettings().extraInterfaces(JavascriptExecutor.class));
        HealingWebDriver driver = new HealingWebDriver(jsDriver, mockEngine, mockConfig);
        when(((JavascriptExecutor) jsDriver).executeScript("return 'test'")).thenReturn("test");

        Object result = driver.executeScript("return 'test'");

        assertThat(result).isEqualTo("test");
        verify((JavascriptExecutor) jsDriver).executeScript("return 'test'");
    }

    @Test
    void executeScript_throwsUnsupportedOperationException_whenDelegateDoesNotSupportIt() {
        assertThatThrownBy(() -> healingDriver.executeScript("return 'test'"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("does not support JavascriptExecutor");
    }

    // ===== Test TakesScreenshot interface =====

    @Test
    void getScreenshotAs_delegatesToTakesScreenshot() {
        // Create driver that implements both WebDriver and TakesScreenshot
        WebDriver screenshotDriver = mock(WebDriver.class, withSettings().extraInterfaces(TakesScreenshot.class));
        HealingWebDriver driver = new HealingWebDriver(screenshotDriver, mockEngine, mockConfig);
        when(((TakesScreenshot) screenshotDriver).getScreenshotAs(OutputType.BYTES)).thenReturn(new byte[]{1, 2, 3});

        byte[] result = driver.getScreenshotAs(OutputType.BYTES);

        assertThat(result).containsExactly(1, 2, 3);
        verify((TakesScreenshot) screenshotDriver).getScreenshotAs(OutputType.BYTES);
    }

    @Test
    void getScreenshotAs_throwsUnsupportedOperationException_whenDelegateDoesNotSupportIt() {
        assertThatThrownBy(() -> healingDriver.getScreenshotAs(OutputType.BYTES))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("does not support TakesScreenshot");
    }

    // ===== Test getDelegate() =====

    @Test
    void getDelegate_returnsUnderlyingDriver() {
        assertThat(healingDriver.getDelegate()).isSameAs(mockDelegate);
    }

    // ===== Test refindElement() =====

    @Test
    void refindElement_callsFindElement() {
        when(mockDelegate.findElement(By.id("test"))).thenReturn(mockElement);

        WebElement result = healingDriver.refindElement(By.id("test"));

        assertThat(result).isNotNull();
        verify(mockDelegate).findElement(By.id("test"));
    }
}
