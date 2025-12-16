# Auto-Update Source Code After Validated Heals

## Problem Statement

When Intent Healer successfully heals a locator and the test passes, it proves the heal was correct. However, the source code is never updated, causing:
- Repeated element lookup failures on every run
- Cache lookup overhead
- Heal application time
- Wasted execution time across hundreds of tests

**Goal**: Automatically update source code when a heal is validated by a passing test.

---

## Current Architecture Gaps

| Component | Current State | Gap |
|-----------|--------------|-----|
| `LocatorPatchGenerator` | Has `sourceFile`, `lineNumber` fields | Never populated |
| `FailureContext` | Has exception, locator, step | No source location |
| `HealResult` | Has healedLocator, confidence | No source location |
| `HealEvent` | Complete heal tracking | No source location |
| Test Frameworks | Have success hooks | No auto-update trigger |

---

## Implementation Plan

### Phase 1: Capture Source Location

**Objective**: Extract source file and line number from exception stack trace

#### 1.1 Add SourceLocation Model
**File**: `healer-core/src/main/java/com/intenthealer/core/model/SourceLocation.java` (NEW)

```java
public class SourceLocation {
    private final String filePath;      // Full path to source file
    private final String className;     // Fully qualified class name
    private final String methodName;    // Method containing locator
    private final int lineNumber;       // Line number
    private final String locatorCode;   // The actual code line
}
```

#### 1.2 Create StackTraceAnalyzer Utility
**File**: `healer-core/src/main/java/com/intenthealer/core/util/StackTraceAnalyzer.java` (NEW)

- Analyze exception stack trace to find test/step class
- Filter out framework classes (selenium, intenthealer, junit, testng)
- Extract first user test class frame
- Resolve source file path from classpath

#### 1.3 Update FailureContext
**File**: `healer-core/src/main/java/com/intenthealer/core/model/FailureContext.java`

- Add `sourceLocation` field
- Update builder to accept source location

#### 1.4 Update HealingWebDriver
**File**: `healer-selenium/src/main/java/com/intenthealer/selenium/driver/HealingWebDriver.java`

- In `handleFindElementFailure()`, capture stack trace
- Use `StackTraceAnalyzer` to extract source location
- Pass to `FailureContext`

---

### Phase 2: Track Validated Heals

**Objective**: Identify heals that are validated by passing tests

#### 2.1 Create ValidatedHeal Model
**File**: `healer-core/src/main/java/com/intenthealer/core/model/ValidatedHeal.java` (NEW)

```java
public class ValidatedHeal {
    private final String healId;
    private final SourceLocation sourceLocation;
    private final String originalLocator;
    private final String healedLocator;
    private final double confidence;
    private final String testName;
    private final Instant validatedAt;
}
```

#### 2.2 Create ValidatedHealRegistry
**File**: `healer-core/src/main/java/com/intenthealer/core/engine/patch/ValidatedHealRegistry.java` (NEW)

- Store pending heals during test execution
- Mark heals as validated when test passes
- Provide list of validated heals for auto-update

#### 2.3 Update HealResult
**File**: `healer-core/src/main/java/com/intenthealer/core/model/HealResult.java`

- Add `sourceLocation` field
- Flow source location through healing pipeline

---

### Phase 3: Auto-Update Service

**Objective**: Apply validated patches to source code

#### 3.1 Create SourceCodeUpdater Service
**File**: `healer-core/src/main/java/com/intenthealer/core/engine/patch/SourceCodeUpdater.java` (NEW)

```java
public class SourceCodeUpdater {
    public UpdateResult updateSource(ValidatedHeal heal);
    public List<UpdateResult> applyAllValidated(List<ValidatedHeal> heals);
    public void rollback(UpdateResult result);
}
```

**Logic**:
1. Read source file at `sourceLocation.filePath`
2. Find line at `sourceLocation.lineNumber`
3. Parse locator pattern (By.id, By.xpath, By.cssSelector, etc.)
4. Replace old locator value with new healed locator
5. Write updated file
6. Create backup for rollback

#### 3.2 Create Locator Pattern Matchers
**File**: `healer-core/src/main/java/com/intenthealer/core/engine/patch/LocatorPatternMatcher.java` (NEW)

Regex patterns for:
- `By.id("...")` → `By.id("newValue")`
- `By.xpath("...")` → `By.xpath("newValue")`
- `By.cssSelector("...")` → `By.cssSelector("newValue")`
- `By.name("...")` → `By.name("newValue")`
- `By.className("...")` → `By.className("newValue")`
- `@FindBy(id = "...")` → `@FindBy(id = "newValue")`

#### 3.3 Configuration
**File**: `healer-core/src/main/java/com/intenthealer/core/config/AutoUpdateConfig.java` (NEW)

```yaml
auto_update:
  enabled: true                    # Master switch
  min_confidence: 0.85            # Only update high-confidence heals
  require_test_pass: true         # Only update after test passes
  backup_enabled: true            # Create .bak files
  backup_dir: .healer/backups
  exclude_patterns:               # Don't update these files
    - "**/src/test/resources/**"
    - "**/*IT.java"
```

