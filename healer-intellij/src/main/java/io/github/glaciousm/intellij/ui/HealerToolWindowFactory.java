package io.github.glaciousm.intellij.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for the Intent Healer tool window.
 */
public class HealerToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // Create dashboard panel
        HealerDashboardPanel dashboardPanel = new HealerDashboardPanel(project);
        Content dashboardContent = ContentFactory.getInstance()
                .createContent(dashboardPanel, "Dashboard", false);
        toolWindow.getContentManager().addContent(dashboardContent);

        // Create live events panel (real-time monitoring)
        LiveEventsPanel liveEventsPanel = new LiveEventsPanel(project);
        Content liveContent = ContentFactory.getInstance()
                .createContent(liveEventsPanel, "Live", false);
        toolWindow.getContentManager().addContent(liveContent);

        // Create history panel
        HealHistoryPanel historyPanel = new HealHistoryPanel(project);
        Content historyContent = ContentFactory.getInstance()
                .createContent(historyPanel, "History", false);
        toolWindow.getContentManager().addContent(historyContent);

        // Create stability panel
        LocatorStabilityPanel stabilityPanel = new LocatorStabilityPanel(project);
        Content stabilityContent = ContentFactory.getInstance()
                .createContent(stabilityPanel, "Stability", false);
        toolWindow.getContentManager().addContent(stabilityContent);
    }
}
