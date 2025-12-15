import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';

export interface HealEntry {
    id: string;
    timestamp: string;
    featureName: string;
    scenarioName: string;
    stepText: string;
    originalLocator: string;
    healedLocator: string;
    confidence: number;
    reasoning: string;
    status: 'pending' | 'accepted' | 'rejected' | 'blacklisted';
}

export interface TrustLevelInfo {
    level: string;
    consecutiveSuccesses: number;
    failuresInWindow: number;
    successRate: number;
}

export interface LocatorStability {
    locator: string;
    score: number;
    level: string;
    successes: number;
    failures: number;
    heals: number;
}

export interface HealerStats {
    totalHeals: number;
    successRate: number;
    trustLevel: string;
    pendingCount: number;
    recentHeals: HealEntry[];
}

export class HealerService {
    private context: vscode.ExtensionContext;
    private healHistory: HealEntry[] = [];
    private trustLevel: TrustLevelInfo = {
        level: 'L0_SHADOW',
        consecutiveSuccesses: 0,
        failuresInWindow: 0,
        successRate: 0
    };
    private locatorStabilities: Map<string, LocatorStability> = new Map();

    constructor(context: vscode.ExtensionContext) {
        this.context = context;
        this.loadData();
    }

    private getCacheDir(): string {
        const config = vscode.workspace.getConfiguration('intentHealer');
        const cacheDir = config.get<string>('cacheDirectory', '.intent-healer');

        if (vscode.workspace.workspaceFolders && vscode.workspace.workspaceFolders.length > 0) {
            return path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, cacheDir);
        }
        return cacheDir;
    }

    private loadData(): void {
        const cacheDir = this.getCacheDir();

        // Load heal history
        const historyPath = path.join(cacheDir, 'heal-history.json');
        if (fs.existsSync(historyPath)) {
            try {
                const data = fs.readFileSync(historyPath, 'utf8');
                this.healHistory = JSON.parse(data);
            } catch (e) {
                console.error('Failed to load heal history:', e);
            }
        }

        // Load trust level
        const trustPath = path.join(cacheDir, 'trust-level.json');
        if (fs.existsSync(trustPath)) {
            try {
                const data = fs.readFileSync(trustPath, 'utf8');
                this.trustLevel = JSON.parse(data);
            } catch (e) {
                console.error('Failed to load trust level:', e);
            }
        }

        // Load stability data
        const stabilityPath = path.join(cacheDir, 'locator-stability.json');
        if (fs.existsSync(stabilityPath)) {
            try {
                const data = fs.readFileSync(stabilityPath, 'utf8');
                const entries: LocatorStability[] = JSON.parse(data);
                entries.forEach(e => this.locatorStabilities.set(e.locator, e));
            } catch (e) {
                console.error('Failed to load stability data:', e);
            }
        }
    }

    private saveData(): void {
        const cacheDir = this.getCacheDir();

        if (!fs.existsSync(cacheDir)) {
            fs.mkdirSync(cacheDir, { recursive: true });
        }

        // Save heal history
        const historyPath = path.join(cacheDir, 'heal-history.json');
        fs.writeFileSync(historyPath, JSON.stringify(this.healHistory, null, 2));
    }

    getHealHistory(): HealEntry[] {
        return [...this.healHistory];
    }

    getTrustLevel(): TrustLevelInfo {
        return { ...this.trustLevel };
    }

    getLocatorStabilities(): LocatorStability[] {
        return Array.from(this.locatorStabilities.values());
    }

    getStats(): HealerStats {
        const accepted = this.healHistory.filter(h => h.status === 'accepted').length;
        const total = this.healHistory.length;
        const pending = this.healHistory.filter(h => h.status === 'pending').length;

        return {
            totalHeals: total,
            successRate: total > 0 ? (accepted / total) * 100 : 0,
            trustLevel: this.trustLevel.level,
            pendingCount: pending,
            recentHeals: this.healHistory.slice(0, 10)
        };
    }

    acceptHeal(healId: string): void {
        const heal = this.healHistory.find(h => h.id === healId);
        if (heal) {
            heal.status = 'accepted';
            this.saveData();
        }
    }

    rejectHeal(healId: string): void {
        const heal = this.healHistory.find(h => h.id === healId);
        if (heal) {
            heal.status = 'rejected';
            this.saveData();
        }
    }

    blacklistHeal(healId: string, reason: string): void {
        const heal = this.healHistory.find(h => h.id === healId);
        if (heal) {
            heal.status = 'blacklisted';
            heal.reasoning = reason;
            this.saveData();
        }
    }

    clearCache(): void {
        this.healHistory = [];
        this.locatorStabilities.clear();
        this.trustLevel = {
            level: 'L0_SHADOW',
            consecutiveSuccesses: 0,
            failuresInWindow: 0,
            successRate: 0
        };
        this.saveData();
    }

    async exportReport(filePath: string): Promise<void> {
        const stats = this.getStats();
        const ext = path.extname(filePath).toLowerCase();

        if (ext === '.json') {
            const report = {
                generated: new Date().toISOString(),
                stats,
                healHistory: this.healHistory,
                trustLevel: this.trustLevel,
                locatorStabilities: this.getLocatorStabilities()
            };
            fs.writeFileSync(filePath, JSON.stringify(report, null, 2));
        } else {
            // Generate HTML report
            const html = this.generateHtmlReport(stats);
            fs.writeFileSync(filePath, html);
        }
    }

    private generateHtmlReport(stats: HealerStats): string {
        return `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Intent Healer Report</title>
    <style>
        body { font-family: system-ui, sans-serif; max-width: 1200px; margin: 0 auto; padding: 20px; }
        h1 { color: #333; }
        .grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px; margin: 20px 0; }
        .card { background: #f5f5f5; border-radius: 8px; padding: 20px; }
        .card h2 { font-size: 14px; color: #666; margin: 0 0 10px; }
        .stat { font-size: 32px; font-weight: bold; }
        table { width: 100%; border-collapse: collapse; }
        th, td { padding: 10px; text-align: left; border-bottom: 1px solid #ddd; }
    </style>
</head>
<body>
    <h1>Intent Healer Report</h1>
    <p>Generated: ${new Date().toLocaleString()}</p>

    <div class="grid">
        <div class="card">
            <h2>Total Heals</h2>
            <div class="stat">${stats.totalHeals}</div>
        </div>
        <div class="card">
            <h2>Success Rate</h2>
            <div class="stat">${stats.successRate.toFixed(1)}%</div>
        </div>
        <div class="card">
            <h2>Trust Level</h2>
            <div class="stat">${stats.trustLevel}</div>
        </div>
        <div class="card">
            <h2>Pending</h2>
            <div class="stat">${stats.pendingCount}</div>
        </div>
    </div>

    <h2>Heal History</h2>
    <table>
        <tr>
            <th>Time</th>
            <th>Step</th>
            <th>Original</th>
            <th>Healed</th>
            <th>Confidence</th>
            <th>Status</th>
        </tr>
        ${this.healHistory.map(h => `
            <tr>
                <td>${new Date(h.timestamp).toLocaleString()}</td>
                <td>${h.stepText}</td>
                <td><code>${h.originalLocator}</code></td>
                <td><code>${h.healedLocator}</code></td>
                <td>${(h.confidence * 100).toFixed(0)}%</td>
                <td>${h.status}</td>
            </tr>
        `).join('')}
    </table>
</body>
</html>`;
    }

    reloadConfiguration(): void {
        this.loadData();
    }

    dispose(): void {
        // Cleanup if needed
    }
}