---

### Phase 4: Test Framework Integration

**Objective**: Trigger auto-update after tests pass

#### 4.1 Update HealerExtension (JUnit)
**File**: `healer-junit/src/main/java/com/intenthealer/junit/HealerExtension.java`

In `testSuccessful()`:
```java
@Override
public void testSuccessful(ExtensionContext context) {
    // Existing report finalization
    finalizeReport(context, "PASSED");

    // NEW: Trigger auto-update for validated heals
    if (config.getAutoUpdate().isEnabled()) {
        List<ValidatedHeal> heals = registry.getValidatedHeals(testId);
        sourceCodeUpdater.applyAllValidated(heals);
    }
}
```

#### 4.2 Update HealerTestListener (TestNG)
**File**: `healer-testng/src/main/java/com/intenthealer/testng/HealerTestListener.java`

Same pattern in `onTestSuccess()`

#### 4.3 Update HealerCucumberPlugin (Cucumber)
**File**: `healer-cucumber/src/main/java/com/intenthealer/cucumber/HealerCucumberPlugin.java`

Same pattern in `onTestCaseFinished()` when status is PASSED

---

### Phase 5: Reporting & Audit Trail

**Objective**: Track what was auto-updated

#### 5.1 Update HealEvent
**File**: `healer-report/src/main/java/com/intenthealer/report/model/HealEvent.java`

Add:
```java
private SourceLocationInfo sourceLocation;  // Where in source code
private boolean autoUpdated;                // Was source updated?
private String backupPath;                  // Path to backup file
```

#### 5.2 Create Auto-Update Report
**File**: `healer-report/src/main/java/com/intenthealer/report/AutoUpdateReport.java` (NEW)

Generate report showing:
- Files updated
- Before/after locators
- Confidence scores
- Backup locations
- Rollback instructions

---

## Files to Modify

### New Files (8)
```
healer-core/src/main/java/com/intenthealer/core/model/SourceLocation.java
healer-core/src/main/java/com/intenthealer/core/model/ValidatedHeal.java
healer-core/src/main/java/com/intenthealer/core/util/StackTraceAnalyzer.java
healer-core/src/main/java/com/intenthealer/core/engine/patch/ValidatedHealRegistry.java
healer-core/src/main/java/com/intenthealer/core/engine/patch/SourceCodeUpdater.java
healer-core/src/main/java/com/intenthealer/core/engine/patch/LocatorPatternMatcher.java
healer-core/src/main/java/com/intenthealer/core/config/AutoUpdateConfig.java
healer-report/src/main/java/com/intenthealer/report/AutoUpdateReport.java
```

### Modified Files (9)
```
healer-core/src/main/java/com/intenthealer/core/model/FailureContext.java
healer-core/src/main/java/com/intenthealer/core/model/HealResult.java
healer-core/src/main/java/com/intenthealer/core/config/HealerConfig.java
healer-selenium/src/main/java/com/intenthealer/selenium/driver/HealingWebDriver.java
healer-junit/src/main/java/com/intenthealer/junit/HealerExtension.java
healer-testng/src/main/java/com/intenthealer/testng/HealerTestListener.java
healer-cucumber/src/main/java/com/intenthealer/cucumber/HealerCucumberPlugin.java
healer-report/src/main/java/com/intenthealer/report/model/HealEvent.java
docs/USER_GUIDE.md
```

---

## Execution Order

1. **Phase 1**: Source location capture (foundation)
2. **Phase 2**: Validated heal tracking (data model)
3. **Phase 3**: Auto-update service (core feature)
4. **Phase 4**: Framework integration (triggers)
5. **Phase 5**: Reporting (audit trail)

---

## User Decisions

| Decision | Choice |
|----------|--------|
| **Update timing** | After each passing test (immediate) |
| **Confidence threshold** | 85% minimum |
| **Page Object support** | Both `By.*` and `@FindBy` annotations |

---

## Safety Measures

1. **Backup files** before modification (`.bak` extension)
2. **Confidence threshold** - only update heals with ≥85% confidence
3. **Test must pass** - only update after validated by passing test
4. **Exclude patterns** - skip certain file patterns
5. **Dry-run mode** - show what would be updated without changing
6. **Rollback command** - `healer patch rollback` to restore backups

---

## Example Flow

```
1. Test runs: driver.findElement(By.id("login-btn"))
2. NoSuchElementException thrown
3. StackTraceAnalyzer extracts: LoginTest.java:45
4. HealingEngine heals: "login-btn" → "signin-button"
5. Test continues and PASSES
6. testSuccessful() triggered
7. ValidatedHealRegistry marks heal as validated
8. SourceCodeUpdater reads LoginTest.java
9. Line 45: By.id("login-btn") → By.id("signin-button")
10. File saved, backup created
11. Report generated showing update

Next run:
1. Test runs: driver.findElement(By.id("signin-button"))
2. Element found immediately - NO HEALING NEEDED
3. Test passes faster
```
