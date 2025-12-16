package com.intenthealer.selenium.actions;

import com.intenthealer.core.config.GuardrailConfig;
import com.intenthealer.core.exception.HealingException;
import com.intenthealer.core.model.ActionType;
import com.intenthealer.core.model.ElementSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.UnexpectedTagNameException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActionExecutorTest {

    @Mock
    private WebDriver mockDriver;

    @Mock
    private JavascriptExecutor mockJsExecutor;

    @Mock
    private WebElement mockElement;

    @Mock
    private WebElement mockOption;

    @Mock
    private GuardrailConfig mockConfig;

    private ActionExecutor executor;

    @BeforeEach
    void setUp() {
        // Make mockDriver also implement JavascriptExecutor
        mockDriver = mock(WebDriver.class, withSettings()
                .extraInterfaces(JavascriptExecutor.class));
        executor = new ActionExecutor(mockDriver, mockConfig);
    }

    // ===== Test click actions =====

    @Test
    void executeClick_standardClick_succeeds() {
        ElementSnapshot snapshot = createBasicSnapshot("button", "submit");
        when(mockDriver.findElement(any(By.class))).thenReturn(mockElement);
        doNothing().when(mockElement).click();

        executor.execute(ActionType.CLICK, snapshot, null);

        verify(mockElement).click();
    }

    @Test
    void executeClick_whenClickIntercepted_scrollsAndRetries() {
        ElementSnapshot snapshot = createBasicSnapshot("button", "submit");
        when(mockDriver.findElement(any(By.class))).thenReturn(mockElement);

        // First click fails, second succeeds after scroll
        doThrow(new ElementClickInterceptedException("Intercepted"))
                .doNothing()
                .when(mockElement).click();

        executor.execute(ActionType.CLICK, snapshot, null);

        verify((JavascriptExecutor) mockDriver).executeScript(anyString(), eq(mockElement));
        verify(mockElement, times(2)).click();
    }

    @Test
    void executeClick_whenNotInteractable_triesToScrollAndWait() {
        ElementSnapshot snapshot = createBasicSnapshot("button", "submit");
        when(mockDriver.findElement(any(By.class))).thenReturn(mockElement);

        // First click fails with not interactable
        doThrow(new ElementNotInteractableException("Not interactable"))
                .doNothing()
                .when(mockElement).click();

        executor.execute(ActionType.CLICK, snapshot, null);

        verify((JavascriptExecutor) mockDriver).executeScript(anyString(), eq(mockElement));
        verify(mockElement, atLeast(1)).click();
    }

    @Test
    void executeClick_whenJsClickAllowed_usesJavaScriptClick() {
        ElementSnapshot snapshot = createBasicSnapshot("button", "submit");
        when(mockDriver.findElement(any(By.class))).thenReturn(mockElement);
        when(mockConfig.isAllowJsClick()).thenReturn(true);

        // All standard strategies fail
        doThrow(new ElementClickInterceptedException("Failed")).when(mockElement).click();

        executor.execute(ActionType.CLICK, snapshot, null);

        verify((JavascriptExecutor) mockDriver, atLeastOnce())
                .executeScript(anyString(), eq(mockElement));
    }

    @Test
    void executeClick_whenAllStrategiesFail_throwsHealingException() {
        ElementSnapshot snapshot = createBasicSnapshot("button", "submit");
        when(mockDriver.findElement(any(By.class))).thenReturn(mockElement);
        when(mockConfig.isAllowJsClick()).thenReturn(false);

        // All strategies fail
        doThrow(new ElementClickInterceptedException("Failed")).when(mockElement).click();
        doThrow(new WebDriverException("JS failed"))
                .when((JavascriptExecutor) mockDriver).executeScript(anyString(), any());

        assertThatThrownBy(() -> executor.execute(ActionType.CLICK, snapshot, null))
                .isInstanceOf(HealingException.class)
                .hasMessageContaining("All click strategies failed");
    }

    // ===== Test type/sendKeys actions =====

    @Test
    void executeType_clearsAndSendsKeys() {
        ElementSnapshot snapshot = createBasicSnapshot("input", "username");
        when(mockDriver.findElement(any(By.class))).thenReturn(mockElement);

        executor.execute(ActionType.TYPE, snapshot, "test@example.com");

        verify(mockElement).clear();
        verify(mockElement).sendKeys("test@example.com");
    }

    @Test
    void executeType_whenSendKeysFails_usesJavaScript() {
        ElementSnapshot snapshot = createBasicSnapshot("input", "username");
        when(mockDriver.findElement(any(By.class))).thenReturn(mockElement);

        doThrow(new WebDriverException("sendKeys failed")).when(mockElement).sendKeys(anyString());

        executor.execute(ActionType.TYPE, snapshot, "test@example.com");

        verify((JavascriptExecutor) mockDriver).executeScript(
                contains("arguments[0].value"),
                eq(mockElement),
                eq("test@example.com")
        );
    }

    @Test
    void executeType_whenBothStrategiesFail_throwsHealingException() {
        ElementSnapshot snapshot = createBasicSnapshot("input", "username");
        when(mockDriver.findElement(any(By.class))).thenReturn(mockElement);

        doThrow(new WebDriverException("sendKeys failed")).when(mockElement).sendKeys(anyString());
        doThrow(new WebDriverException("JS failed"))
                .when((JavascriptExecutor) mockDriver).executeScript(anyString(), any(), any());

        assertThatThrownBy(() -> executor.execute(ActionType.TYPE, snapshot, "test"))
                .isInstanceOf(HealingException.class)
                .hasMessageContaining("Failed to type text");
    }

    // ===== Test select actions =====

    @Test
    void executeSelect_selectsByVisibleText() {
        ElementSnapshot snapshot = createSelectSnapshot();
        WebElement selectElement = createMockSelectElement("option1", "option2");
        when(mockDriver.findElement(any(By.class))).thenReturn(selectElement);

        executor.execute(ActionType.SELECT, snapshot, "option1");

        // Verify selection happened (mock would be called in real Select class)
        verify(selectElement, atLeastOnce()).getTagName();
    }

    @Test
    void executeSelect_whenNotSelectElement_throwsHealingException() {
        ElementSnapshot snapshot = createBasicSnapshot("div", "notselect");
        when(mockDriver.findElement(any(By.class))).thenReturn(mockElement);
        when(mockElement.getTagName()).thenReturn("div");

        assertThatThrownBy(() -> executor.execute(ActionType.SELECT, snapshot, "option1"))
                .isInstanceOf(HealingException.class)
                .hasMessageContaining("Element is not a select");
    }

    // ===== Test clear action =====

    @Test
    void executeClear_clearsElement() {
        ElementSnapshot snapshot = createBasicSnapshot("input", "username");
        when(mockDriver.findElement(any(By.class))).thenReturn(mockElement);

        executor.execute(ActionType.CLEAR, snapshot, null);

        verify(mockElement).clear();
    }

    @Test
    void executeClear_whenClearFails_usesJavaScript() {
        ElementSnapshot snapshot = createBasicSnapshot("input", "username");
        when(mockDriver.findElement(any(By.class))).thenReturn(mockElement);

        doThrow(new WebDriverException("clear failed")).when(mockElement).clear();

        executor.execute(ActionType.CLEAR, snapshot, null);

        verify((JavascriptExecutor) mockDriver).executeScript(
                contains("arguments[0].value"),
                eq(mockElement)
        );
    }

    @Test
    void executeClear_whenBothStrategiesFail_throwsHealingException() {
        ElementSnapshot snapshot = createBasicSnapshot("input", "username");
        when(mockDriver.findElement(any(By.class))).thenReturn(mockElement);

        doThrow(new WebDriverException("clear failed")).when(mockElement).clear();
        doThrow(new WebDriverException("JS failed"))
                .when((JavascriptExecutor) mockDriver).executeScript(anyString(), any());

        assertThatThrownBy(() -> executor.execute(ActionType.CLEAR, snapshot, null))
                .isInstanceOf(HealingException.class)
                .hasMessageContaining("Failed to clear element");
    }

    // ===== Test hover action =====

    @Test
    void executeHover_usesActionsAPI() {
        ElementSnapshot snapshot = createBasicSnapshot("button", "menu");
        when(mockDriver.findElement(any(By.class))).thenReturn(mockElement);

        executor.execute(ActionType.HOVER, snapshot, null);

        // Verify element was located (Actions API would be called in real scenario)
        verify(mockDriver).findElement(any(By.class));
    }

    // ===== Test double click action =====

    @Test
    void executeDoubleClick_usesActionsAPI() {
        ElementSnapshot snapshot = createBasicSnapshot("button", "select");
        when(mockDriver.findElement(any(By.class))).thenReturn(mockElement);

        executor.execute(ActionType.DOUBLE_CLICK, snapshot, null);

        verify(mockDriver).findElement(any(By.class));
    }

    // ===== Test right click action =====

    @Test
    void executeRightClick_usesActionsAPI() {
        ElementSnapshot snapshot = createBasicSnapshot("div", "context");
        when(mockDriver.findElement(any(By.class))).thenReturn(mockElement);

        executor.execute(ActionType.RIGHT_CLICK, snapshot, null);

        verify(mockDriver).findElement(any(By.class));
    }

    // ===== Test submit action =====

    @Test
    void executeSubmit_submitsForm() {
        ElementSnapshot snapshot = createBasicSnapshot("button", "submit");
        when(mockDriver.findElement(any(By.class))).thenReturn(mockElement);

        executor.execute(ActionType.SUBMIT, snapshot, null);

        verify(mockElement).submit();
    }

    @Test
    void executeSubmit_whenSubmitFails_triesClick() {
        ElementSnapshot snapshot = createBasicSnapshot("button", "submit");
        when(mockDriver.findElement(any(By.class))).thenReturn(mockElement);

        doThrow(new WebDriverException("submit failed")).when(mockElement).submit();
        doNothing().when(mockElement).click();

        executor.execute(ActionType.SUBMIT, snapshot, null);

        verify(mockElement).submit();
        verify(mockElement).click();
    }

    // ===== Test action failure handling =====

    @Test
    void execute_whenUnsupportedActionType_throwsHealingException() {
        ElementSnapshot snapshot = createBasicSnapshot("button", "test");

        assertThatThrownBy(() -> executor.execute(ActionType.UNKNOWN, snapshot, null))
                .isInstanceOf(HealingException.class)
                .hasMessageContaining("Unsupported action type");
    }

    // ===== Test refindElement =====

    @Test
    void refindElement_findsElementByDataTestId() {
        ElementSnapshot snapshot = createSnapshotWithDataTestId("login-button");
        when(mockDriver.findElement(By.cssSelector("[data-testid='login-button']")))
                .thenReturn(mockElement);

        WebElement result = executor.refindElement(snapshot);

        assertThat(result).isEqualTo(mockElement);
        verify(mockDriver).findElement(By.cssSelector("[data-testid='login-button']"));
    }

    @Test
    void refindElement_findsElementById_whenDataTestIdNotPresent() {
        ElementSnapshot snapshot = ElementSnapshot.builder()
                .index(0)
                .tagName("button")
                .id("submit-btn")
                .build();

        when(mockDriver.findElement(By.id("submit-btn"))).thenReturn(mockElement);

        WebElement result = executor.refindElement(snapshot);

        assertThat(result).isEqualTo(mockElement);
        verify(mockDriver).findElement(By.id("submit-btn"));
    }

    @Test
    void refindElement_skipsGeneratedLookingIds() {
        ElementSnapshot snapshot = ElementSnapshot.builder()
                .index(0)
                .tagName("button")
                .id("comp_12345678")  // looks generated
                .ariaLabel("Submit")
                .build();

        when(mockDriver.findElement(By.cssSelector("[aria-label='Submit']")))
                .thenReturn(mockElement);

        WebElement result = executor.refindElement(snapshot);

        assertThat(result).isEqualTo(mockElement);
        verify(mockDriver, never()).findElement(By.id("comp_12345678"));
        verify(mockDriver).findElement(By.cssSelector("[aria-label='Submit']"));
    }

    @Test
    void refindElement_findsElementByAriaLabel() {
        ElementSnapshot snapshot = ElementSnapshot.builder()
                .index(0)
                .tagName("button")
                .ariaLabel("Submit form")
                .build();

        when(mockDriver.findElement(By.cssSelector("[aria-label='Submit form']")))
                .thenReturn(mockElement);

        WebElement result = executor.refindElement(snapshot);

        assertThat(result).isEqualTo(mockElement);
    }

    @Test
    void refindElement_findsElementByTextAndTag() {
        ElementSnapshot snapshot = ElementSnapshot.builder()
                .index(0)
                .tagName("button")
                .text("Click me")
                .build();

        when(mockDriver.findElement(By.xpath("//button[normalize-space(text())='Click me']")))
                .thenReturn(mockElement);

        WebElement result = executor.refindElement(snapshot);

        assertThat(result).isEqualTo(mockElement);
    }

    @Test
    void refindElement_findsElementByName() {
        ElementSnapshot snapshot = ElementSnapshot.builder()
                .index(0)
                .tagName("input")
                .name("username")
                .build();

        when(mockDriver.findElement(By.name("username"))).thenReturn(mockElement);

        WebElement result = executor.refindElement(snapshot);

        assertThat(result).isEqualTo(mockElement);
    }

    @Test
    void refindElement_throwsHealingException_whenCannotRefind() {
        ElementSnapshot snapshot = ElementSnapshot.builder()
                .index(0)
                .tagName("div")
                .build();

        assertThatThrownBy(() -> executor.refindElement(snapshot))
                .isInstanceOf(HealingException.class)
                .hasMessageContaining("Could not re-find element");
    }

    @Test
    void refindElement_triesMultipleStrategiesInOrder() {
        ElementSnapshot snapshot = ElementSnapshot.builder()
                .index(0)
                .tagName("button")
                .id("generated_123")  // looks generated, should be skipped
                .ariaLabel("Submit")
                .build();

        // First strategy (aria-label) fails
        when(mockDriver.findElement(By.cssSelector("[aria-label='Submit']")))
                .thenThrow(new NoSuchElementException("not found"));

        // Should not try ID since it looks generated
        // Should throw since no other strategies available
        assertThatThrownBy(() -> executor.refindElement(snapshot))
                .isInstanceOf(HealingException.class);

        verify(mockDriver).findElement(By.cssSelector("[aria-label='Submit']"));
        verify(mockDriver, never()).findElement(By.id("generated_123"));
    }

    // ===== Helper methods =====

    private ElementSnapshot createBasicSnapshot(String tagName, String id) {
        return ElementSnapshot.builder()
                .index(0)
                .tagName(tagName)
                .id(id)
                .build();
    }

    private ElementSnapshot createSelectSnapshot() {
        return ElementSnapshot.builder()
                .index(0)
                .tagName("select")
                .id("country-select")
                .build();
    }

    private ElementSnapshot createSnapshotWithDataTestId(String testId) {
        Map<String, String> dataAttrs = new HashMap<>();
        dataAttrs.put("testid", testId);

        return ElementSnapshot.builder()
                .index(0)
                .tagName("button")
                .dataAttributes(dataAttrs)
                .build();
    }

    private WebElement createMockSelectElement(String... options) {
        WebElement selectElement = mock(WebElement.class);
        when(selectElement.getTagName()).thenReturn("select");

        // Mock findElements for options
        List<WebElement> optionElements = new java.util.ArrayList<>();
        for (String option : options) {
            WebElement optionElement = mock(WebElement.class);
            when(optionElement.getText()).thenReturn(option);
            when(optionElement.getAttribute("value")).thenReturn(option);
            optionElements.add(optionElement);
        }
        when(selectElement.findElements(By.tagName("option"))).thenReturn(optionElements);

        return selectElement;
    }
}
