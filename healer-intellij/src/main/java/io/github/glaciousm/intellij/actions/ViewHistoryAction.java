package io.github.glaciousm.intellij.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

/**
 * Action to view heal history.
 */
public class ViewHistoryAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        ToolWindow toolWindow = ToolWindowManager.getInstance(project)
                .getToolWindow("Intent Healer");

        if (toolWindow != null) {
            toolWindow.show(() -> {
                // Select the History tab
                var contentManager = toolWindow.getContentManager();
                var historyContent = contentManager.findContent("History");
                if (historyContent != null) {
                    contentManager.setSelectedContent(historyContent);
                }
            });
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }
}
