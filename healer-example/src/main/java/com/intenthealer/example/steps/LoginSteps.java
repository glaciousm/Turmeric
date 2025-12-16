package com.intenthealer.example.steps;

import com.intenthealer.core.model.HealPolicy;
import com.intenthealer.core.model.IntentContract;
import com.intenthealer.cucumber.annotations.Intent;
import com.intenthealer.cucumber.annotations.Invariant;
import com.intenthealer.cucumber.annotations.Outcome;
import com.intenthealer.example.config.HealerTestConfig;
import com.intenthealer.example.pages.LoginPage;
import com.intenthealer.core.intent.checks.builtin.*;
import com.intenthealer.selenium.driver.HealingWebDriver;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Step definitions for login scenarios with Intent-based healing.
 */
public class LoginSteps {

    private HealingWebDriver driver;
    private LoginPage loginPage;
    private String baseUrl = "https://example.com";

    @Before
    public void setUp() {
        HealerTestConfig config = HealerTestConfig.getInstance();
        driver = config.createDriver();
        loginPage = new LoginPage(driver);
    }

    @After
    public void tearDown() {
        HealerTestConfig.getInstance().shutdown();
    }

    @Given("I am on the login page")
    @Intent(
        action = "navigate_to_login",
        description = "Navigate to the application login page",
        policy = HealPolicy.AUTO_SAFE
    )
    public void i_am_on_the_login_page() {
        driver.setCurrentIntent(
            IntentContract.builder()
                .action("navigate_to_login")
                .description("Navigate to the application login page")
                .policy(HealPolicy.AUTO_SAFE)
                .build(),
            "I am on the login page"
        );

        loginPage.navigate(baseUrl);
        driver.clearCurrentIntent();
    }

    @When("I enter username {string}")
    @Intent(
        action = "enter_username",
        description = "Enter the username in the login form",
        policy = HealPolicy.AUTO_SAFE
    )
    @Invariant(value = NoErrorBannerCheck.class)
    public void i_enter_username(String username) {
        driver.setCurrentIntent(
            IntentContract.builder()
                .action("enter_username")
                .description("Enter the username '" + username + "' in the login form")
                .policy(HealPolicy.AUTO_SAFE)
                .build(),
            "I enter username " + username
        );

        loginPage.enterUsername(username);
        driver.clearCurrentIntent();
    }

    @When("I enter password {string}")
    @Intent(
        action = "enter_password",
        description = "Enter the password in the login form",
        policy = HealPolicy.AUTO_SAFE
    )
    @Invariant(value = NoErrorBannerCheck.class)
    public void i_enter_password(String password) {
        driver.setCurrentIntent(
            IntentContract.builder()
                .action("enter_password")
                .description("Enter the password in the login form (masked)")
                .policy(HealPolicy.AUTO_SAFE)
                .build(),
            "I enter password"
        );

        loginPage.enterPassword(password);
        driver.clearCurrentIntent();
    }

    @When("I click the login button")
    @Intent(
        action = "submit_login",
        description = "Click the login/submit button to authenticate",
        policy = HealPolicy.AUTO_SAFE
    )
    @Outcome(
        checks = {UrlChangedCheck.class},
        description = "URL should change after successful login"
    )
    public void i_click_the_login_button() {
        driver.setCurrentIntent(
            IntentContract.builder()
                .action("submit_login")
                .description("Click the login/submit button to authenticate")
                .policy(HealPolicy.AUTO_SAFE)
                .build(),
            "I click the login button"
        );

        loginPage.clickLogin();
        driver.clearCurrentIntent();
    }

    @Then("I should be logged in successfully")
    @Intent(
        action = "verify_login_success",
        description = "Verify the user is logged in and on the dashboard",
        policy = HealPolicy.OFF  // Assertions should not be healed
    )
    public void i_should_be_logged_in_successfully() {
        String currentUrl = driver.getCurrentUrl();
        assertTrue(
            currentUrl.contains("/dashboard") || currentUrl.contains("/home"),
            "Expected to be on dashboard after login, but was on: " + currentUrl
        );
    }

    @Then("I should see an error message {string}")
    @Intent(
        action = "verify_error_message",
        description = "Verify an error message is displayed",
        policy = HealPolicy.OFF  // Assertions should not be healed
    )
    public void i_should_see_an_error_message(String expectedMessage) {
        String actualError = loginPage.getErrorMessage();
        assertNotNull(actualError, "Expected an error message but none was displayed");
        assertTrue(
            actualError.contains(expectedMessage),
            "Expected error message containing '" + expectedMessage + "' but got: " + actualError
        );
    }

    @Then("I should still be on the login page")
    @Intent(
        action = "verify_still_on_login",
        description = "Verify the user remains on the login page",
        policy = HealPolicy.OFF
    )
    public void i_should_still_be_on_the_login_page() {
        String currentUrl = driver.getCurrentUrl();
        assertTrue(
            currentUrl.contains("/login"),
            "Expected to still be on login page, but was on: " + currentUrl
        );
    }
}
