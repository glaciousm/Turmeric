package io.github.glaciousm.intellij.ui;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.JBColor;
import io.github.glaciousm.intellij.settings.HealerSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Line marker provider for @Intent annotations.
 * Shows a gutter icon for methods with healing intent annotations.
 */
public class IntentLineMarkerProvider implements LineMarkerProvider {

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!HealerSettings.getInstance().showLineMarkers) {
            return null;
        }

        // Only process method name identifiers
        if (!(element instanceof PsiIdentifier)) {
            return null;
        }

        PsiElement parent = element.getParent();
        if (!(parent instanceof PsiMethod method)) {
            return null;
        }

        // Check for Intent Healer annotations
        PsiAnnotation intentAnnotation = method.getAnnotation("io.github.glaciousm.cucumber.annotations.Intent");
        if (intentAnnotation == null) {
            intentAnnotation = method.getAnnotation("Intent");
        }

        if (intentAnnotation != null) {
            return createLineMarkerInfo(element, "Intent-aware step definition");
        }

        // Check for @Outcome annotation
        PsiAnnotation outcomeAnnotation = method.getAnnotation("io.github.glaciousm.cucumber.annotations.Outcome");
        if (outcomeAnnotation == null) {
            outcomeAnnotation = method.getAnnotation("Outcome");
        }

        if (outcomeAnnotation != null) {
            return createLineMarkerInfo(element, "Outcome validation defined");
        }

        return null;
    }

    private LineMarkerInfo<PsiElement> createLineMarkerInfo(PsiElement element, String tooltip) {
        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                createHealerIcon(),
                e -> tooltip,
                null,
                GutterIconRenderer.Alignment.LEFT,
                () -> tooltip
        );
    }

    private Icon createHealerIcon() {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Draw a small healing symbol
                g2d.setColor(JBColor.namedColor("Intent.Healer.icon", new JBColor(
                        new Color(76, 175, 80),
                        new Color(129, 199, 132)
                )));

                // Cross shape for healing
                g2d.fillRect(x + 5, y + 2, 4, 12);
                g2d.fillRect(x + 2, y + 5, 10, 4);

                g2d.dispose();
            }

            @Override
            public int getIconWidth() {
                return 14;
            }

            @Override
            public int getIconHeight() {
                return 14;
            }
        };
    }
}
