package io.github.glaciousm.example.runners;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.*;

/**
 * Cucumber test runner using JUnit 5 Platform.
 *
 * Runs all feature files in src/test/resources/features/
 * with step definitions from io.github.glaciousm.example
 *
 * To run specific scenarios, use tags:
 *   mvn test -Dtest=CucumberRunner -Dcucumber.filter.tags="@healing-test"
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "io.github.glaciousm.example.steps,io.github.glaciousm.example.hooks")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,html:target/cucumber-reports/cucumber.html,json:target/cucumber-reports/cucumber.json,io.github.glaciousm.cucumber.HealerCucumberPlugin")
@ConfigurationParameter(key = SNIPPET_TYPE_PROPERTY_NAME, value = "camelcase")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@healing-test")
public class CucumberRunner {
    // This class is a test runner configuration only
    // No code needed - annotations configure everything
}
