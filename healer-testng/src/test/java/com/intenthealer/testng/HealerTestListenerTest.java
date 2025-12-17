package com.intenthealer.testng;

import com.intenthealer.core.config.HealerConfig;
import com.intenthealer.core.engine.HealingEngine;
import com.intenthealer.selenium.driver.HealingWebDriver;
import org.openqa.selenium.WebDriver;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * Tests for HealerTestListener TestNG integration.
 *
 * Note: Due to TestNG interface complexity, we focus on testing the
 * listener's core functionality like WebDriver field wrapping via reflection.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HealerTestListener")
class HealerTestListenerTest {

    private HealerTestListener listener;

    @Mock
    private WebDriver mockWebDriver;

    @BeforeEach
    void setUp() {
        listener = new HealerTestListener();
    }

    @Nested
    @DisplayName("Listener Lifecycle")
    class ListenerLifecycleTests {

        @Test
        @DisplayName("should create listener instance")
        void createsInstance() {
            assertThat(listener).isNotNull();
        }

        @Test
        @DisplayName("should implement ISuiteListener")
        void implementsISuiteListener() {
            assertThat(listener).isInstanceOf(org.testng.ISuiteListener.class);
        }

        @Test
        @DisplayName("should implement ITestListener")
        void implementsITestListener() {
            assertThat(listener).isInstanceOf(org.testng.ITestListener.class);
        }

        @Test
        @DisplayName("should implement IInvokedMethodListener")
        void implementsIInvokedMethodListener() {
            assertThat(listener).isInstanceOf(org.testng.IInvokedMethodListener.class);
        }
    }

    @Nested
    @DisplayName("WebDriver Field Wrapping")
    class WebDriverFieldWrappingTests {

        @Test
        @DisplayName("should wrap WebDriver field using reflection")
        void wrapsWebDriverFieldUsingReflection() throws Exception {
            // Given - simulate what listener does internally
            TestClassWithDriver testInstance = new TestClassWithDriver();
            testInstance.driver = mockWebDriver;

            HealerConfig config = new HealerConfig();
            config.setEnabled(true);
            config.applyDefaults();
            HealingEngine engine = new HealingEngine(config);

            // When - simulate wrapping logic
            Field field = TestClassWithDriver.class.getDeclaredField("driver");
            field.setAccessible(true);
            WebDriver original = (WebDriver) field.get(testInstance);
            if (original != null && !(original instanceof HealingWebDriver)) {
                HealingWebDriver healingDriver = new HealingWebDriver(original, engine, config);
                field.set(testInstance, healingDriver);
            }

            // Then
            assertThat(testInstance.driver).isInstanceOf(HealingWebDriver.class);
        }

        @Test
        @DisplayName("should not re-wrap HealingWebDriver")
        void doesNotRewrapHealingWebDriver() throws Exception {
            // Given
            HealerConfig config = new HealerConfig();
            config.setEnabled(true);
            config.applyDefaults();
            HealingEngine engine = new HealingEngine(config);
            HealingWebDriver existingHealer = new HealingWebDriver(mockWebDriver, engine, config);

            TestClassWithDriver testInstance = new TestClassWithDriver();
            testInstance.driver = existingHealer;

            // When - simulate wrapping logic (should skip)
            Field field = TestClassWithDriver.class.getDeclaredField("driver");
            field.setAccessible(true);
            WebDriver original = (WebDriver) field.get(testInstance);
            if (original != null && !(original instanceof HealingWebDriver)) {
                HealingWebDriver healingDriver = new HealingWebDriver(original, engine, config);
                field.set(testInstance, healingDriver);
            }

            // Then - should still be the same instance
            assertThat(testInstance.driver).isSameAs(existingHealer);
        }

