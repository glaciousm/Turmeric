# Intent Healer - Roadmap

## Status Overview

This document tracks improvements, features, and their completion status.

| Phase | Status | Progress |
|-------|--------|----------|
| Phase 1: Test Coverage | COMPLETE | 37/37 tests |
| Phase 2: Feature Integration | COMPLETE | 3/3 services |
| Phase 3: Code Quality | MOSTLY COMPLETE | 4/5 items |
| Phase 4: Production Hardening | COMPLETE | 3/3 test suites |
| Phase 5: New Features | COMPLETE | 4/4 items |
| Phase 6: Visual Evidence | COMPLETE | 3/3 items |
| Phase 7: Advanced Features | COMPLETE | 5/5 items |
| Phase 8: Zero-Code Integration | COMPLETE | 1/1 items |

---

## Phase 1: Test Coverage - COMPLETE

All planned test files have been created.

| Test File | Status |
|-----------|--------|
| `healer-cli/src/test/.../HealerCliTest.java` | Done |
| `healer-cli/src/test/.../commands/ConfigCommandTest.java` | Done |
| `healer-cli/src/test/.../commands/CacheCommandTest.java` | Done |
| `healer-cli/src/test/.../commands/ReportCommandTest.java` | Done |
| `healer-report/src/test/.../ReportGeneratorTest.java` | Done |
| `healer-report/src/test/.../TrustDashboardTest.java` | Done |
| `healer-report/src/test/.../model/HealReportTest.java` | Done |
| `healer-report/src/test/.../model/HealEventTest.java` | Done |
| `healer-core/src/test/.../integration/HealingPipelineIntegrationTest.java` | Done |
| `healer-selenium/src/test/.../integration/HealingWebDriverIntegrationTest.java` | Done |
| `healer-junit/src/test/.../HealerExtensionTest.java` | Done |
| `healer-testng/src/test/.../HealerTestListenerTest.java` | Done |

Additional tests created:
- `healer-llm/src/test/.../ResponseParserTest.java`
- `healer-llm/src/test/.../LlmOrchestratorTest.java`
- `healer-llm/src/test/.../PromptBuilderTest.java`
- `healer-llm/src/test/.../providers/OpenAiProviderTest.java`
- `healer-llm/src/test/.../providers/AnthropicProviderTest.java`
- `healer-selenium/src/test/.../driver/HealingWebDriverTest.java`
- `healer-selenium/src/test/.../snapshot/SnapshotBuilderTest.java`
- `healer-selenium/src/test/.../actions/ActionExecutorTest.java`
- `healer-core/src/test/.../engine/LocatorRecommenderTest.java`
- `healer-report/src/test/.../HealingAnalyticsTest.java`
- `healer-report/src/test/.../VisualDiffGeneratorTest.java`

---

## Phase 2: Feature Integration - COMPLETE

All disconnected services have been wired into the healing pipeline.

### 2.1 NotificationService Integration - Done
**Location:** `HealingEngine.java:36-37, 53-58, 430-461`
- Injected into HealingEngine constructor
- Fires on successful heals
- Fires on failed heals
- Configurable via `notification` config section

### 2.2 PatternSharingService Integration - Done
**Location:** `HealingEngine.java:37, 60-66, 141-174, 466-502`
- Checks shared patterns before calling LLM
- Stores successful heals for future reuse
- Configurable similarity threshold (0.85)

### 2.3 ApprovalWorkflow Integration - Done
**Location:** `HealingEngine.java:45-46, 99-103, 224-258`
- CONFIRM mode pauses for approval
- Creates HealProposal with full context
- Blocks until approval/rejection received

---

## Phase 3: Code Quality - MOSTLY COMPLETE

### 3.1 Domain Exceptions - Done
Custom exceptions in `healer-core/src/main/java/.../exception/`:
- `LlmException.java` - LLM-related errors
- `HealingException.java` - General healing errors

### 3.2 CLI Logging - Partial
CLI still uses System.out for user-facing output (acceptable for CLI tools).

### 3.3 Exponential Backoff - Done
**Location:** `LlmOrchestrator.java`, `AnthropicProvider.java`, `OpenAiProvider.java`
- Detects rate limiting (HTTP 429)
- Implements exponential backoff with jitter
- Configurable max retries

