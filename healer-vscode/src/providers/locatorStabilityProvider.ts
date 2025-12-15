import * as vscode from 'vscode';
import { HealerService, LocatorStability } from '../services/healerService';

export class LocatorStabilityProvider implements vscode.TreeDataProvider<LocatorStabilityItem> {
    private _onDidChangeTreeData: vscode.EventEmitter<LocatorStabilityItem | undefined | null | void> =
        new vscode.EventEmitter<LocatorStabilityItem | undefined | null | void>();
    readonly onDidChangeTreeData: vscode.Event<LocatorStabilityItem | undefined | null | void> =
        this._onDidChangeTreeData.event;

    constructor(private service: HealerService) {}

    refresh(): void {
        this._onDidChangeTreeData.fire();
    }

    getTreeItem(element: LocatorStabilityItem): vscode.TreeItem {
        return element;
    }

    getChildren(element?: LocatorStabilityItem): Thenable<LocatorStabilityItem[]> {
        if (element) {
            // Show details for a locator
            return Promise.resolve(this.getLocatorDetails(element.stability));
        }

        // Root level - show stability summary and locators
        const stabilities = this.service.getLocatorStabilities();

        if (stabilities.length === 0) {
            return Promise.resolve([
                new LocatorStabilityItem(
                    { locator: 'No data', score: 0, level: 'N/A', successes: 0, failures: 0, heals: 0 },
                    'No stability data available',
                    vscode.TreeItemCollapsibleState.None,
                    'empty'
                )
            ]);
        }

        // Group by stability level
        const veryStable = stabilities.filter(s => s.level === 'VERY_STABLE');
        const stable = stabilities.filter(s => s.level === 'STABLE');
        const moderate = stabilities.filter(s => s.level === 'MODERATE');
        const unstable = stabilities.filter(s => s.level === 'UNSTABLE' || s.level === 'VERY_UNSTABLE');

        const items: LocatorStabilityItem[] = [];

        // Summary
        items.push(new LocatorStabilityItem(
            { locator: '', score: 0, level: '', successes: 0, failures: 0, heals: 0 },
            `Total: ${stabilities.length} locators tracked`,
            vscode.TreeItemCollapsibleState.None,
            'summary'
        ));

        // Categories
        if (veryStable.length > 0 || stable.length > 0) {
            items.push(new LocatorStabilityItem(
                { locator: '', score: 0, level: 'STABLE', successes: 0, failures: 0, heals: 0 },
                `Stable (${veryStable.length + stable.length})`,
                vscode.TreeItemCollapsibleState.Collapsed,
                'category'
            ));
        }

        if (moderate.length > 0) {
            items.push(new LocatorStabilityItem(
                { locator: '', score: 0, level: 'MODERATE', successes: 0, failures: 0, heals: 0 },
                `Moderate (${moderate.length})`,
                vscode.TreeItemCollapsibleState.Collapsed,
                'category'
            ));
        }

        if (unstable.length > 0) {
            items.push(new LocatorStabilityItem(
                { locator: '', score: 0, level: 'UNSTABLE', successes: 0, failures: 0, heals: 0 },
                `Unstable (${unstable.length})`,
                vscode.TreeItemCollapsibleState.Expanded,
                'category'
            ));
        }

        // Sort by score (lowest first for attention)
        const sortedLocators = [...stabilities].sort((a, b) => a.score - b.score);

        // Show top 10 most unstable
        const topUnstable = sortedLocators.slice(0, 10);
        topUnstable.forEach(s => {
            items.push(new LocatorStabilityItem(
                s,
                this.truncate(s.locator, 50),
                vscode.TreeItemCollapsibleState.Collapsed,
                'locator'
            ));
        });

        return Promise.resolve(items);
    }

    private getLocatorDetails(stability: LocatorStability): LocatorStabilityItem[] {
        return [
            new LocatorStabilityItem(stability, `Score: ${stability.score.toFixed(1)}`, vscode.TreeItemCollapsibleState.None, 'detail'),
            new LocatorStabilityItem(stability, `Level: ${stability.level}`, vscode.TreeItemCollapsibleState.None, 'detail'),
            new LocatorStabilityItem(stability, `Successes: ${stability.successes}`, vscode.TreeItemCollapsibleState.None, 'detail'),
            new LocatorStabilityItem(stability, `Failures: ${stability.failures}`, vscode.TreeItemCollapsibleState.None, 'detail'),
            new LocatorStabilityItem(stability, `Heals: ${stability.heals}`, vscode.TreeItemCollapsibleState.None, 'detail'),
        ];
    }

    private truncate(str: string, maxLen: number): string {
        return str.length > maxLen ? str.substring(0, maxLen - 3) + '...' : str;
    }
}

export class LocatorStabilityItem extends vscode.TreeItem {
    constructor(
        public readonly stability: LocatorStability,
        public readonly label: string,
        public readonly collapsibleState: vscode.TreeItemCollapsibleState,
        public readonly itemType: 'locator' | 'detail' | 'category' | 'summary' | 'empty'
    ) {
        super(label, collapsibleState);

        switch (itemType) {
            case 'locator':
                this.contextValue = 'locatorEntry';
                this.tooltip = `${stability.locator}\nScore: ${stability.score.toFixed(1)}\nLevel: ${stability.level}`;
                this.description = `${stability.score.toFixed(0)} - ${stability.level}`;
                this.iconPath = this.getStabilityIcon(stability.level);
                break;
            case 'category':
                this.contextValue = 'category';
                this.iconPath = this.getCategoryIcon(stability.level);
                break;
            case 'summary':
                this.contextValue = 'summary';
                this.iconPath = new vscode.ThemeIcon('list-tree');
                break;
            case 'detail':
                this.contextValue = 'detail';
                this.iconPath = new vscode.ThemeIcon('dash');
                break;
            case 'empty':
                this.contextValue = 'empty';
                this.iconPath = new vscode.ThemeIcon('info');
                break;
        }
    }

    private getStabilityIcon(level: string): vscode.ThemeIcon {
        switch (level) {
            case 'VERY_STABLE':
                return new vscode.ThemeIcon('shield', new vscode.ThemeColor('testing.iconPassed'));
            case 'STABLE':
                return new vscode.ThemeIcon('pass', new vscode.ThemeColor('testing.iconPassed'));
            case 'MODERATE':
                return new vscode.ThemeIcon('warning', new vscode.ThemeColor('editorWarning.foreground'));
            case 'UNSTABLE':
                return new vscode.ThemeIcon('error', new vscode.ThemeColor('editorError.foreground'));
            case 'VERY_UNSTABLE':
                return new vscode.ThemeIcon('flame', new vscode.ThemeColor('editorError.foreground'));
            default:
                return new vscode.ThemeIcon('circle');
        }
    }

    private getCategoryIcon(level: string): vscode.ThemeIcon {
        switch (level) {
            case 'STABLE':
                return new vscode.ThemeIcon('folder', new vscode.ThemeColor('testing.iconPassed'));
            case 'MODERATE':
                return new vscode.ThemeIcon('folder', new vscode.ThemeColor('editorWarning.foreground'));
            case 'UNSTABLE':
                return new vscode.ThemeIcon('folder', new vscode.ThemeColor('editorError.foreground'));
            default:
                return new vscode.ThemeIcon('folder');
        }
    }
}
