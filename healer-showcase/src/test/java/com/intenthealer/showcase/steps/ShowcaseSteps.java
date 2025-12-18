package com.intenthealer.showcase.steps;

import com.intenthealer.showcase.config.ShowcaseConfig;
import com.intenthealer.showcase.pages.HerokuappPages;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Step definitions for Intent Healer Showcase.
 *
 * <p>Each step uses INTENTIONALLY WRONG locators to demonstrate self-healing.
 * The healing happens transparently - tests pass despite broken locators.</p>
 *
 * <p><b>ZERO-CODE Integration:</b> This class uses a regular WebDriver.
 * The Java Agent automatically adds self-healing capability - no
 * HealingWebDriver wrapper needed!</p>
 */
public class ShowcaseSteps {

    private static final Logger logger = LoggerFactory.getLogger(ShowcaseSteps.class);

    // Regular WebDriver - the Java Agent adds self-healing automatically!
    private WebDriver driver;
    private HerokuappPages pages;

    private void ensureDriver() {
        if (driver == null) {
            driver = ShowcaseConfig.getInstance().getDriver();
            pages = new HerokuappPages(driver);
        }
    }

    // ==========================================================================
    // BACKGROUND
    // ==========================================================================

    @Given("Intent Healer is enabled with mock LLM provider")
    public void intentHealerIsEnabledWithMockLlmProvider() {
        ensureDriver();
        logger.info("========================================");
        logger.info("Intent Healer Showcase - Self-Healing Demo");
        logger.info("LLM Provider: mock (heuristic-based)");
        logger.info("========================================");
    }

    // ==========================================================================
    // TEST 1: Login with Wrong ID
    // ==========================================================================

    @Given("I am on the Herokuapp login page")
    public void iAmOnTheHerokuappLoginPage() {
        ensureDriver();
        pages.navigateToLogin();
        logger.info("[TEST] Navigated to login page");
    }

    @When("I enter username {string}")
    public void iEnterUsername(String username) {
        pages.enterUsername(username);
        logger.info("[TEST] Entered username: {}", username);
    }

    @And("I enter password {string}")
    public void iEnterPassword(String password) {
        pages.enterPassword(password);
        logger.info("[TEST] Entered password: ****");
    }

    @And("I click the login button using wrong ID {string}")
    public void iClickTheLoginButtonUsingWrongId(String wrongId) {
        logger.info("[HEALING TEST] Using WRONG locator: By.id(\"{}\")", wrongId);
        logger.info("[HEALING TEST] Real element: <button class=\"radius\" type=\"submit\">");
        pages.clickLoginButtonWithWrongId(wrongId);
        logger.info("[HEALING SUCCESS] Button clicked despite wrong locator!");
    }

    @Then("I should see the secure area")
    public void iShouldSeeTheSecureArea() {
        assertTrue(pages.isOnSecureArea(), "Should be on secure area after login");
        logger.info("[VERIFIED] Successfully logged in - secure area visible");
    }

    // ==========================================================================
    // TEST 2: Checkbox with Wrong Class
    // ==========================================================================

    @Given("I am on the Herokuapp checkboxes page")
    public void iAmOnTheHerokuappCheckboxesPage() {
        ensureDriver();
        pages.navigateToCheckboxes();
        logger.info("[TEST] Navigated to checkboxes page");
    }

    @When("I click the checkbox using wrong class {string}")
    public void iClickTheCheckboxUsingWrongClass(String wrongClass) {
        logger.info("[HEALING TEST] Using WRONG locator: By.className(\"{}\")", wrongClass);
        logger.info("[HEALING TEST] Real element: <input type=\"checkbox\"> (no class)");
        pages.clickCheckboxWithWrongClass(wrongClass);
        logger.info("[HEALING SUCCESS] Checkbox clicked despite wrong locator!");
    }

    @Then("the checkbox should be checked")
    public void theCheckboxShouldBeChecked() {
        assertTrue(pages.isCheckboxChecked(), "Checkbox should be checked");
        logger.info("[VERIFIED] Checkbox is now checked");
    }

    // ==========================================================================
    // TEST 3: Add Element with Wrong Tag
    // ==========================================================================

    @Given("I am on the Herokuapp add remove elements page")
    public void iAmOnTheHerokuappAddRemoveElementsPage() {
        ensureDriver();
        pages.navigateToAddRemove();
        logger.info("[TEST] Navigated to add/remove elements page");
    }

    @When("I click add element using link text {string}")
    public void iClickAddElementUsingLinkText(String linkText) {
        logger.info("[HEALING TEST] Using WRONG locator: By.linkText(\"{}\")", linkText);
        logger.info("[HEALING TEST] Real element: <button>Add Element</button> (not a link!)");
        pages.clickAddElementWithLinkText(linkText);
        logger.info("[HEALING SUCCESS] Button clicked despite looking for link!");
    }

    @Then("a delete button should appear")
    public void aDeleteButtonShouldAppear() {
        assertTrue(pages.isDeleteButtonVisible(), "Delete button should appear");
        logger.info("[VERIFIED] Delete button appeared");
    }

    // ==========================================================================
    // TEST 4: Dropdown with Wrong CSS
    // ==========================================================================

    @Given("I am on the Herokuapp dropdown page")
    public void iAmOnTheHerokuappDropdownPage() {
        ensureDriver();
        pages.navigateToDropdown();
        logger.info("[TEST] Navigated to dropdown page");
    }

    @When("I select {string} from dropdown using wrong CSS {string}")
    public void iSelectFromDropdownUsingWrongCss(String option, String wrongCss) {
        logger.info("[HEALING TEST] Using WRONG locator: By.cssSelector(\"{}\")", wrongCss);
        logger.info("[HEALING TEST] Real element: <select id=\"dropdown\">");
        pages.selectDropdownWithWrongCss(wrongCss, option);
        logger.info("[HEALING SUCCESS] Dropdown selected despite wrong CSS!");
    }

