package com.intenthealer.junit;

import com.intenthealer.core.config.HealerConfig;
import com.intenthealer.core.engine.HealingEngine;
import com.intenthealer.core.engine.patch.ValidatedHealRegistry;
import com.intenthealer.report.ReportGenerator;
import com.intenthealer.report.model.HealReport;
import com.intenthealer.selenium.driver.HealingWebDriver;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openqa.selenium.WebDriver;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * Tests for HealerExtension JUnit 5 integration.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HealerExtension")
class HealerExtensionTest {

    private HealerExtension healerExtension;

    @Mock
    private ExtensionContext extensionContext;

    @Mock
    private ExtensionContext.Store store;

    @Mock
    private WebDriver mockWebDriver;

    @BeforeEach
    void setUp() {
        healerExtension = new HealerExtension();
        // Default store mock setup
        lenient().when(extensionContext.getStore(any())).thenReturn(store);
    }

    @Nested
    @DisplayName("beforeAll()")
    class BeforeAllTests {

        @Test
        @DisplayName("should initialize with default config when no config file exists")
        void initializesWithDefaultConfig() throws Exception {
            // Given
            when(extensionContext.getRequiredTestClass()).thenReturn((Class) TestClass.class);

            // When / Then - should not throw
            assertThatCode(() -> healerExtension.beforeAll(extensionContext))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should store config in extension context")
        void storesConfigInContext() throws Exception {
            // Given
            when(extensionContext.getRequiredTestClass()).thenReturn((Class) TestClass.class);

            // When
            healerExtension.beforeAll(extensionContext);

            // Then
            verify(store).put(eq("config"), any(HealerConfig.class));
        }

        @Test
        @DisplayName("should store engine in context when enabled")
        void storesEngineWhenEnabled() throws Exception {
            // Given
            when(extensionContext.getRequiredTestClass()).thenReturn((Class) TestClass.class);

            // When
            healerExtension.beforeAll(extensionContext);

            // Then
            verify(store).put(eq("engine"), any(HealingEngine.class));
        }

        @Test
        @DisplayName("should store report generator in context")
        void storesReportGenerator() throws Exception {
            // Given
            when(extensionContext.getRequiredTestClass()).thenReturn((Class) TestClass.class);

            // When
            healerExtension.beforeAll(extensionContext);

            // Then
            verify(store).put(eq("generator"), any(ReportGenerator.class));
        }

        @Test
        @DisplayName("should store registry in context")
        void storesRegistry() throws Exception {
            // Given
            when(extensionContext.getRequiredTestClass()).thenReturn((Class) TestClass.class);

            // When
            healerExtension.beforeAll(extensionContext);

            // Then
            verify(store).put(eq("registry"), any(ValidatedHealRegistry.class));
        }
    }

    @Nested
    @DisplayName("beforeEach()")
    class BeforeEachTests {

        @Test
        @DisplayName("should skip when config is null")
        void skipsWhenConfigNull() throws Exception {
            // Given
            when(store.get("config", HealerConfig.class)).thenReturn(null);

            // When / Then - should complete without creating report
            assertThatCode(() -> healerExtension.beforeEach(extensionContext))
                    .doesNotThrowAnyException();

            verify(store, never()).put(eq("report"), any());
        }

        @Test
        @DisplayName("should skip when healing disabled")
        void skipsWhenDisabled() throws Exception {
            // Given
            HealerConfig config = new HealerConfig();
            config.setEnabled(false);
            when(store.get("config", HealerConfig.class)).thenReturn(config);

            // When / Then
            assertThatCode(() -> healerExtension.beforeEach(extensionContext))
                    .doesNotThrowAnyException();

            verify(store, never()).put(eq("report"), any());
        }

