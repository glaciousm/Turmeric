package com.intenthealer.selenium.driver;

import com.intenthealer.core.config.HealerConfig;
import com.intenthealer.core.engine.HealingEngine;
import com.intenthealer.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealingWebDriverTest {

    @Mock
    private WebDriver mockDelegate;

    @Mock
    private HealingEngine mockEngine;

    @Mock
    private HealerConfig mockConfig;

    @Mock
    private WebElement mockElement;

    @Mock
    private JavascriptExecutor mockJsExecutor;

    @Mock
    private TakesScreenshot mockScreenshot;

    private HealingWebDriver healingDriver;

    @BeforeEach
    void setUp() {
        healingDriver = new HealingWebDriver(mockDelegate, mockEngine, mockConfig);
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
        // Setup: First call throws exception, healing provides new locator
        when(mockDelegate.findElement(By.id("old-id")))
                .thenThrow(new NoSuchElementException("Element not found"));

        HealResult healResult = HealResult.success(
                0,
                0.9,
                "Element was healed using new ID",
                "css=#new-id"
        );
        when(mockEngine.attemptHeal(any(FailureContext.class), any(IntentContract.class)))
                .thenReturn(healResult);

        when(mockDelegate.findElement(By.cssSelector("#new-id")))
                .thenReturn(mockElement);

        // Execute
        WebElement result = healingDriver.findElement(By.id("old-id"));

        // Verify
        assertThat(result).isNotNull();
        verify(mockEngine).attemptHeal(any(FailureContext.class), any(IntentContract.class));
        verify(mockDelegate).findElement(By.id("old-id"));
        verify(mockDelegate).findElement(By.cssSelector("#new-id"));
    }

    @Test
    void findElement_whenHealingFails_throwsOriginalException() {
        NoSuchElementException originalException = new NoSuchElementException("Element not found");
        when(mockDelegate.findElement(By.id("test")))
                .thenThrow(originalException);

        HealResult healResult = HealResult.failed("Could not find element");
        when(mockEngine.attemptHeal(any(FailureContext.class), any(IntentContract.class)))
                .thenReturn(healResult);

        assertThatThrownBy(() -> healingDriver.findElement(By.id("test")))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Element not found");

        verify(mockEngine).attemptHeal(any(FailureContext.class), any(IntentContract.class));
    }

    // ===== Test healing triggered on StaleElementReferenceException =====

    @Test
    void findElement_whenStaleElementException_attemptsHealing() {
        when(mockDelegate.findElement(By.id("stale-id")))
                .thenThrow(new StaleElementReferenceException("Element is stale"));

        HealResult healResult = HealResult.success(
                0,
                0.9,
                "Re-found stale element",
                "id=stale-id"
        );
        when(mockEngine.attemptHeal(any(FailureContext.class), any(IntentContract.class)))
                .thenReturn(healResult);

        when(mockDelegate.findElement(By.id("stale-id")))
                .thenReturn(mockElement);

        // Execute - should attempt healing
        assertThatThrownBy(() -> healingDriver.findElement(By.id("stale-id")))
                .isInstanceOf(StaleElementReferenceException.class);

        verify(mockEngine).attemptHeal(any(FailureContext.class), any(IntentContract.class));
    }

    @Test
    void findElements_whenStaleElementException_attemptsHealing() {
        when(mockDelegate.findElements(By.className("test")))
                .thenThrow(new StaleElementReferenceException("Elements are stale"));

        HealResult healResult = HealResult.success(
                0,
                0.9,
                "Re-found stale elements",
                "class=test"
        );
        when(mockEngine.attemptHeal(any(FailureContext.class), any(IntentContract.class)))
                .thenReturn(healResult);

        assertThatThrownBy(() -> healingDriver.findElements(By.className("test")))
                .isInstanceOf(StaleElementReferenceException.class);

        verify(mockEngine).attemptHeal(any(FailureContext.class), any(IntentContract.class));
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
    void findElement_whenIntentIsNull_attemptsHealing() {
        when(mockDelegate.findElement(By.id("test")))
                .thenThrow(new NoSuchElementException("Element not found"));

        HealResult healResult = HealResult.success(
                0,
                0.9,
                "Healed with default intent",
                "id=test"
        );
        when(mockEngine.attemptHeal(any(FailureContext.class), any(IntentContract.class)))
                .thenReturn(healResult);

        when(mockDelegate.findElement(By.id("test")))
                .thenReturn(mockElement);

        assertThatThrownBy(() -> healingDriver.findElement(By.id("test")))
                .isInstanceOf(NoSuchElementException.class);

        verify(mockEngine).attemptHeal(any(FailureContext.class), any(IntentContract.class));
    }

    // ===== Test current intent context management =====

    @Test
    void setCurrentIntent_storesIntentAndStepText() {
        IntentContract intent = IntentContract.builder()
                .action("click")
                .description("Click login button")
                .build();

        healingDriver.setCurrentIntent(intent, "When I click the login button");

        // Trigger healing to verify intent is used
        when(mockDelegate.findElement(By.id("test")))
                .thenThrow(new NoSuchElementException("Element not found"));

        HealResult healResult = HealResult.failed("No candidates");
        when(mockEngine.attemptHeal(any(FailureContext.class), eq(intent)))
                .thenReturn(healResult);

        assertThatThrownBy(() -> healingDriver.findElement(By.id("test")))
                .isInstanceOf(NoSuchElementException.class);

        verify(mockEngine).attemptHeal(any(FailureContext.class), eq(intent));
    }

    @Test
    void clearCurrentIntent_removesIntentContext() {
        IntentContract intent = IntentContract.builder()
                .action("click")
                .description("Click button")
                .build();

        healingDriver.setCurrentIntent(intent, "When I click button");
        healingDriver.clearCurrentIntent();

        // Trigger healing - should use default intent now
        when(mockDelegate.findElement(By.id("test")))
                .thenThrow(new NoSuchElementException("Element not found"));

        HealResult healResult = HealResult.failed("No candidates");
        when(mockEngine.attemptHeal(any(FailureContext.class), any(IntentContract.class)))
                .thenReturn(healResult);

        assertThatThrownBy(() -> healingDriver.findElement(By.id("test")))
                .isInstanceOf(NoSuchElementException.class);

        verify(mockEngine).attemptHeal(
                any(FailureContext.class),
                argThat(ic -> !ic.equals(intent))
        );
    }

    // ===== Test byToLocatorInfo() conversion for all locator types =====

    @Test
    void byToLocatorInfo_convertsIdLocator() {
        By by = By.id("test-id");
        when(mockDelegate.findElement(by)).thenThrow(new NoSuchElementException("test"));
        when(mockEngine.attemptHeal(argThat(fc ->
                fc.getOriginalLocator().getStrategy() == LocatorInfo.LocatorStrategy.ID &&
                fc.getOriginalLocator().getValue().equals("test-id")
        ), any())).thenReturn(HealResult.failed(""));

        assertThatThrownBy(() -> healingDriver.findElement(by));
        verify(mockEngine).attemptHeal(any(FailureContext.class), any(IntentContract.class));
    }

    @Test
    void byToLocatorInfo_convertsNameLocator() {
        By by = By.name("username");
        when(mockDelegate.findElement(by)).thenThrow(new NoSuchElementException("test"));
        when(mockEngine.attemptHeal(argThat(fc ->
                fc.getOriginalLocator().getStrategy() == LocatorInfo.LocatorStrategy.NAME &&
                fc.getOriginalLocator().getValue().equals("username")
        ), any())).thenReturn(HealResult.failed(""));

        assertThatThrownBy(() -> healingDriver.findElement(by));
        verify(mockEngine).attemptHeal(any(FailureContext.class), any(IntentContract.class));
    }

    @Test
    void byToLocatorInfo_convertsClassNameLocator() {
        By by = By.className("btn-primary");
        when(mockDelegate.findElement(by)).thenThrow(new NoSuchElementException("test"));
        when(mockEngine.attemptHeal(argThat(fc ->
                fc.getOriginalLocator().getStrategy() == LocatorInfo.LocatorStrategy.CLASS_NAME &&
                fc.getOriginalLocator().getValue().equals("btn-primary")
        ), any())).thenReturn(HealResult.failed(""));

        assertThatThrownBy(() -> healingDriver.findElement(by));
        verify(mockEngine).attemptHeal(any(FailureContext.class), any(IntentContract.class));
    }

    @Test
    void byToLocatorInfo_convertsTagNameLocator() {
        By by = By.tagName("button");
        when(mockDelegate.findElement(by)).thenThrow(new NoSuchElementException("test"));
        when(mockEngine.attemptHeal(argThat(fc ->
                fc.getOriginalLocator().getStrategy() == LocatorInfo.LocatorStrategy.TAG_NAME &&
                fc.getOriginalLocator().getValue().equals("button")
        ), any())).thenReturn(HealResult.failed(""));

        assertThatThrownBy(() -> healingDriver.findElement(by));
        verify(mockEngine).attemptHeal(any(FailureContext.class), any(IntentContract.class));
    }

    @Test
    void byToLocatorInfo_convertsLinkTextLocator() {
        By by = By.linkText("Click here");
        when(mockDelegate.findElement(by)).thenThrow(new NoSuchElementException("test"));
        when(mockEngine.attemptHeal(argThat(fc ->
                fc.getOriginalLocator().getStrategy() == LocatorInfo.LocatorStrategy.LINK_TEXT &&
                fc.getOriginalLocator().getValue().equals("Click here")
        ), any())).thenReturn(HealResult.failed(""));

        assertThatThrownBy(() -> healingDriver.findElement(by));
        verify(mockEngine).attemptHeal(any(FailureContext.class), any(IntentContract.class));
    }

    @Test
    void byToLocatorInfo_convertsPartialLinkTextLocator() {
        By by = By.partialLinkText("Click");
        when(mockDelegate.findElement(by)).thenThrow(new NoSuchElementException("test"));
        when(mockEngine.attemptHeal(argThat(fc ->
                fc.getOriginalLocator().getStrategy() == LocatorInfo.LocatorStrategy.PARTIAL_LINK_TEXT &&
                fc.getOriginalLocator().getValue().equals("Click")
        ), any())).thenReturn(HealResult.failed(""));

        assertThatThrownBy(() -> healingDriver.findElement(by));
        verify(mockEngine).attemptHeal(any(FailureContext.class), any(IntentContract.class));
    }

    @Test
    void byToLocatorInfo_convertsCssSelectorLocator() {
        By by = By.cssSelector("div.container > button");
        when(mockDelegate.findElement(by)).thenThrow(new NoSuchElementException("test"));
        when(mockEngine.attemptHeal(argThat(fc ->
                fc.getOriginalLocator().getStrategy() == LocatorInfo.LocatorStrategy.CSS &&
                fc.getOriginalLocator().getValue().equals("div.container > button")
        ), any())).thenReturn(HealResult.failed(""));

        assertThatThrownBy(() -> healingDriver.findElement(by));
        verify(mockEngine).attemptHeal(any(FailureContext.class), any(IntentContract.class));
    }

    @Test
    void byToLocatorInfo_convertsXpathLocator() {
        By by = By.xpath("//button[@id='submit']");
        when(mockDelegate.findElement(by)).thenThrow(new NoSuchElementException("test"));
        when(mockEngine.attemptHeal(argThat(fc ->
                fc.getOriginalLocator().getStrategy() == LocatorInfo.LocatorStrategy.XPATH &&
                fc.getOriginalLocator().getValue().equals("//button[@id='submit']")
        ), any())).thenReturn(HealResult.failed(""));

        assertThatThrownBy(() -> healingDriver.findElement(by));
        verify(mockEngine).attemptHeal(any(FailureContext.class), any(IntentContract.class));
    }

    // ===== Test locatorInfoToBy() conversion =====

    @Test
    void locatorInfoToBy_convertsAllStrategiesCorrectly() {
        // Test ID
        when(mockDelegate.findElement(By.id("old")))
                .thenThrow(new NoSuchElementException("test"));
        when(mockEngine.attemptHeal(any(), any()))
                .thenReturn(HealResult.success(0, 0.9, "healed", "id=new"));
        when(mockDelegate.findElement(By.id("new"))).thenReturn(mockElement);
        assertThatThrownBy(() -> healingDriver.findElement(By.id("old")));

        // Test NAME
        reset(mockDelegate, mockEngine);
        when(mockDelegate.findElement(By.name("old")))
                .thenThrow(new NoSuchElementException("test"));
        when(mockEngine.attemptHeal(any(), any()))
                .thenReturn(HealResult.success(0, 0.9, "healed", "name=new"));
        when(mockDelegate.findElement(By.name("new"))).thenReturn(mockElement);
        assertThatThrownBy(() -> healingDriver.findElement(By.name("old")));

        // Test XPATH
        reset(mockDelegate, mockEngine);
        when(mockDelegate.findElement(By.xpath("//old")))
                .thenThrow(new NoSuchElementException("test"));
        when(mockEngine.attemptHeal(any(), any()))
                .thenReturn(HealResult.success(0, 0.9, "healed", "xpath=//new"));
        when(mockDelegate.findElement(By.xpath("//new"))).thenReturn(mockElement);
        assertThatThrownBy(() -> healingDriver.findElement(By.xpath("//old")));
    }

    // ===== Test parseLocatorString() with various formats =====

    @Test
    void parseLocatorString_handlesStandardFormat() {
        when(mockDelegate.findElement(By.id("old")))
                .thenThrow(new NoSuchElementException("test"));
        when(mockEngine.attemptHeal(any(), any()))
                .thenReturn(HealResult.success(0, 0.9, "healed", "ID=test-id"));
        when(mockDelegate.findElement(By.id("test-id"))).thenReturn(mockElement);

        assertThatThrownBy(() -> healingDriver.findElement(By.id("old")));
        verify(mockDelegate).findElement(By.id("test-id"));
    }

    @Test
    void parseLocatorString_handlesVariations() {
        // Test CLASSNAME -> CLASS_NAME
        when(mockDelegate.findElement(By.id("old")))
                .thenThrow(new NoSuchElementException("test"));
        when(mockEngine.attemptHeal(any(), any()))
                .thenReturn(HealResult.success(0, 0.9, "healed", "CLASSNAME=btn"));
        when(mockDelegate.findElement(By.className("btn"))).thenReturn(mockElement);
        assertThatThrownBy(() -> healingDriver.findElement(By.id("old")));

        // Test CSSSELECTOR -> CSS
        reset(mockDelegate, mockEngine);
        when(mockDelegate.findElement(By.id("old")))
                .thenThrow(new NoSuchElementException("test"));
        when(mockEngine.attemptHeal(any(), any()))
                .thenReturn(HealResult.success(0, 0.9, "healed", "CSSSELECTOR=.btn"));
        when(mockDelegate.findElement(By.cssSelector(".btn"))).thenReturn(mockElement);
        assertThatThrownBy(() -> healingDriver.findElement(By.id("old")));
    }

    @Test
    void parseLocatorString_defaultsToCssWhenNoStrategySpecified() {
        when(mockDelegate.findElement(By.id("old")))
                .thenThrow(new NoSuchElementException("test"));
        when(mockEngine.attemptHeal(any(), any()))
                .thenReturn(HealResult.success(0, 0.9, "healed", ".btn-primary"));
        when(mockDelegate.findElement(By.cssSelector(".btn-primary"))).thenReturn(mockElement);

        assertThatThrownBy(() -> healingDriver.findElement(By.id("old")));
        verify(mockDelegate).findElement(By.cssSelector(".btn-primary"));
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