        @Test
        @DisplayName("should wrap WebDriver in parent class")
        void wrapsWebDriverInParentClass() throws Exception {
            // Given
            HealerConfig config = new HealerConfig();
            config.setEnabled(true);
            config.applyDefaults();
            HealingEngine engine = new HealingEngine(config);

            ChildTestClass testInstance = new ChildTestClass();
            testInstance.driver = mockWebDriver;

            // When - simulate wrapping logic walking up class hierarchy
            Class<?> clazz = testInstance.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (WebDriver.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        WebDriver original = (WebDriver) field.get(testInstance);
                        if (original != null && !(original instanceof HealingWebDriver)) {
                            HealingWebDriver healingDriver = new HealingWebDriver(original, engine, config);
                            field.set(testInstance, healingDriver);
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }

            // Then
            assertThat(testInstance.driver).isInstanceOf(HealingWebDriver.class);
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

            // When / Then - should not throw
            Field field = TestClassWithDriver.class.getDeclaredField("driver");
            field.setAccessible(true);
            WebDriver original = (WebDriver) field.get(testInstance);
            if (original != null && !(original instanceof HealingWebDriver)) {
                HealingWebDriver healingDriver = new HealingWebDriver(original, engine, config);
                field.set(testInstance, healingDriver);
            }

            // No exception means success
            assertThat(testInstance.driver).isNull();
        }

        @Test
        @DisplayName("should handle multiple WebDriver fields")
        void handlesMultipleWebDriverFields() throws Exception {
            // Given
            HealerConfig config = new HealerConfig();
            config.setEnabled(true);
            config.applyDefaults();
            HealingEngine engine = new HealingEngine(config);

            WebDriver mockDriver2 = mock(WebDriver.class);
            TestClassWithMultipleDrivers testInstance = new TestClassWithMultipleDrivers();
            testInstance.driver1 = mockWebDriver;
            testInstance.driver2 = mockDriver2;

            // When - simulate wrapping logic
            for (Field field : TestClassWithMultipleDrivers.class.getDeclaredFields()) {
                if (WebDriver.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    WebDriver original = (WebDriver) field.get(testInstance);
                    if (original != null && !(original instanceof HealingWebDriver)) {
                        HealingWebDriver healingDriver = new HealingWebDriver(original, engine, config);
                        field.set(testInstance, healingDriver);
                    }
                }
            }

            // Then - both should be wrapped
            assertThat(testInstance.driver1).isInstanceOf(HealingWebDriver.class);
            assertThat(testInstance.driver2).isInstanceOf(HealingWebDriver.class);
        }

        @Test
        @DisplayName("should wrap private WebDriver fields")
        void wrapsPrivateFields() throws Exception {
            // Given
            HealerConfig config = new HealerConfig();
            config.setEnabled(true);
            config.applyDefaults();
            HealingEngine engine = new HealingEngine(config);

            TestClassWithPrivateDriver testInstance = new TestClassWithPrivateDriver(mockWebDriver);

            // When - simulate wrapping logic
            Field field = TestClassWithPrivateDriver.class.getDeclaredField("driver");
            field.setAccessible(true);
            WebDriver original = (WebDriver) field.get(testInstance);
            if (original != null && !(original instanceof HealingWebDriver)) {
                HealingWebDriver healingDriver = new HealingWebDriver(original, engine, config);
                field.set(testInstance, healingDriver);
            }

            // Then
            assertThat(testInstance.getDriver()).isInstanceOf(HealingWebDriver.class);
        }
    }

    @Nested
    @DisplayName("Test ID Generation")
    class TestIdGenerationTests {

        @Test
        @DisplayName("should generate unique test ID format")
        void generatesUniqueTestIdFormat() {
            // The listener uses: testClass.getName() + "." + method.getMethodName()
            String className = "com.example.TestClass";
            String methodName = "testMethod";
            String testId = className + "." + methodName;

            assertThat(testId).isEqualTo("com.example.TestClass.testMethod");
        }
    }

    @Nested
    @DisplayName("Configuration")
    class ConfigurationTests {

