# Intent Healer - Comprehensive Improvement Plan

## Overview

This plan covers all improvements needed to make Intent Healer production-ready:
1. Test Coverage (Critical)
2. Feature Integration (Wire up disconnected services)
3. Code Quality Fixes
4. Production Hardening
5. New Features

---

## Phase 1: Test Coverage (Critical - 0% modules)

### 1.1 CLI Module Tests
**Files to create:**
- `healer-cli/src/test/java/com/intenthealer/cli/HealerCliTest.java`
- `healer-cli/src/test/java/com/intenthealer/cli/commands/ConfigCommandTest.java`
- `healer-cli/src/test/java/com/intenthealer/cli/commands/CacheCommandTest.java`
- `healer-cli/src/test/java/com/intenthealer/cli/commands/ReportCommandTest.java`

**Test scenarios:**
- Command parsing and dispatch
- `config show` - displays current config
- `config validate` - validates config file
- `config init` - creates default config
- `cache stats` - shows cache statistics
- `cache clear` - clears cache with confirmation
- `cache warmup` - loads cache from reports
- `report summary` - displays healing summary
- `report list` - lists recent heals
- Error handling for invalid arguments

### 1.2 Report Module Tests
**Files to create:**
- `healer-report/src/test/java/com/intenthealer/report/ReportGeneratorTest.java`
- `healer-report/src/test/java/com/intenthealer/report/TrustDashboardTest.java`
- `healer-report/src/test/java/com/intenthealer/report/model/HealReportTest.java`
- `healer-report/src/test/java/com/intenthealer/report/model/HealEventTest.java`

**Test scenarios:**
- JSON report generation
- HTML report generation
- Report with 0 events
- Report with 100+ events
- HTML escaping of special characters
- Report loading from JSON file
- Summary statistics calculation

### 1.3 Integration Tests
**Files to create:**
- `healer-core/src/test/java/com/intenthealer/core/integration/HealingPipelineIntegrationTest.java`
- `healer-selenium/src/test/java/com/intenthealer/selenium/integration/HealingWebDriverIntegrationTest.java`

**Test scenarios:**
- Full healing pipeline: failure → snapshot → LLM → heal → success
- Cache hit scenario
- Cache miss scenario
- Guardrail rejection
- Circuit breaker activation

### 1.4 TestNG/JUnit Integration Tests
**Files to create:**
- `healer-testng/src/test/java/com/intenthealer/testng/HealerTestListenerTest.java`
- `healer-junit/src/test/java/com/intenthealer/junit/HealerExtensionTest.java`

---

## Phase 2: Feature Integration (Wire up disconnected services)

### 2.1 NotificationService Integration
**Current state:** Service exists but not called from healing pipeline

**Files to modify:**
- `healer-core/src/main/java/com/intenthealer/core/engine/HealingEngine.java`

**Changes:**
- Inject NotificationService into HealingEngine
- Call `notificationService.notify()` after successful heals
- Call `notificationService.notifyFailure()` on heal failures
- Add configuration toggle for notifications

### 2.2 PatternSharingService Integration
**Current state:** Service exists but not invoked

**Files to modify:**
- `healer-core/src/main/java/com/intenthealer/core/engine/HealingEngine.java`
- `healer-core/src/main/java/com/intenthealer/core/config/HealerConfig.java`

**Changes:**
- Check shared patterns before calling LLM
- Store successful heals in shared pattern repository
- Add configuration for pattern sharing (enable/disable, repository path)

### 2.3 ApprovalWorkflow Integration
**Current state:** Workflow exists but CONFIRM mode not fully wired

**Files to modify:**
- `healer-selenium/src/main/java/com/intenthealer/selenium/driver/HealingWebDriver.java`

**Changes:**
- When mode=CONFIRM, pause and await approval before applying heal
- Integrate with ApprovalWorkflow.submitForApproval()
- Handle timeout for approval (configurable)

---

## Phase 3: Code Quality Fixes

