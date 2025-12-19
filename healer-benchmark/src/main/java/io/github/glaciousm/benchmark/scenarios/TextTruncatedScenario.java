/*
 * Intent Healer - Self-Healing Test Automation
 * Copyright (C) 2025 Menelaos Mamouzellos
 *
 * This program is dual-licensed under AGPL-3.0 and Commercial License.
 * See LICENSE and LICENSE-COMMERCIAL.md for details.
 */
package io.github.glaciousm.benchmark.scenarios;

import io.github.glaciousm.benchmark.BenchmarkResult.ExpectedOutcome;
import org.openqa.selenium.By;

/**
 * Scenario 21: Text truncated (Read More → More...)
 * Tests healing when link text is shortened/truncated.
 */
public class TextTruncatedScenario extends AbstractBenchmarkScenario {

    @Override
    public String getId() {
        return "21";
    }

    @Override
    public String getName() {
        return "Text Truncated";
    }

    @Override
    public String getCategory() {
        return "Text/Content Changes";
    }

    @Override
    public String getDescription() {
        return "Link text changed from 'Read More' to 'More...'. " +
               "The healer should find the link by its href or parent context.";
    }

    @Override
    public ExpectedOutcome getExpectedOutcome() {
        return ExpectedOutcome.HEAL;
    }

    @Override
    public By getOriginalLocator() {
        return By.linkText("Read More");
    }

    @Override
    public By getExpectedHealedLocator() {
        return By.partialLinkText("More");
    }

    @Override
    public String getBeforeHtml() {
        return wrapHtml("Blog - Before", """
            <section class="blog-posts">
                <article class="post" data-post-id="101">
                    <h2>Introduction to Test Automation</h2>
                    <p class="meta">Posted on Jan 15, 2025</p>
                    <p class="excerpt">Learn the fundamentals of automated testing
                       and how it can improve your development workflow...</p>
                    <a href="/blog/test-automation-intro" class="read-more-link">Read More</a>
                </article>
                <article class="post" data-post-id="102">
                    <h2>Best Practices for Selenium</h2>
                    <p class="meta">Posted on Jan 10, 2025</p>
                    <p class="excerpt">Discover the best practices for writing
                       maintainable and reliable Selenium tests...</p>
                    <a href="/blog/selenium-best-practices" class="read-more-link">Read More</a>
                </article>
            </section>
            """);
    }

    @Override
    public String getAfterHtml() {
        return wrapHtml("Blog - After", """
            <section class="blog-posts">
                <article class="post" data-post-id="101">
                    <h2>Introduction to Test Automation</h2>
                    <p class="meta">Posted on Jan 15, 2025</p>
                    <p class="excerpt">Learn the fundamentals of automated testing
                       and how it can improve your development workflow...</p>
                    <a href="/blog/test-automation-intro" class="read-more-link">More...</a>
                </article>
                <article class="post" data-post-id="102">
                    <h2>Best Practices for Selenium</h2>
                    <p class="meta">Posted on Jan 10, 2025</p>
                    <p class="excerpt">Discover the best practices for writing
                       maintainable and reliable Selenium tests...</p>
                    <a href="/blog/selenium-best-practices" class="read-more-link">More...</a>
                </article>
            </section>
            """);
    }
}