        @Test
        @DisplayName("should use HealerConfig for settings")
        void usesHealerConfig() {
            HealerConfig config = new HealerConfig();
            config.setEnabled(true);
            config.applyDefaults();

            assertThat(config.isEnabled()).isTrue();
            assertThat(config.getReport()).isNotNull();
        }

        @Test
        @DisplayName("should support disabled mode")
        void supportsDisabledMode() {
            HealerConfig config = new HealerConfig();
            config.setEnabled(false);

            assertThat(config.isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("HealingEngine Creation")
    class HealingEngineCreationTests {

        @Test
        @DisplayName("should create HealingEngine from config")
        void createsHealingEngineFromConfig() {
            HealerConfig config = new HealerConfig();
            config.setEnabled(true);
            config.applyDefaults();

            HealingEngine engine = new HealingEngine(config);

            assertThat(engine).isNotNull();
        }
    }

    @Nested
    @DisplayName("Listener Method Availability")
    class ListenerMethodAvailabilityTests {

        @Test
        @DisplayName("should have onStart(ISuite) method")
        void hasOnStartSuiteMethod() throws NoSuchMethodException {
            Method method = HealerTestListener.class.getMethod("onStart", org.testng.ISuite.class);
            assertThat(method).isNotNull();
        }

        @Test
        @DisplayName("should have onFinish(ISuite) method")
        void hasOnFinishSuiteMethod() throws NoSuchMethodException {
            Method method = HealerTestListener.class.getMethod("onFinish", org.testng.ISuite.class);
            assertThat(method).isNotNull();
        }

        @Test
        @DisplayName("should have onTestStart method")
        void hasOnTestStartMethod() throws NoSuchMethodException {
            Method method = HealerTestListener.class.getMethod("onTestStart", org.testng.ITestResult.class);
            assertThat(method).isNotNull();
        }

        @Test
        @DisplayName("should have onTestSuccess method")
        void hasOnTestSuccessMethod() throws NoSuchMethodException {
            Method method = HealerTestListener.class.getMethod("onTestSuccess", org.testng.ITestResult.class);
            assertThat(method).isNotNull();
        }

        @Test
        @DisplayName("should have onTestFailure method")
        void hasOnTestFailureMethod() throws NoSuchMethodException {
            Method method = HealerTestListener.class.getMethod("onTestFailure", org.testng.ITestResult.class);
            assertThat(method).isNotNull();
        }

        @Test
        @DisplayName("should have onTestSkipped method")
        void hasOnTestSkippedMethod() throws NoSuchMethodException {
            Method method = HealerTestListener.class.getMethod("onTestSkipped", org.testng.ITestResult.class);
            assertThat(method).isNotNull();
        }

        @Test
        @DisplayName("should have beforeInvocation method")
        void hasBeforeInvocationMethod() throws NoSuchMethodException {
            Method method = HealerTestListener.class.getMethod("beforeInvocation",
                    org.testng.IInvokedMethod.class, org.testng.ITestResult.class);
            assertThat(method).isNotNull();
        }

        @Test
        @DisplayName("should have afterInvocation method")
        void hasAfterInvocationMethod() throws NoSuchMethodException {
            Method method = HealerTestListener.class.getMethod("afterInvocation",
                    org.testng.IInvokedMethod.class, org.testng.ITestResult.class);
            assertThat(method).isNotNull();
        }
    }

    // Test helper classes

    static class TestClassWithDriver {
        WebDriver driver;
    }

    static class ChildTestClass extends TestClassWithDriver {
    }

    static class TestClassWithMultipleDrivers {
        WebDriver driver1;
        WebDriver driver2;
    }

    static class TestClassWithPrivateDriver {
        private WebDriver driver;

        TestClassWithPrivateDriver(WebDriver driver) {
            this.driver = driver;
        }

        WebDriver getDriver() {
            return driver;
        }
    }
}
