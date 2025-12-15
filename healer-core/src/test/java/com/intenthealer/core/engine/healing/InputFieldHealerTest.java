package com.intenthealer.core.engine.healing;

import com.intenthealer.core.model.ElementCandidate;
import com.intenthealer.core.model.HealingRequest;
import com.intenthealer.core.model.UiSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InputFieldHealerTest {

    @Mock
    private WebDriver driver;

    @Mock
    private WebElement emailInput;

    @Mock
    private WebElement passwordInput;

    @Mock
    private WebElement searchInput;

    @Mock
    private WebElement labelElement;

    private InputFieldHealer healer;

    @BeforeEach
    void setUp() {
        healer = new InputFieldHealer();
    }

    @Test
    void findInputCandidates_matchesByPlaceholder() {
        // Given
        setupMockInput(emailInput, "email", "email-field", "Enter your email", null, "text");
        when(driver.findElements(By.cssSelector("input"))).thenReturn(List.of(emailInput));
        when(driver.findElements(By.tagName("textarea"))).thenReturn(List.of());
        when(driver.findElements(By.cssSelector("[contenteditable='true']"))).thenReturn(List.of());

        HealingRequest request = HealingRequest.builder()
                .intentDescription("Enter email address")
                .build();

        // When
        List<ElementCandidate> candidates = healer.findInputCandidates(driver, request, null);

        // Then
        assertThat(candidates).isNotEmpty();
        assertThat(candidates.get(0).getLocator()).contains("email");
    }

    @Test
    void findInputCandidates_matchesByLabel() {
        // Given
        setupMockInput(emailInput, "text", "username", null, "Username", "text");
        when(driver.findElements(By.cssSelector("input"))).thenReturn(List.of(emailInput));
        when(driver.findElements(By.tagName("textarea"))).thenReturn(List.of());
        when(driver.findElements(By.cssSelector("[contenteditable='true']"))).thenReturn(List.of());

        when(driver.findElements(By.cssSelector("label[for='username']"))).thenReturn(List.of(labelElement));
        when(labelElement.getText()).thenReturn("Username");

        HealingRequest request = HealingRequest.builder()
                .intentDescription("Enter username in the field")
                .build();

        // When
        List<ElementCandidate> candidates = healer.findInputCandidates(driver, request, null);

        // Then
        assertThat(candidates).isNotEmpty();
    }

    @Test
    void findInputCandidates_matchesByType() {
        // Given
        setupMockInput(passwordInput, "password", "pwd", null, null, "password");
        when(driver.findElements(By.cssSelector("input"))).thenReturn(List.of(passwordInput));
        when(driver.findElements(By.tagName("textarea"))).thenReturn(List.of());
        when(driver.findElements(By.cssSelector("[contenteditable='true']"))).thenReturn(List.of());

        HealingRequest request = HealingRequest.builder()
                .intentDescription("Enter password")
                .build();

        // When
        List<ElementCandidate> candidates = healer.findInputCandidates(driver, request, null);

        // Then
        assertThat(candidates).isNotEmpty();
    }

    @Test
    void findInputCandidates_limitsResults() {
        // Given - more than 5 inputs
        List<WebElement> manyInputs = Arrays.asList(
                createMockInput("input1", "text", "placeholder1"),
                createMockInput("input2", "text", "placeholder2"),
                createMockInput("input3", "text", "placeholder3"),
                createMockInput("input4", "text", "placeholder4"),
                createMockInput("input5", "text", "placeholder5"),
                createMockInput("input6", "text", "placeholder6"),
                createMockInput("input7", "text", "placeholder7")
        );

        when(driver.findElements(By.cssSelector("input"))).thenReturn(manyInputs);
        when(driver.findElements(By.tagName("textarea"))).thenReturn(List.of());
        when(driver.findElements(By.cssSelector("[contenteditable='true']"))).thenReturn(List.of());

        HealingRequest request = HealingRequest.builder()
                .intentDescription("Enter text in placeholder field")
                .build();

        // When
        List<ElementCandidate> candidates = healer.findInputCandidates(driver, request, null);

        // Then
        assertThat(candidates).hasSizeLessThanOrEqualTo(5);
    }

    private void setupMockInput(WebElement element, String type, String id,
                                 String placeholder, String ariaLabel, String typeAttr) {
        when(element.getAttribute("type")).thenReturn(typeAttr);
        when(element.getAttribute("id")).thenReturn(id);
        when(element.getAttribute("name")).thenReturn(id);
        when(element.getAttribute("placeholder")).thenReturn(placeholder);
        when(element.getAttribute("aria-label")).thenReturn(ariaLabel);
        when(element.getAttribute("class")).thenReturn("form-input");
        when(element.getTagName()).thenReturn("input");
        when(element.isDisplayed()).thenReturn(true);
        when(element.isEnabled()).thenReturn(true);
    }

    private WebElement createMockInput(String id, String type, String placeholder) {
        WebElement mock = org.mockito.Mockito.mock(WebElement.class);
        when(mock.getAttribute("type")).thenReturn(type);
        when(mock.getAttribute("id")).thenReturn(id);
        when(mock.getAttribute("name")).thenReturn(id);
        when(mock.getAttribute("placeholder")).thenReturn(placeholder);
        when(mock.getAttribute("class")).thenReturn("form-input");
        when(mock.getTagName()).thenReturn("input");
        when(mock.isDisplayed()).thenReturn(true);
        when(mock.isEnabled()).thenReturn(true);
        return mock;
    }
}
