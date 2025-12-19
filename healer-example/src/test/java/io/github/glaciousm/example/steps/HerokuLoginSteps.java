package io.github.glaciousm.example.steps;

import io.github.glaciousm.example.config.HealerTestConfig;
import io.github.glaciousm.example.pages.HerokuLoginPage;
import io.github.glaciousm.selenium.driver.HealingWebDriver;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.And;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Step definitions for Herokuapp login scenarios.
 * Uses HealingWebDriver to automatically heal broken locators.
 */
public class HerokuLoginSteps {

    private final HealingWebDriver driver;
    private final HerokuLoginPage loginPage;

    public HerokuLoginSteps() {
        this.driver = HealerTestConfig.getInstance().getDriver();
        this.loginPage = new HerokuLoginPage(driver);
    }

    // ========== Given Steps ==========

    @Given("I am on the Herokuapp login page")
    public void iAmOnTheHerokuappLoginPage() {
        loginPage.navigateTo();
        assertTrue(loginPage.getPageTitle().contains("The Internet"),
                "Should be on The Internet website");
    }

    // ========== When Steps ==========

    @When("I enter username {string}")
    public void iEnterUsername(String username) {
        loginPage.enterUsername(username);
    }

    @When("I enter password {string}")
    public void iEnterPassword(String password) {
        loginPage.enterPassword(password);
    }

    @When("I click the login button")
    public void iClickTheLoginButton() {
        // This step will trigger the healing!
        // The locator By.id("login-btn") is WRONG
        // HealingWebDriver will catch the NoSuchElementException
        // and find the correct button element
        loginPage.clickLoginButton();
    }

    @When("I login with username {string} and password {string}")
    public void iLoginWithUsernameAndPassword(String username, String password) {
        loginPage.login(username, password);
    }

    @When("I click the logout button")
    public void iClickTheLogoutButton() {
        loginPage.logout();
    }

    // ========== Then Steps ==========

    @Then("I should see the secure area page")
    public void iShouldSeeTheSecureAreaPage() {
        assertTrue(loginPage.isOnSecureArea(),
                "Should be on the Secure Area page after login");
    }

    @Then("I should see a success message containing {string}")
    public void iShouldSeeASuccessMessageContaining(String expectedText) {
        assertTrue(loginPage.isLoginSuccessful(),
                "Success message should be displayed");
        String actualMessage = loginPage.getSuccessMessage();
        assertTrue(actualMessage.contains(expectedText),
                "Success message should contain: " + expectedText + ", but was: " + actualMessage);
    }

    @Then("I should see an error message containing {string}")
    public void iShouldSeeAnErrorMessageContaining(String expectedText) {
        assertTrue(loginPage.isErrorDisplayed(),
                "Error message should be displayed");
        String actualMessage = loginPage.getErrorMessage();
        assertTrue(actualMessage.contains(expectedText),
                "Error message should contain: " + expectedText + ", but was: " + actualMessage);
    }

    @Then("I should still be on the login page")
    public void iShouldStillBeOnTheLoginPage() {
        String currentUrl = driver.getCurrentUrl();
        assertTrue(currentUrl.contains("/login"),
                "Should still be on login page, but URL is: " + currentUrl);
    }

    @Then("I should be back on the login page")
    public void iShouldBeBackOnTheLoginPage() {
        String currentUrl = driver.getCurrentUrl();
        assertTrue(currentUrl.contains("/login"),
                "Should be back on login page after logout, but URL is: " + currentUrl);
    }

    // ========== And Steps ==========

    @And("I should be able to logout")
    public void iShouldBeAbleToLogout() {
        assertTrue(loginPage.isLogoutButtonVisible(),
                "Logout button should be visible after login");
    }
}
