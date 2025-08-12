package com.ultracodeai.listeners

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.startup.StartupManager
import com.ultracodeai.services.AIModelService
import com.ultracodeai.utils.SettingsState

class ProjectOpenListener : ProjectManagerListener {
    companion object {
        private val LOG = Logger.getInstance(ProjectOpenListener::class.java)
    }

    override fun projectOpened(project: Project) {
        LOG.info("UltraCodeAI: Project opened - ${project.name}")

        StartupManager.getInstance(project).runAfterOpened {
            initializeProjectServices(project)
        }
    }

    override fun projectClosed(project: Project) {
        LOG.info("UltraCodeAI: Project closed - ${project.name}")
        cleanupProjectServices(project)
    }

    private fun initializeProjectServices(project: Project) {
        try {
            LOG.debug("Initializing UltraCodeAI services for project: ${project.name}")

            // Initialize project-level services
            val completionService = project.service<com.ultracodeai.services.CodeCompletionService>()
            val errorDetectionService = project.service<com.ultracodeai.services.ErrorDetectionService>()
            val explanationService = project.service<com.ultracodeai.services.ExplanationService>()

            // Check AI availability
            val aiService = service<AIModelService>()
            if (!aiService.isAvailable()) {
                LOG.warn("AI service not available for project ${project.name}")
                showAIUnavailableNotification(project)
            }

            // Log project initialization
            val settings = SettingsState.getInstance()
            LOG.info("UltraCodeAI initialized for project '${project.name}' with features: " +
                    "Completion=${settings.enableCompletion}, " +
                    "ErrorDetection=${settings.enableErrorDetection}, " +
                    "Explanation=${settings.enableExplanation}")

        } catch (e: Exception) {
            LOG.error("Failed to initialize UltraCodeAI services for project ${project.name}", e)
        }
    }

    private fun cleanupProjectServices(project: Project) {
        try {
            LOG.debug("Cleaning up UltraCodeAI services for project: ${project.name}")

            // Clear caches in project services
            val completionService = project.service<com.ultracodeai.services.CodeCompletionService>()
            val errorDetectionService = project.service<com.ultracodeai.services.ErrorDetectionService>()
            val explanationService = project.service<com.ultracodeai.services.ExplanationService>()

            completionService.clearCache()
            errorDetectionService.clearCache()
            explanationService.clearCache()

            LOG.debug("UltraCodeAI cleanup completed for project: ${project.name}")

        } catch (e: Exception) {
            LOG.warn("Error during UltraCodeAI cleanup for project ${project.name}: ${e.message}")
        }
    }

    private fun showAIUnavailableNotification(project: Project) {
        val notification = com.intellij.notification.Notification(
            "UltraCodeAI",
            "🤖 UltraCodeAI Setup Required",
            """
                AI features are currently unavailable. To enable UltraCodeAI:
                
                1. Install Ollama: <a href="https://ollama.ai">ollama.ai</a>
                2. Pull model: <code>ollama pull qwen2.5-coder:7b</code>
                3. Start server: <code>ollama serve</code>
                
                <a href="settings">Open Settings</a> | <a href="help">Setup Guide</a>
            """.trimIndent(),
            com.intellij.notification.NotificationType.WARNING
        )

        // Add action handlers
        notification.addAction(object : com.intellij.notification.NotificationAction("Open Settings") {
            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent, notification: com.intellij.notification.Notification) {
                com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                    .showSettingsDialog(project, "UltraCodeAI")
                notification.expire()
            }
        })

        notification.addAction(object : com.intellij.notification.NotificationAction("Setup Guide") {
            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent, notification: com.intellij.notification.Notification) {
                com.intellij.openapi.ui.Messages.showInfoMessage(
                    project,
                    """
                        🚀 UltraCodeAI Setup Instructions:
                        
                        1. Install Ollama:
                           curl -fsSL https://ollama.ai/install.sh | sh
                        
                        2. Pull the AI model:
                           ollama pull qwen2.5-coder:7b
                        
                        3. Start Ollama server:
                           ollama serve
                        
                        4. Restart PyCharm and enjoy AI-powered coding!
                        
                        For more help, check the plugin settings.
                    """.trimIndent(),
                    "🚀 UltraCodeAI Setup Guide"
                )
                notification.expire()
            }
        })

        com.intellij.notification.Notifications.Bus.notify(notification, project)
    }
}
