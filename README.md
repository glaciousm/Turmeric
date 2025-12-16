# Intent Healer

> **Self-Healing Selenium Tests with AI-Powered Locator Recovery**

Intent Healer is an intelligent test automation framework that automatically fixes broken element locators using AI. When your Selenium tests fail due to DOM changes, Intent Healer detects the failure, analyzes the page, and finds the correct element - all transparently without test code changes.

---

## Features

- **Automatic Healing**: Broken locators are fixed at runtime - tests don't fail
- **AI-Powered**: Uses LLM (OpenAI, Anthropic, Ollama) or heuristic matching
- **Framework Integration**: Works with Cucumber, TestNG, JUnit 5
- **Intent Annotations**: Semantic `@Intent`, `@Invariant`, `@Outcome` for smarter healing
- **Configurable Guardrails**: Control when and how healing occurs
- **Caching**: Remember healed locators to speed up subsequent runs
- **Healing Summary**: Console report after each run showing what was healed
- **Reports**: HTML dashboards showing healing activity and trends

---

## Quick Start

### 1. Add Dependencies

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

### 2. Create Configuration

Create `src/test/resources/healer-config.yml`:

```yaml
healer:
  mode: AUTO_SAFE
  enabled: true
  llm:
    provider: mock  # or ollama, openai, anthropic
    model: heuristic
```

### 3. Wrap Your WebDriver

```java
HealerConfig config = new ConfigLoader().load();
HealingEngine engine = new HealingEngine(config);
WebDriver chrome = new ChromeDriver();
HealingWebDriver driver = new HealingWebDriver(chrome, engine, config);

// Use driver normally - healing happens automatically!
driver.findElement(By.id("some-locator")).click();
```

---

## Modules

| Module | Description |
|--------|-------------|
| `healer-core` | Core engine, models, configuration, healing logic |
| `healer-llm` | LLM providers (OpenAI, Anthropic, Ollama, Azure, Bedrock) |
| `healer-selenium` | HealingWebDriver, element wrappers, DOM snapshot capture |
| `healer-cucumber` | Cucumber integration with `@Intent` annotations |
| `healer-testng` | TestNG listener integration |
| `healer-junit` | JUnit 5 extension integration |
| `healer-report` | HTML reports, dashboards, trend analysis |
| `healer-cli` | Command-line interface for config/cache/reports |
| `healer-intellij` | IntelliJ IDEA plugin for heal history |
| `healer-showcase` | Demo project with 10 self-healing test examples |

---

## Module Dependency Graph

```
healer-core
    ↑
healer-llm
    ↑
healer-selenium
    ↑
├── healer-cucumber
├── healer-testng
└── healer-junit
    ↑
healer-showcase (demo)

Standalone:
├── healer-report
├── healer-cli
└── healer-intellij
```

---

## How Healing Works

```
Test Step                    HealingWebDriver              LLM/Heuristic
    │                              │                             │
    │  findElement(By.id("x"))     │                             │
    │─────────────────────────────>│                             │
    │                              │                             │
    │                         NoSuchElementException             │
    │                              │                             │
    │                              │  Capture DOM Snapshot       │
    │                              │─────────────────────────────>
    │                              │                             │
    │                              │  Analyze & Find Element     │
    │                              │<─────────────────────────────
    │                              │                             │
    │     Return Healed Element    │                             │
    │<─────────────────────────────│                             │
    │                              │                             │
    │  Test continues normally!    │                             │
```

---

## Showcase Demo

The `healer-showcase` module contains 10 tests that demonstrate healing in action. Each test uses **intentionally wrong locators** that would normally fail:

```bash
# Run the showcase
mvn test -pl healer-showcase
```

**Results**: All 10 tests pass with 100% healing success rate.

| Test | Wrong Locator | Real Element | Result |
|------|---------------|--------------|--------|
| 1 | `By.id("login-btn")` | `<button class="radius">` | Healed |
| 2 | `By.className("checkbox-input")` | `<input type="checkbox">` | Healed |
| 3 | `By.linkText("Add Element")` | `<button>Add Element</button>` | Healed |
| ... | ... | ... | ... |

