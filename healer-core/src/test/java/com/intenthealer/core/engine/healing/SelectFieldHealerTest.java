package com.intenthealer.core.engine.healing;

import com.intenthealer.core.model.ElementCandidate;
import com.intenthealer.core.model.HealingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SelectFieldHealerTest {

    @Mock
    private WebDriver driver;

    @Mock
    private WebElement selectElement;

    @Mock
    private WebElement option1;

    @Mock
    private WebElement option2;

    @Mock
    private WebElement option3;

    @Mock
    private WebElement labelElement;

    private SelectFieldHealer healer;

    @BeforeEach
    void setUp() {
        healer = new SelectFieldHealer();
    }

    @Test
    void findSelectCandidates_matchesByLabel() {
        // Given
        setupMockSelect(selectElement, "country-select", "country", "Select Country");
        when(driver.findElements(By.tagName("select"))).thenReturn(List.of(selectElement));
        when(driver.findElements(By.cssSelector("label[for='country-select']"))).thenReturn(List.of(labelElement));
        when(labelElement.getText()).thenReturn("Country");

        HealingRequest request = HealingRequest.builder()
                .intentDescription("Select country from dropdown")
                .build();

        // When
        List<ElementCandidate> candidates = healer.findSelectCandidates(driver, request, null);

        // Then
        assertThat(candidates).isNotEmpty();
        assertThat(candidates.get(0).getLocator()).contains("country");
    }

    @Test
    void findSelectCandidates_matchesByAriaLabel() {
        // Given
        setupMockSelect(selectElement, "lang", "language", "Select Language");
        when(driver.findElements(By.tagName("select"))).thenReturn(List.of(selectElement));
        when(driver.findElements(By.cssSelector("label[for='lang']"))).thenReturn(List.of());

        HealingRequest request = HealingRequest.builder()
                .intentDescription("Choose language preference")
                .build();

        // When
        List<ElementCandidate> candidates = healer.findSelectCandidates(driver, request, null);

        // Then
        assertThat(candidates).isNotEmpty();
    }

    @Test
    void findMatchingOption_exactMatch() {
        // Given
        when(selectElement.getTagName()).thenReturn("select");
        when(selectElement.findElements(By.tagName("option"))).thenReturn(List.of(option1, option2, option3));

        when(option1.getText()).thenReturn("United States");
        when(option1.getAttribute("value")).thenReturn("US");

        when(option2.getText()).thenReturn("Canada");
        when(option2.getAttribute("value")).thenReturn("CA");

        when(option3.getText()).thenReturn("United Kingdom");
        when(option3.getAttribute("value")).thenReturn("UK");

        // When
        Optional<WebElement> result = healer.findMatchingOption(driver, selectElement, "Canada");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getText()).isEqualTo("Canada");
    }

    @Test
    void findMatchingOption_partialMatch() {
        // Given
        when(selectElement.getTagName()).thenReturn("select");
        when(selectElement.findElements(By.tagName("option"))).thenReturn(List.of(option1, option2));

        when(option1.getText()).thenReturn("United States of America");
        when(option1.getAttribute("value")).thenReturn("US");

        when(option2.getText()).thenReturn("Mexico");
        when(option2.getAttribute("value")).thenReturn("MX");

        // When
        Optional<WebElement> result = healer.findMatchingOption(driver, selectElement, "United States");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getText()).contains("United States");
    }

    @Test
    void findMatchingOption_matchByValue() {
        // Given
        when(selectElement.getTagName()).thenReturn("select");
        when(selectElement.findElements(By.tagName("option"))).thenReturn(List.of(option1));

        when(option1.getText()).thenReturn("Option Display Text");
        when(option1.getAttribute("value")).thenReturn("option_value");

        // When
        Optional<WebElement> result = healer.findMatchingOption(driver, selectElement, "option_value");

        // Then
        assertThat(result).isPresent();
    }

    @Test
    void findMatchingOption_fuzzyMatch() {
        // Given
        when(selectElement.getTagName()).thenReturn("select");
        when(selectElement.findElements(By.tagName("option"))).thenReturn(List.of(option1, option2));

        when(option1.getText()).thenReturn("California");
        when(option1.getAttribute("value")).thenReturn("CA");

        when(option2.getText()).thenReturn("Colorado");
        when(option2.getAttribute("value")).thenReturn("CO");

        // When - typo "Califronia"
        Optional<WebElement> result = healer.findMatchingOption(driver, selectElement, "Califronia");

        // Then - should match California via fuzzy matching
        assertThat(result).isPresent();
        assertThat(result.get().getText()).isEqualTo("California");
    }

    @Test
    void findMatchingOption_noMatch() {
        // Given
        when(selectElement.getTagName()).thenReturn("select");
        when(selectElement.findElements(By.tagName("option"))).thenReturn(List.of(option1));

        when(option1.getText()).thenReturn("Apple");
        when(option1.getAttribute("value")).thenReturn("apple");

        // When
        Optional<WebElement> result = healer.findMatchingOption(driver, selectElement, "Banana");

        // Then
        assertThat(result).isEmpty();
    }

    private void setupMockSelect(WebElement element, String id, String name, String ariaLabel) {
        when(element.getAttribute("id")).thenReturn(id);
        when(element.getAttribute("name")).thenReturn(name);
        when(element.getAttribute("aria-label")).thenReturn(ariaLabel);
        when(element.getAttribute("class")).thenReturn("form-select");
        when(element.getTagName()).thenReturn("select");
        when(element.isDisplayed()).thenReturn(true);
        when(element.isEnabled()).thenReturn(true);
        when(element.findElements(By.tagName("option"))).thenReturn(List.of());
    }
}