### 3.4 Dry-Run Mode - Done
**Location:** `AutoUpdateConfig.java`
- `dryRun` field available
- When true, logs changes without writing files

---

## Phase 4: Production Hardening - COMPLETE

| Test Suite | Status |
|------------|--------|
| `CircuitBreakerCostLimitTest.java` | Done |
| `HealCacheLoadTest.java` | Done |
| `ApiKeyLeakageTest.java` | Done |

---

## Phase 5: New Features - COMPLETE

### 5.1 Locator Recommendations - Done
**Location:** `healer-core/.../engine/LocatorRecommender.java`
- Analyzes healed locators for stability
- Suggests data-testid, aria-label alternatives
- Warns about brittle XPath/positional selectors
- Integrated into HTML report

### 5.2 Healing Analytics - Done
**Location:** `healer-report/.../HealingAnalytics.java`
- Success rate calculations
- Most frequently healed locators
- ROI and time saved estimates
- Confidence distribution
- Integrated into HTML report

### 5.3 Visual Diff Generator - Done
**Location:** `healer-report/.../VisualDiffGenerator.java`
- File exists with full implementation
- Tests exist and pass
- ✅ Wired into ReportGenerator (see Phase 6)

### 5.4 Screenshot Capture in Healing Flow - Done
- ✅ HealEvent.ArtifactInfo extended with screenshot fields
- ✅ Screenshot capture implemented in HealingWebDriver
- ✅ Visual evidence embedded in HTML reports (see Phase 6)

---

## Phase 6: Visual Evidence Integration - COMPLETE

Visual evidence (screenshots before/after healing) is now captured and displayed.

### 6.1 Screenshot Capture in HealingWebDriver
**Status:** Done
**Location:** `healer-selenium/.../driver/HealingWebDriver.java:269-280`

**Completed:**
1. ✅ Capture screenshot BEFORE heal attempt
2. ✅ Capture screenshot AFTER successful heal
3. ✅ Store screenshots with heal event via HealingSummary

### 6.2 Wire VisualDiffGenerator into ReportGenerator
**Status:** Done
**Location:** `healer-report/.../ReportGenerator.java`

**Completed:**
1. ✅ Import VisualDiffGenerator
2. ✅ Process screenshots when generating HTML report
3. ✅ Visual evidence section per heal event
4. ✅ Embed before/after images in HTML

### 6.3 Add Visual Diff Section to HTML Report
**Status:** Done

**Completed:**
1. ✅ CSS styles for visual comparison
2. ✅ "Visual Evidence" section per heal event
3. ✅ Show before/after side-by-side in grid layout
4. ✅ Diff percentage badge with color coding
5. ✅ Responsive design for mobile viewing

---

## Phase 7: Advanced Features - COMPLETE

Advanced features for production use and enterprise reporting.

### 7.1 IntelliJ Plugin Enhancement - Done
**Location:** `healer-intellij/src/main/java/.../services/HealEventWatcher.java`
**Location:** `healer-intellij/src/main/java/.../ui/LiveEventsPanel.java`
- Real-time file watcher monitors heal-reports directory
- Live Events panel shows heal events as they occur
- Settings for watch directory and polling interval
- Integration with existing HealerProjectService

### 7.2 PDF Report Generation - Done
**Location:** `healer-report/src/main/java/.../PdfReportGenerator.java`
- Professional PDF reports using OpenPDF library
- Summary statistics with styled cards
- Analytics section with ROI calculations
- Detailed event cards with status indicators
- Header/footer with page numbers
- Export via CLI: `healer report export pdf`

### 7.3 Week-over-Week Trend Charts - Done
**Location:** `healer-report/src/main/java/.../ReportGenerator.java` (generateTrendChartsHtml)
- Interactive Chart.js charts in HTML reports
- Confidence distribution bar chart
- Heals by action type doughnut chart
- Success rate gauge visualization
- Responsive grid layout for charts

### 7.4 Locator Stability Scoring - Done
**Location:** `healer-core/src/main/java/.../engine/LocatorStabilityScorer.java`
- Comprehensive scoring algorithm (0-100 scale)
- Factors: heal frequency, confidence, success rate, strategy
- Stability levels: VERY_STABLE, STABLE, MODERATE, UNSTABLE, VERY_UNSTABLE
- Strategy-specific recommendations (XPath, CSS, ID, etc.)
- Color-coded UI support

