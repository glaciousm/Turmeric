import * as vscode from 'vscode';
import { HealerService, HealEntry } from '../services/healerService';

export class HealHistoryProvider implements vscode.TreeDataProvider<HealHistoryItem> {
    private _onDidChangeTreeData: vscode.EventEmitter<HealHistoryItem | undefined | null | void> =
        new vscode.EventEmitter<HealHistoryItem | undefined | null | void>();
    readonly onDidChangeTreeData: vscode.Event<HealHistoryItem | undefined | null | void> =
        this._onDidChangeTreeData.event;

    constructor(private service: HealerService) {}

    refresh(): void {
        this._onDidChangeTreeData.fire();
    }

    getTreeItem(element: HealHistoryItem): vscode.TreeItem {
        return element;
    }

    getChildren(element?: HealHistoryItem): Thenable<HealHistoryItem[]> {
        if (element) {
            // Show details for a heal entry
            return Promise.resolve(this.getHealDetails(element.heal));
        }

        // Root level - show all heals
        const history = this.service.getHealHistory();
        return Promise.resolve(
            history.map(heal => new HealHistoryItem(
                heal,
                `${heal.stepText.substring(0, 40)}...`,
                vscode.TreeItemCollapsibleState.Collapsed
            ))
        );
    }

    private getHealDetails(heal: HealEntry): HealHistoryItem[] {
        return [
            new HealHistoryItem(heal, `Feature: ${heal.featureName}`, vscode.TreeItemCollapsibleState.None, 'detail'),
            new HealHistoryItem(heal, `Scenario: ${heal.scenarioName}`, vscode.TreeItemCollapsibleState.None, 'detail'),
            new HealHistoryItem(heal, `Original: ${heal.originalLocator}`, vscode.TreeItemCollapsibleState.None, 'detail'),
            new HealHistoryItem(heal, `Healed: ${heal.healedLocator}`, vscode.TreeItemCollapsibleState.None, 'detail'),
            new HealHistoryItem(heal, `Confidence: ${(heal.confidence * 100).toFixed(1)}%`, vscode.TreeItemCollapsibleState.None, 'detail'),
            new HealHistoryItem(heal, `Status: ${heal.status}`, vscode.TreeItemCollapsibleState.None, 'detail'),
            new HealHistoryItem(heal, `Reasoning: ${heal.reasoning}`, vscode.TreeItemCollapsibleState.None, 'detail'),
        ];
    }
}

export class HealHistoryItem extends vscode.TreeItem {
    constructor(
        public readonly heal: HealEntry,
        public readonly label: string,
        public readonly collapsibleState: vscode.TreeItemCollapsibleState,
        public readonly itemType: 'healEntry' | 'detail' = 'healEntry'
    ) {
        super(label, collapsibleState);

        if (itemType === 'healEntry') {
            this.contextValue = 'healEntry';
            this.tooltip = `${heal.stepText}\n${heal.originalLocator} -> ${heal.healedLocator}`;
            this.description = `${(heal.confidence * 100).toFixed(0)}% - ${heal.status}`;

            // Set icon based on status
            switch (heal.status) {
                case 'accepted':
                    this.iconPath = new vscode.ThemeIcon('pass', new vscode.ThemeColor('testing.iconPassed'));
                    break;
                case 'rejected':
                    this.iconPath = new vscode.ThemeIcon('error', new vscode.ThemeColor('testing.iconFailed'));
                    break;
                case 'blacklisted':
                    this.iconPath = new vscode.ThemeIcon('circle-slash', new vscode.ThemeColor('errorForeground'));
                    break;
                default:
                    this.iconPath = new vscode.ThemeIcon('question', new vscode.ThemeColor('editorWarning.foreground'));
            }
        } else {
            this.contextValue = 'detail';
            this.iconPath = new vscode.ThemeIcon('dash');
        }
    }

    get healId(): string {
        return this.heal.id;
    }
}