See [healer-showcase/README.md](healer-showcase/README.md) for complete details.

### Healing Summary

After each test run, a summary is printed showing all healed locators:

```
╔════════════════════════════════════════════════════════════════════════════╗
║                    INTENT HEALER - HEALING SUMMARY                        ║
╠════════════════════════════════════════════════════════════════════════════╣
║  Total healed locators: 3                                                  ║
╚════════════════════════════════════════════════════════════════════════════╝

  [1] I click the login button
      │ ORIGINAL:  By.id: login-btn
      │ HEALED TO: By.cssSelector: button.radius
      │ Confidence: 93%

  TIP: Update your Page Objects with the healed locators above to prevent
       repeated healing and improve test execution speed.
```

This helps you quickly identify and fix broken locators in your source code.

---

## Configuration Options

```yaml
healer:
  # Healing mode
  mode: AUTO_SAFE           # AUTO_SAFE, CONFIRM, OFF
  enabled: true

  # LLM provider
  llm:
    provider: ollama        # mock, ollama, openai, anthropic, azure, bedrock
    model: llama3.2
    api_key: ${API_KEY}     # Environment variable
    timeout_seconds: 60
    max_retries: 2
    fallback_providers:     # Fallback chain
      - openai
      - mock

  # Guardrails
  guardrails:
    min_confidence: 0.75
    max_heals_per_scenario: 10
    forbidden_url_patterns:
      - /admin/
      - /payment/
    destructive_actions:
      - delete
      - remove

  # Caching
  cache:
    enabled: true
    ttl_hours: 24
    max_entries: 1000

  # Circuit breaker (for LLM failures)
  circuit_breaker:
    enabled: true
    failure_threshold: 5
    reset_timeout_seconds: 60

  # Reporting
  report:
    enabled: true
    output_dir: ./target/healer-reports
    formats:
      - json
      - html
```

---

## Intent Annotations (Cucumber)

```java
@Given("I click the submit button")
@Intent(
    action = "CLICK",
    description = "Submit the login form",
    policy = HealPolicy.AUTO_SAFE
)
@Invariant("Login form must be visible")
@Outcome(expected = "User navigates to dashboard")
public void clickSubmit() {
    driver.findElement(By.id("submit")).click();
}
```

---

## LLM Providers

### Mock (No External LLM)
Heuristic-based matching using element attributes, text, and DOM position.

### Ollama (Local)
```bash
ollama pull llama3.2
ollama serve
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

## Building

```bash
# Build all modules
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Run tests for a specific module
mvn test -pl healer-core

# Run a single test
mvn test -pl healer-core -Dtest=CircuitBreakerTest
```

---

## CLI Commands

```bash
# View configuration
mvn -pl healer-cli exec:java -Dexec.args="config show"

# Clear cache
mvn -pl healer-cli exec:java -Dexec.args="cache clear"

# Generate report
mvn -pl healer-cli exec:java -Dexec.args="report generate"
```

---

## Project Structure

```
intent-healer/
├── pom.xml                     # Parent POM
├── CLAUDE.md                   # AI assistant instructions
├── README.md                   # This file
│
├── healer-core/                # Core engine and models
├── healer-llm/                 # LLM provider implementations
├── healer-selenium/            # WebDriver wrapper
├── healer-cucumber/            # Cucumber integration
├── healer-testng/              # TestNG integration
├── healer-junit/               # JUnit 5 integration
├── healer-report/              # Report generation
├── healer-cli/                 # Command-line interface
├── healer-intellij/            # IDE plugin
├── healer-showcase/            # Demo project
└── healer-example/             # Usage examples
```

---

## Requirements

- Java 21+
- Maven 3.8+
- Chrome browser (for Selenium tests)
- Optional: Ollama for local LLM

---

## License

[License details here]

---

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests: `mvn test`
5. Submit a pull request