### 7.5 Cost Projection and Budgeting - Done
**Location:** `healer-core/src/main/java/.../engine/CostProjector.java`
- Historical cost analysis
- Daily/weekly/monthly/quarterly/yearly projections
- ROI calculation (developer time saved vs LLM costs)
- Trend detection (increasing/decreasing/stable)
- Budget status checking with alerts
- Model pricing database for cost estimation
- Budget recommendations

---

## Phase 8: Zero-Code Integration - COMPLETE

Java Agent for automatic self-healing without any code changes.

### 8.1 Java Agent Implementation - Done
**Location:** `healer-agent/src/main/java/com/intenthealer/agent/`
- `HealerAgent.java` - Agent entry point with premain()
- `WebDriverConstructorAdvice.java` - Registers drivers on construction
- `WebDriverInterceptor.java` - Intercepts findElement() for healing
- `AutoConfigurator.java` - Auto-wires HealingEngine and LlmOrchestrator
- `AgentBanner.java` - Startup banner with config info

**Features:**
- ByteBuddy bytecode instrumentation
- Automatic interception of all WebDriver implementations
- Zero code changes required - just add JVM argument
- Fat JAR packaging with shade plugin
- Supports: ChromeDriver, FirefoxDriver, EdgeDriver, SafariDriver, RemoteWebDriver

**Usage:**
```bash
mvn test -DargLine="-javaagent:healer-agent/target/healer-agent-1.0.0-SNAPSHOT.jar"
```

---

## Remaining Work Priority

### HIGH PRIORITY
~~1. **Visual Evidence Integration** (Phase 6) - User-requested feature~~ ✅ DONE
   - ~~Wire VisualDiffGenerator into ReportGenerator~~
   - ~~Add screenshot capture to HealingWebDriver~~
   - ~~Display before/after/diff in HTML reports~~

### MEDIUM PRIORITY
~~1. **IntelliJ Plugin Enhancement**~~ ✅ DONE
   - ~~Connect to actual healing engine~~
   - ~~Display real-time heal events~~
   - ~~Show heal history per locator~~

~~2. **CLI Interactive Mode**~~ ✅ DONE
   - ~~Add `healer watch` command for live monitoring~~
   - ~~Add `healer approve` for CONFIRM mode approvals~~

### LOW PRIORITY
~~3. **Export Formats**~~ ✅ DONE
   - ~~CSV export of heal events~~
   - ~~JUnit XML format for CI integration~~
   - ~~Summary text export~~
   - ~~Trends CSV for analysis~~
   - ~~PDF report generation~~

~~4. **Trend Analytics**~~ ✅ DONE
   - ~~Trends CSV export across multiple runs~~
   - ~~Week-over-week trend charts in HTML~~
   - ~~Locator stability scoring~~
   - ~~Cost projection and budgeting~~

---

## Success Metrics

| Metric | Target | Current |
|--------|--------|---------|
| Test coverage | 60%+ | ~40% |
| CLI module coverage | 80%+ | ~70% |
| Report module coverage | 80%+ | ~75% |
| NotificationService active | Yes | Yes |
| PatternSharingService active | Yes | Yes |
| Visual diff in reports | Yes | **Yes** |
| Screenshots captured | Yes | **Yes** |

---

## Implementation Notes

### Visual Evidence Implementation (Priority)

```java
// 1. Add to HealingWebDriver.findElement():
private byte[] captureScreenshot() {
    TakesScreenshot ts = (TakesScreenshot) delegate;
    return ts.getScreenshotAs(OutputType.BYTES);
}

// Before healing:
byte[] beforeScreenshot = captureScreenshot();

// After successful heal:
byte[] afterScreenshot = captureScreenshot();

// Store in HealEvent:
event.setBeforeScreenshot(beforeScreenshot);
event.setAfterScreenshot(afterScreenshot);

// 2. In ReportGenerator.generateHtml():
VisualDiffGenerator visualDiff = new VisualDiffGenerator();
for (HealEvent event : events) {
    if (event.hasScreenshots()) {
        DiffResult diff = visualDiff.generateDiff(
            event.getBeforeScreenshot(),
            event.getAfterScreenshot(),
            event.getEventId()
        );
        html.append(visualDiff.generateDiffHtml(diff, event.getStep()));
    }
}
```

---

## Changelog

