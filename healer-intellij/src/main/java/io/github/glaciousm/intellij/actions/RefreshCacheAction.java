package io.github.glaciousm.intellij.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import io.github.glaciousm.intellij.services.HealerProjectService;
import org.jetbrains.annotations.NotNull;

/**
 * Action to refresh heal cache from disk.
 */
public class RefreshCacheAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        HealerProjectService service = HealerProjectService.getInstance(project);
        service.refresh();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }
}