        @Test
        @DisplayName("should create report when enabled")
        void createsReportWhenEnabled() throws Exception {
            // Given
            HealerConfig config = new HealerConfig();
            config.setEnabled(true);
            config.applyDefaults();

            when(store.get("config", HealerConfig.class)).thenReturn(config);
            when(extensionContext.getTestMethod()).thenReturn(Optional.of(
                    TestClass.class.getDeclaredMethod("testMethod")));
            when(extensionContext.getRequiredTestClass()).thenReturn((Class) TestClass.class);
            when(extensionContext.getDisplayName()).thenReturn("Test Display Name");
            when(extensionContext.getTestInstance()).thenReturn(Optional.empty());

            // When
            healerExtension.beforeEach(extensionContext);

            // Then
            verify(store).put(eq("report"), any(HealReport.class));
        }

        @Test
        @DisplayName("should skip when @HealDisabled annotation on method")
        void skipsWhenHealDisabledOnMethod() throws Exception {
            // Given
            HealerConfig config = new HealerConfig();
            config.setEnabled(true);
            config.applyDefaults();

            when(store.get("config", HealerConfig.class)).thenReturn(config);
            when(extensionContext.getTestMethod()).thenReturn(Optional.of(
                    DisabledMethodTestClass.class.getMethod("disabledMethod")));
            when(extensionContext.getRequiredTestClass()).thenReturn((Class) DisabledMethodTestClass.class);

            // When
            healerExtension.beforeEach(extensionContext);

            // Then - should not create report
            verify(store, never()).put(eq("report"), any());
        }

        @Test
        @DisplayName("should skip when @HealDisabled annotation on class")
        void skipsWhenHealDisabledOnClass() throws Exception {
            // Given
            HealerConfig config = new HealerConfig();
            config.setEnabled(true);
            config.applyDefaults();

            when(store.get("config", HealerConfig.class)).thenReturn(config);
            when(extensionContext.getTestMethod()).thenReturn(Optional.empty());
            when(extensionContext.getRequiredTestClass()).thenReturn((Class) DisabledClassTestClass.class);

            // When
            healerExtension.beforeEach(extensionContext);

            // Then - should not create report
            verify(store, never()).put(eq("report"), any());
        }
    }

    @Nested
    @DisplayName("afterEach()")
    class AfterEachTests {

        @Test
        @DisplayName("should set end time on report")
        void setsEndTimeOnReport() throws Exception {
            // Given
            HealReport report = new HealReport();
            report.setStartTime(Instant.now().minusSeconds(5));
            when(store.get("report", HealReport.class)).thenReturn(report);

            // When
            healerExtension.afterEach(extensionContext);

            // Then
            assertThat(report.getEndTime()).isNotNull();
        }

