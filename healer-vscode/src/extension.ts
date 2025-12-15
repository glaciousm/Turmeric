import * as vscode from 'vscode';
import { HealHistoryProvider } from './providers/healHistoryProvider';
import { TrustLevelProvider } from './providers/trustLevelProvider';
import { LocatorStabilityProvider } from './providers/locatorStabilityProvider';
import { HealerService } from './services/healerService';

let healerService: HealerService;

export function activate(context: vscode.ExtensionContext) {
    console.log('Intent Healer extension is now active');

    // Initialize service
    healerService = new HealerService(context);

    // Register tree data providers
    const healHistoryProvider = new HealHistoryProvider(healerService);
    const trustLevelProvider = new TrustLevelProvider(healerService);
    const locatorStabilityProvider = new LocatorStabilityProvider(healerService);

    vscode.window.registerTreeDataProvider('healHistory', healHistoryProvider);
    vscode.window.registerTreeDataProvider('trustLevel', trustLevelProvider);
    vscode.window.registerTreeDataProvider('locatorStability', locatorStabilityProvider);

    // Register commands
    context.subscriptions.push(
        vscode.commands.registerCommand('intentHealer.refreshHistory', () => {
            healHistoryProvider.refresh();
            trustLevelProvider.refresh();
            locatorStabilityProvider.refresh();
            vscode.window.showInformationMessage('Intent Healer: Refreshed');
        }),

        vscode.commands.registerCommand('intentHealer.acceptHeal', (item) => {
            healerService.acceptHeal(item.healId);
            healHistoryProvider.refresh();
            vscode.window.showInformationMessage(`Heal accepted: ${item.healId}`);
        }),

        vscode.commands.registerCommand('intentHealer.rejectHeal', (item) => {
            healerService.rejectHeal(item.healId);
            healHistoryProvider.refresh();
            vscode.window.showInformationMessage(`Heal rejected: ${item.healId}`);
        }),

        vscode.commands.registerCommand('intentHealer.blacklistHeal', async (item) => {
            const reason = await vscode.window.showInputBox({
                prompt: 'Enter reason for blacklisting',
                placeHolder: 'e.g., Incorrect heal, breaks functionality'
            });
            if (reason) {
                healerService.blacklistHeal(item.healId, reason);
                healHistoryProvider.refresh();
                vscode.window.showInformationMessage(`Heal blacklisted: ${item.healId}`);
            }
        }),

        vscode.commands.registerCommand('intentHealer.openDashboard', () => {
            const panel = vscode.window.createWebviewPanel(
                'intentHealerDashboard',
                'Intent Healer Dashboard',
                vscode.ViewColumn.One,
                { enableScripts: true }
            );
            panel.webview.html = getDashboardHtml(healerService);
        }),

        vscode.commands.registerCommand('intentHealer.exportReport', async () => {
            const uri = await vscode.window.showSaveDialog({
                filters: { 'HTML': ['html'], 'JSON': ['json'] },
                saveLabel: 'Export Report'
            });
            if (uri) {
                await healerService.exportReport(uri.fsPath);
                vscode.window.showInformationMessage(`Report exported to ${uri.fsPath}`);
            }
        }),

        vscode.commands.registerCommand('intentHealer.clearCache', async () => {
            const confirm = await vscode.window.showWarningMessage(
                'Are you sure you want to clear the heal cache?',
                'Yes', 'No'
            );
            if (confirm === 'Yes') {
                healerService.clearCache();
                healHistoryProvider.refresh();
                trustLevelProvider.refresh();
                vscode.window.showInformationMessage('Heal cache cleared');
            }
        })
    );

    // Setup auto-refresh if enabled
    const config = vscode.workspace.getConfiguration('intentHealer');
    if (config.get<boolean>('autoRefresh')) {
        const interval = config.get<number>('refreshInterval', 30) * 1000;
        const refreshTimer = setInterval(() => {
            healHistoryProvider.refresh();
            trustLevelProvider.refresh();
        }, interval);
        context.subscriptions.push({ dispose: () => clearInterval(refreshTimer) });
    }

    // Watch for configuration changes
    context.subscriptions.push(
        vscode.workspace.onDidChangeConfiguration(e => {
            if (e.affectsConfiguration('intentHealer')) {
                healerService.reloadConfiguration();
            }
        })
    );

    // File system watcher for heal cache
    const cacheDir = config.get<string>('cacheDirectory', '.intent-healer');
    if (vscode.workspace.workspaceFolders) {
        const pattern = new vscode.RelativePattern(
            vscode.workspace.workspaceFolders[0],
            `${cacheDir}/**/*.json`
        );
        const watcher = vscode.workspace.createFileSystemWatcher(pattern);
        watcher.onDidChange(() => {
            healHistoryProvider.refresh();
            trustLevelProvider.refresh();
        });
        context.subscriptions.push(watcher);
    }
}

