package io.github.glaciousm.intellij.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

/**
 * Action to open the Intent Healer dashboard.
 */
public class OpenDashboardAction extends AnAction {

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
                // Select the Dashboard tab
                var contentManager = toolWindow.getContentManager();
                var dashboardContent = contentManager.findContent("Dashboard");
                if (dashboardContent != null) {
                    contentManager.setSelectedContent(dashboardContent);
                }
            });
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }
}
