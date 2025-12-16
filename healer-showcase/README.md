# Intent Healer Showcase

> **Self-Healing Selenium Tests with AI-Powered Locator Recovery**

This showcase demonstrates Intent Healer's ability to automatically fix broken element locators in Selenium tests. Each test scenario uses **intentionally wrong locators** that would normally cause test failures, but Intent Healer detects the failures and finds the correct elements using intelligent analysis.

---

## Table of Contents

- [Overview](#overview)
- [How It Works](#how-it-works)
- [Test Results](#test-results)
- [Test Scenarios](#test-scenarios)
- [Project Structure](#project-structure)
- [Running the Showcase](#running-the-showcase)
- [Configuration](#configuration)
- [Integration Guide](#integration-guide)

---

## Overview

Intent Healer is a self-healing framework for Selenium-based test automation. When an element locator fails (due to DOM changes, dynamic IDs, or incorrect selectors), Intent Healer:

1. **Captures** the current page state (DOM snapshot)
2. **Analyzes** the failure context and element characteristics
3. **Finds** the correct element using AI-powered matching
4. **Heals** the locator transparently - tests continue without interruption

### Key Benefits

| Feature | Description |
|---------|-------------|
| **Zero Test Maintenance** | Broken locators are fixed automatically |
| **AI-Powered** | Uses LLM or heuristic matching to find elements |
| **Framework Agnostic** | Works with Cucumber, TestNG, JUnit |
| **Non-Intrusive** | Wrap your existing WebDriver - no code changes |
| **Configurable** | Control healing behavior, guardrails, and policies |

---

## How It Works

```
┌─────────────────────────────────────────────────────────────────┐
│                        Test Execution                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. Test calls: driver.findElement(By.id("wrong-id"))          │
│                              │                                  │
│                              ▼                                  │
│  2. NoSuchElementException thrown                              │
│                              │                                  │
│                              ▼                                  │
│  3. HealingWebDriver intercepts exception                      │
│                              │                                  │
│                              ▼                                  │
│  4. SnapshotBuilder captures DOM state                         │
│                              │                                  │
│                              ▼                                  │
│  5. LLM/Heuristic analyzes and finds correct element           │
│                              │                                  │
│                              ▼                                  │
│  6. Healed element returned - test continues!                  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Test Results

All 10 showcase tests pass successfully, demonstrating healing across various locator failure types:

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.intenthealer.showcase.runners.ShowcaseRunner

============================================================
  Intent Healer - Self-Healing Selenium Framework
============================================================
  Mode:          AUTO_SAFE
  LLM Provider:  mock
  Auto-Healing:  ENABLED
============================================================

10 Scenarios (10 passed)
30 Steps (30 passed)
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Healing Success Rate: 100%

Every test uses a **broken locator** that would fail without Intent Healer.

### Healing Summary Output

After tests complete, a summary shows exactly what was healed:

```
╔════════════════════════════════════════════════════════════════════════════╗
║                    INTENT HEALER - HEALING SUMMARY                        ║
╠════════════════════════════════════════════════════════════════════════════╣
║  Total healed locators: 10                                                 ║
╚════════════════════════════════════════════════════════════════════════════╝

  [1] I click the login button using wrong ID "login-btn"
      │ ORIGINAL:  By.id: login-btn
      │ HEALED TO: By.cssSelector: button.radius
      │ Confidence: 93%

  [2] I click the checkbox using wrong class "checkbox-input"
      │ ORIGINAL:  By.className: checkbox-input
      │ HEALED TO: By.cssSelector: input[type='checkbox']
      │ Confidence: 95%

  ... (all heals listed)

  TIP: Update your Page Objects with the healed locators above to prevent
       repeated healing and improve test execution speed.
```

This actionable output helps you update your source code with the correct locators.

| Test | Wrong Locator | Real Element | Healed? |
|------|---------------|--------------|---------|
| 1 | `By.id("login-btn")` | `<button class="radius">` | ✅ |
| 2 | `By.className("checkbox-input")` | `<input type="checkbox">` | ✅ |
| 3 | `By.linkText("Add Element")` | `<button>Add Element</button>` | ✅ |
| 4 | `By.cssSelector("select.dropdown-menu")` | `<select id="dropdown">` | ✅ |
| 5 | `By.xpath("//form[@id='login-form']/...")` | `<button class="radius">` | ✅ |
| 6 | `By.cssSelector("input.submit-btn")` | `<button class="radius">` | ✅ |
| 7 | `By.id("btn-action-12345")` | `<a class="button">` | ✅ |
| 8 | `By.cssSelector("input[type='submit']")` | `<button>Start</button>` | ✅ |
| 9 | `By.id("add-button")` | `<button>Add Element</button>` | ✅ |
| 10 | `By.cssSelector("table#users tr[data-id='1']...")` | `<a href="#edit">` | ✅ |

---

## Test Scenarios

### Test 1: Wrong ID
**Scenario**: Login button with non-existent ID

```gherkin
Scenario: Login button with wrong ID
  Given I am on the Herokuapp login page
  When I enter username "tomsmith"
  And I enter password "SuperSecretPassword!"
  And I click the login button using wrong ID "login-btn"
  Then I should see the secure area
```

- **Wrong**: `By.id("login-btn")` - This ID doesn't exist
- **Real**: `<button class="radius" type="submit">`
- **Healed**: Found by button type and class attributes

---

### Test 2: Wrong Class Name
**Scenario**: Checkbox with non-existent class

```gherkin
Scenario: Checkbox with wrong class name
  Given I am on the Herokuapp checkboxes page
  When I click the checkbox using wrong class "checkbox-input"
  Then the checkbox should be checked
```

- **Wrong**: `By.className("checkbox-input")` - No such class
- **Real**: `<input type="checkbox">` (no class attribute)
- **Healed**: Found by input type attribute

---

### Test 3: Wrong Tag Type
**Scenario**: Looking for `<a>` when element is `<button>`

```gherkin
Scenario: Add button mistaken for link
  Given I am on the Herokuapp add remove elements page
  When I click add element using link text "Add Element"
  Then a delete button should appear
```

- **Wrong**: `By.linkText("Add Element")` - Looks for anchor tag
- **Real**: `<button onclick="addElement()">Add Element</button>`
- **Healed**: Found button with matching text content

---

### Test 4: Wrong CSS Selector
**Scenario**: Dropdown with incorrect class selector

```gherkin
Scenario: Dropdown with wrong CSS selector
  Given I am on the Herokuapp dropdown page
  When I select "Option 1" from dropdown using wrong CSS "select.dropdown-menu"
  Then the dropdown should show "Option 1"
```

- **Wrong**: `By.cssSelector("select.dropdown-menu")` - Wrong class
- **Real**: `<select id="dropdown">`
- **Healed**: Found by tag name and ID

---

### Test 5-6: Wrong XPath/CSS for Login
**Scenario**: Various incorrect selectors for login button

- **Test 5**: Wrong XPath with non-existent form structure
- **Test 6**: CSS selector looking for input instead of button

Both tests successfully heal to find the login button.

---

### Test 7: Dynamic ID
**Scenario**: Challenging DOM with fake dynamic ID

```gherkin
Scenario: Challenging DOM with dynamic ID
  Given I am on the Herokuapp challenging DOM page
  When I click the first action button using wrong ID "btn-action-12345"
  Then the page should remain functional
```

- **Wrong**: `By.id("btn-action-12345")` - Fake UUID-style ID
- **Real**: `<a class="button" href="#edit">edit</a>`
- **Healed**: Found by class and link text

---

### Test 8: Wrong Element Type
**Scenario**: Looking for input when element is button

```gherkin
Scenario: Dynamic loading start button with wrong type
  Given I am on the Herokuapp dynamic loading page
  When I click start using wrong element type "input[type='submit']"
  And I wait for the loading to complete
  Then the finish message should be visible
```

- **Wrong**: `By.cssSelector("input[type='submit']")` - Wrong tag
- **Real**: `<button>Start</button>`
- **Healed**: Found button with matching text

---

### Test 9-10: More Complex Scenarios
**Scenario**: Additional healing demonstrations

- **Test 9**: Wrong ID for Add Element button
- **Test 10**: Wrong nested CSS selector in table structure

---

## Project Structure

```
healer-showcase/
├── pom.xml                                    # Maven configuration
├── README.md                                  # This file
│
├── src/main/java/com/intenthealer/showcase/
│   ├── config/
│   │   └── ShowcaseConfig.java               # HealingWebDriver setup
│   └── pages/
│       └── HerokuappPages.java               # Page Objects (wrong locators)
│
└── src/test/
    ├── java/com/intenthealer/showcase/
    │   ├── hooks/
    │   │   └── ShowcaseHooks.java            # Cucumber Before/After
    │   ├── runners/
    │   │   └── ShowcaseRunner.java           # JUnit 5 test runner
    │   └── steps/
    │       └── ShowcaseSteps.java            # Step definitions
    │
    └── resources/
        ├── features/
        │   └── self_healing_showcase.feature # 10 test scenarios
        └── healer-config.yml                 # Intent Healer config
```

---

## Running the Showcase

### Prerequisites

1. **Java 21+** installed
2. **Maven 3.8+** installed
3. **Chrome browser** installed
4. **Intent Healer** project built

### Build and Run

```bash
# From the project root directory

# 1. Build all modules
mvn clean install -DskipTests

# 2. Run the showcase tests
mvn test -pl healer-showcase

# Or run with verbose output
mvn test -pl healer-showcase -Dtest=ShowcaseRunner
```

### Expected Output

You should see:
- Chrome browser opens
- Tests navigate to https://the-internet.herokuapp.com
- Each test demonstrates healing (logged in console)
- All 10 tests pass

---

## Configuration

### healer-config.yml

```yaml
healer:
  # Healing Mode
  mode: AUTO_SAFE
  enabled: true

  # LLM Provider (mock = heuristic-based, no external LLM required)
  llm:
    provider: mock
    model: heuristic
    timeout_seconds: 60
    max_retries: 2

  # Guardrails
  guardrails:
    min_confidence: 0.75
    max_heals_per_scenario: 10
    forbidden_url_patterns:
      - /admin/
      - /delete/
      - /payment/

  # Caching (disabled for showcase)
  cache:
    enabled: false

  # Reporting
  report:
    enabled: true
    output_dir: ./target/healer-reports
    formats:
      - json
      - html
```

### Configuration Options

| Setting | Description | Values |
|---------|-------------|--------|
| `mode` | Healing policy | `AUTO_SAFE`, `CONFIRM`, `OFF` |
| `llm.provider` | LLM provider | `mock`, `openai`, `anthropic`, `ollama` |
| `guardrails.min_confidence` | Minimum confidence to accept heal | 0.0 - 1.0 |
| `cache.enabled` | Cache healing decisions | `true`, `false` |

---

## Integration Guide

### Step 1: Add Dependencies

```xml
<dependency>
    <groupId>com.intenthealer</groupId>
    <artifactId>healer-selenium</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>com.intenthealer</groupId>
    <artifactId>healer-llm</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Step 2: Create Configuration

Create `src/test/resources/healer-config.yml`:

```yaml
healer:
  mode: AUTO_SAFE
  enabled: true
  llm:
    provider: ollama  # or openai, anthropic
    model: llama3.2
```

### Step 3: Wrap Your WebDriver

```java
import com.intenthealer.core.config.ConfigLoader;
import com.intenthealer.core.config.HealerConfig;
import com.intenthealer.core.engine.HealingEngine;
import com.intenthealer.selenium.driver.HealingWebDriver;

public class TestConfig {

    public HealingWebDriver createDriver() {
        // Load configuration
        HealerConfig config = new ConfigLoader().load();

        // Create healing engine
        HealingEngine engine = new HealingEngine(config);

        // Create standard WebDriver
        WebDriver chromeDriver = new ChromeDriver();

        // Wrap with HealingWebDriver
        return new HealingWebDriver(chromeDriver, engine, config);
    }
}
```

### Step 4: Use Normally

```java
// Your existing test code works unchanged!
driver.findElement(By.id("some-locator")).click();

// If "some-locator" breaks, Intent Healer automatically:
// 1. Detects the failure
// 2. Captures the DOM
// 3. Finds the correct element
// 4. Returns it - test continues!
```

---

## LLM Provider Options

### Mock (Default - No External LLM)

Uses heuristic matching based on element attributes, text content, and DOM position. Great for testing and demos.

```yaml
llm:
  provider: mock
  model: heuristic
```

### Ollama (Local LLM)

Run AI locally with Ollama:

```bash
# Install Ollama and pull a model
ollama pull llama3.2
```

```yaml
llm:
  provider: ollama
  model: llama3.2
  base_url: http://localhost:11434
```

### OpenAI

```yaml
llm:
  provider: openai
  model: gpt-4
  api_key: ${OPENAI_API_KEY}
```

### Anthropic Claude

```yaml
llm:
  provider: anthropic
  model: claude-3-sonnet
  api_key: ${ANTHROPIC_API_KEY}
```

---

## Troubleshooting

### Tests Not Running

Ensure all modules are built:
```bash
mvn clean install -DskipTests
```

### Chrome Driver Issues

WebDriverManager should auto-download ChromeDriver. If issues persist:
```bash
# Check Chrome version
google-chrome --version

# Manually set driver path if needed
export CHROME_DRIVER=/path/to/chromedriver
```

### Healing Not Working

1. Check `healer-config.yml` has correct format (root `healer:` key)
2. Verify `mode: AUTO_SAFE` and `enabled: true`
3. Check logs for healing activity

---

## License

This project is part of the Intent Healer framework.

---

## Contact

For questions or support, please open an issue in the repository.
