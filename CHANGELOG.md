# Changelog

All notable changes to Intent Healer will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.5] - 2025-12-23

### Added
- **Visual Evidence in HTML Reports**: Screenshots captured before and after healing are now displayed in HTML reports
  - Side-by-side comparison view with "Before (Failed)" and "After (Healed)" screenshots
  - Toggle button to show/hide screenshots for each healed locator
  - Red border for failed state, green border for healed state
- **Healed Locator Caching**: Cache healed locators to avoid repeated LLM calls for the same broken locator
  - `healedLocatorCache` in `AutoConfigurator` stores successful heals
  - Automatic cache invalidation when cached locator no longer works
- **Diagnostic Logging**: Enhanced agent startup diagnostics for troubleshooting
  - Step-by-step initialization progress with colored output
  - `getDisabledReason()` method in `AutoConfigurator` for clear error messages
  - ConfigLoader now logs which config files are checked and loaded

### Changed
- **Console Output Improvements**: Refactored console output to prevent Maven Surefire stream fragmentation
  - Replaced Unicode box-drawing characters (`╔═╗║`) with ASCII equivalents (`+|-`)
  - Single `StringBuilder` + `System.out.print()` instead of multiple `println()` calls
  - Added ANSI color support (cyan headers, green healed, yellow original, magenta low-confidence)
  - UTF-8 PrintStream for international character support
- **Agent Banner**: Changed from `System.err` to colored `System.out` output
  - Banner now uses ANSI colors for better visibility
  - Shows ACTIVE (green) or INACTIVE (yellow) status prominently
- **Provider Availability Check**: `isProviderAvailable()` now accepts `LlmConfig` parameter
  - Checks config values (base_url, api_key_env) before falling back to environment variables
  - More accurate availability detection for Azure OpenAI

### Fixed
- **HTML Report `%` Character Escaping**: Fixed `IllegalFormatException` when locators contain `%` characters
  - Added `escapeForFormat()` method to escape `%` as `%%` for `String.format()`
  - Fixed CSS `border-radius: 50%` to `50%%` in HTML template
- **Healing Summary Deduplication**: Prevent duplicate heal records for the same locator
  - Added `recordedLocators` set using `ConcurrentHashMap.newKeySet()`
  - Deduplicates based on original locator string
- **Shutdown Hook ClassNotFoundException**: Fixed `ClassNotFoundException: HealingSummary` in JVM shutdown
  - Pre-load `HealingSummary` and `HealingReportGenerator` instances before registering shutdown hook
  - Avoids class loading during shutdown when classloader may be unavailable

---

## [1.0.4] - 2025-12-21

### Added
- **Vision LLM Support (Multimodal Healing)**: Analyze screenshots alongside DOM for improved healing accuracy
  - Vision support for OpenAI (GPT-4o, GPT-4 Turbo), Anthropic (Claude 3), and Ollama (LLaVA, Llama 3.2 Vision)
  - Configurable vision strategies: `VISION_FIRST`, `DOM_FIRST`, `HYBRID`
  - `VisionConfig` for controlling screenshot-based healing behavior
  - `supportsVision()` and `isVisionModel()` methods in `LlmProvider` interface
  - Vision-enhanced prompts with element position descriptions
  - Local vision model support via Ollama (llava, bakllava, moondream, minicpm-v)
- **Playwright Integration**: New `healer-playwright` module for Playwright-based test automation
  - `HealingPage` wrapper with automatic locator healing
  - `HealingLocator` with self-healing on element interaction failures
  - `PlaywrightSnapshotBuilder` for UI state capture
  - ThreadLocal-based intent context management
- **Comprehensive Test Coverage**
  - IntelliJ plugin unit tests (HealerSettingsTest, HealHistoryToolWindowTest, etc.)
  - Exception hierarchy tests (27 tests for HealingException, LlmException, etc.)
  - Notification service tests (20 tests for Slack, Teams, custom webhooks)
  - Visual regression tests (24 tests for ScreenshotComparator)
  - Agent tests (HealerAgentTest, AutoConfiguratorTest, WebDriverInterceptorTest)

### Changed
- `PromptBuilder` now includes `buildVisionHealingPrompt()` for multimodal prompts
- All major providers (OpenAI, Anthropic, Ollama) updated with vision capabilities
- Improved thread safety in `HealingWebDriver` with ThreadLocal and volatile fields

