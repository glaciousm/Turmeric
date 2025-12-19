package io.github.glaciousm.selenium.snapshot;

import io.github.glaciousm.core.config.SnapshotConfig;
import io.github.glaciousm.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openqa.selenium.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SnapshotBuilderTest {

    @Mock
    private WebDriver mockDriver;

    @Mock
    private JavascriptExecutor mockJsExecutor;

    @Mock
    private TakesScreenshot mockScreenshot;

    @Mock
    private WebElement mockElement;

    private SnapshotBuilder snapshotBuilder;
    private SnapshotConfig config;

    @BeforeEach
    void setUp() {
        // Make mockDriver implement multiple interfaces
        mockDriver = mock(WebDriver.class, withSettings()
                .extraInterfaces(JavascriptExecutor.class, TakesScreenshot.class));

        config = new SnapshotConfig();
        snapshotBuilder = new SnapshotBuilder(mockDriver, config);
    }

    /**
     * Sets up default stubs for executeScript to handle different script types.
     * This prevents ClassCastException when scripts return different types.
     */
    private void setupDefaultExecuteScriptStubs(List<WebElement> elementsToReturn) {
        // One-argument executeScript (for element capture, language detection, DOM capture)
        when(((JavascriptExecutor) mockDriver).executeScript(anyString())).thenAnswer(invocation -> {
            String script = invocation.getArgument(0);
            if (script.contains("document.documentElement.lang")) {
                return "en";  // Language detection returns String
            }
            if (script.contains("document.documentElement.outerHTML")) {
                return "<html></html>";  // DOM capture returns String
            }
            if (script.contains("document.querySelectorAll")) {
                return elementsToReturn;  // Element capture returns List<WebElement>
            }
            return new ArrayList<>();  // Default: return empty list
        });

        // Two-argument executeScript (for container, labels, data attributes)
        when(((JavascriptExecutor) mockDriver).executeScript(anyString(), any())).thenAnswer(invocation -> {
            String script = invocation.getArgument(0);
            if (script.contains("parentElement")) {
                return "body";  // Container finding returns String
            }
            if (script.contains("label")) {
                return new ArrayList<String>();  // Nearby labels returns List<String>
            }
            if (script.contains("data-")) {
                return new HashMap<String, String>();  // Data attributes returns Map
            }
            return null;
        });
    }

    // ===== Test snapshot capture with mocked WebDriver =====

    @Test
    void captureAll_capturesBasicPageInformation() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com");
        when(mockDriver.getTitle()).thenReturn("Example Page");
        when(((JavascriptExecutor) mockDriver).executeScript(anyString()))
                .thenReturn("en");
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.querySelectorAll")))
                .thenReturn(new ArrayList<>());

        UiSnapshot snapshot = snapshotBuilder.captureAll();

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getUrl()).isEqualTo("https://example.com");
        assertThat(snapshot.getTitle()).isEqualTo("Example Page");
        assertThat(snapshot.getDetectedLanguage()).isEqualTo("en");
        assertThat(snapshot.getTimestamp()).isNotNull();
    }

    @Test
    void captureAll_capturesInteractiveElements() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com");
        when(mockDriver.getTitle()).thenReturn("Test Page");
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.documentElement.lang")))
                .thenReturn("en");

        List<WebElement> mockElements = List.of(mockElement);
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.querySelectorAll")))
                .thenReturn(mockElements);

        setupMockElement(mockElement, "button", "submit-btn", "Submit");

        UiSnapshot snapshot = snapshotBuilder.captureAll();

        assertThat(snapshot.getInteractiveElements()).isNotEmpty();
        assertThat(snapshot.getInteractiveElements().get(0).getTagName()).isEqualTo("button");
    }

    @Test
    void capture_withFailureContext_capturesOnlyRelevantElements() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com");
        when(mockDriver.getTitle()).thenReturn("Test Page");
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.documentElement.lang")))
                .thenReturn("en");

        FailureContext clickFailure = FailureContext.builder()
                .actionType(ActionType.CLICK)
                .exceptionType("NoSuchElementException")
                .exceptionMessage("Element not found")
                .stepText("Click the login button")
                .build();

        List<WebElement> mockElements = List.of(mockElement);
        when(((JavascriptExecutor) mockDriver).executeScript(contains("button")))
                .thenReturn(mockElements);

        setupMockElement(mockElement, "button", "login", "Login");

        UiSnapshot snapshot = snapshotBuilder.capture(clickFailure);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getInteractiveElements()).isNotEmpty();
    }

    @Test
    void capture_forTypeAction_capturesInputElements() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com");
        when(mockDriver.getTitle()).thenReturn("Form Page");
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.documentElement.lang")))
                .thenReturn("en");

        FailureContext typeFailure = FailureContext.builder()
                .actionType(ActionType.TYPE)
                .exceptionType("NoSuchElementException")
                .exceptionMessage("Input not found")
                .stepText("Enter username")
                .build();

        List<WebElement> mockElements = List.of(mockElement);
        when(((JavascriptExecutor) mockDriver).executeScript(contains("input")))
                .thenReturn(mockElements);

        setupMockElement(mockElement, "input", "username", null);
        when(mockElement.getAttribute("type")).thenReturn("text");

        UiSnapshot snapshot = snapshotBuilder.capture(typeFailure);

        assertThat(snapshot.getInteractiveElements()).isNotEmpty();
        assertThat(snapshot.getInteractiveElements().get(0).getTagName()).isEqualTo("input");
    }

    @Test
    void capture_forSelectAction_capturesSelectElements() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com");
        when(mockDriver.getTitle()).thenReturn("Form Page");
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.documentElement.lang")))
                .thenReturn("en");

        FailureContext selectFailure = FailureContext.builder()
                .actionType(ActionType.SELECT)
                .exceptionType("NoSuchElementException")
                .exceptionMessage("Select not found")
                .stepText("Select country")
                .build();

        List<WebElement> mockElements = List.of(mockElement);
        when(((JavascriptExecutor) mockDriver).executeScript(contains("select")))
                .thenReturn(mockElements);

        setupMockElement(mockElement, "select", "country", null);

        UiSnapshot snapshot = snapshotBuilder.capture(selectFailure);

        assertThat(snapshot.getInteractiveElements()).isNotEmpty();
        assertThat(snapshot.getInteractiveElements().get(0).getTagName()).isEqualTo("select");
    }

    // ===== Test element extraction =====

    @Test
    void captureElement_extractsAllAttributes() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com");
        when(mockDriver.getTitle()).thenReturn("Test");
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.documentElement.lang")))
                .thenReturn("en");

        List<WebElement> mockElements = List.of(mockElement);
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.querySelectorAll")))
                .thenReturn(mockElements);

        setupDetailedMockElement(mockElement);

        UiSnapshot snapshot = snapshotBuilder.captureAll();

        assertThat(snapshot.getInteractiveElements()).hasSize(1);
        ElementSnapshot element = snapshot.getInteractiveElements().get(0);

        assertThat(element.getTagName()).isEqualTo("button");
        assertThat(element.getId()).isEqualTo("submit-btn");
        assertThat(element.getName()).isEqualTo("submit");
        assertThat(element.getText()).isEqualTo("Submit Form");
        assertThat(element.getAriaLabel()).isEqualTo("Submit the form");
        assertThat(element.isVisible()).isTrue();
        assertThat(element.isEnabled()).isTrue();
    }

    @Test
    void captureElement_handlesStaleElement() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com");
        when(mockDriver.getTitle()).thenReturn("Test");
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.documentElement.lang")))
                .thenReturn("en");

        List<WebElement> mockElements = List.of(mockElement);
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.querySelectorAll")))
                .thenReturn(mockElements);

        when(mockElement.getTagName()).thenThrow(new StaleElementReferenceException("Stale"));

        UiSnapshot snapshot = snapshotBuilder.captureAll();

        // Should handle stale element gracefully
        assertThat(snapshot.getInteractiveElements()).isEmpty();
    }

    @Test
    void captureElement_normalizesLongText() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com");
        when(mockDriver.getTitle()).thenReturn("Test");
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.documentElement.lang")))
                .thenReturn("en");

        List<WebElement> mockElements = List.of(mockElement);
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.querySelectorAll")))
                .thenReturn(mockElements);

        String longText = "a".repeat(1000);
        setupMockElement(mockElement, "button", "test", longText);

        UiSnapshot snapshot = snapshotBuilder.captureAll();

        ElementSnapshot element = snapshot.getInteractiveElements().get(0);
        assertThat(element.getText()).hasSizeLessThan(500); // Default max text length
        assertThat(element.getText()).endsWith("...");
    }

    @Test
    void captureElement_capturesClasses() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com");
        when(mockDriver.getTitle()).thenReturn("Test");
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.documentElement.lang")))
                .thenReturn("en");

        List<WebElement> mockElements = List.of(mockElement);
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.querySelectorAll")))
                .thenReturn(mockElements);

        setupMockElement(mockElement, "button", "test", "Click");
        when(mockElement.getAttribute("class")).thenReturn("btn btn-primary btn-lg");

        UiSnapshot snapshot = snapshotBuilder.captureAll();

        ElementSnapshot element = snapshot.getInteractiveElements().get(0);
        assertThat(element.getClasses()).containsExactly("btn", "btn-primary", "btn-lg");
    }

    @Test
    void captureElement_capturesRectangle() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com");
        when(mockDriver.getTitle()).thenReturn("Test");
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.documentElement.lang")))
                .thenReturn("en");

        List<WebElement> mockElements = List.of(mockElement);
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.querySelectorAll")))
                .thenReturn(mockElements);

        setupMockElement(mockElement, "button", "test", "Click");
        // Rectangle constructor: (x, y, height, width)
        when(mockElement.getRect()).thenReturn(new Rectangle(10, 20, 50, 100));

        UiSnapshot snapshot = snapshotBuilder.captureAll();

        ElementSnapshot element = snapshot.getInteractiveElements().get(0);
        assertThat(element.getRect()).isNotNull();
        assertThat(element.getRect().getX()).isEqualTo(10);
        assertThat(element.getRect().getY()).isEqualTo(20);
        assertThat(element.getRect().getWidth()).isEqualTo(100);
        assertThat(element.getRect().getHeight()).isEqualTo(50);
    }

    // ===== Test container finding =====

    @Test
    void captureElement_findsContainer() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com");
        when(mockDriver.getTitle()).thenReturn("Test");

        // Stub for language detection
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.documentElement.lang")))
                .thenReturn("en");

        // Stub for element capture - must be more specific than anyString
        List<WebElement> mockElements = List.of(mockElement);
        when(((JavascriptExecutor) mockDriver).executeScript(contains("querySelectorAll")))
                .thenReturn(mockElements);

        setupMockElement(mockElement, "button", "test", "Submit");

        // Two-argument stubs for element details
        when(((JavascriptExecutor) mockDriver).executeScript(contains("parentElement"), any()))
                .thenReturn("FORM#login-form");
        when(((JavascriptExecutor) mockDriver).executeScript(contains("label"), any()))
                .thenReturn(new ArrayList<String>());
        when(((JavascriptExecutor) mockDriver).executeScript(contains("data-"), any()))
                .thenReturn(new HashMap<String, String>());

        UiSnapshot snapshot = snapshotBuilder.captureAll();

        ElementSnapshot element = snapshot.getInteractiveElements().get(0);
        assertThat(element.getContainer()).isEqualTo("FORM#login-form");
    }

    // ===== Test nearby labels finding =====

    @Test
    void captureElement_findsNearbyLabels() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com");
        when(mockDriver.getTitle()).thenReturn("Test");

        // Stub for language detection
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.documentElement.lang")))
                .thenReturn("en");

        // Stub for element capture
        List<WebElement> mockElements = List.of(mockElement);
        when(((JavascriptExecutor) mockDriver).executeScript(contains("querySelectorAll")))
                .thenReturn(mockElements);

        setupMockElement(mockElement, "input", "username", null);

        // Two-argument stubs for element details
        when(((JavascriptExecutor) mockDriver).executeScript(contains("parentElement"), any()))
                .thenReturn("body");
        List<String> labels = List.of("Username:", "Email or Username");
        when(((JavascriptExecutor) mockDriver).executeScript(contains("label"), any()))
                .thenReturn(labels);
        when(((JavascriptExecutor) mockDriver).executeScript(contains("data-"), any()))
                .thenReturn(new HashMap<String, String>());

        UiSnapshot snapshot = snapshotBuilder.captureAll();

        ElementSnapshot element = snapshot.getInteractiveElements().get(0);
        assertThat(element.getNearbyLabels()).containsExactly("Username:", "Email or Username");
    }

    // ===== Test data attributes capture =====

    @Test
    void captureElement_capturesDataAttributes() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com");
        when(mockDriver.getTitle()).thenReturn("Test");
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.documentElement.lang")))
                .thenReturn("en");

        List<WebElement> mockElements = List.of(mockElement);
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.querySelectorAll")))
                .thenReturn(mockElements);

        setupMockElement(mockElement, "button", "test", "Click");

        // Mock data attributes script
        Map<String, String> dataAttrs = new HashMap<>();
        dataAttrs.put("testid", "submit-button");
        dataAttrs.put("action", "login");
        when(((JavascriptExecutor) mockDriver).executeScript(
                argThat((String script) -> script.contains("data-")),
                eq(mockElement)
        )).thenReturn(dataAttrs);

        UiSnapshot snapshot = snapshotBuilder.captureAll();

        ElementSnapshot element = snapshot.getInteractiveElements().get(0);
        assertThat(element.getDataAttributes()).containsEntry("testid", "submit-button");
        assertThat(element.getDataAttributes()).containsEntry("action", "login");
    }

    // ===== Test language detection =====

    @Test
    void captureAll_detectsLanguage() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com");
        when(mockDriver.getTitle()).thenReturn("Test");
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.documentElement.lang")))
                .thenReturn("fr");
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.querySelectorAll")))
                .thenReturn(new ArrayList<>());

        UiSnapshot snapshot = snapshotBuilder.captureAll();

        assertThat(snapshot.getDetectedLanguage()).isEqualTo("fr");
    }

    @Test
    void captureAll_defaultsToEnglish_whenLanguageDetectionFails() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com");
        when(mockDriver.getTitle()).thenReturn("Test");
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.documentElement.lang")))
                .thenThrow(new WebDriverException("Script failed"));
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.querySelectorAll")))
                .thenReturn(new ArrayList<>());

        UiSnapshot snapshot = snapshotBuilder.captureAll();

        assertThat(snapshot.getDetectedLanguage()).isEqualTo("en");
    }

    // ===== Test screenshot capture =====

    @Test
    void capture_capturesScreenshot_whenConfigured() {
        SnapshotConfig configWithScreenshot = new SnapshotConfig();
        configWithScreenshot.setCaptureScreenshot(true);
        snapshotBuilder = new SnapshotBuilder(mockDriver, configWithScreenshot);

        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com");
        when(mockDriver.getTitle()).thenReturn("Test");
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.documentElement.lang")))
                .thenReturn("en");
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.querySelectorAll")))
                .thenReturn(new ArrayList<>());
        when(((TakesScreenshot) mockDriver).getScreenshotAs(OutputType.BASE64))
                .thenReturn("base64data");

        FailureContext failure = FailureContext.builder()
                .actionType(ActionType.CLICK)
                .exceptionType("NoSuchElementException")
                .exceptionMessage("Not found")
                .stepText("Click submit button")
                .build();

        UiSnapshot snapshot = snapshotBuilder.capture(failure);

        assertThat(snapshot.getScreenshotBase64()).isPresent();
        assertThat(snapshot.getScreenshotBase64().get()).isEqualTo("base64data");
    }

    @Test
    void capture_handlesScreenshotFailure() {
        SnapshotConfig configWithScreenshot = new SnapshotConfig();
        configWithScreenshot.setCaptureScreenshot(true);
        snapshotBuilder = new SnapshotBuilder(mockDriver, configWithScreenshot);

        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com");
        when(mockDriver.getTitle()).thenReturn("Test");
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.documentElement.lang")))
                .thenReturn("en");
        when(((JavascriptExecutor) mockDriver).executeScript(contains("querySelectorAll")))
                .thenReturn(new ArrayList<>());
        when(((TakesScreenshot) mockDriver).getScreenshotAs(OutputType.BASE64))
                .thenThrow(new WebDriverException("Screenshot failed"));

        FailureContext failure = FailureContext.builder()
                .actionType(ActionType.CLICK)
                .exceptionType("NoSuchElementException")
                .exceptionMessage("Not found")
                .stepText("Click submit button")
                .build();

        UiSnapshot snapshot = snapshotBuilder.capture(failure);

        assertThat(snapshot.getScreenshotBase64()).isEmpty();
    }

    // ===== Test DOM capture =====

    @Test
    void capture_capturesDom_whenConfigured() {
        SnapshotConfig configWithDom = new SnapshotConfig();
        configWithDom.setCaptureDom(true);
        snapshotBuilder = new SnapshotBuilder(mockDriver, configWithDom);

        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com");
        when(mockDriver.getTitle()).thenReturn("Test");
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.documentElement.lang")))
                .thenReturn("en");
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.querySelectorAll")))
                .thenReturn(new ArrayList<>());
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.documentElement.outerHTML")))
                .thenReturn("<html><body>Test</body></html>");

        FailureContext failure = FailureContext.builder()
                .actionType(ActionType.CLICK)
                .exceptionType("NoSuchElementException")
                .exceptionMessage("Not found")
                .stepText("Click submit button")
                .build();

        UiSnapshot snapshot = snapshotBuilder.capture(failure);

        assertThat(snapshot.getDomSnapshot()).isPresent();
        assertThat(snapshot.getDomSnapshot().get()).isEqualTo("<html><body>Test</body></html>");
    }

    // ===== Test max elements limit =====

    @Test
    void captureAll_respectsMaxElementsLimit() {
        SnapshotConfig configWithLimit = new SnapshotConfig();
        configWithLimit.setMaxElements(2);
        snapshotBuilder = new SnapshotBuilder(mockDriver, configWithLimit);

        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com");
        when(mockDriver.getTitle()).thenReturn("Test");
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.documentElement.lang")))
                .thenReturn("en");

        // Create 5 mock elements but config limits to 2
        List<WebElement> mockElements = List.of(
                mockElement, mockElement, mockElement, mockElement, mockElement
        );
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.querySelectorAll")))
                .thenReturn(mockElements);

        setupMockElement(mockElement, "button", "test", "Click");

        UiSnapshot snapshot = snapshotBuilder.captureAll();

        // JavaScript will slice to 2, so we should get at most 2
        verify((JavascriptExecutor) mockDriver).executeScript(contains(".slice(0, 2)"));
    }

    // ===== Test constructor with null config =====

    @Test
    void constructor_withNullConfig_usesDefaults() {
        SnapshotBuilder builder = new SnapshotBuilder(mockDriver, null);

        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com");
        when(mockDriver.getTitle()).thenReturn("Test");
        when(((JavascriptExecutor) mockDriver).executeScript(anyString())).thenReturn("en");
        when(((JavascriptExecutor) mockDriver).executeScript(contains("document.querySelectorAll")))
                .thenReturn(new ArrayList<>());

        UiSnapshot snapshot = builder.captureAll();

        assertThat(snapshot).isNotNull();
    }

    @Test
    void constructor_withNullDriver_throwsException() {
        assertThatThrownBy(() -> new SnapshotBuilder(null, config))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("driver cannot be null");
    }

    // ===== Helper methods =====

    private void setupMockElement(WebElement element, String tagName, String id, String text) {
        when(element.getTagName()).thenReturn(tagName);
        when(element.getAttribute("id")).thenReturn(id);
        when(element.getAttribute("name")).thenReturn(null);
        when(element.getAttribute("class")).thenReturn(null);
        when(element.getAttribute("type")).thenReturn(null);
        when(element.getAttribute("value")).thenReturn(null);
        when(element.getAttribute("placeholder")).thenReturn(null);
        when(element.getAttribute("aria-label")).thenReturn(null);
        when(element.getAttribute("aria-labelledby")).thenReturn(null);
        when(element.getAttribute("aria-describedby")).thenReturn(null);
        when(element.getAttribute("role")).thenReturn(null);
        when(element.getAttribute("title")).thenReturn(null);
        when(element.getText()).thenReturn(text);
        when(element.isDisplayed()).thenReturn(true);
        when(element.isEnabled()).thenReturn(true);
        when(element.isSelected()).thenReturn(false);
        when(element.getRect()).thenReturn(new Rectangle(0, 0, 100, 30));
    }

    private void setupDetailedMockElement(WebElement element) {
        when(element.getTagName()).thenReturn("button");
        when(element.getAttribute("id")).thenReturn("submit-btn");
        when(element.getAttribute("name")).thenReturn("submit");
        when(element.getAttribute("class")).thenReturn("btn btn-primary");
        when(element.getAttribute("type")).thenReturn("submit");
        when(element.getAttribute("value")).thenReturn("Submit");
        when(element.getAttribute("placeholder")).thenReturn(null);
        when(element.getAttribute("aria-label")).thenReturn("Submit the form");
        when(element.getAttribute("aria-labelledby")).thenReturn(null);
        when(element.getAttribute("aria-describedby")).thenReturn("form-help");
        when(element.getAttribute("role")).thenReturn("button");
        when(element.getAttribute("title")).thenReturn("Submit this form");
        when(element.getText()).thenReturn("Submit Form");
        when(element.isDisplayed()).thenReturn(true);
        when(element.isEnabled()).thenReturn(true);
        when(element.isSelected()).thenReturn(false);
        when(element.getRect()).thenReturn(new Rectangle(10, 20, 120, 40));
    }
}
