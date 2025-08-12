package com.ultracodeai.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "UltraCodeAISettings",
    storages = [Storage("ultracodeai-settings.xml")]
)
class SettingsState : PersistentStateComponent<SettingsState> {
    companion object {
        fun getInstance(): SettingsState =
            ApplicationManager.getApplication().getService(SettingsState::class.java)
    }

    // Feature toggles
    var enableCompletion: Boolean = true
    var enableErrorDetection: Boolean = true
    var enableExplanation: Boolean = true

    // AI Model settings
    var ollamaUrl: String = "http://localhost:11434"
    var modelName: String = "qwen2.5-coder:7b"
    var maxTokens: Int = 512
    var temperature: Double = 0.2

    // Performance settings
    var enableCaching: Boolean = true
    var lowPowerMode: Boolean = false
    var completionDelay: Int = 300 // milliseconds
    var maxContextLines: Int = 20

    // Advanced settings
    var enableDebugLogging: Boolean = false
    var maxCacheSize: Int = 100
    var cacheTtlMinutes: Int = 10
    var requestTimeoutSeconds: Int = 30

    // UI settings
    var showConfidenceScores: Boolean = true
    var enableQuickActions: Boolean = true
    var showAIStatusInToolbar: Boolean = true

    // Analytics (local only)
    var completionCount: Long = 0
    var errorDetectionCount: Long = 0
    var explanationCount: Long = 0
    var lastUsed: Long = System.currentTimeMillis()

    override fun getState(): SettingsState = this

    override fun loadState(state: SettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    /**
     * Validates current settings and returns validation errors
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        // Validate URL
        if (ollamaUrl.isBlank()) {
            errors.add("Ollama URL cannot be empty")
        } else if (!ollamaUrl.startsWith("http://") && !ollamaUrl.startsWith("https://")) {
            errors.add("Ollama URL must start with http:// or https://")
        }

        // Validate model name
        if (modelName.isBlank()) {
            errors.add("Model name cannot be empty")
        }

        // Validate numeric ranges
        if (maxTokens !in 50..4096) {
            errors.add("Max tokens must be between 50 and 4096")
        }

        if (temperature !in 0.0..2.0) {
            errors.add("Temperature must be between 0.0 and 2.0")
        }

        if (completionDelay !in 100..5000) {
            errors.add("Completion delay must be between 100 and 5000 milliseconds")
        }

        if (maxContextLines !in 5..50) {
            errors.add("Max context lines must be between 5 and 50")
        }

        if (maxCacheSize !in 10..1000) {
            errors.add("Max cache size must be between 10 and 1000")
        }

        if (cacheTtlMinutes !in 1..60) {
            errors.add("Cache TTL must be between 1 and 60 minutes")
        }

        if (requestTimeoutSeconds !in 10..300) {
            errors.add("Request timeout must be between 10 and 300 seconds")
        }

        return errors
    }

    /**
     * Resets all settings to default values
     */
    fun resetToDefaults() {
        enableCompletion = true
        enableErrorDetection = true
        enableExplanation = true
        ollamaUrl = "http://localhost:11434"
        modelName = "qwen2.5-coder:7b"
        maxTokens = 512
        temperature = 0.2
        enableCaching = true
        lowPowerMode = false
        completionDelay = 300
        maxContextLines = 20
        enableDebugLogging = false
        maxCacheSize = 100
        cacheTtlMinutes = 10
        requestTimeoutSeconds = 30
        showConfidenceScores = true
        enableQuickActions = true
        showAIStatusInToolbar = true
    }

    /**
     * Updates usage statistics
     */
    fun recordUsage(feature: UsageFeature) {
        when (feature) {
            UsageFeature.COMPLETION -> completionCount++
            UsageFeature.ERROR_DETECTION -> errorDetectionCount++
            UsageFeature.EXPLANATION -> explanationCount++
        }
        lastUsed = System.currentTimeMillis()
    }

    /**
     * Gets usage statistics
     */
    fun getUsageStats(): UsageStats {
        return UsageStats(
            totalCompletions = completionCount,
            totalErrorDetections = errorDetectionCount,
            totalExplanations = explanationCount,
            lastUsedDate = lastUsed
        )
    }

    /**
     * Exports settings as a map for backup/sharing
     */
    fun exportSettings(): Map<String, Any> {
        return mapOf(
            "enableCompletion" to enableCompletion,
            "enableErrorDetection" to enableErrorDetection,
            "enableExplanation" to enableExplanation,
            "ollamaUrl" to ollamaUrl,
            "modelName" to modelName,
            "maxTokens" to maxTokens,
            "temperature" to temperature,
            "enableCaching" to enableCaching,
            "lowPowerMode" to lowPowerMode,
            "completionDelay" to completionDelay,
            "maxContextLines" to maxContextLines,
            "enableDebugLogging" to enableDebugLogging,
            "showConfidenceScores" to showConfidenceScores,
            "enableQuickActions" to enableQuickActions
        )
    }

    /**
     * Imports settings from a map
     */
    fun importSettings(settings: Map<String, Any>) {
        try {
            settings["enableCompletion"]?.let { enableCompletion = it as Boolean }
            settings["enableErrorDetection"]?.let { enableErrorDetection = it as Boolean }
            settings["enableExplanation"]?.let { enableExplanation = it as Boolean }
            settings["ollamaUrl"]?.let { ollamaUrl = it as String }
            settings["modelName"]?.let { modelName = it as String }
            settings["maxTokens"]?.let { maxTokens = (it as Number).toInt() }
            settings["temperature"]?.let { temperature = (it as Number).toDouble() }
            settings["enableCaching"]?.let { enableCaching = it as Boolean }
            settings["lowPowerMode"]?.let { lowPowerMode = it as Boolean }
            settings["completionDelay"]?.let { completionDelay = (it as Number).toInt() }
            settings["maxContextLines"]?.let { maxContextLines = (it as Number).toInt() }
            settings["enableDebugLogging"]?.let { enableDebugLogging = it as Boolean }
            settings["showConfidenceScores"]?.let { showConfidenceScores = it as Boolean }
            settings["enableQuickActions"]?.let { enableQuickActions = it as Boolean }
        } catch (e: Exception) {
            // Ignore import errors and keep current settings
        }
    }

    data class UsageStats(
        val totalCompletions: Long,
        val totalErrorDetections: Long,
        val totalExplanations: Long,
        val lastUsedDate: Long
    )

    enum class UsageFeature {
        COMPLETION, ERROR_DETECTION, EXPLANATION
    }
}
