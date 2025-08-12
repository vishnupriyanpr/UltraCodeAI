package com.ultracodeai.ui

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.ultracodeai.utils.SettingsState
import javax.swing.*

class SettingsConfigurable : Configurable {
    private var settingsPanel: SettingsPanel? = null
    
    override fun getDisplayName(): String = "UltraCodeAI"
    
    override fun createComponent(): JComponent? {
        settingsPanel = SettingsPanel()
        return settingsPanel?.mainPanel
    }
    
    override fun isModified(): Boolean {
        val settings = SettingsState.getInstance()
        val panel = settingsPanel ?: return false
        
        return panel.enableCompletion.isSelected != settings.enableCompletion ||
               panel.enableErrorDetection.isSelected != settings.enableErrorDetection ||
               panel.enableExplanation.isSelected != settings.enableExplanation ||
               panel.ollamaUrl.text != settings.ollamaUrl ||
               panel.modelName.text != settings.modelName
    }
    
    override fun apply() {
        val settings = SettingsState.getInstance()
        val panel = settingsPanel ?: return
        
        try {
            settings.enableCompletion = panel.enableCompletion.isSelected
            settings.enableErrorDetection = panel.enableErrorDetection.isSelected
            settings.enableExplanation = panel.enableExplanation.isSelected
            settings.ollamaUrl = panel.ollamaUrl.text.trim()
            settings.modelName = panel.modelName.text.trim()
            
            Messages.showInfoMessage(
                "Settings saved successfully!",
                "UltraCodeAI Settings"
            )
        } catch (e: Exception) {
            Messages.showErrorDialog(
                "Error saving settings: ${e.message}",
                "UltraCodeAI Settings Error"
            )
        }
    }
    
    override fun reset() {
        val settings = SettingsState.getInstance()
        val panel = settingsPanel ?: return
        
        panel.enableCompletion.isSelected = settings.enableCompletion
        panel.enableErrorDetection.isSelected = settings.enableErrorDetection
        panel.enableExplanation.isSelected = settings.enableExplanation
        panel.ollamaUrl.text = settings.ollamaUrl
        panel.modelName.text = settings.modelName
    }
    
    private inner class SettingsPanel {
        val enableCompletion = JBCheckBox("Enable AI Code Completion")
        val enableErrorDetection = JBCheckBox("Enable AI Error Detection") 
        val enableExplanation = JBCheckBox("Enable Code Explanation")
        val ollamaUrl = JBTextField(30)
        val modelName = JBTextField(30)
        
        val mainPanel: JPanel
        
        init {
            val settings = SettingsState.getInstance()
            enableCompletion.isSelected = settings.enableCompletion
            enableErrorDetection.isSelected = settings.enableErrorDetection
            enableExplanation.isSelected = settings.enableExplanation
            ollamaUrl.text = settings.ollamaUrl
            modelName.text = settings.modelName
            
            mainPanel = createMainPanel()
        }
        
        private fun createMainPanel(): JPanel {
            return FormBuilder.createFormBuilder()
                .addComponent(JBLabel("<html><h2>UltraCodeAI Configuration</h2></html>"))
                .addVerticalGap(10)
                
                .addComponent(JBLabel("<html><b>Features</b></html>"))
                .addComponent(enableCompletion)
                .addComponent(enableErrorDetection) 
                .addComponent(enableExplanation)
                .addVerticalGap(15)
                
                .addComponent(JBLabel("<html><b>AI Model Settings</b></html>"))
                .addLabeledComponent("Ollama URL:", ollamaUrl)
                .addLabeledComponent("Model Name:", modelName)
                .addVerticalGap(15)
                
                .addComponentFillVertically(JPanel(), 0)
                .panel.apply {
                    border = JBUI.Borders.empty(20)
                }
        }
    }
}
