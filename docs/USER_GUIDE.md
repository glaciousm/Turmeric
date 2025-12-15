# Intent Healer - User Guide

A comprehensive guide to configuring, integrating, and using the LLM-powered self-healing framework for Selenium + Java + Cucumber tests.

---

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Quick Start](#quick-start)
4. [Configuration](#configuration)
5. [Integration Guide](#integration-guide)
6. [CLI Reference](#cli-reference)
7. [IDE Plugins](#ide-plugins)
8. [Healing Modes & Policies](#healing-modes--policies)
9. [Trust Levels](#trust-levels)
10. [Reports & Monitoring](#reports--monitoring)
11. [Troubleshooting](#troubleshooting)
12. [Best Practices](#best-practices)

---

## Overview

Intent Healer is an intelligent self-healing framework that automatically fixes broken element locators in Selenium tests using Large Language Models (LLMs). When a test fails due to a changed UI element, the framework:

1. Captures a snapshot of the current UI state
2. Sends the context to an LLM for analysis
3. Identifies the best matching element
4. Optionally auto-heals and continues the test
5. Logs the healing for review and approval

### Key Features

- **Multiple LLM Providers**: OpenAI, Anthropic, Azure, AWS Bedrock, Ollama (local)
- **Test Framework Integration**: Cucumber, JUnit 5, TestNG
- **Safety Guardrails**: Confidence thresholds, forbidden actions, cost limits
- **Trust Level System**: Progressive automation based on track record
- **IDE Plugins**: IntelliJ IDEA and VS Code support
- **Comprehensive Reporting**: JSON and HTML reports with screenshots

---

## Prerequisites

### Required

- **Java 17+** (LTS recommended)
- **Gradle 8.0+** or Maven 3.8+
- **Selenium WebDriver 4.x**
- **LLM API Access** (at least one):
  - OpenAI API key, OR
  - Anthropic API key, OR
  - Azure OpenAI deployment, OR
  - AWS Bedrock access, OR
  - Local Ollama installation

### Recommended

- Chrome/Firefox browser with matching WebDriver
- IDE with plugin support (IntelliJ IDEA or VS Code)

---

## Quick Start

### Step 1: Add Dependencies

**Gradle (build.gradle.kts)**
```kotlin
dependencies {
    // Core healing engine
    testImplementation("com.intenthealer:healer-core:1.0.0")
    testImplementation("com.intenthealer:healer-llm:1.0.0")
    testImplementation("com.intenthealer:healer-selenium:1.0.0")

    // Choose your test framework integration
    testImplementation("com.intenthealer:healer-cucumber:1.0.0")  // For Cucumber
    // OR
    testImplementation("com.intenthealer:healer-junit:1.0.0")     // For JUnit 5
    // OR
    testImplementation("com.intenthealer:healer-testng:1.0.0")    // For TestNG
}
```

**Maven (pom.xml)**
```xml
<dependencies>
    <dependency>
        <groupId>com.intenthealer</groupId>
        <artifactId>healer-core</artifactId>
        <version>1.0.0</version>
        <scope>test</scope>
    </dependency>
    <!-- Add other modules as needed -->
</dependencies>
```

### Step 2: Set Up API Key

Set your LLM provider API key as an environment variable:

```bash
# For OpenAI
export OPENAI_API_KEY="sk-your-api-key-here"

# For Anthropic
export ANTHROPIC_API_KEY="sk-ant-your-key-here"

# For Azure OpenAI
export AZURE_OPENAI_API_KEY="your-azure-key"
export AZURE_OPENAI_ENDPOINT="https://your-resource.openai.azure.com"
```

### Step 3: Create Configuration File

Create `healer-config.yml` in your project root or `src/test/resources/`:

```yaml
healer:
  mode: AUTO_SAFE          # Start with safe auto-healing
  enabled: true

llm:
  provider: openai
  model: gpt-4o-mini       # Cost-effective model
  api_key_env: OPENAI_API_KEY
  confidence_threshold: 0.80
  max_cost_per_run_usd: 5.00

guardrails:
  min_confidence: 0.80
  max_heal_attempts_per_step: 2
  max_heals_per_scenario: 5

cache:
  enabled: true
  ttl_hours: 24

report:
  output_dir: build/healer-reports
  html_enabled: true
```

### Step 4: Run Your Tests

Run your existing tests - healing happens automatically when elements aren't found!

```bash
./gradlew test
```

Check the reports in `build/healer-reports/` after the run.

---

## Configuration

### Configuration File Location

The framework searches for configuration in this order:
1. `healer-config.yml` (project root)
2. `healer-config.yaml` (project root)
3. `src/test/resources/healer-config.yml`
4. `.healer/config.yml`

### Complete Configuration Reference

```yaml
# =============================================================================
# HEALER CONFIGURATION
# =============================================================================

healer:
  # Healing mode: OFF, SUGGEST, AUTO_SAFE, AUTO_ALL
  mode: AUTO_SAFE

  # Master switch to enable/disable healing
  enabled: true

# =============================================================================
# LLM PROVIDER CONFIGURATION
# =============================================================================

llm:
  # Provider: openai, anthropic, azure, bedrock, ollama, local
  provider: openai

  # Model name (provider-specific)
  model: gpt-4o-mini

  # Environment variable containing API key
  api_key_env: OPENAI_API_KEY

  # Custom base URL (optional, for proxies or custom endpoints)
  base_url: null

  # Request timeout in seconds
  timeout_seconds: 30

  # Number of retries on failure
  max_retries: 2

  # LLM temperature (lower = more consistent)
  temperature: 0.1

  # Minimum confidence to accept a heal
  confidence_threshold: 0.80

  # Max tokens per LLM request
  max_tokens_per_request: 2000

  # Max LLM requests per test run
  max_requests_per_test_run: 100

  # Cost limit per run (USD)
  max_cost_per_run_usd: 5.00

  # Require LLM to provide reasoning
  require_reasoning: true

  # Fallback providers (tried in order if primary fails)
  fallback:
    - provider: anthropic
      model: claude-3-haiku-20240307
      api_key_env: ANTHROPIC_API_KEY

# =============================================================================
# SAFETY GUARDRAILS
# =============================================================================

guardrails:
  # Minimum confidence score to attempt healing
  min_confidence: 0.80

  # Max heal attempts per failing step
  max_heal_attempts_per_step: 2

  # Max heals allowed per scenario
  max_heals_per_scenario: 5

  # Keywords that prevent healing (element text/attributes)
  forbidden_keywords:
    - delete
    - remove
    - cancel
    - unsubscribe
    - terminate
    - deactivate
    - 削除          # Japanese
    - キャンセル    # Japanese
    - löschen      # German
    - supprimer    # French

  # CSS selectors for error indicators (healing pauses if found)
  error_selectors:
    - ".error-banner"
    - ".alert-danger"
    - ".alert-error"
    - "[role='alert']"
    - ".notification-error"

  # URL patterns where healing is forbidden
  forbidden_url_patterns:
    - "/admin/"
    - "/settings/delete"
    - "/account/close"

  # Allow JavaScript-based clicks (less safe)
  allow_js_click: false

# =============================================================================
# UI SNAPSHOT SETTINGS
# =============================================================================

snapshot:
  # Maximum elements to capture in snapshot
  max_elements: 500

  # Include hidden elements
  include_hidden: false

  # Include disabled elements
  include_disabled: true

  # Capture screenshot with snapshot
  capture_screenshot: true

  # Capture full DOM (increases payload size)
  capture_dom: false

  # Max text length per element
  max_text_length: 200

  # Snapshot capture timeout (ms)
  timeout_ms: 5000

# =============================================================================
# CACHE CONFIGURATION
# =============================================================================

cache:
  # Enable caching of successful heals
  enabled: true

  # Cache time-to-live in hours
  ttl_hours: 24

  # Maximum cache entries
  max_entries: 10000

  # Storage backend: MEMORY, FILE, REDIS
  storage: MEMORY

  # Minimum confidence to cache a heal
  min_confidence_to_cache: 0.70

  # File path for FILE storage
  file_path: .healer/cache

  # Enable persistence (FILE/REDIS only)
  persistence_enabled: true

  # Redis connection URL (REDIS only)
  redis_url: null

# =============================================================================
# REPORT CONFIGURATION
# =============================================================================

report:
  # Output directory for reports
  output_dir: build/healer-reports

  # Generate JSON reports
  json_enabled: true

  # Generate HTML reports
  html_enabled: true

  # Include screenshots in reports
  include_screenshots: true

  # Include LLM prompts (debugging)
  include_llm_prompts: false

  # Include DOM snapshots (large files)
  include_dom_snapshots: false

  # Max artifacts per report
  max_artifacts_per_report: 100

# =============================================================================
# CIRCUIT BREAKER (COST & RELIABILITY PROTECTION)
# =============================================================================

circuit_breaker:
  # Enable circuit breaker
  enabled: true

  # Max false heal rate before circuit opens
  false_heal_rate_threshold: 0.01

  # Window for false heal rate calculation (days)
  false_heal_rate_window_days: 7

  # Minimum samples before rate calculation
  false_heal_rate_min_samples: 20

  # Consecutive failures to open circuit
  consecutive_failures_threshold: 3

  # Successes needed to close circuit
  success_threshold_to_close: 2

  # P95 latency threshold (ms)
  p95_latency_threshold_ms: 10000

  # Latency monitoring window (hours)
  latency_window_hours: 1

  # Daily cost limit (USD)
  daily_cost_limit_usd: 10.00

  # Cooldown period after circuit opens (minutes)
  cooldown_minutes: 30

  # How long circuit stays open (seconds)
  open_duration_seconds: 1800

  # Max attempts in half-open state
  half_open_max_attempts: 3

  # Test heals required in half-open state
  test_heals_required: 3
```

### Provider-Specific Configuration

#### OpenAI
```yaml
llm:
  provider: openai
  model: gpt-4o-mini  # or gpt-4, gpt-3.5-turbo
  api_key_env: OPENAI_API_KEY
```

#### Anthropic
```yaml
llm:
  provider: anthropic
  model: claude-3-haiku-20240307  # or claude-3-opus-20240229
  api_key_env: ANTHROPIC_API_KEY
```

#### Azure OpenAI
```yaml
llm:
  provider: azure
  model: your-deployment-name
  api_key_env: AZURE_OPENAI_API_KEY
  base_url: https://your-resource.openai.azure.com
```

#### AWS Bedrock
```yaml
llm:
  provider: bedrock
  model: anthropic.claude-3-haiku-20240307-v1:0
  # Uses AWS credentials from environment or ~/.aws/credentials
```

#### Ollama (Local)
```yaml
llm:
  provider: ollama
  model: llama2  # or codellama, mistral, etc.
  base_url: http://localhost:11434
  # No API key required
```

---

## Integration Guide

### Cucumber Integration

#### Step 1: Register the Plugin

```java
@CucumberOptions(
    features = "src/test/resources/features",
    glue = "com.example.steps",
    plugin = {
        "pretty",
        "html:build/cucumber-reports",
        "com.intenthealer.cucumber.HealerCucumberPlugin"  // Add this
    }
)
@RunWith(Cucumber.class)
public class RunCucumberTest {
}
```

#### Step 2: Set Up WebDriver in Steps

```java
public class BaseSteps {
    protected static HealingWebDriver driver;

    @Before
    public void setUp() {
        HealerTestConfig config = HealerTestConfig.getInstance();
        driver = config.createDriver();
    }

    @After
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }
}
```

#### Step 3: Add Intent Annotations (Optional but Recommended)

```java
public class LoginSteps extends BaseSteps {

    @When("I enter username {string}")
    @Intent(
        action = "enter_username",
        description = "Enter username in the login form",
        healPolicy = HealPolicy.AUTO_SAFE
    )
    public void enterUsername(String username) {
        driver.findElement(By.id("username")).sendKeys(username);
    }

    @When("I click the login button")
    @Intent(
        action = "click_login",
        description = "Submit the login form",
        healPolicy = HealPolicy.AUTO_SAFE
    )
    @Invariant(check = NoErrorBannerCheck.class)  // Pre-action check
    @Outcome(check = UrlChangedCheck.class)       // Post-action validation
    public void clickLogin() {
        driver.findElement(By.xpath("//button[@type='submit']")).click();
    }
}
```

### JUnit 5 Integration

#### Step 1: Add Extension

```java
@ExtendWith(HealerExtension.class)
public class LoginTest {

    private WebDriver driver;  // Auto-wrapped by extension

    @BeforeEach
    void setUp() {
        driver = new ChromeDriver();
        driver.get("https://example.com/login");
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    @HealEnabled(intent = "Login with valid credentials")
    void testValidLogin() {
        // Healing is automatic - just write normal Selenium code
        driver.findElement(By.id("username")).sendKeys("user@test.com");
        driver.findElement(By.id("password")).sendKeys("password123");
        driver.findElement(By.cssSelector("button[type='submit']")).click();

        // Assertions
        assertTrue(driver.getCurrentUrl().contains("/dashboard"));
    }

    @Test
    @HealDisabled  // Explicitly disable healing for this test
    void testWithoutHealing() {
        // Standard Selenium - no healing
    }
}
```

### TestNG Integration

#### Step 1: Add Listener

**Via testng.xml:**
```xml
<!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">
<suite name="Test Suite">
    <listeners>
        <listener class-name="com.intenthealer.testng.HealerTestListener"/>
    </listeners>

    <test name="Login Tests">
        <classes>
            <class name="com.example.LoginTest"/>
        </classes>
    </test>
</suite>
```

**Or via annotation:**
```java
@Listeners(HealerTestListener.class)
public class LoginTest {
    private WebDriver driver;  // Auto-wrapped

    @BeforeMethod
    public void setUp() {
        driver = new ChromeDriver();
    }

    @Test
    public void testLogin() {
        driver.findElement(By.id("username")).sendKeys("test");
        // Healing happens automatically on element not found
    }
}
```

### Programmatic Integration

For custom setups or non-standard test frameworks:

```java
public class CustomIntegration {

    public static void main(String[] args) {
        // 1. Load configuration
        HealerConfig config = ConfigLoader.load();

        // 2. Create the healing engine
        HealingEngine engine = new HealingEngine(config);

        // 3. Configure LLM orchestrator
        LlmOrchestrator llm = new LlmOrchestrator();

        // 4. Wire up the engine
        engine.setSnapshotCapture(failure -> {
            // Capture UI snapshot
            return SnapshotBuilder.capture(driver);
        });

        engine.setLlmEvaluator((failure, snapshot) -> {
            // Call LLM for healing decision
            return llm.evaluateCandidates(failure, snapshot);
        });

        engine.setActionExecutor((action, element, data) -> {
            // Execute the healed action
            switch (action) {
                case CLICK -> element.click();
                case SEND_KEYS -> element.sendKeys(data);
                // etc.
            }
        });

        // 5. Create healing driver
        WebDriver rawDriver = new ChromeDriver();
        HealingWebDriver driver = new HealingWebDriver(rawDriver, engine, config);

        // 6. Use normally - healing is automatic
        driver.get("https://example.com");

        IntentContract intent = IntentContract.builder()
            .action("login")
            .description("Perform user login")
            .policy(HealPolicy.AUTO_SAFE)
            .build();

        driver.setCurrentIntent(intent, "Login step");

        try {
            driver.findElement(By.id("login-btn")).click();
        } finally {
            driver.clearCurrentIntent();
            driver.quit();
        }
    }
}
```

---

## CLI Reference

Install the CLI tool or run via Gradle:

```bash
# Via Gradle
./gradlew :healer-cli:run --args="<command>"

# Or if installed globally
healer <command>
```

### Configuration Commands

```bash
# Show current configuration
healer config show

# Validate configuration file
healer config validate

# Create default configuration file
healer config init
healer config init ./custom/path/config.yml

# Show configuration file search locations
healer config where
```

### Cache Commands

```bash
# Show cache statistics
healer cache stats

# Clear the cache (requires confirmation)
healer cache clear
healer cache clear --force

# Warm up cache from existing reports
healer cache warmup
healer cache warmup ./path/to/reports

# Export cache to file
healer cache export
healer cache export ./backup/cache.json

# Import cache from file
healer cache import ./backup/cache.json
```

### Report Commands

```bash
# Show summary of recent healing activity
healer report summary
healer report summary ./custom/reports/dir

# List recent heals
healer report list
healer report list ./reports 50  # Show 50 entries

# Generate report from data
healer report generate ./input ./output html
healer report generate ./input ./output json
```

### Utility Commands

```bash
# Show version
healer version

# Show help
healer help
healer config help
healer cache help
```

---

## IDE Plugins

### IntelliJ IDEA Plugin

#### Installation

1. Open IntelliJ IDEA
2. Go to **Settings** → **Plugins** → **Marketplace**
3. Search for "Intent Healer"
4. Click **Install** and restart IDE

#### Features

| Feature | Access |
|---------|--------|
| Dashboard | View → Tool Windows → Intent Healer |
| Settings | Settings → Tools → Intent Healer |
| Heal History | Tools → Intent Healer → View Heal History |
| Refresh Cache | Tools → Intent Healer → Refresh Cache |
| Clear Cache | Tools → Intent Healer → Clear Cache |
| Suggest Locator | Right-click in editor → Suggest Stable Locator |

#### Configuration

1. Go to **Settings** → **Tools** → **Intent Healer**
2. Configure:
   - **Healer Mode**: OFF / SUGGEST / AUTO_SAFE / AUTO_ALL
   - **API Key**: Your LLM provider API key
   - **Cache Directory**: Location for heal cache
   - **Enable Notifications**: Show heal notifications
   - **Max History Entries**: Number of entries to keep
   - **Persist History**: Save history between sessions

#### Using the Dashboard

1. Open the **Intent Healer** tool window (right sidebar)
2. **Dashboard Tab**: View real-time healing activity
3. **History Tab**: Browse past heals
   - Double-click to view details
   - Use **Accept/Reject/Blacklist** buttons
4. **Stability Tab**: View locator stability scores

### VS Code Extension

#### Installation

1. Open VS Code
2. Go to Extensions (Ctrl+Shift+X)
3. Search for "Intent Healer"
4. Click **Install**

#### Features

| Feature | Access |
|---------|--------|
| Views | Activity Bar → Intent Healer icon |
| Commands | Command Palette (Ctrl+Shift+P) → "Intent Healer" |
| Settings | Settings → Extensions → Intent Healer |

#### Views

**Heal History View**
- Browse healing events
- Expand entries for details
- Actions: Accept, Reject, Blacklist
- Refresh button

**Trust Level View**
- Current trust level and score
- Trust by locator strategy
- Trust by element type

**Locator Stability View**
- Stability scores for locators
- Recommendations for unstable locators

#### Commands

| Command | Description |
|---------|-------------|
| `Intent Healer: Refresh History` | Reload heal history |
| `Intent Healer: Accept Heal` | Accept selected heal |
| `Intent Healer: Reject Heal` | Reject selected heal |
| `Intent Healer: Blacklist Heal` | Blacklist heal pattern |
| `Intent Healer: Open Dashboard` | Open full dashboard |
| `Intent Healer: Export Report` | Export healing report |
| `Intent Healer: Clear Cache` | Clear heal cache |

#### Settings

```json
{
    "intentHealer.cacheDirectory": ".intent-healer",
    "intentHealer.autoRefresh": true,
    "intentHealer.refreshInterval": 30,
    "intentHealer.showNotifications": true,
    "intentHealer.confidenceWarningThreshold": 0.75
}
```

---

## Healing Modes & Policies

### Healing Modes

| Mode | Description | When to Use |
|------|-------------|-------------|
| `OFF` | Healing completely disabled | Production, debugging |
| `SUGGEST` | Log suggestions, don't auto-heal | Initial evaluation |
| `AUTO_SAFE` | Auto-heal safe actions only | Recommended default |
| `AUTO_ALL` | Auto-heal all healable failures | Mature, trusted setup |

### Heal Policies (Per-Step)

| Policy | Description |
|--------|-------------|
| `OFF` | Never heal this step |
| `SUGGEST` | Suggest only, don't auto-heal |
| `AUTO_SAFE` | Auto-heal if action is safe |
| `AUTO_ALL` | Auto-heal regardless of action type |

### What's Considered "Safe"?

**Safe Actions (AUTO_SAFE):**
- Reading element text
- Getting element attributes
- Clicking non-destructive buttons
- Entering text in input fields
- Navigation within the app

**Unsafe Actions (require AUTO_ALL):**
- Clicking delete/remove buttons
- Form submissions to destructive endpoints
- Actions matching forbidden keywords
- Actions on admin/settings pages

### Healable vs Non-Healable Failures

**Healable:**
- `NoSuchElementException` - Element not found
- `StaleElementReferenceException` - Element became stale
- `ElementClickInterceptedException` - Click blocked
- `ElementNotInteractableException` - Can't interact
- `TimeoutException` - Element wait timeout

**Not Healable:**
- `AssertionError` - Test assertions
- `WebDriverException` - Browser/driver issues
- Network errors
- JavaScript errors

---

## Trust Levels

The framework uses a progressive trust system that increases automation as healing proves reliable.

### Trust Level Progression

| Level | Name | Description | Auto-Heal |
|-------|------|-------------|-----------|
| L0 | SHADOW | Learning mode, no auto-heal | No |
| L1 | SUGGEST | Suggestions logged for review | No |
| L2 | CONFIRM | Requires human confirmation | Ask |
| L3 | AUTO_SAFE | Auto-heal safe actions | Safe only |
| L4 | SILENT | Full auto-healing | Yes |

### Progression Criteria

**L0 → L1 (Shadow → Suggest)**
- 5+ successful shadow heals
- 0 rejections

**L1 → L2 (Suggest → Confirm)**
- 10+ accepted suggestions
- <5% rejection rate

**L2 → L3 (Confirm → Auto-Safe)**
- 20+ confirmed heals
- <2% false positive rate
- 7+ days of data

**L3 → L4 (Auto-Safe → Silent)**
- 50+ successful auto-heals
- <1% false positive rate
- 30+ days of data

### Demotion Triggers

- 3 consecutive false positives → Drop 1 level
- >5% false positive rate in 7 days → Drop to L1
- Manual demotion via CLI/IDE

---

## Reports & Monitoring

### Report Types

**JSON Report** (`healer-report-{timestamp}.json`)
```json
{
  "summary": {
    "totalAttempts": 15,
    "successes": 12,
    "refusals": 2,
    "failures": 1,
    "totalCost": 0.0234
  },
  "events": [
    {
      "id": "heal-123",
      "timestamp": "2024-01-15T10:30:00Z",
      "stepText": "I click the login button",
      "originalLocator": "By.id: login-btn",
      "healedLocator": "By.xpath: //button[@data-testid='login']",
      "confidence": 0.92,
      "reasoning": "Found button with matching test ID...",
      "outcome": "SUCCESS"
    }
  ]
}
```

**HTML Report** (`healer-report-{timestamp}.html`)
- Interactive, styled report
- Collapsible event entries
- Screenshots inline
- Filter by outcome
- Search functionality

### Viewing Reports

```bash
# Generate summary
healer report summary

# List recent heals
healer report list

# Open HTML report in browser
open build/healer-reports/healer-report-*.html
```

### Monitoring Metrics

**Key Metrics to Track:**
- **Heal Success Rate**: % of heals that worked
- **False Positive Rate**: % of heals that were wrong
- **LLM Cost**: Total spend on LLM calls
- **Latency**: Time taken for healing
- **Cache Hit Rate**: % of heals served from cache

**Circuit Breaker States:**
- **CLOSED**: Normal operation
- **OPEN**: Healing disabled (too many failures or cost exceeded)
- **HALF_OPEN**: Testing if healing should resume

---

## Troubleshooting

### Common Issues

#### "No API key found"
```
Error: LLM API key not configured
```
**Solution:** Set the environment variable:
```bash
export OPENAI_API_KEY="sk-your-key"
```

#### "Confidence too low"
```
Heal refused: confidence 0.65 below threshold 0.80
```
**Solution:** Lower `confidence_threshold` in config, or improve locator specificity.

#### "Circuit breaker open"
```
Healing disabled: circuit breaker open
```
**Solution:** Wait for cooldown, or check:
- Daily cost limit not exceeded
- False positive rate acceptable
- Check logs for consecutive failures

#### "Max heals per scenario exceeded"
```
Healing stopped: reached max heals (5) for scenario
```
**Solution:** Increase `max_heals_per_scenario` or fix underlying locator issues.

#### "Forbidden keyword detected"
```
Heal refused: element contains forbidden keyword 'delete'
```
**Solution:** Remove keyword from `forbidden_keywords` if safe, or use `HealPolicy.AUTO_ALL`.

### Debug Mode

Enable detailed logging:

```yaml
# In healer-config.yml
report:
  include_llm_prompts: true
  include_dom_snapshots: true
```

Or via environment:
```bash
export HEALER_DEBUG=true
./gradlew test
```

### Getting Help

1. Check logs in `build/healer-reports/`
2. Review heal history in IDE plugin
3. Run `healer config validate` to check configuration
4. Enable debug mode for detailed diagnostics

---

## Best Practices

### Configuration

1. **Start Conservative**: Begin with `mode: SUGGEST` to evaluate before auto-healing
2. **Set Cost Limits**: Always configure `max_cost_per_run_usd` and `daily_cost_limit_usd`
3. **Use Fallback Providers**: Configure multiple LLM providers for reliability
4. **Enable Caching**: Dramatically reduces costs and latency

### Test Design

1. **Use Descriptive Intents**: Clear `@Intent` annotations help LLM understand context
2. **Prefer Stable Locators**: Use `data-testid`, `aria-label` over brittle XPaths
3. **Add Invariants**: Use `@Invariant` to prevent healing when errors are present
4. **Validate Outcomes**: Use `@Outcome` to verify healing worked correctly

### Operations

1. **Review Heals Regularly**: Accept/reject heals to improve trust level
2. **Monitor Costs**: Check daily LLM spend in reports
3. **Blacklist Bad Patterns**: Permanently block incorrect heal patterns
4. **Update Locators**: Use heal suggestions to update source locators

### Security

1. **Never Commit API Keys**: Use environment variables
2. **Set Forbidden Keywords**: Block destructive actions in your language
3. **Configure Forbidden URLs**: Protect admin/sensitive pages
4. **Review Before AUTO_ALL**: Only use full auto-heal with established trust

---

## Appendix: Quick Reference

### Environment Variables

| Variable | Description |
|----------|-------------|
| `OPENAI_API_KEY` | OpenAI API key |
| `ANTHROPIC_API_KEY` | Anthropic API key |
| `AZURE_OPENAI_API_KEY` | Azure OpenAI API key |
| `AZURE_OPENAI_ENDPOINT` | Azure OpenAI endpoint URL |
| `AWS_ACCESS_KEY_ID` | AWS access key (for Bedrock) |
| `AWS_SECRET_ACCESS_KEY` | AWS secret key (for Bedrock) |
| `HEALER_CONFIG` | Custom config file path |
| `HEALER_DEBUG` | Enable debug logging |

### Annotations

| Annotation | Purpose |
|------------|---------|
| `@Intent` | Describe step intent for healing context |
| `@Invariant` | Pre-action safety check |
| `@Outcome` | Post-action validation |
| `@HealEnabled` | Enable healing (JUnit) |
| `@HealDisabled` | Disable healing (JUnit) |

### CLI Quick Reference

```bash
healer config show          # View config
healer config validate      # Validate config
healer config init          # Create config
healer cache stats          # Cache info
healer cache clear --force  # Clear cache
healer report summary       # View summary
healer report list          # List heals
healer version              # Version info
healer help                 # Help
```

---

*Intent Healer v1.0 - LLM-Powered Self-Healing for Selenium Tests*