    @Then("the dropdown should show {string}")
    public void theDropdownShouldShow(String expected) {
        assertEquals(expected, pages.getSelectedDropdownValue());
        logger.info("[VERIFIED] Dropdown shows: {}", expected);
    }

    // ==========================================================================
    // TEST 5: Login with Wrong XPath
    // ==========================================================================

    @And("I click login using wrong XPath {string}")
    public void iClickLoginUsingWrongXpath(String wrongXpath) {
        logger.info("[HEALING TEST] Using WRONG locator: By.xpath(\"{}\")", wrongXpath);
        logger.info("[HEALING TEST] Real element: <button class=\"radius\" type=\"submit\">");
        pages.clickLoginButtonWithWrongXpath(wrongXpath);
        logger.info("[HEALING SUCCESS] Login button clicked despite wrong XPath!");
    }

    // ==========================================================================
    // TEST 6: Login with Wrong CSS for Submit
    // ==========================================================================

    @And("I click login using wrong CSS {string}")
    public void iClickLoginUsingWrongCss(String wrongCss) {
        logger.info("[HEALING TEST] Using WRONG locator: By.cssSelector(\"{}\")", wrongCss);
        logger.info("[HEALING TEST] Real element: <button class=\"radius\" type=\"submit\">");
        pages.clickLoginButtonWithWrongCss(wrongCss);
        logger.info("[HEALING SUCCESS] Login button clicked despite wrong CSS!");
    }

    // ==========================================================================
    // TEST 7: Challenging DOM with Dynamic ID
    // ==========================================================================

    @Given("I am on the Herokuapp challenging DOM page")
    public void iAmOnTheHerokuappChallengingDomPage() {
        ensureDriver();
        pages.navigateToChallengingDom();
        logger.info("[TEST] Navigated to challenging DOM page");
    }

    @When("I click the first action button using wrong ID {string}")
    public void iClickTheFirstActionButtonUsingWrongId(String wrongId) {
        logger.info("[HEALING TEST] Using WRONG locator: By.id(\"{}\")", wrongId);
        logger.info("[HEALING TEST] Real element: <a class=\"button\"> (dynamic classes)");
        pages.clickActionButtonWithWrongId(wrongId);
        logger.info("[HEALING SUCCESS] Button clicked despite fake dynamic ID!");
    }

    @Then("the page should remain functional")
    public void thePageShouldRemainFunctional() {
        assertTrue(pages.isChallengingDomFunctional(), "Page should remain functional");
        logger.info("[VERIFIED] Challenging DOM page is functional");
    }

    // ==========================================================================
    // TEST 8: Dynamic Loading with Wrong Element Type
    // ==========================================================================

    @Given("I am on the Herokuapp dynamic loading page")
    public void iAmOnTheHerokuappDynamicLoadingPage() {
        ensureDriver();
        pages.navigateToDynamicLoading();
        logger.info("[TEST] Navigated to dynamic loading page");
    }

    @When("I click start using wrong element type {string}")
    public void iClickStartUsingWrongElementType(String wrongCss) {
        logger.info("[HEALING TEST] Using WRONG locator: By.cssSelector(\"{}\")", wrongCss);
        logger.info("[HEALING TEST] Real element: <button>Start</button>");
        pages.clickStartWithWrongType(wrongCss);
        logger.info("[HEALING SUCCESS] Start button clicked despite wrong type!");
    }

    @And("I wait for the loading to complete")
    public void iWaitForTheLoadingToComplete() {
        pages.waitForLoadingComplete();
        logger.info("[TEST] Loading complete");
    }

    @Then("the finish message should be visible")
    public void theFinishMessageShouldBeVisible() {
        assertTrue(pages.isFinishMessageVisible(), "Finish message should be visible");
        logger.info("[VERIFIED] Finish message is visible");
    }

    // ==========================================================================
    // TEST 9: Add Button with Wrong ID
    // ==========================================================================

    @When("I click add using wrong ID {string}")
    public void iClickAddUsingWrongId(String wrongId) {
        logger.info("[HEALING TEST] Using WRONG locator: By.id(\"{}\")", wrongId);
        logger.info("[HEALING TEST] Real element: <button onclick=\"addElement()\">Add Element</button>");
        pages.clickAddElementWithWrongId(wrongId);
        logger.info("[HEALING SUCCESS] Add button clicked despite wrong ID!");
    }

    // ==========================================================================
    // TEST 10: Table Cell with Wrong Nested Selector
    // ==========================================================================

    @Given("I am on the Herokuapp tables page")
    public void iAmOnTheHerokuappTablesPage() {
        ensureDriver();
        pages.navigateToTables();
        logger.info("[TEST] Navigated to tables page");
    }

    @When("I click edit for {string} using wrong selector {string}")
    public void iClickEditForUsingWrongSelector(String userName, String wrongCss) {
        logger.info("[HEALING TEST] Using WRONG locator: By.cssSelector(\"{}\")", wrongCss);
        logger.info("[HEALING TEST] Real element: <a href=\"#edit\">edit</a> in table");
        pages.clickEditForUserWithWrongSelector(wrongCss, userName);
        logger.info("[HEALING SUCCESS] Edit link clicked despite wrong nested selector!");
    }

    @Then("the URL should contain {string}")
    public void theUrlShouldContain(String expected) {
        assertTrue(pages.doesUrlContain(expected), "URL should contain: " + expected);
        logger.info("[VERIFIED] URL contains: {}", expected);
    }
}
