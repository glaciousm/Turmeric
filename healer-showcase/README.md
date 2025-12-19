# Intent Healer Showcase

> **Self-Healing Selenium Tests with ZERO-CODE Integration**

This showcase demonstrates Intent Healer's ability to automatically fix broken element locators in Selenium tests using the **Java Agent approach** - **no code changes required!**

Each test scenario uses **intentionally wrong locators** that would normally cause test failures, but Intent Healer detects the failures and finds the correct elements using intelligent analysis.

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
| **Zero Code Changes** | Uses Java Agent - just add JVM argument |
| **AI-Powered** | Uses LLM or heuristic matching to find elements |
| **Framework Agnostic** | Works with Cucumber, TestNG, JUnit |
| **Non-Intrusive** | Regular WebDriver - agent handles everything |
| **Configurable** | Control healing behavior, guardrails, and policies |

---

## How It Works

This showcase uses the **Java Agent approach** for zero-code integration:

```
+===============================================================+
|           INTENT HEALER AGENT - ACTIVE                        |
+---------------------------------------------------------------+
|  Mode:       AUTO_SAFE                                        |
|  Provider:   ollama                                           |
|  Model:      llama3.1                                         |
|  Healing:    ENABLED                                          |
+===============================================================+

  Self-healing is active for all WebDriver instances.
  Broken locators will be automatically fixed at runtime.
```

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
│  3. Java Agent intercepts exception (ByteBuddy)                │
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

**No HealingWebDriver wrapper needed!** The agent automatically intercepts all WebDriver instances.

---

## Test Results

All 10 showcase tests pass successfully, demonstrating healing across various locator failure types:

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running io.github.glaciousm.showcase.runners.ShowcaseRunner

============================================================
  Intent Healer Showcase - ZERO-CODE Integration Demo
============================================================
  Integration:   Java Agent (no code changes needed!)
  Mode:          AUTO_SAFE
  LLM Provider:  ollama
  Auto-Healing:  ENABLED (via agent interception)
============================================================

  This demo uses regular WebDriver - NO HealingWebDriver!
  The Java Agent automatically adds self-healing.

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
+================================================================================+
|                    INTENT HEALER - HEALING SUMMARY                             |
+--------------------------------------------------------------------------------+
|  Total healed locators: 10                                                     |
+================================================================================+

  [1] I click the login button using wrong ID "login-btn"
      | ORIGINAL:  By.id: login-btn
      | HEALED TO: By.cssSelector: button.radius
      | Confidence: 93%

  [2] I click the checkbox using wrong class "checkbox-input"
      | ORIGINAL:  By.className: checkbox-input
      | HEALED TO: By.cssSelector: input[type='checkbox']
      | Confidence: 95%

  ... (all heals listed)

  TIP: Update your Page Objects with the healed locators above to prevent
       repeated healing and improve test execution speed.
```

This actionable output helps you update your source code with the correct locators.

| Test | Wrong Locator | Real Element | Healed? |
|------|---------------|--------------|---------|
| 1 | `By.id("login-btn")` | `<button class="radius">` | Yes |
| 2 | `By.className("checkbox-input")` | `<input type="checkbox">` | Yes |
| 3 | `By.linkText("Add Element")` | `<button>Add Element</button>` | Yes |
| 4 | `By.cssSelector("select.dropdown-menu")` | `<select id="dropdown">` | Yes |
| 5 | `By.xpath("//form[@id='login-form']/...")` | `<button class="radius">` | Yes |
| 6 | `By.cssSelector("input.submit-btn")` | `<button class="radius">` | Yes |
| 7 | `By.id("btn-action-12345")` | `<a class="button">` | Yes |
| 8 | `By.cssSelector("input[type='submit']")` | `<button>Start</button>` | Yes |
| 9 | `By.id("add-button")` | `<button>Add Element</button>` | Yes |
| 10 | `By.cssSelector("table#users tr[data-id='1']...")` | `<a href="#edit">` | Yes |

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

- **Wrong**: `By.className("checkbox-input")` - No such class
- **Real**: `<input type="checkbox">` (no class attribute)
- **Healed**: Found by input type attribute

---

### Test 3: Wrong Tag Type
**Scenario**: Looking for `<a>` when element is `<button>`

- **Wrong**: `By.linkText("Add Element")` - Looks for anchor tag
- **Real**: `<button onclick="addElement()">Add Element</button>`
- **Healed**: Found button with matching text content

---

### Tests 4-10: More Scenarios

Each test demonstrates a different type of locator failure:
- Wrong CSS selector
- Wrong XPath
- Dynamic IDs
- Wrong element types
- Wrong nested selectors

All are healed automatically by the Java Agent.

---

## Project Structure

```
healer-showcase/
├── pom.xml                                    # Maven config with Java Agent
├── README.md                                  # This file
│
├── src/main/java/io/github/glaciousm/showcase/
│   └── pages/
│       └── HerokuappPages.java               # Page Objects (wrong locators)
│
└── src/test/
    ├── java/io/github/glaciousm/showcase/
    │   ├── config/
    │   │   └── ShowcaseConfig.java           # Regular WebDriver setup
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
4. **Intent Healer** project built (including healer-agent)

