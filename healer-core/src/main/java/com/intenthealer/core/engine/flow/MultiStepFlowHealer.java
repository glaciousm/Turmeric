package com.intenthealer.core.engine.flow;

import com.intenthealer.core.model.HealResult;
import com.intenthealer.core.model.LocatorInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Handles healing of multi-step flows where multiple elements need to be
 * healed together to maintain flow coherence.
 *
 * Use cases:
 * - Wizard/multi-page forms where elements shift together
 * - Dynamic workflows where element positions change as a group
 * - Navigation sequences with related elements
 */
public class MultiStepFlowHealer {

    private static final Logger logger = LoggerFactory.getLogger(MultiStepFlowHealer.class);

    private final Map<String, FlowDefinition> registeredFlows;
    private final Map<String, FlowExecution> activeExecutions;
    private final FlowHealingStrategy healingStrategy;
    private final Duration flowTimeout;

    public MultiStepFlowHealer() {
        this(FlowHealingStrategy.SEQUENTIAL, Duration.ofMinutes(5));
    }

    public MultiStepFlowHealer(FlowHealingStrategy strategy, Duration timeout) {
        this.registeredFlows = new ConcurrentHashMap<>();
        this.activeExecutions = new ConcurrentHashMap<>();
        this.healingStrategy = strategy;
        this.flowTimeout = timeout;
    }

    /**
     * Register a flow definition.
     */
    public void registerFlow(FlowDefinition flow) {
        registeredFlows.put(flow.flowId(), flow);
        logger.info("Registered flow: {} with {} steps", flow.flowId(), flow.steps().size());
    }

    /**
     * Start tracking a flow execution.
     */
    public FlowExecution startFlow(String flowId, String executionId) {
        FlowDefinition definition = registeredFlows.get(flowId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown flow: " + flowId);
        }

        FlowExecution execution = new FlowExecution(executionId, definition);
        activeExecutions.put(executionId, execution);
        logger.debug("Started flow execution: {} for flow: {}", executionId, flowId);
        return execution;
    }

    /**
     * Record a step failure in a flow.
     */
    public void recordStepFailure(String executionId, String stepId, LocatorInfo failedLocator) {
        FlowExecution execution = activeExecutions.get(executionId);
        if (execution == null) {
            logger.warn("Unknown flow execution: {}", executionId);
            return;
        }

        execution.recordFailure(stepId, failedLocator);
        logger.debug("Recorded failure in flow {}, step: {}", executionId, stepId);
    }

    /**
     * Heal a flow based on accumulated failures.
     */
    public FlowHealResult healFlow(String executionId, Function<HealRequest, HealResult> healer) {
        FlowExecution execution = activeExecutions.get(executionId);
        if (execution == null) {
            return FlowHealResult.notFound(executionId);
        }

        if (execution.getFailures().isEmpty()) {
            return FlowHealResult.noFailures(executionId);
        }

        logger.info("Healing flow {} with {} failures using {} strategy",
                executionId, execution.getFailures().size(), healingStrategy);

        List<StepHealResult> stepResults = new ArrayList<>();

        switch (healingStrategy) {
            case SEQUENTIAL -> stepResults = healSequentially(execution, healer);
            case PARALLEL -> stepResults = healInParallel(execution, healer);
            case CONTEXTUAL -> stepResults = healWithContext(execution, healer);
        }

        // Calculate overall success
        long successCount = stepResults.stream().filter(StepHealResult::success).count();
        boolean overallSuccess = successCount == stepResults.size();
        double avgConfidence = stepResults.stream()
                .mapToDouble(StepHealResult::confidence)
                .average().orElse(0);

        FlowHealResult result = new FlowHealResult(
                executionId,
                execution.getDefinition().flowId(),
                overallSuccess,
                stepResults,
                avgConfidence,
                null
        );

        // Clean up completed execution
        if (overallSuccess) {
            activeExecutions.remove(executionId);
        }

        return result;
    }