export function deactivate() {
    if (healerService) {
        healerService.dispose();
    }
}

function getDashboardHtml(service: HealerService): string {
    const stats = service.getStats();

    return `<!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Intent Healer Dashboard</title>
        <style>
            body {
                font-family: var(--vscode-font-family);
                background: var(--vscode-editor-background);
                color: var(--vscode-editor-foreground);
                padding: 20px;
            }
            h1 { color: var(--vscode-textLink-foreground); }
            .grid {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                gap: 20px;
                margin: 20px 0;
            }
            .card {
                background: var(--vscode-editor-inactiveSelectionBackground);
                border-radius: 8px;
                padding: 20px;
            }
            .card h2 {
                font-size: 14px;
                color: var(--vscode-descriptionForeground);
                margin: 0 0 10px 0;
            }
            .stat {
                font-size: 32px;
                font-weight: bold;
            }
            .stat.good { color: #4caf50; }
            .stat.warning { color: #ff9800; }
            .stat.bad { color: #f44336; }
            table {
                width: 100%;
                border-collapse: collapse;
                margin-top: 20px;
            }
            th, td {
                padding: 10px;
                text-align: left;
                border-bottom: 1px solid var(--vscode-widget-border);
            }
            th { font-weight: 600; }
        </style>
    </head>
    <body>
        <h1>Intent Healer Dashboard</h1>

        <div class="grid">
            <div class="card">
                <h2>Total Heals</h2>
                <div class="stat">${stats.totalHeals}</div>
            </div>
            <div class="card">
                <h2>Success Rate</h2>
                <div class="stat ${stats.successRate >= 80 ? 'good' : stats.successRate >= 60 ? 'warning' : 'bad'}">
                    ${stats.successRate.toFixed(1)}%
                </div>
            </div>
            <div class="card">
                <h2>Trust Level</h2>
                <div class="stat">${stats.trustLevel}</div>
            </div>
            <div class="card">
                <h2>Pending Review</h2>
                <div class="stat ${stats.pendingCount > 0 ? 'warning' : 'good'}">${stats.pendingCount}</div>
            </div>
        </div>

        <h2>Recent Heals</h2>
        <table>
            <tr>
                <th>Time</th>
                <th>Step</th>
                <th>Original</th>
                <th>Healed</th>
                <th>Confidence</th>
                <th>Status</th>
            </tr>
            ${stats.recentHeals.map(heal => `
                <tr>
                    <td>${new Date(heal.timestamp).toLocaleString()}</td>
                    <td>${heal.stepText}</td>
                    <td><code>${truncate(heal.originalLocator, 30)}</code></td>
                    <td><code>${truncate(heal.healedLocator, 30)}</code></td>
                    <td>${(heal.confidence * 100).toFixed(0)}%</td>
                    <td>${heal.status}</td>
                </tr>
            `).join('')}
        </table>

        <script>
            function truncate(str, len) {
                return str.length > len ? str.substring(0, len - 3) + '...' : str;
            }
        </script>
    </body>
    </html>`;
}

function truncate(str: string, len: number): string {
    return str.length > len ? str.substring(0, len - 3) + '...' : str;
}