        @Test
        @DisplayName("should handle null report gracefully")
        void handlesNullReport() throws Exception {
            // Given
            when(store.get("report", HealReport.class)).thenReturn(null);

            // When / Then
            assertThatCode(() -> healerExtension.afterEach(extensionContext))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("testSuccessful()")
    class TestSuccessfulTests {

        @Test
        @DisplayName("should finalize report with PASSED status")
        void finalizesReportAsPassed() {
            // Given
            HealReport report = new HealReport();
            HealerConfig config = new HealerConfig();
            config.applyDefaults();
            config.getReport().setJsonEnabled(false);
            config.getReport().setHtmlEnabled(false);

            when(store.get("report", HealReport.class)).thenReturn(report);
            when(store.get("config", HealerConfig.class)).thenReturn(config);

            // When
            healerExtension.testSuccessful(extensionContext);

            // Then
            assertThat(report.getTestStatus()).isEqualTo("PASSED");
        }
    }

    @Nested
    @DisplayName("testFailed()")
    class TestFailedTests {

        @Test
        @DisplayName("should finalize report with FAILED status")
        void finalizesReportAsFailed() {
            // Given
            HealReport report = new HealReport();
            HealerConfig config = new HealerConfig();
            config.applyDefaults();
            config.getReport().setJsonEnabled(false);
            config.getReport().setHtmlEnabled(false);

            when(store.get("report", HealReport.class)).thenReturn(report);
            when(store.get("config", HealerConfig.class)).thenReturn(config);

            // When
            healerExtension.testFailed(extensionContext, new RuntimeException("Test failed"));

            // Then
            assertThat(report.getTestStatus()).isEqualTo("FAILED");
        }

        @Test
        @DisplayName("should discard pending heals on failure")
        void discardsPendingHealsOnFailure() {
            // Given
            HealReport report = new HealReport();
            HealerConfig config = new HealerConfig();
            config.applyDefaults();
            config.getReport().setJsonEnabled(false);
            config.getReport().setHtmlEnabled(false);
            ValidatedHealRegistry registry = mock(ValidatedHealRegistry.class);

            when(store.get(eq("report"), eq(HealReport.class))).thenReturn(report);
            when(store.get(eq("config"), eq(HealerConfig.class))).thenReturn(config);
            when(store.get(eq("registry"), eq(ValidatedHealRegistry.class))).thenReturn(registry);
            when(store.get(eq("generator"), eq(ReportGenerator.class))).thenReturn(null);
            when(extensionContext.getUniqueId()).thenReturn("test-123");

            // When
            healerExtension.testFailed(extensionContext, new RuntimeException("Test failed"));

            // Then
            verify(registry).discardPending("test-123");
        }
    }

    @Nested
    @DisplayName("testDisabled()")
    class TestDisabledTests {

        @Test
        @DisplayName("should finalize report with SKIPPED status")
        void finalizesReportAsSkipped() {
            // Given
            HealReport report = new HealReport();
            HealerConfig config = new HealerConfig();
            config.applyDefaults();
            config.getReport().setJsonEnabled(false);
            config.getReport().setHtmlEnabled(false);

            when(store.get("report", HealReport.class)).thenReturn(report);
            when(store.get("config", HealerConfig.class)).thenReturn(config);

            // When
            healerExtension.testDisabled(extensionContext, Optional.of("disabled reason"));

            // Then
            assertThat(report.getTestStatus()).isEqualTo("SKIPPED");
        }
    }

    @Nested
    @DisplayName("testAborted()")
    class TestAbortedTests {

        @Test
        @DisplayName("should finalize report with ABORTED status")
        void finalizesReportAsAborted() {
            // Given
            HealReport report = new HealReport();
            HealerConfig config = new HealerConfig();
            config.applyDefaults();
            config.getReport().setJsonEnabled(false);
            config.getReport().setHtmlEnabled(false);

            when(store.get("report", HealReport.class)).thenReturn(report);
            when(store.get("config", HealerConfig.class)).thenReturn(config);

            // When
            healerExtension.testAborted(extensionContext, new RuntimeException("Test aborted"));

            // Then
            assertThat(report.getTestStatus()).isEqualTo("ABORTED");
        }
    }

    @Nested
    @DisplayName("WebDriver Field Wrapping")
    class WebDriverFieldWrappingTests {

        @Test
        @DisplayName("should wrap WebDriver field with HealingWebDriver")
        void wrapsWebDriverField() throws Exception {
            // Given
            HealerConfig config = new HealerConfig();
            config.setEnabled(true);
            config.applyDefaults();

            HealingEngine engine = new HealingEngine(config);
            TestClassWithDriver testInstance = new TestClassWithDriver();
            testInstance.driver = mockWebDriver;

            when(store.get("config", HealerConfig.class)).thenReturn(config);
            when(store.get("engine", HealingEngine.class)).thenReturn(engine);
            when(extensionContext.getTestMethod()).thenReturn(Optional.empty());
            when(extensionContext.getRequiredTestClass()).thenReturn((Class) TestClassWithDriver.class);
            when(extensionContext.getDisplayName()).thenReturn("Test");
            when(extensionContext.getTestInstance()).thenReturn(Optional.of(testInstance));

            // When
            healerExtension.beforeEach(extensionContext);

            // Then
            assertThat(testInstance.driver).isInstanceOf(HealingWebDriver.class);
        }

        @Test
        @DisplayName("should not re-wrap already wrapped WebDriver")
        void doesNotRewrapHealingWebDriver() throws Exception {
            // Given
            HealerConfig config = new HealerConfig();
            config.setEnabled(true);
            config.applyDefaults();

            HealingEngine engine = new HealingEngine(config);
            HealingWebDriver existingHealer = new HealingWebDriver(mockWebDriver, engine, config);
            TestClassWithDriver testInstance = new TestClassWithDriver();
            testInstance.driver = existingHealer;

            when(store.get("config", HealerConfig.class)).thenReturn(config);
            when(store.get("engine", HealingEngine.class)).thenReturn(engine);
            when(extensionContext.getTestMethod()).thenReturn(Optional.empty());
            when(extensionContext.getRequiredTestClass()).thenReturn((Class) TestClassWithDriver.class);
            when(extensionContext.getDisplayName()).thenReturn("Test");
            when(extensionContext.getTestInstance()).thenReturn(Optional.of(testInstance));

            // When
            healerExtension.beforeEach(extensionContext);

            // Then - should still be the same instance
            assertThat(testInstance.driver).isSameAs(existingHealer);
        }

        @Test
        @DisplayName("should handle null WebDriver field gracefully")
        void handlesNullWebDriverField() throws Exception {
            // Given
            HealerConfig config = new HealerConfig();
            config.setEnabled(true);
            config.applyDefaults();

            HealingEngine engine = new HealingEngine(config);
            TestClassWithDriver testInstance = new TestClassWithDriver();
            testInstance.driver = null;

            when(store.get("config", HealerConfig.class)).thenReturn(config);
            when(store.get("engine", HealingEngine.class)).thenReturn(engine);
            when(extensionContext.getTestMethod()).thenReturn(Optional.empty());
            when(extensionContext.getRequiredTestClass()).thenReturn((Class) TestClassWithDriver.class);
            when(extensionContext.getDisplayName()).thenReturn("Test");
            when(extensionContext.getTestInstance()).thenReturn(Optional.of(testInstance));

            // When / Then - should not throw
            assertThatCode(() -> healerExtension.beforeEach(extensionContext))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should wrap WebDriver field in parent class")
        void wrapsWebDriverFieldInParentClass() throws Exception {
            // Given
            HealerConfig config = new HealerConfig();
            config.setEnabled(true);
            config.applyDefaults();

            HealingEngine engine = new HealingEngine(config);
            ChildTestClass testInstance = new ChildTestClass();
            testInstance.driver = mockWebDriver;

            when(store.get("config", HealerConfig.class)).thenReturn(config);
            when(store.get("engine", HealingEngine.class)).thenReturn(engine);
            when(extensionContext.getTestMethod()).thenReturn(Optional.empty());
            when(extensionContext.getRequiredTestClass()).thenReturn((Class) ChildTestClass.class);
            when(extensionContext.getDisplayName()).thenReturn("Test");
            when(extensionContext.getTestInstance()).thenReturn(Optional.of(testInstance));

            // When
            healerExtension.beforeEach(extensionContext);

            // Then
            assertThat(testInstance.driver).isInstanceOf(HealingWebDriver.class);
        }
    }

    @Nested
    @DisplayName("afterAll()")
    class AfterAllTests {

        @Test
        @DisplayName("should generate class report when enabled")
        void generatesClassReport() throws Exception {
            // Given
            HealerConfig config = new HealerConfig();
            config.applyDefaults();
            config.getReport().setJsonEnabled(true);
            config.getReport().setHtmlEnabled(true);

            ReportGenerator generator = mock(ReportGenerator.class);

            when(store.get("config", HealerConfig.class)).thenReturn(config);
            when(store.get("generator", ReportGenerator.class)).thenReturn(generator);
            when(extensionContext.getRequiredTestClass()).thenReturn((Class) TestClass.class);

            // When
            healerExtension.afterAll(extensionContext);

            // Then
            verify(generator).startReport();
            verify(generator).finishReport();
        }

        @Test
        @DisplayName("should skip report generation when disabled")
        void skipsReportWhenDisabled() throws Exception {
            // Given
            HealerConfig config = new HealerConfig();
            config.applyDefaults();
            config.getReport().setJsonEnabled(false);
            config.getReport().setHtmlEnabled(false);

            ReportGenerator generator = mock(ReportGenerator.class);

            when(store.get("config", HealerConfig.class)).thenReturn(config);
            when(store.get("generator", ReportGenerator.class)).thenReturn(generator);

            // When
            healerExtension.afterAll(extensionContext);

            // Then - generator is fetched but startReport() should not be called
            verify(generator, never()).startReport();
            verify(generator, never()).finishReport();
        }
    }

    @Nested
    @DisplayName("@HealEnabled Annotation")
    class HealEnabledAnnotationTests {

        @Test
        @DisplayName("should recognize @HealEnabled on method")
        void recognizesHealEnabledOnMethod() throws Exception {
            // Just verify the annotation exists and has correct values
            Method method = AnnotatedTestClass.class.getMethod("enabledTest");
            HealEnabled annotation = method.getAnnotation(HealEnabled.class);

            assertThat(annotation).isNotNull();
            assertThat(annotation.intent()).isEqualTo("Test login flow");
            assertThat(annotation.minConfidence()).isEqualTo(0.9);
        }

        @Test
        @DisplayName("should recognize @HealEnabled with all options")
        void recognizesHealEnabledWithAllOptions() throws Exception {
            Method method = AnnotatedTestClass.class.getMethod("fullyAnnotatedTest");
            HealEnabled annotation = method.getAnnotation(HealEnabled.class);

            assertThat(annotation).isNotNull();
            assertThat(annotation.intent()).isEqualTo("Complete checkout");
            assertThat(annotation.expectedOutcome()).isEqualTo("Order confirmed");
            assertThat(annotation.invariants()).contains("cart not empty");
            assertThat(annotation.mode()).isEqualTo("AUTO_SAFE");
            assertThat(annotation.maxAttempts()).isEqualTo(5);
            assertThat(annotation.tags()).contains("smoke", "checkout");
        }
    }

    @Nested
    @DisplayName("@HealDisabled Annotation")
    class HealDisabledAnnotationTests {

        @Test
        @DisplayName("should recognize @HealDisabled with reason")
        void recognizesHealDisabledWithReason() throws Exception {
            Method method = DisabledMethodTestClass.class.getMethod("disabledMethod");
            HealDisabled annotation = method.getAnnotation(HealDisabled.class);

            assertThat(annotation).isNotNull();
            assertThat(annotation.reason()).isEqualTo("Testing error handling");
        }
    }

    // Test helper classes

    static class TestClass {
        public void testMethod() {
        }
    }

    static class TestClassWithDriver {
        WebDriver driver;
    }

    static class ChildTestClass extends TestClassWithDriver {
    }

    static class DisabledMethodTestClass {
        @HealDisabled(reason = "Testing error handling")
        public void disabledMethod() {
        }
    }

    @HealDisabled
    static class DisabledClassTestClass {
    }

    static class AnnotatedTestClass {
        @HealEnabled(intent = "Test login flow", minConfidence = 0.9)
        public void enabledTest() {
        }

        @HealEnabled(
                intent = "Complete checkout",
                expectedOutcome = "Order confirmed",
                invariants = {"cart not empty"},
                mode = "AUTO_SAFE",
                minConfidence = 0.85,
                maxAttempts = 5,
                tags = {"smoke", "checkout"}
        )
        public void fullyAnnotatedTest() {
        }
    }
}
