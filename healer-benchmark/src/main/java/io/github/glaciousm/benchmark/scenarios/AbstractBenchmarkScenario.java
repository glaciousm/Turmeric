/*
 * Intent Healer - Self-Healing Test Automation
 * Copyright (C) 2025 Menelaos Mamouzellos
 *
 * This program is dual-licensed under AGPL-3.0 and Commercial License.
 * See LICENSE and LICENSE-COMMERCIAL.md for details.
 */
package io.github.glaciousm.benchmark.scenarios;

import io.github.glaciousm.benchmark.BenchmarkScenario;

/**
 * Abstract base class providing common HTML templates for benchmark scenarios.
 */
public abstract class AbstractBenchmarkScenario extends BenchmarkScenario {

    /**
     * Generate a complete HTML page with the given body content.
     */
    protected String wrapHtml(String title, String bodyContent) {
        return String.format("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; }
                    .container { max-width: 800px; margin: 0 auto; }
                    .form-group { margin-bottom: 15px; }
                    label { display: block; margin-bottom: 5px; }
                    input, select, textarea { padding: 8px; width: 100%%; box-sizing: border-box; }
                    button, .btn { padding: 10px 20px; cursor: pointer; }
                    .btn-primary { background: #007bff; color: white; border: none; }
                    .btn-danger { background: #dc3545; color: white; border: none; }
                    table { width: 100%%; border-collapse: collapse; }
                    th, td { padding: 8px; border: 1px solid #ddd; text-align: left; }
                    .hidden { display: none; }
                    .grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
                    .card { border: 1px solid #ddd; padding: 15px; }
                </style>
            </head>
            <body>
                <div class="container">
                    %s
                </div>
            </body>
            </html>
            """, title, bodyContent);
    }

    /**
     * Generate a simple login form HTML.
     */
    protected String loginFormHtml(String buttonId, String buttonClass, String buttonText) {
        return String.format("""
            <h1>Login</h1>
            <form id="login-form">
                <div class="form-group">
                    <label for="username">Username</label>
                    <input type="text" id="username" name="username" placeholder="Enter username">
                </div>
                <div class="form-group">
                    <label for="password">Password</label>
                    <input type="password" id="password" name="password" placeholder="Enter password">
                </div>
                <button type="submit" %s %s>%s</button>
            </form>
            """,
            buttonId != null ? "id=\"" + buttonId + "\"" : "",
            buttonClass != null ? "class=\"" + buttonClass + "\"" : "",
            buttonText);
    }

    /**
     * Generate a product listing HTML with an add-to-cart button.
     */
    protected String productCardHtml(String productName, String buttonId, String buttonClass,
                                     String buttonText, String dataTestId) {
        return String.format("""
            <div class="card">
                <h2>%s</h2>
                <p>Price: $99.99</p>
                <button %s %s %s>%s</button>
            </div>
            """,
            productName,
            buttonId != null ? "id=\"" + buttonId + "\"" : "",
            buttonClass != null ? "class=\"" + buttonClass + "\"" : "",
            dataTestId != null ? "data-testid=\"" + dataTestId + "\"" : "",
            buttonText);
    }

    /**
     * Generate a settings form HTML.
     */
    protected String settingsFormHtml(String checkboxId, String checkboxName,
                                       String checkboxClass, String labelText) {
        return String.format("""
            <h1>Settings</h1>
            <form id="settings-form">
                <div class="form-group">
                    <label>
                        <input type="checkbox" %s %s %s>
                        %s
                    </label>
                </div>
                <button type="submit" class="btn-primary">Save Settings</button>
            </form>
            """,
            checkboxId != null ? "id=\"" + checkboxId + "\"" : "",
            checkboxName != null ? "name=\"" + checkboxName + "\"" : "",
            checkboxClass != null ? "class=\"" + checkboxClass + "\"" : "",
            labelText);
    }

    /**
     * Generate a navigation menu HTML.
     */
    protected String navMenuHtml(String... items) {
        StringBuilder sb = new StringBuilder("<nav><ul>\n");
        for (String item : items) {
            sb.append(String.format("    <li><a href=\"#\">%s</a></li>\n", item));
        }
        sb.append("</ul></nav>");
        return sb.toString();
    }

    /**
     * Generate a data table HTML.
     */
    protected String dataTableHtml(String tableId, String[] headers, String[][] rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("<table %s>\n<thead><tr>\n",
            tableId != null ? "id=\"" + tableId + "\"" : ""));

        for (String header : headers) {
            sb.append(String.format("    <th>%s</th>\n", header));
        }
        sb.append("</tr></thead>\n<tbody>\n");

        for (String[] row : rows) {
            sb.append("<tr>\n");
            for (String cell : row) {
                sb.append(String.format("    <td>%s</td>\n", cell));
            }
            sb.append("</tr>\n");
        }
        sb.append("</tbody></table>");

        return sb.toString();
    }
}
