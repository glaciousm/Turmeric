package io.github.glaciousm.intellij.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import io.github.glaciousm.intellij.services.HealerProjectService;
import org.jetbrains.annotations.NotNull;

/**
 * Action to clear all cached heals.
 */
public class ClearCacheAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        int result = Messages.showYesNoDialog(
                project,
                "Are you sure you want to clear all heal history? This cannot be undone.",
                "Clear Heal Cache",
                Messages.getWarningIcon()
        );

        if (result == Messages.YES) {
            HealerProjectService service = HealerProjectService.getInstance(project);
            service.clearHistory();
            Messages.showInfoMessage(project, "Heal cache cleared successfully.", "Cache Cleared");
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }
}