### Fixed
- **JUnit Platform Leakage**: Prevent JUnit 5 service files from leaking into shaded `healer-agent` JAR
  - Exclude `META-INF/services/org.junit.*` and `junit.*` from shade plugin
  - Add JUnit exclusions to all healer module dependencies (healer-core, healer-selenium, healer-llm)
  - Change Cucumber and Selenium dependencies to `provided` scope in healer-cucumber
  - Fixes Surefire test provider auto-detection conflicts in JUnit 4 projects

### Documentation
- Added Vision-Capable Models section to USER_GUIDE.md
- Documented vision configuration options and strategy comparison
- Updated module documentation with Playwright integration

---

## [1.0.3] - 2025-12-21

### Added
- SECURITY.md with LLM data flow, offline mode, and vulnerability reporting
- CONTRIBUTING.md with development setup and PR guidelines
- CHANGELOG.md in Keep a Changelog format
- GitHub issue templates (bug report, feature request)
- GitHub pull request template
- CI badge in README

### Documentation
- Added "What Intent Healer Does (and Doesn't)" section
- Added "When NOT to Use Intent Healer" section

## [1.0.2] - 2025-12-20

### Added
- HTML/JSON report generation at JVM shutdown via `HealingReportGenerator`
- Graceful handling when LLM provider is unavailable (missing API key)
- `isProviderAvailable()` method in `LlmOrchestrator`
- Agent banner shows INACTIVE state when provider not configured
- Gradle wrapper for `healer-intellij` module

### Fixed
- Better API key validation error messages for Azure, OpenAI, and Anthropic providers
- IntelliJ plugin compatibility extended to 2025.2 (build 252.*)
- Clear error when Azure `base_url` includes full endpoint path

### Documentation
- Clarified Azure OpenAI configuration (`base_url` should be base URL only)
- Fixed configuration examples (YAML nesting, `api_key_env` instead of `api_key`)
- Updated all version references from 1.0.1 to 1.0.2

## [1.0.1] - 2025-12-18

### Added
- Java Agent for zero-code self-healing integration (`healer-agent` module)
- ByteBuddy bytecode instrumentation for WebDriver interception
- Automatic interception of ChromeDriver, FirefoxDriver, EdgeDriver, SafariDriver, RemoteWebDriver
- Fat JAR packaging for easy distribution
- Startup banner showing configuration status

### Changed
- Improved documentation with complete integration guides
- Added zero-code integration section to README and USER_GUIDE

## [1.0.0] - 2025-12-17

### Added
- **Core Framework**
  - `HealingEngine` - Main orchestration with pluggable components
  - `HealingWebDriver` - WebDriver wrapper with automatic healing
  - `HealCache` - Caffeine-based caching for healing decisions
  - `CircuitBreaker` - Fail-fast mechanism for LLM failures
  - `GuardrailChecker` - Pre/post-LLM safety checks

- **LLM Providers**
  - OpenAI (GPT-4o, GPT-4o-mini)
  - Anthropic (Claude 3 Haiku, Sonnet, Opus)
  - Azure OpenAI
  - AWS Bedrock
  - Ollama (local LLM)
  - Mock provider (heuristic matching)

- **Test Framework Integrations**
  - Cucumber with `@Intent`, `@Invariant`, `@Outcome` annotations
  - TestNG listener
  - JUnit 5 extension

- **Reporting**
  - HTML reports with visual evidence
  - JSON export for CI integration
  - PDF report generation
  - Week-over-week trend charts
  - Locator stability scoring
  - Cost projection and budgeting

- **CLI**
  - Configuration management
  - Cache operations
  - Report generation
  - Real-time monitoring (`healer watch`)
  - Approval workflow (`healer approve`)

- **IntelliJ Plugin**
  - Dashboard for healing statistics
  - Heal history browser
  - Live event monitoring
  - Locator suggestions

- **Benchmark Suite**
  - 35 scenarios covering various DOM change types
  - Negative tests for false heal detection
  - Provider comparison reports

### Security
- API key sanitization via `SecurityUtils`
- Environment variable-based key management
- Configurable forbidden URL patterns
- Destructive action detection

---

## Version History

| Version | Date | Highlights |
|---------|------|------------|
| 1.0.5 | 2025-12-23 | Screenshot evidence in reports, healed locator caching, console output fixes |
| 1.0.4 | 2025-12-21 | Vision LLM support, Playwright integration, comprehensive test coverage |
| 1.0.3 | 2025-12-21 | Community files, README improvements |
| 1.0.2 | 2025-12-20 | Report generation, graceful provider handling |
| 1.0.1 | 2025-12-18 | Java Agent for zero-code integration |
| 1.0.0 | 2025-12-17 | Initial release on Maven Central |
