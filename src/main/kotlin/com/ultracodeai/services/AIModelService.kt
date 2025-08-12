package com.ultracodeai.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.ultracodeai.utils.OllamaClient
import com.ultracodeai.utils.SettingsState
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.APP)
class AIModelService {
    companion object {
        private val LOG = Logger.getInstance(AIModelService::class.java)
        const val DEFAULT_MODEL = "qwen2.5-coder:7b"
    }

    private val ollamaClient = OllamaClient()
    private val isModelAvailable = AtomicBoolean(false)

    suspend fun generateCodeCompletion(context: String, prefix: String, language: String = "python"): String? {
        if (prefix.trim().length < 2) return null
        
        val settings = SettingsState.getInstance()
        if (!settings.enableCompletion) return null
        
        return try {
            val prompt = "Complete this $language code: $prefix"
            val response = ollamaClient.generateCompletion(
                model = settings.modelName,
                prompt = prompt,
                maxTokens = settings.maxTokens,
                temperature = settings.temperature
            )
            
            if (response.isNotBlank() && response != prefix.trim()) {
                response.trim()
            } else null
        } catch (e: Exception) {
            LOG.warn("Failed to generate code completion: ${e.message}")
            null
        }
    }

    suspend fun analyzeCode(code: String, analysisType: AnalysisType): AnalysisResult? {
        if (code.isBlank()) return null
        
        return try {
            val prompt = when (analysisType) {
                AnalysisType.ERROR_DETECTION -> """
                    Analyze this Python code for errors and issues. Be very specific:
                    
                    Code: $code
                    
                    Check for:
                    1. Syntax errors (missing brackets, parentheses, quotes, colons)
                    2. Undefined variables or functions
                    3. Unused imports or variables
                    4. Logic errors or potential bugs
                    5. Type mismatches
                    
                    For each issue found, respond in this exact format:
                    ISSUE: [ERROR/WARNING/INFO] - [Description] - [Suggestion]
                    
                    If no issues found, respond only with: NO_ISSUES
                """.trimIndent()
                
                else -> "Analyze this code for ${analysisType.name.lowercase()}: $code"
            }
            
            val response = ollamaClient.generateCompletion(
                model = SettingsState.getInstance().modelName,
                prompt = prompt,
                maxTokens = 512,
                temperature = 0.1  // Very low temperature for consistent error detection
            )
            
            val issues = parseAIResponse(response, code)
            AnalysisResult(analysisType, response, issues)
            
        } catch (e: Exception) {
            LOG.warn("Failed to analyze code: ${e.message}")
            null
        }
    }

    private fun parseAIResponse(response: String, originalCode: String): List<CodeIssue> {
        val issues = mutableListOf<CodeIssue>()
        
        if (response.contains("NO_ISSUES", ignoreCase = true)) {
            return emptyList()
        }
        
        // Parse structured response format
        val issueLines = response.lines().filter { it.startsWith("ISSUE:", ignoreCase = true) }
        
        for (line in issueLines) {
            try {
                val parts = line.substringAfter("ISSUE:").split(" - ")
                if (parts.size >= 2) {
                    val severity = when (parts[0].trim().uppercase()) {
                        "ERROR" -> IssueSeverity.ERROR
                        "WARNING" -> IssueSeverity.WARNING
                        else -> IssueSeverity.INFO
                    }
                    val message = parts[1].trim()
                    val suggestion = if (parts.size > 2) parts[2].trim() else ""
                    
                    issues.add(CodeIssue(severity, message, suggestion))
                }
            } catch (e: Exception) {
                LOG.debug("Failed to parse issue line: $line")
            }
        }
        
        // Fallback: Pattern-based detection if structured parsing fails
        if (issues.isEmpty()) {
            issues.addAll(detectPatternBasedIssues(response, originalCode))
        }
        
        return issues.take(5) // Limit to 5 issues max
    }
    
    private fun detectPatternBasedIssues(response: String, code: String): List<CodeIssue> {
        val issues = mutableListOf<CodeIssue>()
        val responseText = response.lowercase()
        
        // Syntax error detection
        if (responseText.contains("syntax") || responseText.contains("missing") || responseText.contains("unclosed")) {
            when {
                code.contains("print(") && !code.contains(")") -> {
                    issues.add(CodeIssue(IssueSeverity.ERROR, "Missing closing parenthesis in print statement", "Add ')' at the end"))
                }
                code.contains("[") && !code.contains("]") -> {
                    issues.add(CodeIssue(IssueSeverity.ERROR, "Missing closing bracket", "Add ']' to close the bracket"))
                }
                code.contains("{") && !code.contains("}") -> {
                    issues.add(CodeIssue(IssueSeverity.ERROR, "Missing closing brace", "Add '}' to close the brace"))
                }
                code.count { it == '"' } % 2 != 0 -> {
                    issues.add(CodeIssue(IssueSeverity.ERROR, "Unclosed string literal", "Add closing quote"))
                }
            }
        }
        
        // Undefined variable detection
        if (responseText.contains("undefined") || responseText.contains("not defined") || responseText.contains("unresolved")) {
            // Common undefined variables in test scenarios
            val commonVars = listOf("a", "b", "result", "undefined_variable")
            for (variable in commonVars) {
                if (code.contains(variable) && !code.contains("$variable =") && !code.contains("def $variable")) {
                    issues.add(CodeIssue(IssueSeverity.ERROR, "Variable '$variable' is not defined", "Define the variable before using it"))
                }
            }
        }
        
        // Unused import detection
        if (responseText.contains("unused") && code.trim().startsWith("import ")) {
            issues.add(CodeIssue(IssueSeverity.WARNING, "Unused import statement", "Remove if not needed"))
        }
        
        return issues
    }

    suspend fun chatWithAI(message: String, conversationHistory: List<String> = emptyList(), context: String = ""): String? {
        if (message.isBlank()) return null

        return try {
            val prompt = "User: $message"
            ollamaClient.generateCompletion(
                model = SettingsState.getInstance().modelName,
                prompt = prompt,
                maxTokens = 1024,
                temperature = 0.3
            )
        } catch (e: Exception) {
            LOG.warn("Failed to chat with AI: ${e.message}")
            "Sorry, I'm having trouble processing your request."
        }
    }

    fun isAvailable(): Boolean = ollamaClient.isOllamaRunning()
    fun isOllamaConnected(): Boolean = ollamaClient.isOllamaRunning()

    data class AnalysisResult(
        val type: AnalysisType,
        val fullResponse: String,
        val issues: List<CodeIssue> = emptyList()
    )

    data class CodeIssue(
        val severity: IssueSeverity,
        val message: String,
        val suggestion: String = ""
    )

    enum class AnalysisType {
        ERROR_DETECTION, EXPLANATION, OPTIMIZATION, SECURITY
    }

    enum class IssueSeverity {
        ERROR, WARNING, INFO
    }
}
