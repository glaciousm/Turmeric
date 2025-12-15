import * as vscode from 'vscode';
import { HealerService, TrustLevelInfo } from '../services/healerService';

export class TrustLevelProvider implements vscode.TreeDataProvider<TrustLevelItem> {
    private _onDidChangeTreeData: vscode.EventEmitter<TrustLevelItem | undefined | null | void> =
        new vscode.EventEmitter<TrustLevelItem | undefined | null | void>();
    readonly onDidChangeTreeData: vscode.Event<TrustLevelItem | undefined | null | void> =
        this._onDidChangeTreeData.event;

    constructor(private service: HealerService) {}

    refresh(): void {
        this._onDidChangeTreeData.fire();
    }

    getTreeItem(element: TrustLevelItem): vscode.TreeItem {
        return element;
    }

    getChildren(element?: TrustLevelItem): Thenable<TrustLevelItem[]> {
        const trustLevel = this.service.getTrustLevel();

        return Promise.resolve([
            new TrustLevelItem(
                `Current Level: ${trustLevel.level}`,
                this.getLevelDescription(trustLevel.level),
                this.getLevelIcon(trustLevel.level)
            ),
            new TrustLevelItem(
                `Success Rate: ${trustLevel.successRate.toFixed(1)}%`,
                'Overall success rate of heals',
                'graph'
            ),
            new TrustLevelItem(
                `Consecutive Successes: ${trustLevel.consecutiveSuccesses}`,
                'Successes needed for promotion',
                'check-all'
            ),
            new TrustLevelItem(
                `Failures in Window: ${trustLevel.failuresInWindow}`,
                'Recent failures (affects demotion)',
                'warning'
            ),
            new TrustLevelItem(
                '',
                '',
                'dash',
                true
            ),
            ...this.getLevelGuide()
        ]);
    }

    private getLevelDescription(level: string): string {
        switch (level) {
            case 'L0_SHADOW': return 'Shadow mode - heals are logged only';
            case 'L1_SUGGEST': return 'Suggest mode - heals are suggested to user';
            case 'L2_PROMPT': return 'Prompt mode - user prompted before applying';
            case 'L3_AUTO': return 'Auto mode - heals applied with notification';
            case 'L4_SILENT': return 'Silent mode - heals applied silently';
            default: return 'Unknown level';
        }
    }

    private getLevelIcon(level: string): string {
        switch (level) {
            case 'L0_SHADOW': return 'eye-closed';
            case 'L1_SUGGEST': return 'lightbulb';
            case 'L2_PROMPT': return 'question';
            case 'L3_AUTO': return 'zap';
            case 'L4_SILENT': return 'check';
            default: return 'circle';
        }
    }

    private getLevelGuide(): TrustLevelItem[] {
        return [
            new TrustLevelItem('Trust Level Guide', '', 'book'),
            new TrustLevelItem('  L0: Shadow - Log only', '', 'circle-outline'),
            new TrustLevelItem('  L1: Suggest - Show suggestions', '', 'circle-outline'),
            new TrustLevelItem('  L2: Prompt - Ask before applying', '', 'circle-outline'),
            new TrustLevelItem('  L3: Auto - Apply with notification', '', 'circle-outline'),
            new TrustLevelItem('  L4: Silent - Apply automatically', '', 'circle-outline'),
        ];
    }
}

export class TrustLevelItem extends vscode.TreeItem {
    constructor(
        public readonly label: string,
        public readonly description: string,
        public readonly iconId: string,
        public readonly isSeparator: boolean = false
    ) {
        super(label, vscode.TreeItemCollapsibleState.None);
        this.tooltip = description;
        this.description = isSeparator ? '' : description;
        this.iconPath = new vscode.ThemeIcon(iconId);
        this.contextValue = isSeparator ? 'separator' : 'trustInfo';
    }
}
