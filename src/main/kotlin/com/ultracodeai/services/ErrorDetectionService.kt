package com.ultracodeai.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.ultracodeai.utils.SettingsState
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class ErrorDetectionService(private val project: Project) {
    companion object {
        private val LOG = Logger.getInstance(ErrorDetectionService::class.java)
        private const val MIN_CODE_LENGTH = 10
        private const val MAX_CODE_LENGTH = 500
        private const val ANALYSIS_DELAY = 1000L // 1 second debounce
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val analysisCache = ConcurrentHashMap<String, AnalysisResult>()
    private val pendingAnalysis = ConcurrentHashMap<String, Job>()

    data class AnalysisResult(
        val issues: List<DetectedIssue>,
        val codeHash: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class DetectedIssue(
        val severity: Severity,
        val message: String,
        val lineNumber: Int,
        val columnStart: Int = 0,
        val columnEnd: Int = 0,
        val suggestion: String = "",
        val category: String = "General"
    )

    enum class Severity {
        ERROR, WARNING, INFO
    }

    suspend fun analyzeDocumentChange(document: Document, lineNumber: Int) {
        val settings = SettingsState.getInstance()
        if (!settings.enableErrorDetection || settings.lowPowerMode) return

        val documentKey = "${document.hashCode()}_$lineNumber"

        // Cancel previous analysis for this document/line
        pendingAnalysis[documentKey]?.cancel()

        // Start new analysis with debounce
        val job = scope.launch {
            delay(ANALYSIS_DELAY)

            try {
                val lineStart = document.getLineStartOffset(lineNumber)
                val lineEnd = document.getLineEndOffset(lineNumber)
                val lineText = document.getText().substring(lineStart, lineEnd)

                if (lineText.trim().length >= MIN_CODE_LENGTH) {
                    performAnalysis(lineText, lineNumber, documentKey)
                }
            } catch (e: Exception) {
                LOG.debug("Error analyzing document change at line $lineNumber: ${e.message}")
            } finally {
                pendingAnalysis.remove(documentKey)
            }
        }

        pendingAnalysis[documentKey] = job
    }

    suspend fun analyzeCodeBlock(code: String): AnalysisResult? {
        if (code.length < MIN_CODE_LENGTH || code.length > MAX_CODE_LENGTH) return null

        val codeHash = code.hashCode().toString()

        // Check cache
        analysisCache[codeHash]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < 300_000) { // 5 min TTL
                return cached
            }
        }

        return performFullAnalysis(code, codeHash)
    }

    private suspend fun performAnalysis(lineText: String, lineNumber: Int, cacheKey: String) {
        try {
            val aiService = com.intellij.openapi.components.service<AIModelService>()
            val analysis = aiService.analyzeCode(lineText, AIModelService.AnalysisType.ERROR_DETECTION)

            analysis?.let { result ->
                val issues = result.issues.mapIndexed { index, issue ->
                    DetectedIssue(
                        severity = mapSeverity(issue.severity),
                        message = issue.message,
                        lineNumber = lineNumber,
                        suggestion = issue.suggestion,
                        category = categorizeIssue(issue.message)
                    )
                }

                val analysisResult = AnalysisResult(issues, lineText.hashCode().toString())
                analysisCache[cacheKey] = analysisResult

                if (issues.isNotEmpty()) {
                    LOG.debug("Found ${issues.size} issues in line $lineNumber: ${issues.joinToString { it.severity.name }}")
                }
            }
        } catch (e: Exception) {
            LOG.warn("Failed to analyze code line $lineNumber: ${e.message}")
        }
    }

    private suspend fun performFullAnalysis(code: String, codeHash: String): AnalysisResult? {
        return try {
            val aiService = com.intellij.openapi.components.service<AIModelService>()
            val analysis = aiService.analyzeCode(code, AIModelService.AnalysisType.ERROR_DETECTION)

            analysis?.let { result ->
                val issues = result.issues.map { issue ->
                    DetectedIssue(
                        severity = mapSeverity(issue.severity),
                        message = issue.message,
                        lineNumber = 0, // Full block analysis
                        suggestion = issue.suggestion,
                        category = categorizeIssue(issue.message)
                    )
                }

                val analysisResult = AnalysisResult(issues, codeHash)
                analysisCache[codeHash] = analysisResult
                analysisResult
            }
        } catch (e: Exception) {
            LOG.warn("Failed to perform full code analysis: ${e.message}")
            null
        }
    }

    private fun mapSeverity(severity: AIModelService.IssueSeverity): Severity {
        return when (severity) {
            AIModelService.IssueSeverity.ERROR -> Severity.ERROR
            AIModelService.IssueSeverity.WARNING -> Severity.WARNING
            AIModelService.IssueSeverity.INFO -> Severity.INFO
        }
    }

    private fun categorizeIssue(message: String): String {
        val lowerMessage = message.lowercase()
        return when {
            lowerMessage.contains("syntax") -> "Syntax"
            lowerMessage.contains("logic") || lowerMessage.contains("bug") -> "Logic"
            lowerMessage.contains("performance") || lowerMessage.contains("optimization") -> "Performance"
            lowerMessage.contains("security") || lowerMessage.contains("vulnerability") -> "Security"
            lowerMessage.contains("style") || lowerMessage.contains("convention") -> "Style"
            else -> "General"
        }
    }

    fun getIssuesForLine(lineNumber: Int): List<DetectedIssue> {
        return analysisCache.values
            .flatMap { it.issues }
            .filter { it.lineNumber == lineNumber }
    }

    fun getAllIssues(): List<DetectedIssue> {
        return analysisCache.values.flatMap { it.issues }
    }

    fun clearCache() {
        analysisCache.clear()
        pendingAnalysis.values.forEach { it.cancel() }
        pendingAnalysis.clear()
        LOG.debug("Error detection cache cleared")
    }

    fun getCacheStats(): Pair<Int, Int> {
        val totalIssues = analysisCache.values.sumOf { it.issues.size }
        return Pair(totalIssues, analysisCache.size)
    }
}
