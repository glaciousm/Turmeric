package io.github.glaciousm.example.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Page Object for the Herokuapp Login page.
 * URL: https://the-internet.herokuapp.com/login
 *
 * This page object contains an INTENTIONALLY WRONG locator for the login button
 * to demonstrate the self-healing capability of Intent Healer.
 */
public class HerokuLoginPage {

    private static final String URL = "https://the-internet.herokuapp.com/login";

    private final WebDriver driver;
    private final WebDriverWait wait;

    // Locators - correct ones
    private final By usernameField = By.id("username");
    private final By passwordField = By.id("password");

    // INTENTIONALLY WRONG locator to trigger healing!
    // The actual button has: <button class="radius" type="submit">
    // We use a non-existent ID to force the healer to find the correct element
    private final By loginButton = By.id("login-btn");

    // Success/failure indicators
    private final By successMessage = By.cssSelector(".flash.success");
    private final By errorMessage = By.cssSelector(".flash.error");
    private final By logoutButton = By.cssSelector("a.button[href='/logout']");

    // Secure area header
    private final By secureAreaHeader = By.tagName("h2");

    public HerokuLoginPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    /**
     * Navigate to the login page.
     */
    public void navigateTo() {
        driver.get(URL);
        wait.until(ExpectedConditions.visibilityOfElementLocated(usernameField));
    }

    /**
     * Enter username in the username field.
     */
    public void enterUsername(String username) {
        WebElement field = driver.findElement(usernameField);
        field.clear();
        field.sendKeys(username);
    }

    /**
     * Enter password in the password field.
     */
    public void enterPassword(String password) {
        WebElement field = driver.findElement(passwordField);
        field.clear();
        field.sendKeys(password);
    }

    /**
     * Click the login button.
     * This will trigger healing because the locator is WRONG.
     */
    public void clickLoginButton() {
        // This line will fail with NoSuchElementException
        // The HealingWebDriver will catch it and find the correct button
        driver.findElement(loginButton).click();
    }

    /**
     * Perform complete login flow.
     */
    public void login(String username, String password) {
        enterUsername(username);
        enterPassword(password);
        clickLoginButton();
    }

    /**
     * Check if login was successful.
     */
    public boolean isLoginSuccessful() {
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(successMessage));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if error message is displayed.
     */
    public boolean isErrorDisplayed() {
        try {
            return driver.findElement(errorMessage).isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the success message text.
     */
    public String getSuccessMessage() {
        return driver.findElement(successMessage).getText();
    }

    /**
     * Get the error message text.
     */
    public String getErrorMessage() {
        return driver.findElement(errorMessage).getText();
    }

    /**
     * Check if we're on the secure area page.
     */
    public boolean isOnSecureArea() {
        try {
            WebElement header = wait.until(ExpectedConditions.visibilityOfElementLocated(secureAreaHeader));
            return header.getText().contains("Secure Area");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Click logout button.
     */
    public void logout() {
        driver.findElement(logoutButton).click();
    }

    /**
     * Check if logout button is visible.
     */
    public boolean isLogoutButtonVisible() {
        try {
            return driver.findElement(logoutButton).isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get page title.
     */
    public String getPageTitle() {
        return driver.getTitle();
    }
}
