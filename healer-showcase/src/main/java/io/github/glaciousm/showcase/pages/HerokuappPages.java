package io.github.glaciousm.showcase.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Page Objects for the-internet.herokuapp.com test site.
 *
 * IMPORTANT: This class uses INTENTIONALLY WRONG LOCATORS to demonstrate
 * Intent Healer's self-healing capabilities. In a real project, you would
 * use correct locators - but here we're showing what happens when locators
 * break and how Intent Healer automatically fixes them.
 *
 * Each method documents:
 * - WRONG: The broken locator being used
 * - REAL: What the actual element looks like
 * - HEALED: What Intent Healer will fix it to
 */
public class HerokuappPages {

    private final WebDriver driver;
    private final WebDriverWait wait;

    private static final String BASE_URL = "https://the-internet.herokuapp.com";

    public HerokuappPages(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    // ==========================================================================
    // NAVIGATION
    // ==========================================================================

    public void navigateToLogin() {
        driver.get(BASE_URL + "/login");
    }

    public void navigateToCheckboxes() {
        driver.get(BASE_URL + "/checkboxes");
    }

    public void navigateToAddRemove() {
        driver.get(BASE_URL + "/add_remove_elements/");
    }

    public void navigateToDropdown() {
        driver.get(BASE_URL + "/dropdown");
    }

    public void navigateToChallengingDom() {
        driver.get(BASE_URL + "/challenging_dom");
    }

    public void navigateToDynamicLoading() {
        driver.get(BASE_URL + "/dynamic_loading/1");
    }

    public void navigateToHomepage() {
        driver.get(BASE_URL);
    }

    public void navigateToTables() {
        driver.get(BASE_URL + "/tables");
    }

    // ==========================================================================
    // TEST 1: Login with Wrong ID
    // ==========================================================================

    public void enterUsername(String username) {
        driver.findElement(By.id("username")).sendKeys(username);
    }

    public void enterPassword(String password) {
        driver.findElement(By.id("password")).sendKeys(password);
    }

    /**
     * WRONG: By.id("login-btn") - This ID doesn't exist
     * REAL:  <button class="radius" type="submit"><i class="fa fa-2x fa-sign-in"></i> Login</button>
     * HEALED: Should find by class="radius" or type="submit" or text content
     */
    public void clickLoginButtonWithWrongId(String wrongId) {
        By wrongLocator = By.id(wrongId);
        driver.findElement(wrongLocator).click();
    }

    /**
     * WRONG: By.xpath("//form[@id='login-form']/button[@type='submit']")
     * REAL:  <button class="radius" type="submit"> (form has different ID)
     * HEALED: Should find by class="radius" or type="submit"
     */
    public void clickLoginButtonWithWrongXpath(String wrongXpath) {
        By wrongLocator = By.xpath(wrongXpath);
        driver.findElement(wrongLocator).click();
    }

    /**
     * WRONG: By.cssSelector("input.submit-btn")
     * REAL:  <button class="radius" type="submit">
     * HEALED: Should find by class="radius" or type="submit"
     */
    public void clickLoginButtonWithWrongCss(String wrongCss) {
        By wrongLocator = By.cssSelector(wrongCss);
        driver.findElement(wrongLocator).click();
    }

    public boolean isOnSecureArea() {
        return driver.getCurrentUrl().contains("/secure") ||
               driver.getPageSource().contains("Secure Area");
    }

    // ==========================================================================
    // TEST 2: Checkbox with Wrong Class
    // ==========================================================================

    /**
     * WRONG: By.className("checkbox-input") - This class doesn't exist
     * REAL:  <input type="checkbox"> (no class attribute)
     * HEALED: Should find by type="checkbox"
     */
    public void clickCheckboxWithWrongClass(String wrongClass) {
        By wrongLocator = By.className(wrongClass);
        driver.findElement(wrongLocator).click();
    }

    public boolean isCheckboxChecked() {
        return driver.findElements(By.cssSelector("input[type='checkbox']"))
                .get(0).isSelected();
    }

    // ==========================================================================
    // TEST 3: Add Element with Wrong Tag (Link vs Button)
    // ==========================================================================

    /**
     * WRONG: By.linkText("Add Element") - Looking for <a> tag
     * REAL:  <button onclick="addElement()">Add Element</button>
     * HEALED: Should find button with matching text
     */
    public void clickAddElementWithLinkText(String linkText) {
        By wrongLocator = By.linkText(linkText);
        driver.findElement(wrongLocator).click();
    }

    /**
     * WRONG: By.id("add-button") - No such ID exists
     * REAL:  <button onclick="addElement()">Add Element</button>
     * HEALED: Should find by button tag or text content
     */
    public void clickAddElementWithWrongId(String wrongId) {
        By wrongLocator = By.id(wrongId);
        driver.findElement(wrongLocator).click();
    }

    public boolean isDeleteButtonVisible() {
        return !driver.findElements(By.className("added-manually")).isEmpty();
    }

    /**
     * WRONG: By.cssSelector("button.delete-action")
     * REAL:  <button class="added-manually">Delete</button>
     * HEALED: Should find by class="added-manually" or text content
     */
    public void clickDeleteWithWrongCss(String wrongCss) {
        By wrongLocator = By.cssSelector(wrongCss);
        driver.findElement(wrongLocator).click();
    }

    public boolean isDeleteButtonRemoved() {
        return driver.findElements(By.className("added-manually")).isEmpty();
    }

    // ==========================================================================
    // TEST 4: Dropdown with Wrong CSS Selector
    // ==========================================================================

    /**
     * WRONG: By.cssSelector("select.dropdown-menu") - Wrong class
     * REAL:  <select id="dropdown">
     * HEALED: Should find by tag "select" or id="dropdown"
     */
    public void selectDropdownWithWrongCss(String wrongCss, String optionText) {
        By wrongLocator = By.cssSelector(wrongCss);
        WebElement selectElement = driver.findElement(wrongLocator);
        new Select(selectElement).selectByVisibleText(optionText);
    }

    public String getSelectedDropdownValue() {
        WebElement dropdown = driver.findElement(By.id("dropdown"));
        return new Select(dropdown).getFirstSelectedOption().getText();
    }

    // ==========================================================================
    // TEST 5: Number Input with Wrong XPath (Inputs page)
    // ==========================================================================

    public void navigateToInputs() {
        driver.get(BASE_URL + "/inputs");
    }

    /**
     * WRONG: By.xpath("//input[@name='quantity']")
     * REAL:  <input type="number"> (no name attribute)
     * HEALED: Should find by type="number"
     */
    public void enterNumberWithWrongXpath(String wrongXpath, String number) {
        By wrongLocator = By.xpath(wrongXpath);
        driver.findElement(wrongLocator).sendKeys(number);
    }

    public String getNumberInputValue() {
        return driver.findElement(By.cssSelector("input[type='number']")).getAttribute("value");
    }

    // ==========================================================================
    // TEST 6: Checkbox with Wrong Name
    // ==========================================================================

    /**
     * WRONG: By.name("remember-me")
     * REAL:  <input type="checkbox"> (no name attribute)
     * HEALED: Should find by type="checkbox"
     */
    public void toggleCheckboxWithWrongName(String wrongName) {
        By wrongLocator = By.name(wrongName);
        driver.findElement(wrongLocator).click();
    }

    public boolean isAnyCheckboxChecked() {
        return driver.findElements(By.cssSelector("input[type='checkbox']"))
                .stream().anyMatch(WebElement::isSelected);
    }

    // ==========================================================================
    // TEST 7: Challenging DOM with Dynamic ID
    // ==========================================================================

    /**
     * WRONG: By.id("btn-action-12345") - Fake dynamic ID
     * REAL:  <a class="button" href="#edit">edit</a>
     * HEALED: Should find by class="button" or href containing "edit"
     */
    public void clickActionButtonWithWrongId(String wrongId) {
        By wrongLocator = By.id(wrongId);
        driver.findElement(wrongLocator).click();
    }

    public boolean isChallengingDomFunctional() {
        return driver.findElement(By.id("canvas")) != null;
    }

    // ==========================================================================
    // TEST 8: Dynamic Loading with Wrong Element Type
    // ==========================================================================

    /**
     * WRONG: By.cssSelector("input[type='submit']") - Wrong element type
     * REAL:  <button>Start</button>
     * HEALED: Should find button with text "Start"
     */
    public void clickStartWithWrongType(String wrongCss) {
        By wrongLocator = By.cssSelector(wrongCss);
        driver.findElement(wrongLocator).click();
    }

    public void waitForLoadingComplete() {
        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.id("loading")));
    }

    public boolean isFinishMessageVisible() {
        try {
            return driver.findElement(By.id("finish")).isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * WRONG: By.cssSelector("span#finish-text")
     * REAL:  <h4>Hello World!</h4> inside <div id="finish">
     * HEALED: Should find element containing "Hello World"
     */
    public String getFinishTextWithWrongTag(String wrongCss) {
        By wrongLocator = By.cssSelector(wrongCss);
        return driver.findElement(wrongLocator).getText();
    }

    public String getActualFinishText() {
        return driver.findElement(By.cssSelector("#finish h4")).getText();
    }

    // ==========================================================================
    // TEST 9: Edit Link with Wrong Partial Text
    // ==========================================================================

    /**
     * WRONG: By.partialLinkText("modify")
     * REAL:  <a href="#edit">edit</a>
     * HEALED: Should find link with text "edit"
     */
    public void clickEditLinkWithWrongPartialText(String wrongText) {
        By wrongLocator = By.partialLinkText(wrongText);
        driver.findElement(wrongLocator).click();
    }

    // ==========================================================================
    // TEST 9: Link with Partial Text Mismatch
    // ==========================================================================

    /**
     * WRONG: By.linkText("Checkboxes Page") - Extra word
     * REAL:  <a href="/checkboxes">Checkboxes</a>
     * HEALED: Should find by partial text or href
     */
    public void clickLinkWithWrongText(String wrongText) {
        By wrongLocator = By.linkText(wrongText);
        driver.findElement(wrongLocator).click();
    }

    public boolean isOnCheckboxesPage() {
        return driver.getCurrentUrl().contains("/checkboxes");
    }

    // ==========================================================================
    // TEST 10: Table Cell with Wrong Nested Selector
    // ==========================================================================

    /**
     * WRONG: By.cssSelector("table#users tr[data-id='1'] a.edit-btn")
     * REAL:  <a href="#edit">edit</a> in table without those attributes
     * HEALED: Should find edit link in table row containing "Smith"
     */
    public void clickEditForUserWithWrongSelector(String wrongCss, String userName) {
        By wrongLocator = By.cssSelector(wrongCss);
        driver.findElement(wrongLocator).click();
    }

    public boolean doesUrlContain(String text) {
        return driver.getCurrentUrl().contains(text);
    }

    // ==========================================================================
    // UTILITY
    // ==========================================================================

    public String getCurrentUrl() {
        return driver.getCurrentUrl();
    }

    public String getPageTitle() {
        return driver.getTitle();
    }
}
