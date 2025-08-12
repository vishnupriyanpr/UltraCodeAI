package com.ultracodeai.inspections

import com.intellij.codeInspection.*
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.ultracodeai.services.AIModelService
import com.ultracodeai.utils.SettingsState
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class AICodeInspection : LocalInspectionTool() {
    
    companion object {
        private val LOG = Logger.getInstance(AICodeInspection::class.java)
        private const val MIN_CODE_LENGTH = 30
        private const val MAX_CODE_LENGTH = 1000
        private val inspectionStats = AtomicInteger(0)
        private val issuesFound = AtomicInteger(0)
    }
    
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val settings = SettingsState.getInstance()
        if (!settings.enableErrorDetection) {
            return PsiElementVisitor.EMPTY_VISITOR
        }
        
        return AIInspectionVisitor(holder, isOnTheFly)
    }
    
    override fun getDisplayName(): String = "UltraCodeAI Code Quality Analysis"
    override fun getShortName(): String = "UltraCodeAIInspection"
    override fun getGroupDisplayName(): String = "UltraCodeAI"
    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WARNING
    override fun isEnabledByDefault(): Boolean = true
    
    private inner class AIInspectionVisitor(
        private val holder: ProblemsHolder,
        private val isOnTheFly: Boolean
    ) : PsiElementVisitor() {
        
        private val aiService = service<AIModelService>()
        private val processedElements = ConcurrentHashMap<String, Boolean>()
        
        override fun visitFile(file: PsiFile) {
            super.visitFile(file)
            
            if (!aiService.isAvailable()) return
            
            // File-level analysis for large files
            val lines = file.text.lines()
            if (lines.size > 500) {
                holder.registerProblem(
                    file,
                    "🟡 AI: Large file (${lines.size} lines) - consider refactoring",
                    ProblemHighlightType.WARNING,
                    RefactoringSuggestionFix("Large files can be difficult to maintain")
                )
            }
        }
        
        override fun visitElement(element: PsiElement) {
            super.visitElement(element)
            
            if (!shouldInspectElement(element)) return
            
            val code = element.text.trim()
            if (code.length < MIN_CODE_LENGTH || code.length > MAX_CODE_LENGTH) return
            
            val elementKey = generateElementKey(element, code)
            if (processedElements.containsKey(elementKey)) return
            
            try {
                inspectionStats.incrementAndGet()
                
                if (!isOnTheFly || isSignificantElement(element)) {
                    performQualityInspection(element, code, elementKey)
                }
                
            } catch (e: Exception) {
                LOG.debug("Failed to inspect element: ${e.message}")
            }
        }
        
        private fun shouldInspectElement(element: PsiElement): Boolean {
            val elementType = element.node?.elementType?.toString() ?: return false
            
            return when {
                elementType.contains("FUNCTION", ignoreCase = true) -> true
                elementType.contains("CLASS", ignoreCase = true) -> true
                elementType.contains("METHOD", ignoreCase = true) -> true
                elementType.contains("STATEMENT", ignoreCase = true) && element.text.length > 50 -> true
                else -> false
            }
        }
        
        private fun isSignificantElement(element: PsiElement): Boolean {
            val elementType = element.node?.elementType?.toString() ?: return false
            return elementType.contains("FUNCTION", ignoreCase = true) ||
                   elementType.contains("CLASS", ignoreCase = true) ||
                   element.text.length > 200
        }
        
        private fun performQualityInspection(element: PsiElement, code: String, elementKey: String) {
            runBlocking {
                try {
                    val issues = analyzeCodeQuality(code)
                    
                    if (issues.isNotEmpty()) {
                        processedElements[elementKey] = true
                        createQualityProblems(element, issues)
                        issuesFound.addAndGet(issues.size)
                    }
                    
                } catch (e: Exception) {
                    LOG.warn("Quality inspection failed: ${e.message}")
                }
            }
        }
        
        private suspend fun analyzeCodeQuality(code: String): List<QualityIssue> {
            val issues = mutableListOf<QualityIssue>()
            
            // Code complexity analysis
            val complexity = calculateComplexity(code)
            if (complexity > 15) {
                issues.add(QualityIssue(
                    category = "COMPLEXITY",
                    severity = ProblemHighlightType.WARNING,
                    message = "High complexity ($complexity)",
                    description = "This code has high cyclomatic complexity",
                    suggestion = "Consider breaking into smaller functions"
                ))
            }
            
            // Function length analysis
            if (code.contains("def ") && code.lines().size > 75) {
                issues.add(QualityIssue(
                    category = "MAINTAINABILITY",
                    severity = ProblemHighlightType.WARNING,
                    message = "Long function (${code.lines().size} lines)",
                    description = "Long functions are harder to understand and maintain",
                    suggestion = "Split into smaller, focused functions"
                ))
            }
            
            // Performance patterns
            if (code.contains(Regex("for.*in.*range\\(len\\("))) {
                issues.add(QualityIssue(
                    category = "PERFORMANCE",
                    severity = ProblemHighlightType.INFO,
                    message = "Inefficient iteration pattern",
                    description = "Using range(len()) is less Pythonic",
                    suggestion = "Use direct iteration or enumerate()"
                ))
            }
            
            // AI-powered analysis for substantial code
            if (code.length > 100 && aiService.isAvailable()) {
                issues.addAll(performAIQualityAnalysis(code))
            }
            
            return issues
        }
        
        private suspend fun performAIQualityAnalysis(code: String): List<QualityIssue> {
            val issues = mutableListOf<QualityIssue>()
            
            try {
                val prompt = """
                Analyze this Python code for quality and maintainability issues. Be specific and practical.
                
                Code:
                $code
                
                Focus on:
                1. Code organization and structure
                2. Potential performance improvements
                3. Maintainability concerns
                4. Best practice violations
                
                For each issue, respond with:
                QUALITY: [CATEGORY] - [MESSAGE] - [SUGGESTION]
                
                Categories: STRUCTURE, PERFORMANCE, MAINTAINABILITY, BEST_PRACTICE
                
                If code quality is good, respond: QUALITY_ACCEPTABLE
                """.trimIndent()
                
                val response = aiService.chatWithAI(prompt)
                
                if (!response.isNullOrBlank() && !response.contains("QUALITY_ACCEPTABLE", ignoreCase = true)) {
                    issues.addAll(parseQualityResponse(response))
                }
                
            } catch (e: Exception) {
                LOG.debug("AI quality analysis failed: ${e.message}")
            }
            
            return issues
        }
        
        private fun parseQualityResponse(response: String): List<QualityIssue> {
            val issues = mutableListOf<QualityIssue>()
            
            val qualityLines = response.lines().filter { it.startsWith("QUALITY:", ignoreCase = true) }
            
            for (line in qualityLines) {
                try {
                    val parts = line.substringAfter("QUALITY:").split(" - ")
                    if (parts.size >= 3) {
                        val category = parts[0].trim()
                        val message = parts[1].trim()
                        val suggestion = parts[2].trim()
                        
                        issues.add(QualityIssue(
                            category = category,
                            severity = ProblemHighlightType.INFO,
                            message = message,
                            description = "Code quality improvement opportunity",
                            suggestion = suggestion
                        ))
                    }
                } catch (e: Exception) {
                    LOG.debug("Failed to parse quality line: $line")
                }
            }
            
            return issues
        }
        
        private fun createQualityProblems(element: PsiElement, issues: List<QualityIssue>) {
            for (issue in issues.take(3)) { // Limit to 3 issues
                val description = "🔵 AI Quality: ${issue.message}"
                val fixes = arrayOf<LocalQuickFix>(QualityImprovementFix(issue))
                
                holder.registerProblem(
                    element,
                    description,
                    issue.severity,
                    *fixes
                )
            }
        }
        
        private fun calculateComplexity(code: String): Int {
            val complexityIndicators = listOf(
                "if ", "elif ", "else:", "for ", "while ", "try:", "except", 
                "and ", "or ", "break", "continue", "return"
            )
            
            return complexityIndicators.sumOf { indicator ->
                code.split(indicator).size - 1
            } + 1
        }
        
        private fun generateElementKey(element: PsiElement, code: String): String {
            val file = element.containingFile
            val offset = element.textRange.startOffset
            return "${file?.name ?: "unknown"}:$offset:${code.hashCode()}"
        }
    }
    
    data class QualityIssue(
        val category: String,
        val severity: ProblemHighlightType,
        val message: String,
        val description: String,
        val suggestion: String
    )
    
    class QualityImprovementFix(private val issue: QualityIssue) : LocalQuickFix {
        override fun getName(): String = "View improvement suggestion"
        override fun getFamilyName(): String = "UltraCodeAI Quality"
        
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val message = """
                Code Quality Improvement
                
                Category: ${issue.category}
                Issue: ${issue.message}
                
                Description: ${issue.description}
                
                Suggestion: ${issue.suggestion}
                
                Improving code quality enhances maintainability and reduces bugs.
            """.trimIndent()
            
            Messages.showInfoMessage(project, message, "🔵 Code Quality Insight")
        }
        
        override fun startInWriteAction(): Boolean = false
    }
    
    class RefactoringSuggestionFix(private val suggestion: String) : LocalQuickFix {
        override fun getName(): String = "View refactoring suggestions"
        override fun getFamilyName(): String = "UltraCodeAI Refactoring"
        
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val message = """
                Refactoring Suggestion
                
                $suggestion
                
                Consider:
                • Breaking large files into smaller modules
                • Extracting related functions into classes
                • Using packages to organize code logically
                • Following single responsibility principle
            """.trimIndent()
            
            Messages.showInfoMessage(project, message, "🔧 Refactoring Guidance")
        }
        
        override fun startInWriteAction(): Boolean = false
    }
    
    fun getInspectionStatistics(): Map<String, Int> {
        return mapOf(
            "inspectionsPerformed" to inspectionStats.get(),
            "qualityIssuesFound" to issuesFound.get()
        )
    }
}
