package com.ultracodeai.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.ultracodeai.services.AIModelService
import com.ultracodeai.utils.SettingsState
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

class AIToolbarAction : AnAction() {
    private val aiService = service<AIModelService>()
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        
        // Show AI status popup menu
        val menu = createAIStatusMenu()
        val component = e.inputEvent?.component
        
        if (component != null) {
            menu.show(component, 0, component.height)
        }
    }
    
    override fun update(e: AnActionEvent) {
        val isAvailable = aiService.isAvailable()
        val isConnected = aiService.isOllamaConnected()
        
        when {
            isAvailable -> {
                e.presentation.text = "UltraCodeAI - Ready"
                e.presentation.description = "🟢 AI Ready"
            }
            isConnected -> {
                e.presentation.text = "UltraCodeAI - Loading"
                e.presentation.description = "🟡 Model Loading"
            }
            else -> {
                e.presentation.text = "UltraCodeAI - Offline"
                e.presentation.description = "🔴 Ollama Offline"
            }
        }
    }
    
    private fun createAIStatusMenu(): JPopupMenu {
        val menu = JPopupMenu("UltraCodeAI Status")
        val settings = SettingsState.getInstance()
        
        // Status
        val statusText = when {
            aiService.isAvailable() -> "🟢 AI Ready"
            aiService.isOllamaConnected() -> "🟡 Model Loading"
            else -> "🔴 Ollama Offline"
        }
        
        menu.add(JMenuItem(statusText).apply { isEnabled = false })
        menu.addSeparator()
        
        // Toggle features
        menu.add(javax.swing.JCheckBoxMenuItem("Code Completion", settings.enableCompletion).apply {
            addActionListener { settings.enableCompletion = isSelected }
        })
        
        menu.add(javax.swing.JCheckBoxMenuItem("Error Detection", settings.enableErrorDetection).apply {
            addActionListener { settings.enableErrorDetection = isSelected }
        })
        
        return menu
    }
}
