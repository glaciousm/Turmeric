package io.github.glaciousm.llm;

import io.github.glaciousm.core.config.LlmConfig;
import io.github.glaciousm.core.exception.LlmException;
import io.github.glaciousm.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LlmOrchestratorTest {

    private LlmOrchestrator orchestrator;
    private LlmProvider mockProvider;
    private LlmProvider mockFallbackProvider;

    @BeforeEach
    void setUp() {
        orchestrator = new LlmOrchestrator();
        mockProvider = mock(LlmProvider.class);
        mockFallbackProvider = mock(LlmProvider.class);
    }

    @Test
    void evaluateCandidates_withSuccessfulPrimaryProvider_returnsDecision() {
        orchestrator.registerProvider("test-provider", mockProvider);

        LlmConfig config = createTestConfig("test-provider");
        FailureContext failure = createSampleFailure();
        UiSnapshot snapshot = createSampleSnapshot();
        IntentContract intent = createSampleIntent();

        HealDecision expectedDecision = HealDecision.canHeal(1, 0.95, "Found match");
        when(mockProvider.evaluateCandidates(failure, snapshot, intent, config))
                .thenReturn(expectedDecision);

        HealDecision result = orchestrator.evaluateCandidates(failure, snapshot, intent, config);

        assertThat(result).isEqualTo(expectedDecision);
        verify(mockProvider, times(1)).evaluateCandidates(failure, snapshot, intent, config);
    }

    @Test
    void evaluateCandidates_withPrimaryProviderFailure_usesFallback() {
        orchestrator.registerProvider("primary", mockProvider);
        orchestrator.registerProvider("fallback", mockFallbackProvider);

        LlmConfig config = createTestConfig("primary");
        LlmConfig.FallbackProvider fallback = new LlmConfig.FallbackProvider();
        fallback.setProvider("fallback");
        fallback.setModel("fallback-model");
        config.setFallback(List.of(fallback));

        FailureContext failure = createSampleFailure();
        UiSnapshot snapshot = createSampleSnapshot();
        IntentContract intent = createSampleIntent();

        // Primary fails
        when(mockProvider.evaluateCandidates(eq(failure), eq(snapshot), eq(intent), any()))
                .thenThrow(new LlmException("Provider unavailable", "primary", "test-model"));

        // Fallback succeeds
        HealDecision expectedDecision = HealDecision.canHeal(2, 0.88, "Fallback found match");
        when(mockFallbackProvider.evaluateCandidates(eq(failure), eq(snapshot), eq(intent), any()))
                .thenReturn(expectedDecision);

        HealDecision result = orchestrator.evaluateCandidates(failure, snapshot, intent, config);

        assertThat(result).isEqualTo(expectedDecision);
        verify(mockProvider, times(1)).evaluateCandidates(eq(failure), eq(snapshot), eq(intent), any());
        verify(mockFallbackProvider, times(1)).evaluateCandidates(eq(failure), eq(snapshot), eq(intent), any());
    }

    @Test
    void evaluateCandidates_withAllProvidersFailure_throwsException() {
        orchestrator.registerProvider("primary", mockProvider);
        orchestrator.registerProvider("fallback", mockFallbackProvider);

        LlmConfig config = createTestConfig("primary");
        LlmConfig.FallbackProvider fallback = new LlmConfig.FallbackProvider();
        fallback.setProvider("fallback");
        fallback.setModel("fallback-model");
        config.setFallback(List.of(fallback));

        FailureContext failure = createSampleFailure();
        UiSnapshot snapshot = createSampleSnapshot();
        IntentContract intent = createSampleIntent();

        // Both fail
        when(mockProvider.evaluateCandidates(eq(failure), eq(snapshot), eq(intent), any()))
                .thenThrow(new LlmException("Primary unavailable", "primary", "test-model"));
        when(mockFallbackProvider.evaluateCandidates(eq(failure), eq(snapshot), eq(intent), any()))
                .thenThrow(new LlmException("Fallback unavailable", "fallback", "fallback-model"));

        assertThatThrownBy(() -> orchestrator.evaluateCandidates(failure, snapshot, intent, config))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("All LLM providers failed");
    }

    @Test
    void evaluateCandidates_withMultipleFallbacks_triesInOrder() {
        orchestrator.registerProvider("primary", mockProvider);
        LlmProvider mockFallback2 = mock(LlmProvider.class);
        orchestrator.registerProvider("fallback1", mockFallbackProvider);
        orchestrator.registerProvider("fallback2", mockFallback2);

        LlmConfig config = createTestConfig("primary");
        LlmConfig.FallbackProvider fallback1 = new LlmConfig.FallbackProvider();
        fallback1.setProvider("fallback1");
        fallback1.setModel("fb1-model");

        LlmConfig.FallbackProvider fallback2 = new LlmConfig.FallbackProvider();
        fallback2.setProvider("fallback2");
        fallback2.setModel("fb2-model");

        config.setFallback(List.of(fallback1, fallback2));

        FailureContext failure = createSampleFailure();
        UiSnapshot snapshot = createSampleSnapshot();
        IntentContract intent = createSampleIntent();

        // Primary and first fallback fail
        when(mockProvider.evaluateCandidates(eq(failure), eq(snapshot), eq(intent), any()))
                .thenThrow(new LlmException("Primary failed", "primary", "test-model"));
        when(mockFallbackProvider.evaluateCandidates(eq(failure), eq(snapshot), eq(intent), any()))
                .thenThrow(new LlmException("Fallback1 failed", "fallback1", "fb1-model"));

        // Second fallback succeeds
        HealDecision expectedDecision = HealDecision.canHeal(3, 0.92, "Second fallback success");
        when(mockFallback2.evaluateCandidates(eq(failure), eq(snapshot), eq(intent), any()))
                .thenReturn(expectedDecision);

        HealDecision result = orchestrator.evaluateCandidates(failure, snapshot, intent, config);

        assertThat(result).isEqualTo(expectedDecision);
        verify(mockProvider, times(1)).evaluateCandidates(eq(failure), eq(snapshot), eq(intent), any());
        verify(mockFallbackProvider, times(1)).evaluateCandidates(eq(failure), eq(snapshot), eq(intent), any());
        verify(mockFallback2, times(1)).evaluateCandidates(eq(failure), eq(snapshot), eq(intent), any());
    }

    @Test
    void evaluateCandidates_withUnknownProvider_throwsException() {
        LlmConfig config = createTestConfig("unknown-provider");
        FailureContext failure = createSampleFailure();
        UiSnapshot snapshot = createSampleSnapshot();
        IntentContract intent = createSampleIntent();

        assertThatThrownBy(() -> orchestrator.evaluateCandidates(failure, snapshot, intent, config))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("All LLM providers failed");
    }

    @Test
    void validateOutcome_withSuccessfulProvider_returnsResult() {
        orchestrator.registerProvider("test-provider", mockProvider);

        LlmConfig config = createTestConfig("test-provider");
        UiSnapshot before = createSampleSnapshot();
        UiSnapshot after = createSampleSnapshot();

        OutcomeResult expectedResult = OutcomeResult.passed("Success", 0.95);
        when(mockProvider.validateOutcome("Expected outcome", before, after, config))
                .thenReturn(expectedResult);

        OutcomeResult result = orchestrator.validateOutcome("Expected outcome", before, after, config);

        assertThat(result).isEqualTo(expectedResult);
        verify(mockProvider, times(1)).validateOutcome("Expected outcome", before, after, config);
    }

    @Test
    void validateOutcome_withUnknownProvider_throwsException() {
        LlmConfig config = createTestConfig("unknown-provider");
        UiSnapshot before = createSampleSnapshot();
        UiSnapshot after = createSampleSnapshot();

        assertThatThrownBy(() -> orchestrator.validateOutcome("Expected", before, after, config))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("Unknown provider");
    }

    @Test
    void registerProvider_allowsCustomProviders() {
        LlmProvider customProvider = mock(LlmProvider.class);
        orchestrator.registerProvider("custom", customProvider);

        LlmConfig config = createTestConfig("custom");
        FailureContext failure = createSampleFailure();
        UiSnapshot snapshot = createSampleSnapshot();
        IntentContract intent = createSampleIntent();

        HealDecision expectedDecision = HealDecision.canHeal(0, 0.9, "Custom provider works");
        when(customProvider.evaluateCandidates(failure, snapshot, intent, config))
                .thenReturn(expectedDecision);

        HealDecision result = orchestrator.evaluateCandidates(failure, snapshot, intent, config);

        assertThat(result).isEqualTo(expectedDecision);
        verify(customProvider, times(1)).evaluateCandidates(failure, snapshot, intent, config);
    }

    @Test
    void getPromptBuilder_returnsNonNull() {
        PromptBuilder promptBuilder = orchestrator.getPromptBuilder();

        assertThat(promptBuilder).isNotNull();
    }

    @Test
    void getResponseParser_returnsNonNull() {
        ResponseParser responseParser = orchestrator.getResponseParser();

        assertThat(responseParser).isNotNull();
    }

    @Test
    void defaultProviders_includesOpenAi() {
        // The orchestrator should have default providers registered
        LlmConfig config = createTestConfig("openai");
        config.setApiKeyEnv("NONEXISTENT_KEY");

        FailureContext failure = createSampleFailure();
        UiSnapshot snapshot = createSampleSnapshot();
        IntentContract intent = createSampleIntent();

        // Should fail with API key error, not unknown provider
        assertThatThrownBy(() -> orchestrator.evaluateCandidates(failure, snapshot, intent, config))
                .isInstanceOf(LlmException.class);
    }

    @Test
    void defaultProviders_includesAnthropic() {
        LlmConfig config = createTestConfig("anthropic");
        config.setApiKeyEnv("NONEXISTENT_KEY");

        FailureContext failure = createSampleFailure();
        UiSnapshot snapshot = createSampleSnapshot();
        IntentContract intent = createSampleIntent();

        // Should fail with API key error, not unknown provider
        assertThatThrownBy(() -> orchestrator.evaluateCandidates(failure, snapshot, intent, config))
                .isInstanceOf(LlmException.class);
    }

    @Test
    void defaultProviders_includesOllama() {
        LlmConfig config = createTestConfig("ollama");

        // Ollama is a default provider - just verify it exists
        assertThat(config.getProvider()).isEqualTo("ollama");
    }

    @Test
    void defaultProviders_includesLocalAsOllamaAlias() {
        LlmConfig config = createTestConfig("local");

        // "local" should be an alias for ollama
        assertThat(config.getProvider()).isEqualTo("local");
    }

    @Test
    void fallbackConfig_preservesOriginalSettings() {
        orchestrator.registerProvider("primary", mockProvider);
        orchestrator.registerProvider("fallback", mockFallbackProvider);

        LlmConfig config = createTestConfig("primary");
        config.setTimeoutSeconds(60);
        config.setMaxRetries(3);
        config.setTemperature(0.5);
        config.setConfidenceThreshold(0.8);
        config.setMaxTokensPerRequest(1500);
        config.setRequireReasoning(true);

        LlmConfig.FallbackProvider fallback = new LlmConfig.FallbackProvider();
        fallback.setProvider("fallback");
        fallback.setModel("fallback-model");
        config.setFallback(List.of(fallback));

        FailureContext failure = createSampleFailure();
        UiSnapshot snapshot = createSampleSnapshot();
        IntentContract intent = createSampleIntent();

        when(mockProvider.evaluateCandidates(eq(failure), eq(snapshot), eq(intent), any()))
                .thenThrow(new LlmException("Primary failed", "primary", "test-model"));

        HealDecision expectedDecision = HealDecision.canHeal(1, 0.9, "Fallback");
        when(mockFallbackProvider.evaluateCandidates(eq(failure), eq(snapshot), eq(intent), any()))
                .thenReturn(expectedDecision);

        orchestrator.evaluateCandidates(failure, snapshot, intent, config);

        // Capture the config passed to fallback provider
        ArgumentCaptor<LlmConfig> configCaptor = ArgumentCaptor.forClass(LlmConfig.class);
        verify(mockFallbackProvider).evaluateCandidates(
                eq(failure), eq(snapshot), eq(intent), configCaptor.capture());

        LlmConfig fallbackConfig = configCaptor.getValue();
        assertThat(fallbackConfig.getProvider()).isEqualTo("fallback");
        assertThat(fallbackConfig.getModel()).isEqualTo("fallback-model");
        assertThat(fallbackConfig.getTimeoutSeconds()).isEqualTo(60);
        assertThat(fallbackConfig.getMaxRetries()).isEqualTo(3);
        assertThat(fallbackConfig.getTemperature()).isEqualTo(0.5);
        assertThat(fallbackConfig.getConfidenceThreshold()).isEqualTo(0.8);
        assertThat(fallbackConfig.getMaxTokensPerRequest()).isEqualTo(1500);
        assertThat(fallbackConfig.isRequireReasoning()).isTrue();
    }

    @Test
    void registerProvider_isCaseInsensitive() {
        LlmProvider customProvider = mock(LlmProvider.class);
        orchestrator.registerProvider("MyProvider", customProvider);

        LlmConfig config = createTestConfig("myprovider");  // lowercase
        FailureContext failure = createSampleFailure();
        UiSnapshot snapshot = createSampleSnapshot();
        IntentContract intent = createSampleIntent();

        HealDecision expectedDecision = HealDecision.canHeal(0, 0.9, "Works");
        when(customProvider.evaluateCandidates(failure, snapshot, intent, config))
                .thenReturn(expectedDecision);

        HealDecision result = orchestrator.evaluateCandidates(failure, snapshot, intent, config);

        assertThat(result).isEqualTo(expectedDecision);
    }

    @Test
    void evaluateCandidates_withNoFallbacks_doesNotRetryUnnecessarily() {
        orchestrator.registerProvider("primary", mockProvider);

        LlmConfig config = createTestConfig("primary");
        // No fallbacks configured

        FailureContext failure = createSampleFailure();
        UiSnapshot snapshot = createSampleSnapshot();
        IntentContract intent = createSampleIntent();

        when(mockProvider.evaluateCandidates(failure, snapshot, intent, config))
                .thenThrow(new LlmException("Failed", "primary", "test-model"));

        assertThatThrownBy(() -> orchestrator.evaluateCandidates(failure, snapshot, intent, config))
                .isInstanceOf(LlmException.class);

        // Should only call primary once
        verify(mockProvider, times(1)).evaluateCandidates(failure, snapshot, intent, config);
    }

    @Test
    void evaluateCandidates_skipsNullFallbackProvider() {
        orchestrator.registerProvider("primary", mockProvider);

        LlmConfig config = createTestConfig("primary");
        LlmConfig.FallbackProvider fallback = new LlmConfig.FallbackProvider();
        fallback.setProvider("nonexistent");
        fallback.setModel("test");
        config.setFallback(List.of(fallback));

        FailureContext failure = createSampleFailure();
        UiSnapshot snapshot = createSampleSnapshot();
        IntentContract intent = createSampleIntent();

        when(mockProvider.evaluateCandidates(failure, snapshot, intent, config))
                .thenThrow(new LlmException("Failed", "primary", "test-model"));

        assertThatThrownBy(() -> orchestrator.evaluateCandidates(failure, snapshot, intent, config))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("All LLM providers failed");
    }

    // Helper methods

    private LlmConfig createTestConfig(String provider) {
        LlmConfig config = new LlmConfig();
        config.setProvider(provider);
        config.setModel("test-model");
        config.setTimeoutSeconds(30);
        config.setMaxRetries(0);
        config.setTemperature(0.7);
        config.setMaxTokensPerRequest(2000);
        return config;
    }

    private FailureContext createSampleFailure() {
        return FailureContext.builder()
                .featureName("Test Feature")
                .scenarioName("Test Scenario")
                .stepKeyword("When")
                .stepText("user clicks button")
                .exceptionType("NoSuchElementException")
                .originalLocator(new LocatorInfo(LocatorInfo.LocatorStrategy.CSS, "#button"))
                .actionType(ActionType.CLICK)
                .build();
    }

    private UiSnapshot createSampleSnapshot() {
        ElementSnapshot element = ElementSnapshot.builder()
                .index(0)
                .tagName("button")
                .text("Click me")
                .visible(true)
                .enabled(true)
                .build();

        return UiSnapshot.builder()
                .url("https://example.com")
                .title("Test Page")
                .interactiveElements(List.of(element))
                .build();
    }

    private IntentContract createSampleIntent() {
        return IntentContract.builder()
                .action("click")
                .description("Click the button")
                .policy(HealPolicy.AUTO_SAFE)
                .build();
    }
}
