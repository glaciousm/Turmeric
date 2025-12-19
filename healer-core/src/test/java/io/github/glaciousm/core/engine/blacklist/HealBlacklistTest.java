package io.github.glaciousm.core.engine.blacklist;

import io.github.glaciousm.core.model.LocatorInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HealBlacklist")
class HealBlacklistTest {

    private HealBlacklist blacklist;

    @BeforeEach
    void setUp() {
        blacklist = new HealBlacklist(); // No persistence
    }

    @Test
    @DisplayName("should block blacklisted locators")
    void blockBlacklistedLocators() {
        LocatorInfo original = new LocatorInfo("id", "submit-btn");
        blacklist.addLocator(original, "Known bad locator");

        assertTrue(blacklist.isBlacklisted(
                "https://example.com/page",
                original,
                new LocatorInfo("css", "button")));
    }

    @Test
    @DisplayName("should not block non-blacklisted locators")
    void notBlockNonBlacklisted() {
        assertFalse(blacklist.isBlacklisted(
                "https://example.com/page",
                new LocatorInfo("id", "submit"),
                new LocatorInfo("css", "button")));
    }

    @Test
    @DisplayName("should block specific heal combination")
    void blockSpecificHealCombination() {
        LocatorInfo original = new LocatorInfo("id", "submit");
        LocatorInfo badHealed = new LocatorInfo("css", ".wrong-button");

        blacklist.addLocator(original, badHealed, "This heal is incorrect");

        // Should block the specific bad heal
        assertTrue(blacklist.isBlacklisted(
                "https://example.com/page",
                original,
                badHealed));

        // Should NOT block different healed locator
        assertFalse(blacklist.isBlacklisted(
                "https://example.com/page",
                original,
                new LocatorInfo("css", ".correct-button")));
    }

    @Test
    @DisplayName("should remove entries by ID")
    void removeById() {
        LocatorInfo original = new LocatorInfo("id", "submit");
        BlacklistEntry entry = blacklist.addLocator(original, "Test");

        assertTrue(blacklist.isBlacklisted("https://example.com", original, null));

        blacklist.remove(entry.getId());

        assertFalse(blacklist.isBlacklisted("https://example.com", original, null));
    }

    @Test
    @DisplayName("should remove entries by original locator")
    void removeByOriginalLocator() {
        LocatorInfo original = new LocatorInfo("id", "submit");
        blacklist.addLocator(original, "Reason 1");
        blacklist.addLocator(original, new LocatorInfo("css", ".btn1"), "Reason 2");

        assertEquals(2, blacklist.size());

        int removed = blacklist.removeByOriginalLocator(original);

        assertEquals(2, removed);
        assertEquals(0, blacklist.size());
    }

    @Test
    @DisplayName("should clear all entries")
    void clearAll() {
        blacklist.addLocator(new LocatorInfo("id", "elem1"), "R1");
        blacklist.addLocator(new LocatorInfo("id", "elem2"), "R2");

        assertEquals(2, blacklist.size());

        blacklist.clear();

        assertEquals(0, blacklist.size());
    }

    @Test
    @DisplayName("should get entry by ID")
    void getById() {
        LocatorInfo original = new LocatorInfo("id", "submit");
        BlacklistEntry entry = blacklist.addLocator(original, "Test reason");

        var retrieved = blacklist.get(entry.getId());

        assertTrue(retrieved.isPresent());
        assertEquals("Test reason", retrieved.get().getReason());
    }

    @Test
    @DisplayName("should list all entries")
    void listAllEntries() {
        blacklist.addLocator(new LocatorInfo("id", "elem1"), "R1");
        blacklist.addLocator(new LocatorInfo("id", "elem2"), "R2");

        var all = blacklist.getAll();

        assertEquals(2, all.size());
    }

    @Test
    @DisplayName("BlacklistEntry should match correctly")
    void entryMatching() {
        BlacklistEntry entry = BlacklistEntry.builder()
                .pageUrlPattern("https://example\\.com/users/.*")
                .originalLocator("id", "submit")
                .reason("Test")
                .build();

        // Should match
        assertTrue(entry.matches(
                "https://example.com/users/123",
                "id", "submit",
                null, null));

        // Should not match - different page
        assertFalse(entry.matches(
                "https://example.com/products/123",
                "id", "submit",
                null, null));

        // Should not match - different locator
        assertFalse(entry.matches(
                "https://example.com/users/123",
                "id", "cancel",
                null, null));
    }
}
