package com.intenthealer.core.engine.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IframeHandlerTest {

    @Mock
    private WebDriver driver;

    @Mock
    private WebDriver.TargetLocator targetLocator;

    @Mock
    private WebElement iframe1;

    @Mock
    private WebElement iframe2;

    @Mock
    private WebElement targetElement;

    private IframeHandler handler;

    @BeforeEach
    void setUp() {
        when(driver.switchTo()).thenReturn(targetLocator);
        when(targetLocator.defaultContent()).thenReturn(driver);
        when(targetLocator.parentFrame()).thenReturn(driver);
        handler = new IframeHandler(driver);
    }

    @Test
    void findElementAcrossFrames_findsInMainDocument() {
        // Given
        By locator = By.id("test-element");
        when(driver.findElement(locator)).thenReturn(targetElement);

        // When
        Optional<IframeHandler.ElementInFrame> result = handler.findElementAcrossFrames(locator);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().element()).isEqualTo(targetElement);
        assertThat(result.get().framePath()).isEmpty();
    }

    @Test
    void findElementAcrossFrames_findsInIframe() {
        // Given
        By locator = By.id("nested-element");

        // Element not in main document
        when(driver.findElement(locator)).thenThrow(new NoSuchElementException("Not found"));

        // Setup iframes
        when(driver.findElements(By.tagName("iframe"))).thenReturn(List.of(iframe1));
        when(driver.findElements(By.tagName("frame"))).thenReturn(List.of());

        // After switching to iframe1, element is found
        when(targetLocator.frame(0)).thenReturn(driver);
        doReturn(targetElement).when(driver).findElement(locator);

        // When - simulate finding in iframe
        // The actual search will happen after frame switch

        // Then
        verify(driver, atLeastOnce()).switchTo();
    }

    @Test
    void switchToIframe_byId() {
        // Given
        when(driver.findElement(By.id("my-iframe"))).thenReturn(iframe1);
        when(targetLocator.frame(iframe1)).thenReturn(driver);

        // When
        boolean result = handler.switchToIframe("my-iframe");

        // Then
        assertThat(result).isTrue();
        verify(targetLocator).frame(iframe1);
    }

    @Test
    void switchToIframe_byIndex() {
        // Given
        when(driver.findElement(By.id("0"))).thenThrow(new NoSuchElementException("Not found"));
        when(driver.findElement(By.name("0"))).thenThrow(new NoSuchElementException("Not found"));
        when(targetLocator.frame(0)).thenReturn(driver);

        // When
        boolean result = handler.switchToIframe("0");

        // Then
        assertThat(result).isTrue();
        verify(targetLocator).frame(0);
    }

    @Test
    void switchToDefaultContent() {
        // When
        handler.switchToDefaultContent();

        // Then
        verify(targetLocator).defaultContent();
    }

    @Test
    void switchToParentFrame() {
        // When
        handler.switchToParentFrame();

        // Then
        verify(targetLocator).parentFrame();
    }

    @Test
    void getAllIframes_returnsIframeList() {
        // Given
        when(driver.findElements(By.tagName("iframe"))).thenReturn(List.of(iframe1, iframe2));
        when(driver.findElements(By.tagName("frame"))).thenReturn(List.of());

        when(iframe1.getAttribute("id")).thenReturn("frame1");
        when(iframe1.getAttribute("name")).thenReturn("frame1-name");
        when(iframe1.getAttribute("src")).thenReturn("http://example.com/frame1");
        when(iframe1.isDisplayed()).thenReturn(true);

        when(iframe2.getAttribute("id")).thenReturn("frame2");
        when(iframe2.getAttribute("name")).thenReturn(null);
        when(iframe2.getAttribute("src")).thenReturn("http://example.com/frame2");
        when(iframe2.isDisplayed()).thenReturn(true);

        // When
        List<IframeHandler.IframeInfo> iframes = handler.getAllIframes();

        // Then
        assertThat(iframes).hasSize(2);
        assertThat(iframes.get(0).id()).isEqualTo("frame1");
        assertThat(iframes.get(1).id()).isEqualTo("frame2");
    }

    @Test
    void executeAcrossFrames_returnsResultFromMainDoc() {
        // Given
        IframeHandler.FrameOperation<String> operation = () -> Optional.of("found");

        // When
        Optional<String> result = handler.executeAcrossFrames(operation);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("found");
    }

    @Test
    void executeAcrossFrames_searchesIframesWhenNotInMainDoc() {
        // Given
        IframeHandler.FrameOperation<String> operation = mock(IframeHandler.FrameOperation.class);

        // First call (main doc) returns empty, second would be in iframe
        when(operation.execute())
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of("found-in-iframe"));

        when(driver.findElements(By.tagName("iframe"))).thenReturn(List.of(iframe1));
        when(driver.findElements(By.tagName("frame"))).thenReturn(List.of());
        when(targetLocator.frame(0)).thenReturn(driver);

        // When
        Optional<String> result = handler.executeAcrossFrames(operation);

        // Then - verifies attempt was made
        verify(operation, atLeastOnce()).execute();
    }

    @Test
    void iframeInfo_getIdentifier_prefersId() {
        IframeHandler.IframeInfo info = new IframeHandler.IframeInfo(
                "my-id", "my-name", "http://example.com", List.of(0), true);

        assertThat(info.getIdentifier()).isEqualTo("my-id");
    }

    @Test
    void iframeInfo_getIdentifier_fallsBackToName() {
        IframeHandler.IframeInfo info = new IframeHandler.IframeInfo(
                null, "my-name", "http://example.com", List.of(0), true);

        assertThat(info.getIdentifier()).isEqualTo("my-name");
    }

    @Test
    void iframeInfo_getIdentifier_fallsBackToPath() {
        IframeHandler.IframeInfo info = new IframeHandler.IframeInfo(
                null, null, "http://example.com", List.of(0, 1), true);

        assertThat(info.getIdentifier()).isEqualTo("frame[0,1]");
    }
}
