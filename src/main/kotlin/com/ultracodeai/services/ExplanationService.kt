package com.ultracodeai.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.ultracodeai.utils.SettingsState
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class ExplanationService(private val project: Project) {
    companion object {
        private val LOG = Logger.getInstance(ExplanationService::class.java)
        private const val MIN_CODE_LENGTH = 5
        private const val MAX_CODE_LENGTH = 2000
        private const val CACHE_TTL_MS = 600_000L // 10 minutes
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val explanationCache = ConcurrentHashMap<String, CachedExplanation>()

    data class CodeExplanation(
        val originalCode: String,
        val explanation: String,
        val keyPoints: List<String>,
        val suggestions: List<String>,
        val complexity: String = "Unknown",
        val language: String = "Unknown"
    )

    data class CachedExplanation(
        val explanation: CodeExplanation,
        val timestamp: Long = System.currentTimeMillis()
    )

    suspend fun explainCode(
        code: String,
        language: String = "unknown",
        context: String = ""
    ): CodeExplanation? {
        val settings = SettingsState.getInstance()
        if (!settings.enableExplanation) return null

        if (code.length < MIN_CODE_LENGTH || code.length > MAX_CODE_LENGTH) {
            LOG.debug("Code length ${code.length} is outside acceptable range")
            return null
        }

        val cacheKey = generateCacheKey(code, language)

        // Check cache first
        if (settings.enableCaching) {
            explanationCache[cacheKey]?.let { cached ->
                if (System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
                    LOG.debug("Cache hit for code explanation")
                    return cached.explanation
                }
            }
        }

        return try {
            val aiService = com.intellij.openapi.components.service<AIModelService>()
            val analysis = aiService.analyzeCode(code, AIModelService.AnalysisType.EXPLANATION)

            analysis?.let { result ->
                val explanation = parseExplanation(code, result.fullResponse, language)

                if (settings.enableCaching) {
                    explanationCache[cacheKey] = CachedExplanation(explanation)
                }

                LOG.debug("Generated explanation for ${language} code (${code.length} chars)")
                explanation
            }
        } catch (e: Exception) {
            LOG.warn("Failed to explain code: ${e.message}")
            null
        }
    }

    suspend fun explainCodeWithOptimization(code: String, language: String = "unknown"): CodeExplanation? {
        val basicExplanation = explainCode(code, language) ?: return null

        // Get additional optimization suggestions
        try {
            val aiService = com.intellij.openapi.components.service<AIModelService>()
            val optimization = aiService.analyzeCode(code, AIModelService.AnalysisType.OPTIMIZATION)

            optimization?.let { result ->
                val optimizationSuggestions = extractSuggestions(result.fullResponse)
                return basicExplanation.copy(
                    suggestions = basicExplanation.suggestions + optimizationSuggestions
                )
            }
        } catch (e: Exception) {
            LOG.debug("Failed to get optimization suggestions: ${e.message}")
        }

        return basicExplanation
    }

    private fun parseExplanation(code: String, response: String, language: String): CodeExplanation {
        val keyPoints = extractKeyPoints(response)
        val suggestions = extractSuggestions(response)
        val complexity = estimateComplexity(code)

        return CodeExplanation(
            originalCode = code,
            explanation = cleanResponse(response),
            keyPoints = keyPoints,
            suggestions = suggestions,
            complexity = complexity,
            language = language
        )
    }

    private fun extractKeyPoints(explanation: String): List<String> {
        val points = mutableListOf<String>()

        explanation.split("\n").forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("- ") -> points.add(trimmed.substring(2))
                trimmed.startsWith("-  ") -> points.add(trimmed.substring(2))
                trimmed.matches(Regex("\\d+\\.\\s+.*")) -> {
                    points.add(trimmed.replaceFirst(Regex("\\d+\\.\\s+"), ""))
                }
                trimmed.startsWith("* ") -> points.add(trimmed.substring(2))
            }
        }

        return points.take(8) // Limit to 8 key points
    }

    private fun extractSuggestions(explanation: String): List<String> {
        val suggestions = mutableListOf<String>()

        explanation.split("\n").forEach { line ->
            val lower = line.lowercase()
            if (lower.contains("suggest") ||
                lower.contains("improve") ||
                lower.contains("consider") ||
                lower.contains("could") ||
                lower.contains("might") ||
                lower.contains("recommend")) {
                suggestions.add(line.trim())
            }
        }

        // Also look for common suggestion patterns
        val suggestionPatterns = listOf(
            "try to",
            "you can",
            "it would be better",
            "consider using",
            "instead of"
        )

        explanation.split(".").forEach { sentence ->
            val trimmed = sentence.trim()
            if (suggestionPatterns.any { pattern ->
                    trimmed.contains(pattern, ignoreCase = true)
                }) {
                suggestions.add(trimmed.take(100))
            }
        }

        return suggestions.distinct().take(5) // Limit to 5 unique suggestions
    }

    private fun estimateComplexity(code: String): String {
        val lines = code.split("\n").size
        val cyclomaticIndicators = listOf("if", "while", "for", "switch", "case", "&&", "||")
        val complexity = cyclomaticIndicators.sumOf { indicator ->
            code.split(indicator, ignoreCase = true).size - 1
        }

        return when {
            lines < 10 && complexity < 3 -> "Low"
            lines < 50 && complexity < 8 -> "Medium"
            lines < 100 && complexity < 15 -> "High"
            else -> "Very High"
        }
    }

    private fun cleanResponse(response: String): String {
        return response
            .replace("<|im_end|>", "")
            .replace("```", "")
            .split("\n")
            .filter { it.trim().isNotEmpty() }
            .joinToString("\n")
            .trim()
    }

    private fun generateCacheKey(code: String, language: String): String {
        return "${code.hashCode()}_$language"
    }

    fun clearCache() {
        explanationCache.clear()
        LOG.debug("Explanation cache cleared")
    }

    fun getCacheStats(): Pair<Int, Long> {
        val size = explanationCache.size
        val oldestEntry = explanationCache.values.minByOrNull { it.timestamp }?.timestamp ?: 0L
        return Pair(size, System.currentTimeMillis() - oldestEntry)
    }

    // Cleanup old cache entries
    init {
        scope.launch {
            while (isActive) {
                delay(300_000) // 5 minutes
                val cutoffTime = System.currentTimeMillis() - CACHE_TTL_MS
                explanationCache.entries.removeIf { it.value.timestamp < cutoffTime }
            }
        }
    }
}
