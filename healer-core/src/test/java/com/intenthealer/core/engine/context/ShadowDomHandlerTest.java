package com.intenthealer.core.engine.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShadowDomHandlerTest {

    @Mock
    private WebDriver driver;

    @Mock
    private JavascriptExecutor jsExecutor;

    @Mock
    private WebElement shadowHost;

    @Mock
    private SearchContext shadowRoot;

    @Mock
    private WebElement targetElement;

    private ShadowDomHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ShadowDomHandler(driver);
    }

    @Test
    void findElementAcrossShadowRoots_findsInMainDocument() {
        // Given
        By locator = By.id("main-element");
        when(driver.findElement(locator)).thenReturn(targetElement);

        // When
        Optional<ShadowDomHandler.ElementInShadow> result = handler.findElementAcrossShadowRoots(locator);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().element()).isEqualTo(targetElement);
        assertThat(result.get().shadowPath()).isEmpty();
    }

    @Test
    void findElementAcrossShadowRoots_searchesShadowRoots() {
        // Given
        By locator = By.id("shadow-element");

        // Not in main document
        when(driver.findElement(locator)).thenThrow(new NoSuchElementException("Not found"));

        // When
        Optional<ShadowDomHandler.ElementInShadow> result = handler.findElementAcrossShadowRoots(locator);

        // Then - should have attempted search
        verify(driver).findElement(locator);
    }

    @Test
    void findByShadowPath_simpleCase() {
        // Given
        String shadowPath = "div.container";
        when(driver.findElement(By.cssSelector("div.container"))).thenReturn(targetElement);

        // When
        Optional<WebElement> result = handler.findByShadowPath(shadowPath);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(targetElement);
    }

    @Test
    void findByShadowPath_withShadowPiercing() {
        // Given
        String shadowPath = "my-component >> button.action";

        // First element is shadow host
        when(driver.findElement(By.cssSelector("my-component"))).thenReturn(shadowHost);
        when(shadowHost.getShadowRoot()).thenReturn(shadowRoot);
        when(shadowRoot.findElement(By.cssSelector("button.action"))).thenReturn(targetElement);

        // When
        Optional<WebElement> result = handler.findByShadowPath(shadowPath);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(targetElement);
    }

    @Test
    void findByShadowPath_notFound() {
        // Given
        String shadowPath = "nonexistent";
        when(driver.findElement(By.cssSelector("nonexistent"))).thenThrow(new NoSuchElementException("Not found"));

        // When
        Optional<WebElement> result = handler.findByShadowPath(shadowPath);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void executeAcrossShadowRoots_mainDocSuccess() {
        // Given
        ShadowDomHandler.ShadowOperation<String> operation = context -> Optional.of("main-result");

        // When
        Optional<String> result = handler.executeAcrossShadowRoots(operation);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("main-result");
    }

    @Test
    void buildShadowPiercingLocator_emptyPath() {
        // Given
        when(targetElement.getAttribute("id")).thenReturn("target-id");

        // When
        Optional<String> locator = handler.buildShadowPiercingLocator(targetElement, List.of());

        // Then
        assertThat(locator).isPresent();
        assertThat(locator.get()).isEqualTo("#target-id");
    }

    @Test
    void buildShadowPiercingLocator_withPath() {
        // Given
        when(shadowHost.getAttribute("id")).thenReturn("host-id");
        when(shadowHost.getAttribute("class")).thenReturn(null);
        when(shadowHost.getTagName()).thenReturn("custom-element");

        when(targetElement.getAttribute("id")).thenReturn("target-id");

        // When
        Optional<String> locator = handler.buildShadowPiercingLocator(targetElement, List.of(shadowHost));

        // Then
        assertThat(locator).isPresent();
        assertThat(locator.get()).isEqualTo("#host-id >> #target-id");
    }

    @Test
    void shadowHostInfo_getSelector_prefersId() {
        ShadowDomHandler.ShadowHostInfo info = new ShadowDomHandler.ShadowHostInfo(
                "custom-element", "my-id", "my-class other-class", List.of(), 0);

        assertThat(info.getSelector()).isEqualTo("#my-id");
    }

    @Test
    void shadowHostInfo_getSelector_usesClassIfNoId() {
        ShadowDomHandler.ShadowHostInfo info = new ShadowDomHandler.ShadowHostInfo(
                "custom-element", null, "my-class other-class", List.of(), 0);

        assertThat(info.getSelector()).isEqualTo("custom-element.my-class");
    }

    @Test
    void shadowHostInfo_getSelector_usesTagOnly() {
        ShadowDomHandler.ShadowHostInfo info = new ShadowDomHandler.ShadowHostInfo(
                "custom-element", null, null, List.of(), 0);

        assertThat(info.getSelector()).isEqualTo("custom-element");
    }

    @Test
    void elementInShadow_record() {
        ShadowDomHandler.ElementInShadow eis = new ShadowDomHandler.ElementInShadow(
                targetElement, List.of(shadowHost));

        assertThat(eis.element()).isEqualTo(targetElement);
        assertThat(eis.shadowPath()).hasSize(1);
        assertThat(eis.shadowPath().get(0)).isEqualTo(shadowHost);
    }
}
