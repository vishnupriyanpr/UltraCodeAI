package com.ultracodeai.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.ultracodeai.services.AIModelService
import kotlinx.coroutines.runBlocking

class GenerateTestsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        
        val selectedText = editor.selectionModel.selectedText
        if (selectedText.isNullOrBlank()) {
            Messages.showInfoMessage(
                "Please select some code to generate unit tests for.",
                "UltraCodeAI - Generate Tests"
            )
            return
        }
        
        try {
            val aiService = service<AIModelService>()
            runBlocking {
                val prompt = "Generate comprehensive unit tests for this code: $selectedText"
                val response = aiService.chatWithAI(prompt)
                val message = response ?: "Sorry, couldn't generate tests for this code."
                Messages.showInfoMessage(message, "🧪 Generated Unit Tests")
            }
        } catch (e: Exception) {
            Messages.showErrorDialog(
                "Failed to generate tests: ${e.message}",
                "UltraCodeAI Error"
            )
        }
    }
    
    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor?.selectionModel?.hasSelection() ?: false
    }
}
