package com.devoxx.genie.service.diagnostics;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DaemonListener;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.util.List;
import java.util.function.Consumer;

public class UltraCodeAIDiagnosticsService {

    private final Project project;
    private Consumer<List<HighlightInfo>> updateCallback;

    public UltraCodeAIDiagnosticsService(Project project) {
        this.project = project;

        // Listen for completion of code analysis (when highlighting finishes)
        ((DaemonCodeAnalyzerImpl) DaemonCodeAnalyzer.getInstance(project))
                .addDaemonListener(new DaemonListener() {
                    @Override
                    public void daemonFinished() {
                        sendLatestProblems();
                    }
                }, project);
    }

    /** The Diagnostics tab will register a callback here */
    public void setUpdateCallback(Consumer<List<HighlightInfo>> callback) {
        this.updateCallback = callback;
    }

    /** Fetch current problems from the active editor and send them to the UI */
    private void sendLatestProblems() {
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) return;

        List<HighlightInfo> infos =
                DaemonCodeAnalyzerImpl.getHighlights(editor.getDocument(), null, project);

        if (updateCallback != null) {
            SwingUtilities.invokeLater(() -> updateCallback.accept(infos));
        }
    }
}