### 3.1 Replace RuntimeException with Domain Exceptions
**Files to modify:**
- `healer-core/src/main/java/com/intenthealer/core/util/JsonUtils.java`
  - Replace RuntimeException → JsonSerializationException
- `healer-core/src/main/java/com/intenthealer/core/config/ConfigLoader.java`
  - Replace RuntimeException → ConfigurationException
- `healer-llm/src/main/java/com/intenthealer/llm/providers/BedrockProvider.java`
  - Replace RuntimeException → LlmProviderException

**New exception classes to create:**
- `healer-core/src/main/java/com/intenthealer/core/exception/JsonSerializationException.java`
- `healer-core/src/main/java/com/intenthealer/core/exception/ConfigurationException.java`

### 3.2 Replace System.out/err with Logging
**Files to modify:**
- `healer-cli/src/main/java/com/intenthealer/cli/HealerCli.java`
- `healer-cli/src/main/java/com/intenthealer/cli/commands/ConfigCommand.java`
- `healer-cli/src/main/java/com/intenthealer/cli/commands/CacheCommand.java`
- `healer-cli/src/main/java/com/intenthealer/cli/commands/ReportCommand.java`

**Note:** CLI needs to output to console for user interaction. Create a `CliOutput` utility class that wraps both console output and logging.

### 3.3 Add Exponential Backoff for LLM Rate Limiting
**Files to modify:**
- `healer-llm/src/main/java/com/intenthealer/llm/LlmOrchestrator.java`

**Changes:**
- Detect HTTP 429 responses
- Implement exponential backoff (1s, 2s, 4s, 8s...)
- Max retries from config (default: 3)
- Log retry attempts

---

## Phase 4: Production Hardening

### 4.1 Circuit Breaker Tests
**Files to create:**
- `healer-core/src/test/java/com/intenthealer/core/engine/CircuitBreakerCostLimitTest.java`

**Test scenarios:**
- Circuit opens when daily cost limit exceeded
- Circuit opens after consecutive failures
- Half-open state testing
- Circuit reset after cooldown

### 4.2 Cache Load Testing
**Files to create:**
- `healer-core/src/test/java/com/intenthealer/core/engine/cache/HealCacheLoadTest.java`

**Test scenarios:**
- Insert 100K entries
- Retrieve performance benchmarks
- Memory usage monitoring
- TTL expiration verification

### 4.3 Security Audit
**Files to check:**
- All logger calls for API key exposure
- Configuration examples for hardcoded secrets
- Report generation for sensitive data leakage

**Create:**
- `healer-core/src/test/java/com/intenthealer/core/security/ApiKeyLeakageTest.java`

---

## Phase 5: New Features

### 5.1 Dry-Run Mode for Auto-Update
**Files to modify:**
- `healer-core/src/main/java/com/intenthealer/core/config/AutoUpdateConfig.java`
  - Add `dryRun: boolean` field
- `healer-core/src/main/java/com/intenthealer/core/engine/patch/SourceCodeUpdater.java`
  - When dryRun=true, log changes but don't write files

### 5.2 Locator Recommendations
**Files to create:**
- `healer-core/src/main/java/com/intenthealer/core/engine/LocatorRecommender.java`

**Features:**
- Analyze healed locators
- Suggest stable alternatives (data-testid, aria-label)
- Warn about brittle locators (long XPath, positional selectors)
- Include recommendations in HTML report

### 5.3 Healing Analytics in Report
**Files to modify:**
- `healer-report/src/main/java/com/intenthealer/report/ReportGenerator.java`
- `healer-cucumber/src/main/java/com/intenthealer/cucumber/report/HealerCucumberReportPlugin.java`

**New metrics:**
- Success rate trend (last 7 runs)
- Most frequently healed locators
- Average confidence score
- Time saved by healing vs manual fix

### 5.4 Visual Diff in Reports (Optional - High Effort)
**Files to create:**
- `healer-report/src/main/java/com/intenthealer/report/VisualDiffGenerator.java`

