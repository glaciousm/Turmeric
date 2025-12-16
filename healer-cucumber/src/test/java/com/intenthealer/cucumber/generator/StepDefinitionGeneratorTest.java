package com.intenthealer.cucumber.generator;

import com.intenthealer.cucumber.generator.StepDefinitionGenerator.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class StepDefinitionGeneratorTest {

    private StepDefinitionGenerator generator;
    private GeneratorConfig config;

    @BeforeEach
    void setUp() {
        config = new GeneratorConfig(
                "com.example.steps",
                true,  // generatePageObjects
                true,  // generateIntentAnnotations
                true   // generateOutcomeAnnotations
        );
        generator = new StepDefinitionGenerator(config);
    }

    // ===== Test generating from simple feature content =====

    @Test
    void generateFromContent_createsBasicStepDefinition() {
        String featureContent = """
                Feature: User Login

                Scenario: Successful login
                  Given I am on the login page
                  When I enter my credentials
                  Then I should see the dashboard
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "login.feature");

        assertThat(result).isNotNull();
        assertThat(result.className()).isEqualTo("UserLoginSteps");
        assertThat(result.packageName()).isEqualTo("com.example.steps");
        assertThat(result.stepDefinitionCode()).contains("package com.example.steps");
        assertThat(result.stepDefinitionCode()).contains("public class UserLoginSteps");
        assertThat(result.stepCount()).isEqualTo(3);
    }

    @Test
    void generateFromContent_extractsFeatureName() {
        String featureContent = """
                Feature: Shopping Cart Functionality

                Scenario: Add item to cart
                  When I click add to cart
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "cart.feature");

        assertThat(result.className()).isEqualTo("ShoppingCartFunctionalitySteps");
    }

    @Test
    void generateFromContent_handlesMultipleScenarios() {
        String featureContent = """
                Feature: Login

                Scenario: Valid login
                  Given I am on the login page
                  When I enter valid credentials
                  Then I should be logged in

                Scenario: Invalid login
                  Given I am on the login page
                  When I enter invalid credentials
                  Then I should see an error
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "login.feature");

        assertThat(result.stepCount()).isEqualTo(6);
        assertThat(result.stepDefinitionCode()).contains("iAmOnTheLoginPage");
        assertThat(result.stepDefinitionCode()).contains("iEnterValidCredentials");
        assertThat(result.stepDefinitionCode()).contains("iShouldBeLoggedIn");
        assertThat(result.stepDefinitionCode()).contains("iEnterInvalidCredentials");
        assertThat(result.stepDefinitionCode()).contains("iShouldSeeAnError");
    }

    // ===== Test step extraction with various keywords =====

    @Test
    void extractSteps_handlesGivenWhenThen() {
        String featureContent = """
                Feature: Test
                Scenario: Test scenario
                  Given I have setup
                  When I perform action
                  Then I verify result
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("@Given");
        assertThat(result.stepDefinitionCode()).contains("@When");
        assertThat(result.stepDefinitionCode()).contains("@Then");
    }

    @Test
    void extractSteps_handlesAndKeyword() {
        String featureContent = """
                Feature: Test
                Scenario: Test scenario
                  Given I am logged in
                  And I navigate to settings
                  When I click save
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        // And should be mapped to Given (uses previous step type)
        assertThat(result.stepDefinitionCode()).contains("iAmLoggedIn");
        assertThat(result.stepDefinitionCode()).contains("iNavigateToSettings");
    }

    @Test
    void extractSteps_handlesButKeyword() {
        String featureContent = """
                Feature: Test
                Scenario: Test scenario
                  Given I am on home page
                  But I am not logged in
                  When I click login
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("iAmOnHomePage");
        assertThat(result.stepDefinitionCode()).contains("iAmNotLoggedIn");
    }

    // ===== Test parameter extraction =====

    @Test
    void extractParameters_handlesDoubleQuotedStrings() {
        String featureContent = """
                Feature: Test
                Scenario: Test scenario
                  When I enter "john@example.com" in the email field
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("I enter \"{string}\" in the email field");
        assertThat(result.stepDefinitionCode()).contains("String email");
    }

    @Test
    void extractParameters_handlesSingleQuotedStrings() {
        String featureContent = """
                Feature: Test
                Scenario: Test scenario
                  When I click on 'Submit' button
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("I click on '{string}' button");
    }

    @Test
    void extractParameters_handlesAngleBrackets() {
        String featureContent = """
                Feature: Test
                Scenario Outline: Test scenario
                  When I enter <username> in the field

                  Examples:
                    | username |
                    | john     |
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("{string}");
    }

    @Test
    void extractParameters_handlesNumbers() {
        String featureContent = """
                Feature: Test
                Scenario: Test scenario
                  When I wait for 5 seconds
                  And I select item 10
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("I wait for {int} seconds");
        assertThat(result.stepDefinitionCode()).contains("int ");
        assertThat(result.stepDefinitionCode()).contains("I select item {int}");
    }

    @Test
    void extractParameters_handlesMultipleParameters() {
        String featureContent = """
                Feature: Test
                Scenario: Test scenario
                  When I enter "john" and "password123" to login
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode())
                .contains("I enter \"{string}\" and \"{string}\" to login");
        assertThat(result.stepDefinitionCode()).contains("String param0, String param1");
    }

    // ===== Test pattern generation =====

    @Test
    void toPattern_replacesStringsWithPlaceholder() {
        String featureContent = """
                Feature: Test
                Scenario: Test
                  When I type "hello world" in the field
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode())
                .contains("I type \"{string}\" in the field");
    }

    @Test
    void toPattern_replacesNumbersWithPlaceholder() {
        String featureContent = """
                Feature: Test
                Scenario: Test
                  When I select option 3
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("I select option {int}");
    }

    // ===== Test class name generation =====

    @Test
    void toClassName_convertsFeatureNameToClassName() {
        String featureContent = """
                Feature: user login functionality
                Scenario: Test
                  Given test step
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.className()).isEqualTo("UserLoginFunctionalitySteps");
    }

    @Test
    void toClassName_handlesDashesAndUnderscores() {
        String featureContent = """
                Feature: my-feature_name
                Scenario: Test
                  Given test step
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.className()).isEqualTo("MyFeatureNameSteps");
    }

    @Test
    void toClassName_removesSpecialCharacters() {
        String featureContent = """
                Feature: Test@Feature#Name!
                Scenario: Test
                  Given test step
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.className()).isEqualTo("TestFeatureNameSteps");
    }

    // ===== Test method name generation =====

    @Test
    void toMethodName_convertsToCamelCase() {
        String featureContent = """
                Feature: Test
                Scenario: Test
                  When I click the submit button
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("iClickTheSubmitButton");
    }

    @Test
    void toMethodName_replacesParametersWithParamName() {
        String featureContent = """
                Feature: Test
                Scenario: Test
                  When I enter "test" in "field"
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("iEnterParamInParam");
    }

    @Test
    void toMethodName_handlesNumbers() {
        String featureContent = """
                Feature: Test
                Scenario: Test
                  When I wait 5 seconds
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("iWaitNumSeconds");
    }

    // ===== Test Intent annotation generation =====

    @Test
    void generateStepMethod_addsIntentAnnotation() {
        String featureContent = """
                Feature: Test
                Scenario: Test
                  When I click the login button
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("@Intent(");
        assertThat(result.stepDefinitionCode()).contains("User");
    }

    // ===== Test Outcome annotation generation for Then steps =====

    @Test
    void generateStepMethod_addsOutcomeAnnotationForThenSteps() {
        String featureContent = """
                Feature: Test
                Scenario: Test
                  Then I should see the welcome message
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("@Outcome(");
        assertThat(result.stepDefinitionCode()).contains("Verify");
    }

    @Test
    void generateStepMethod_noOutcomeAnnotationForWhenSteps() {
        String featureContent = """
                Feature: Test
                Scenario: Test
                  When I click the button
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("@Intent(");
        // Count @Outcome occurrences - should be 0
        int outcomeCount = result.stepDefinitionCode().split("@Outcome", -1).length - 1;
        assertThat(outcomeCount).isEqualTo(0);
    }

    // ===== Test page object generation =====

    @Test
    void generateFromContent_generatesPageObject_whenEnabled() {
        String featureContent = """
                Feature: Login
                Scenario: Test
                  When I click "Login"
                  And I enter "username"
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.pageObjectCode()).isNotNull();
        assertThat(result.pageObjectCode()).contains("public class LoginPage");
        assertThat(result.pageObjectCode()).contains("@FindBy");
        assertThat(result.pageObjectCode()).contains("PageFactory");
    }

    @Test
    void generateFromContent_noPageObject_whenDisabled() {
        GeneratorConfig noPageObjectConfig = new GeneratorConfig(
                "com.example.steps",
                false,  // generatePageObjects = false
                true,
                true
        );
        generator = new StepDefinitionGenerator(noPageObjectConfig);

        String featureContent = """
                Feature: Test
                Scenario: Test
                  When I click "button"
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.pageObjectCode()).isNull();
    }

    @Test
    void generatePageObject_extractsElementsFromParameters() {
        String featureContent = """
                Feature: Login
                Scenario: Test
                  When I click "Submit"
                  And I enter "username" in field
                  Then I see "Welcome"
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.pageObjectCode()).contains("Submit");
        assertThat(result.pageObjectCode()).contains("username");
        assertThat(result.pageObjectCode()).contains("Welcome");
    }

    // ===== Test generateMethodBody patterns =====

    @Test
    void generateMethodBody_handlesNavigatePattern() {
        String featureContent = """
                Feature: Test
                Scenario: Test
                  When I navigate to "https://example.com"
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("driver.get(");
    }

    @Test
    void generateMethodBody_handlesGoToPattern() {
        String featureContent = """
                Feature: Test
                Scenario: Test
                  When I go to "https://example.com"
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("driver.get(");
    }

    @Test
    void generateMethodBody_handlesOpenPattern() {
        String featureContent = """
                Feature: Test
                Scenario: Test
                  When I open the application
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("driver.get(");
    }

    @Test
    void generateMethodBody_handlesClickPattern() {
        String featureContent = """
                Feature: Test
                Scenario: Test
                  When I click the "Submit" button
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("element.click()");
        assertThat(result.stepDefinitionCode()).contains("WebDriverWait");
        assertThat(result.stepDefinitionCode()).contains("elementToBeClickable");
    }

    @Test
    void generateMethodBody_handlesEnterPattern() {
        String featureContent = """
                Feature: Test
                Scenario: Test
                  When I enter "john@example.com" in the "Email" field
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("field.sendKeys(");
        assertThat(result.stepDefinitionCode()).contains("field.clear()");
    }

    @Test
    void generateMethodBody_handlesTypePattern() {
        String featureContent = """
                Feature: Test
                Scenario: Test
                  When I type "password"
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("sendKeys(");
    }

    @Test
    void generateMethodBody_handlesFillPattern() {
        String featureContent = """
                Feature: Test
                Scenario: Test
                  When I fill in "username"
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("sendKeys(");
    }

    @Test
    void generateMethodBody_handlesSeePattern() {
        String featureContent = """
                Feature: Test
                Scenario: Test
                  Then I see "Welcome message"
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("assertThat(element.isDisplayed())");
        assertThat(result.stepDefinitionCode()).contains("WebDriverWait");
    }

    @Test
    void generateMethodBody_handlesDisplayedPattern() {
        String featureContent = """
                Feature: Test
                Scenario: Test
                  Then the "Success" message is displayed
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("assertThat(element.isDisplayed())");
    }

    @Test
    void generateMethodBody_handlesSelectPattern() {
        String featureContent = """
                Feature: Test
                Scenario: Test
                  When I select "Option 1"
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("// Select from dropdown");
    }

    @Test
    void generateMethodBody_handlesWaitPattern() {
        String featureContent = """
                Feature: Test
                Scenario: Test
                  When I wait for element
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("WebDriverWait");
        assertThat(result.stepDefinitionCode()).contains("// Wait for condition");
    }

    @Test
    void generateMethodBody_handlesPendingStep() {
        String featureContent = """
                Feature: Test
                Scenario: Test
                  When I do something complex
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("PendingException");
        assertThat(result.stepDefinitionCode()).contains("// TODO: Implement step logic");
    }

    // ===== Test imports generation =====

    @Test
    void generateFromContent_includesNecessaryImports() {
        String featureContent = """
                Feature: Test
                Scenario: Test
                  When I click button
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("import io.cucumber.java.en.*");
        assertThat(result.stepDefinitionCode()).contains("import org.openqa.selenium.WebDriver");
        assertThat(result.stepDefinitionCode()).contains("import org.openqa.selenium.WebElement");
        assertThat(result.stepDefinitionCode()).contains("import org.openqa.selenium.By");
        assertThat(result.stepDefinitionCode()).contains("import com.intenthealer.cucumber.annotations.Intent");
        assertThat(result.stepDefinitionCode()).contains("import com.intenthealer.cucumber.annotations.Outcome");
    }

    // ===== Test constructor and fields =====

    @Test
    void generateFromContent_includesConstructor() {
        String featureContent = """
                Feature: Test
                Scenario: Test
                  When I test
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("public TestSteps(WebDriver driver)");
        assertThat(result.stepDefinitionCode()).contains("this.driver = driver;");
    }

    @Test
    void generateFromContent_includesPageObjectInConstructor_whenEnabled() {
        String featureContent = """
                Feature: Test
                Scenario: Test
                  When I test
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        assertThat(result.stepDefinitionCode()).contains("private final TestPage page;");
        assertThat(result.stepDefinitionCode()).contains("this.page = new TestPage(driver);");
    }

    // ===== Test file operations =====

    @Test
    void generateFromFeature_readsFileAndGenerates(@TempDir Path tempDir) throws IOException {
        Path featureFile = tempDir.resolve("test.feature");
        Files.writeString(featureFile, """
                Feature: Test Feature
                Scenario: Test
                  Given I am on test page
                """);

        GeneratedCode result = generator.generateFromFeature(featureFile);

        assertThat(result).isNotNull();
        assertThat(result.className()).isEqualTo("TestFeatureSteps");
    }

    @Test
    void saveToFiles_writesStepDefinitionFile(@TempDir Path tempDir) throws IOException {
        String featureContent = """
                Feature: Test
                Scenario: Test
                  When I click button
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        generator.saveToFiles(result, tempDir);

        Path expectedFile = tempDir.resolve("com/example/steps/TestSteps.java");
        assertThat(expectedFile).exists();
        assertThat(Files.readString(expectedFile)).contains("public class TestSteps");
    }

    @Test
    void saveToFiles_writesPageObjectFile_whenGenerated(@TempDir Path tempDir) throws IOException {
        String featureContent = """
                Feature: Test
                Scenario: Test
                  When I click "button"
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        generator.saveToFiles(result, tempDir);

        Path expectedFile = tempDir.resolve("com/example/steps/TestPage.java");
        assertThat(expectedFile).exists();
        assertThat(Files.readString(expectedFile)).contains("public class TestPage");
    }

    @Test
    void generateFromDirectory_processesAllFeatureFiles(@TempDir Path tempDir) throws IOException {
        // Create multiple feature files
        Path feature1 = tempDir.resolve("login.feature");
        Files.writeString(feature1, """
                Feature: Login
                Scenario: Test
                  When I login
                """);

        Path feature2 = tempDir.resolve("signup.feature");
        Files.writeString(feature2, """
                Feature: Signup
                Scenario: Test
                  When I signup
                """);

        List<GeneratedCode> results = generator.generateFromDirectory(tempDir);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(GeneratedCode::className)
                .containsExactlyInAnyOrder("LoginSteps", "SignupSteps");
    }

    // ===== Test default config =====

    @Test
    void constructor_withoutConfig_usesDefaults() {
        StepDefinitionGenerator defaultGenerator = new StepDefinitionGenerator();

        String featureContent = """
                Feature: Test
                Scenario: Test
                  When I click button
                """;

        GeneratedCode result = defaultGenerator.generateFromContent(featureContent, "test.feature");

        assertThat(result.packageName()).isEqualTo("com.example.steps");
        assertThat(result.pageObjectCode()).isNotNull(); // Default generates page objects
    }

    // ===== Test deduplication of step patterns =====

    @Test
    void generateFromContent_deduplicatesStepPatterns() {
        String featureContent = """
                Feature: Test
                Scenario: Test 1
                  When I click "Button1"

                Scenario: Test 2
                  When I click "Button2"
                """;

        GeneratedCode result = generator.generateFromContent(featureContent, "test.feature");

        // Should only have one method for "I click {string}"
        String code = result.stepDefinitionCode();
        int clickMethodCount = code.split("public void iClickParam", -1).length - 1;
        assertThat(clickMethodCount).isEqualTo(1);
    }
}
