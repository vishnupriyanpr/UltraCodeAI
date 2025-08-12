package com.ultracodeai

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.ultracodeai.services.AIModelService
import com.ultracodeai.utils.SettingsState

class UltraCodeAIPlugin : StartupActivity {
    companion object {
        private val LOG = Logger.getInstance(UltraCodeAIPlugin::class.java)
        const val PLUGIN_ID = "com.ultracodeai.plugin"
        const val PLUGIN_NAME = "UltraCodeAI"
        const val VERSION = "1.0.0"
    }

    override fun runActivity(project: Project) {
        LOG.info("Initializing UltraCodeAI plugin v$VERSION...")
        
        try {
            // Check AI service availability
            val aiService = service<AIModelService>()
            if (aiService.isAvailable()) {
                LOG.info("✅ UltraCodeAI initialized successfully")
            } else {
                LOG.warn("⚠️ UltraCodeAI initialized but AI model not available")
            }
            
            // Load settings
            val settings = SettingsState.getInstance()
            LOG.info("Settings loaded successfully")
            
        } catch (e: Exception) {
            LOG.error("❌ Failed to initialize UltraCodeAI plugin", e)
        }
    }
}