**Features:**
- Capture screenshot before heal attempt
- Capture screenshot after heal success
- Embed side-by-side comparison in HTML report

---

## Implementation Order

```
Week 1: Test Coverage (Phase 1)
├── Day 1-2: CLI tests (1.1)
├── Day 3-4: Report tests (1.2)
└── Day 5: Integration tests (1.3, 1.4)

Week 2: Feature Integration (Phase 2)
├── Day 1-2: NotificationService (2.1)
├── Day 3: PatternSharingService (2.2)
└── Day 4-5: ApprovalWorkflow (2.3)

Week 3: Code Quality (Phase 3)
├── Day 1-2: Domain exceptions (3.1)
├── Day 3: CLI logging (3.2)
└── Day 4-5: Exponential backoff (3.3)

Week 4: Production Hardening (Phase 4)
├── Day 1-2: Circuit breaker tests (4.1)
├── Day 3-4: Cache load tests (4.2)
└── Day 5: Security audit (4.3)

Week 5: New Features (Phase 5)
├── Day 1: Dry-run mode (5.1)
├── Day 2-3: Locator recommendations (5.2)
├── Day 4: Healing analytics (5.3)
└── Day 5: Visual diff (5.4 - stretch goal)
```

---

## Files Summary

### New Files (19)
```
healer-cli/src/test/java/.../HealerCliTest.java
healer-cli/src/test/java/.../commands/ConfigCommandTest.java
healer-cli/src/test/java/.../commands/CacheCommandTest.java
healer-cli/src/test/java/.../commands/ReportCommandTest.java
healer-report/src/test/java/.../ReportGeneratorTest.java
healer-report/src/test/java/.../TrustDashboardTest.java
healer-report/src/test/java/.../model/HealReportTest.java
healer-report/src/test/java/.../model/HealEventTest.java
healer-core/src/test/java/.../integration/HealingPipelineIntegrationTest.java
healer-selenium/src/test/java/.../integration/HealingWebDriverIntegrationTest.java
healer-testng/src/test/java/.../HealerTestListenerTest.java
healer-junit/src/test/java/.../HealerExtensionTest.java
healer-core/src/main/java/.../exception/JsonSerializationException.java
healer-core/src/main/java/.../exception/ConfigurationException.java
healer-core/src/test/java/.../CircuitBreakerCostLimitTest.java
healer-core/src/test/java/.../cache/HealCacheLoadTest.java
healer-core/src/test/java/.../security/ApiKeyLeakageTest.java
healer-core/src/main/java/.../engine/LocatorRecommender.java
healer-report/src/main/java/.../VisualDiffGenerator.java
```

### Modified Files (12)
```
healer-core/src/main/java/.../engine/HealingEngine.java
healer-core/src/main/java/.../config/HealerConfig.java
healer-core/src/main/java/.../config/AutoUpdateConfig.java
healer-core/src/main/java/.../util/JsonUtils.java
healer-core/src/main/java/.../config/ConfigLoader.java
healer-core/src/main/java/.../engine/patch/SourceCodeUpdater.java
healer-selenium/src/main/java/.../driver/HealingWebDriver.java
healer-llm/src/main/java/.../providers/BedrockProvider.java
healer-llm/src/main/java/.../LlmOrchestrator.java
healer-cli/src/main/java/.../HealerCli.java
healer-cli/src/main/java/.../commands/*.java
healer-report/src/main/java/.../ReportGenerator.java
healer-cucumber/src/main/java/.../report/HealerCucumberReportPlugin.java
```

---

## Success Criteria

- [ ] Test coverage increased from 15% to 60%+
- [ ] CLI module has 80%+ coverage
- [ ] Report module has 80%+ coverage
- [ ] NotificationService fires on healing events
- [ ] PatternSharingService reduces LLM calls by 20%+
- [ ] No RuntimeException in production code
- [ ] Circuit breaker protects against cost overruns
- [ ] Cache handles 100K entries without memory issues
- [ ] Dry-run mode works for auto-update
- [ ] HTML report includes locator recommendations
