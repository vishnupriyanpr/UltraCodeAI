package com.ultracodeai.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.ultracodeai.utils.SettingsState
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class CodeCompletionService(private val project: Project) {
    companion object {
        private val LOG = Logger.getInstance(CodeCompletionService::class.java)
        private const val CACHE_SIZE_LIMIT = 50
        private const val MIN_CONFIDENCE = 0.3
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val completionCache = ConcurrentHashMap<String, CompletionResult>()

    data class CompletionResult(
        val suggestion: String,
        val confidence: Double,
        val language: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    suspend fun getCompletion(
        context: String,
        prefix: String,
        language: String,
        fileName: String
    ): CompletionResult? {
        val settings = SettingsState.getInstance()
        if (!settings.enableCompletion) return null

        val cacheKey = generateCacheKey(context.take(100), prefix, language)

        // Check cache first
        if (settings.enableCaching) {
            completionCache[cacheKey]?.let { cached ->
                if (System.currentTimeMillis() - cached.timestamp < 300_000) { // 5 min TTL
                    LOG.debug("Cache hit for completion: ${prefix.take(20)}...")
                    return cached
                }
            }
        }

        // Generate new completion
        return try {
            val aiService = com.intellij.openapi.components.service<AIModelService>()
            val suggestion = aiService.generateCodeCompletion(context, prefix, language)

            if (!suggestion.isNullOrBlank()) {
                val confidence = calculateConfidence(suggestion, prefix, context)
                if (confidence >= MIN_CONFIDENCE) {
                    val result = CompletionResult(suggestion, confidence, language)

                    if (settings.enableCaching) {
                        cacheCompletion(cacheKey, result)
                    }

                    LOG.debug("Generated completion with confidence ${(confidence * 100).toInt()}%")
                    result
                } else {
                    LOG.debug("Completion confidence too low: ${(confidence * 100).toInt()}%")
                    null
                }
            } else null
        } catch (e: Exception) {
            LOG.warn("Failed to get completion for '${prefix.take(20)}...': ${e.message}")
            null
        }
    }

    private fun calculateConfidence(suggestion: String, prefix: String, context: String): Double {
        var confidence = 0.5 // Base confidence

        // Length-based confidence
        when {
            suggestion.length < 5 -> confidence *= 0.6
            suggestion.length > 100 -> confidence *= 0.8
            else -> confidence *= 1.0
        }

        // Prefix matching
        when {
            suggestion.startsWith(prefix, ignoreCase = true) -> confidence *= 1.2
            suggestion.contains(prefix, ignoreCase = true) -> confidence *= 1.0
            else -> confidence *= 0.7
        }

        // Context relevance (simple heuristic)
        val contextWords = context.split(Regex("\\W+")).filter { it.length > 3 }
        val suggestionWords = suggestion.split(Regex("\\W+")).filter { it.length > 3 }
        val commonWords = contextWords.intersect(suggestionWords.toSet()).size

        if (commonWords > 0) {
            confidence *= 1.1
        }

        // Syntax indicators
        if (suggestion.contains(Regex("[(){}\$$\$$;]"))) {
            confidence *= 1.1 // Likely valid code structure
        }

        return minOf(1.0, confidence)
    }

    private fun generateCacheKey(context: String, prefix: String, language: String): String {
        return "${context.hashCode()}_${prefix.hashCode()}_$language"
    }

    private fun cacheCompletion(key: String, result: CompletionResult) {
        if (completionCache.size >= CACHE_SIZE_LIMIT) {
            // Remove oldest entries
            val oldestKey = completionCache.entries
                .minByOrNull { it.value.timestamp }?.key
            oldestKey?.let { completionCache.remove(it) }
        }
        completionCache[key] = result
    }

    fun clearCache() {
        completionCache.clear()
        LOG.debug("Completion cache cleared")
    }

    fun getCacheStats(): Pair<Int, Int> {
        return Pair(completionCache.size, CACHE_SIZE_LIMIT)
    }
}
