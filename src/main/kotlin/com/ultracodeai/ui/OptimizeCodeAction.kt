package com.ultracodeai.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.ultracodeai.services.AIModelService
import kotlinx.coroutines.runBlocking

class OptimizeCodeAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        
        val selectedText = editor.selectionModel.selectedText
        if (selectedText.isNullOrBlank()) {
            Messages.showInfoMessage(
                "Please select some code to get optimization suggestions.",
                "UltraCodeAI - Optimize Code"
            )
            return
        }
        
        try {
            val aiService = service<AIModelService>()
            runBlocking {
                val analysis = aiService.analyzeCode(selectedText, AIModelService.AnalysisType.OPTIMIZATION)
                val message = analysis?.fullResponse ?: "No optimization suggestions available."
                Messages.showInfoMessage(message, "⚡ Code Optimization Suggestions")
            }
        } catch (e: Exception) {
            Messages.showErrorDialog(
                "Failed to optimize code: ${e.message}",
                "UltraCodeAI Error"
            )
        }
    }
    
    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor?.selectionModel?.hasSelection() ?: false
    }
}