    /**
     * Heal steps one by one, using previous results to inform later heals.
     */
    private List<StepHealResult> healSequentially(FlowExecution execution,
                                                   Function<HealRequest, HealResult> healer) {
        List<StepHealResult> results = new ArrayList<>();
        Map<String, LocatorInfo> healedLocators = new HashMap<>();

        // Process in flow order
        for (FlowStep step : execution.getDefinition().steps()) {
            StepFailure failure = execution.getFailure(step.stepId());
            if (failure == null) continue;

            // Build context from previous heals
            HealRequest request = new HealRequest(
                    step.stepId(),
                    failure.failedLocator(),
                    step.intent(),
                    step.expectedOutcome(),
                    new HashMap<>(healedLocators),
                    execution.getDefinition().flowContext()
            );

            HealResult healResult = healer.apply(request);

            LocatorInfo healedLocator = healResult.getHealedLocator()
                    .map(loc -> new LocatorInfo(LocatorInfo.LocatorStrategy.CSS, loc))
                    .orElse(null);

            StepHealResult stepResult = new StepHealResult(
                    step.stepId(),
                    failure.failedLocator(),
                    healedLocator,
                    healResult.isSuccess(),
                    healResult.getConfidence(),
                    healResult.getReasoning().orElse(null)
            );
            results.add(stepResult);

            if (healResult.isSuccess() && healedLocator != null) {
                healedLocators.put(step.stepId(), healedLocator);
            }
        }

        return results;
    }