### Build and Run

```bash
# From the project root directory

# 1. Build all modules (including healer-agent)
mvn clean install -DskipTests

# 2. Run the showcase tests
mvn test -pl healer-showcase

# Or run with verbose output
mvn test -pl healer-showcase -Dtest=ShowcaseRunner
```

### Expected Output

You should see:
- Java Agent banner printed on startup
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

  # LLM Provider - Using Ollama (local LLM)
  llm:
    provider: ollama
    model: llama3.1
    endpoint: http://localhost:11434
    timeout_seconds: 120
    max_retries: 2
    temperature: 0.1

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

### Zero-Code Integration (Java Agent)

The easiest way to add self-healing to your project - **no code changes required!**

#### Step 1: Build the Agent

```bash
mvn clean install -pl healer-agent
```

#### Step 2: Create Configuration

Create `src/test/resources/healer-config.yml`:

```yaml
healer:
  mode: AUTO_SAFE
  enabled: true

llm:
  provider: ollama   # or openai, anthropic, mock
  model: llama3.1
  endpoint: http://localhost:11434
```

#### Step 3: Add Agent to Maven Surefire

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <argLine>
            -javaagent:${project.basedir}/../healer-agent/target/healer-agent-1.0.0-SNAPSHOT.jar
        </argLine>
    </configuration>
</plugin>
```

#### Step 4: Use Regular WebDriver

```java
// Just use regular WebDriver - agent handles healing!
WebDriver driver = new ChromeDriver();

// Your existing test code works unchanged!
driver.findElement(By.id("some-locator")).click();

// If "some-locator" breaks, Intent Healer automatically:
// 1. Detects the failure
// 2. Captures the DOM
// 3. Finds the correct element
// 4. Returns it - test continues!
```

**That's it!** No HealingWebDriver wrapper, no code changes.

---

## LLM Provider Options

### Ollama (Default - Local LLM)

The showcase uses Ollama by default for local AI processing. Install and run Ollama:

```bash
ollama pull llama3.1
ollama serve
```

```yaml
llm:
  provider: ollama
  model: llama3.1
  endpoint: http://localhost:11434
```

### OpenAI / Anthropic (Production)

For cloud-based AI with higher accuracy:

```yaml
llm:
  provider: openai      # or anthropic
  model: gpt-4          # or claude-3-sonnet
  api_key: ${OPENAI_API_KEY}
```

### Mock (Testing/Demos)

Uses heuristic matching without any LLM. Good for quick demos or when no LLM is available:

```yaml
llm:
  provider: mock
  model: heuristic
```

---

## Troubleshooting

### Tests Not Running

Ensure all modules are built, including healer-agent:
```bash
mvn clean install -DskipTests
```

### Agent Not Loading

Check that the healer-agent JAR exists:
```bash
ls healer-agent/target/healer-agent-*.jar
```

If missing, rebuild with:
```bash
mvn clean install -pl healer-agent
```

### Healing Not Working

1. Check that the agent banner appears on startup
2. Check `healer-config.yml` has `enabled: true`
3. Verify `mode: AUTO_SAFE`
4. Check logs for healing activity

---

## License

This project is part of the Intent Healer framework.
