package io.github.glaciousm.showcase.runners;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.*;

/**
 * Cucumber Test Runner for Intent Healer Showcase.
 *
 * This runner executes all 10 showcase scenarios that demonstrate
 * Intent Healer's self-healing capabilities.
 *
 * Run with:
 *   mvn test -pl healer-showcase
 *
 * Or run specific tests:
 *   mvn test -pl healer-showcase -Dcucumber.filter.tags="@test-1"
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "io.github.glaciousm.showcase.steps,io.github.glaciousm.showcase.hooks")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,html:target/cucumber-reports/showcase.html,json:target/cucumber-reports/showcase.json,io.github.glaciousm.cucumber.report.HealerCucumberReportPlugin:target/healer-report.html")
@ConfigurationParameter(key = SNIPPET_TYPE_PROPERTY_NAME, value = "camelcase")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@showcase")
public class ShowcaseRunner {
    // Test runner configuration only
}