    /**
     * Heal all steps in parallel (independent healing).
     */
    private List<StepHealResult> healInParallel(FlowExecution execution,
                                                 Function<HealRequest, HealResult> healer) {
        return execution.getFailures().entrySet().parallelStream()
                .map(entry -> {
                    String stepId = entry.getKey();
                    StepFailure failure = entry.getValue();
                    FlowStep step = execution.getDefinition().getStep(stepId);

                    HealRequest request = new HealRequest(
                            stepId,
                            failure.failedLocator(),
                            step != null ? step.intent() : null,
                            step != null ? step.expectedOutcome() : null,
                            Map.of(),
                            execution.getDefinition().flowContext()
                    );

                    HealResult healResult = healer.apply(request);

                    LocatorInfo healedLocator = healResult.getHealedLocator()
                            .map(loc -> new LocatorInfo(LocatorInfo.LocatorStrategy.CSS, loc))
                            .orElse(null);

                    return new StepHealResult(
                            stepId,
                            failure.failedLocator(),
                            healedLocator,
                            healResult.isSuccess(),
                            healResult.getConfidence(),
                            healResult.getReasoning().orElse(null)
                    );
                })
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Heal with full flow context awareness.
     */
    private List<StepHealResult> healWithContext(FlowExecution execution,
                                                  Function<HealRequest, HealResult> healer) {
        List<StepHealResult> results = new ArrayList<>();

        // Build complete flow context
        StringBuilder flowContextBuilder = new StringBuilder();
        flowContextBuilder.append("Flow: ").append(execution.getDefinition().flowId()).append("\n");
        flowContextBuilder.append("Description: ").append(execution.getDefinition().description()).append("\n");
        flowContextBuilder.append("Steps in flow:\n");

        for (FlowStep step : execution.getDefinition().steps()) {
            flowContextBuilder.append("  - ").append(step.stepId())
                    .append(": ").append(step.intent()).append("\n");
        }

        String fullContext = flowContextBuilder.toString();

        // First pass: gather all information
        List<LocatorInfo> allFailedLocators = execution.getFailures().values().stream()
                .map(StepFailure::failedLocator)
                .toList();

        // Heal with context
        Map<String, LocatorInfo> healedLocators = new HashMap<>();
        for (FlowStep step : execution.getDefinition().steps()) {
            StepFailure failure = execution.getFailure(step.stepId());
            if (failure == null) continue;

            HealRequest request = new HealRequest(
                    step.stepId(),
                    failure.failedLocator(),
                    step.intent(),
                    step.expectedOutcome(),
                    new HashMap<>(healedLocators),
                    fullContext + "\nAll failed locators: " + allFailedLocators
            );

            HealResult healResult = healer.apply(request);

            LocatorInfo healedLocator = healResult.getHealedLocator()
                    .map(loc -> new LocatorInfo(LocatorInfo.LocatorStrategy.CSS, loc))
                    .orElse(null);

            StepHealResult stepResult = new StepHealResult(
                    step.stepId(),
                    failure.failedLocator(),
                    healedLocator,
                    healResult.isSuccess(),
                    healResult.getConfidence(),
                    healResult.getReasoning().orElse(null)
            );
            results.add(stepResult);

            if (healResult.isSuccess() && healedLocator != null) {
                healedLocators.put(step.stepId(), healedLocator);
            }
        }

        return results;
    }

    /**
     * Clean up expired executions.
     */
    public int cleanupExpired() {
        Instant threshold = Instant.now().minus(flowTimeout);
        int removed = 0;

        Iterator<Map.Entry<String, FlowExecution>> it = activeExecutions.entrySet().iterator();
        while (it.hasNext()) {
            FlowExecution execution = it.next().getValue();
            if (execution.getStartTime().isBefore(threshold)) {
                it.remove();
                removed++;
            }
        }

        if (removed > 0) {
            logger.info("Cleaned up {} expired flow executions", removed);
        }

        return removed;
    }

    /**
     * Get active execution count.
     */
    public int getActiveExecutionCount() {
        return activeExecutions.size();
    }

    /**
     * Get registered flow count.
     */
    public int getRegisteredFlowCount() {
        return registeredFlows.size();
    }

    // Records and classes

    /**
     * Flow definition with steps.
     */
    public record FlowDefinition(
            String flowId,
            String description,
            List<FlowStep> steps,
            String flowContext
    ) {
        public FlowStep getStep(String stepId) {
            return steps.stream()
                    .filter(s -> s.stepId().equals(stepId))
                    .findFirst()
                    .orElse(null);
        }

        public static Builder builder(String flowId) {
            return new Builder(flowId);
        }

        public static class Builder {
            private final String flowId;
            private String description = "";
            private final List<FlowStep> steps = new ArrayList<>();
            private String flowContext = "";

            public Builder(String flowId) {
                this.flowId = flowId;
            }

            public Builder description(String description) {
                this.description = description;
                return this;
            }

            public Builder step(String stepId, String intent, String expectedOutcome) {
                steps.add(new FlowStep(stepId, intent, expectedOutcome, steps.size()));
                return this;
            }

            public Builder context(String context) {
                this.flowContext = context;
                return this;
            }

            public FlowDefinition build() {
                return new FlowDefinition(flowId, description, List.copyOf(steps), flowContext);
            }
        }
    }

    /**
     * Single step in a flow.
     */
    public record FlowStep(
            String stepId,
            String intent,
            String expectedOutcome,
            int order
    ) {}

    /**
     * Tracks execution of a flow.
     */
    public static class FlowExecution {
        private final String executionId;
        private final FlowDefinition definition;
        private final Map<String, StepFailure> failures;
        private final Instant startTime;
        private int currentStepIndex;

        public FlowExecution(String executionId, FlowDefinition definition) {
            this.executionId = executionId;
            this.definition = definition;
            this.failures = new LinkedHashMap<>();
            this.startTime = Instant.now();
            this.currentStepIndex = 0;
        }

        public void recordFailure(String stepId, LocatorInfo failedLocator) {
            failures.put(stepId, new StepFailure(stepId, failedLocator, Instant.now()));
        }

        public StepFailure getFailure(String stepId) {
            return failures.get(stepId);
        }

        public Map<String, StepFailure> getFailures() {
            return Collections.unmodifiableMap(failures);
        }

        public String getExecutionId() { return executionId; }
        public FlowDefinition getDefinition() { return definition; }
        public Instant getStartTime() { return startTime; }
        public int getCurrentStepIndex() { return currentStepIndex; }

        public void advanceStep() { currentStepIndex++; }
    }

    /**
     * Step failure information.
     */
    public record StepFailure(
            String stepId,
            LocatorInfo failedLocator,
            Instant failureTime
    ) {}

    /**
     * Heal request for a step.
     */
    public record HealRequest(
            String stepId,
            LocatorInfo failedLocator,
            String intent,
            String expectedOutcome,
            Map<String, LocatorInfo> previousHeals,
            String flowContext
    ) {}

    /**
     * Result of healing a single step.
     */
    public record StepHealResult(
            String stepId,
            LocatorInfo originalLocator,
            LocatorInfo healedLocator,
            boolean success,
            double confidence,
            String reasoning
    ) {}

    /**
     * Result of healing an entire flow.
     */
    public record FlowHealResult(
            String executionId,
            String flowId,
            boolean success,
            List<StepHealResult> stepResults,
            double avgConfidence,
            String errorMessage
    ) {
        public static FlowHealResult notFound(String executionId) {
            return new FlowHealResult(executionId, null, false, List.of(), 0,
                    "Flow execution not found: " + executionId);
        }

        public static FlowHealResult noFailures(String executionId) {
            return new FlowHealResult(executionId, null, true, List.of(), 1.0,
                    "No failures to heal");
        }

        public int successCount() {
            return (int) stepResults.stream().filter(StepHealResult::success).count();
        }

        public int failureCount() {
            return stepResults.size() - successCount();
        }
    }

    /**
     * Strategy for healing multi-step flows.
     */
    public enum FlowHealingStrategy {
        /** Heal steps one by one, using previous results */
        SEQUENTIAL,
        /** Heal all steps independently in parallel */
        PARALLEL,
        /** Heal with full flow context awareness */
        CONTEXTUAL
    }
}
