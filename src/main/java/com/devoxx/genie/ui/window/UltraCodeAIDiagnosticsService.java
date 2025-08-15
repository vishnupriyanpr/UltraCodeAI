package com.devoxx.genie.service.diagnostics;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class UltraCodeAIDiagnosticsService {

    private final Project project;
    private Consumer<List<HighlightInfo>> updateCallback;
    private MessageBusConnection connection;

    public UltraCodeAIDiagnosticsService(Project project) {
        this.project = project;
        
        // FIXED: Use message bus instead of deprecated addDaemonListener
        connection = project.getMessageBus().connect();
        connection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
            @Override
            public void daemonFinished() {
                sendLatestProblems();
            }
        });
    }

    /** The Diagnostics tab will register a callback here */
    public void setUpdateCallback(Consumer<List<HighlightInfo>> callback) {
        this.updateCallback = callback;
    }

    /** Fetch current problems from the active editor and send them to the UI */
    private void sendLatestProblems() {
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null || updateCallback == null) return;

        try {
            List<HighlightInfo> infos = DaemonCodeAnalyzerImpl.getHighlights(editor.getDocument(), null, project);
            SwingUtilities.invokeLater(() -> updateCallback.accept(infos != null ? infos : Collections.emptyList()));
        } catch (Exception e) {
            // Handle API changes gracefully
            SwingUtilities.invokeLater(() -> updateCallback.accept(Collections.emptyList()));
        }
    }
    
    public void dispose() {
        if (connection != null) {
            connection.disconnect();
        }
    }
}