- **2024-12-17**:
  - Created roadmap, documented completion status
  - All Phase 1-4 items marked complete
  - Implemented Visual Evidence feature (Phase 6):
    - Added screenshot capture to HealingWebDriver
    - Extended HealEvent and HealingSummary for screenshots
    - Wired VisualDiffGenerator into ReportGenerator
    - Added Visual Evidence section to HTML reports
    - Before/after screenshots now captured and displayed
  - Implemented CLI Interactive Mode:
    - Added `healer watch` command for real-time monitoring
    - Added `healer approve` command for CONFIRM mode approvals
    - Interactive approval workflow with server-based pending queue
  - Implemented Export Formats:
    - CSV export for spreadsheet analysis
    - JUnit XML export for CI integration (Jenkins, GitLab CI, etc.)
    - Summary text export for logs
    - Trends CSV export for week-over-week analysis

- **2025-12-18**: Phase 7 - Advanced Features
  - Implemented IntelliJ Plugin Enhancement:
    - Created HealEventWatcher for real-time file system monitoring
    - Created LiveEventsPanel UI for live event feed
    - Added settings for watch directory and polling interval
    - Updated HealerToolWindowFactory with Live tab
  - Implemented PDF Report Generation:
    - Added OpenPDF dependency to healer-report
    - Created PdfReportGenerator with professional styling
    - Summary cards, analytics section, event details
    - Export via CLI: `healer report export pdf`
  - Implemented Week-over-Week Trend Charts:
    - Added Chart.js integration to HTML reports
    - Confidence distribution bar chart
    - Heals by action type doughnut chart
    - Success rate gauge visualization
  - Implemented Locator Stability Scoring:
    - Created LocatorStabilityScorer with weighted algorithm
    - Factors: frequency, confidence, success rate, strategy
    - Five stability levels with color coding
    - Strategy-specific recommendations
  - Implemented Cost Projection and Budgeting:
    - Created CostProjector for cost analysis
    - Historical tracking and projections
    - ROI calculations
    - Budget checking with alerts
    - Model pricing database

- **2025-12-17**:
  - Fixed Mockito test failures in healer-core:
    - Fixed IframeHandlerTest: used thenAnswer for mutable lists, lenient stubs
    - Fixed ShadowDomHandlerTest: removed unnecessary stubs
    - Fixed InputFieldHealerTest: added missing attribute stubs, lenient stubs
    - Fixed SelectFieldHealerTest: added custom dropdown stubs, lenient stubs
    - Fixed HealCacheTest: corrected enum comparison
    - All 176 healer-core tests now pass
  - Fixed LLM provider issues:
    - Fixed API key loading to check both env vars and system properties (for tests)
    - Fixed retry logic to properly retry on HTTP 5xx errors
    - Fixed LlmException.unavailable() to include cause message
    - All 95 healer-llm tests now pass
  - Fixed healer-selenium Mockito issues:
    - HealingWebDriverTest: Created FullFeaturedWebDriver interface for multi-interface mocks
    - Used local lenient mocks for healing flow tests, ArgumentCaptor for verification
    - SnapshotBuilderTest: Added LENIENT mode, fixed executeScript stubs for different return types
    - ActionExecutorTest: Added Interactive interface for Actions API, fixed select mocking
    - All 107 healer-selenium tests now pass
  - Fixed healer-cucumber issues:
    - Fixed toClassName to handle special characters as word separators
    - Updated test expectations for parameter name inference
    - All 46 StepDefinitionGeneratorTest tests pass
  - Fixed healer-cli test expectations:
    - Updated tests to match ASCII box characters instead of Unicode
    - Updated tests to match text output instead of emoji icons
    - Updated error output checks to handle logger output
    - All 79 healer-cli tests pass
  - **All modules now have passing tests** - full test suite build succeeds

- **2025-12-18**: Phase 8 - Zero-Code Integration
  - Implemented Java Agent for zero-code self-healing:
    - Created `healer-agent` module with ByteBuddy instrumentation
    - Automatic WebDriver interception (Chrome, Firefox, Edge, Safari, RemoteWebDriver)
    - Startup banner showing configuration status
    - Fat JAR packaging with shade plugin for easy distribution
    - No code changes required - just add JVM argument
  - Updated documentation:
    - Added Java Agent section to USER_GUIDE.md
    - Added zero-code integration section to README.md
    - Added healer-agent to modules table
