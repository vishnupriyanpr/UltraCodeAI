package com.ultracodeai.utils

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

object CodeAnalysisUtils {
    private val LOG = Logger.getInstance(CodeAnalysisUtils::class.java)

    /**
     * Extracts meaningful context around a given element
     */
    fun extractContext(element: PsiElement, maxLines: Int = 20): String {
        return try {
            val file = element.containingFile
            val document = com.intellij.psi.PsiDocumentManager.getInstance(element.project)
                .getDocument(file) ?: return element.text

            val startOffset = element.textRange.startOffset
            val endOffset = element.textRange.endOffset

            val startLine = document.getLineNumber(startOffset)
            val endLine = document.getLineNumber(endOffset)

            val contextStartLine = maxOf(0, startLine - maxLines / 2)
            val contextEndLine = minOf(document.lineCount - 1, endLine + maxLines / 2)

            val contextStart = document.getLineStartOffset(contextStartLine)
            val contextEnd = document.getLineEndOffset(contextEndLine)

            document.getText().substring(contextStart, contextEnd)
        } catch (e: Exception) {
            LOG.debug("Failed to extract context: ${e.message}")
            element.text
        }
    }

    /**
     * Determines the programming language from file extension
     */
    fun detectLanguage(file: PsiFile): String {
        val extension = file.virtualFile?.extension?.lowercase()
        return when (extension) {
            "py", "pyw" -> "python"
            "java" -> "java"
            "kt", "kts" -> "kotlin"
            "js", "jsx" -> "javascript"
            "ts", "tsx" -> "typescript"
            "cpp", "cc", "cxx", "c" -> "cpp"
            "h", "hpp" -> "c"
            "cs" -> "csharp"
            "php" -> "php"
            "rb" -> "ruby"
            "go" -> "go"
            "rs" -> "rust"
            "sql" -> "sql"
            "html", "htm" -> "html"
            "css" -> "css"
            "xml" -> "xml"
            "json" -> "json"
            "yaml", "yml" -> "yaml"
            "sh", "bash" -> "bash"
            "ps1" -> "powershell"
            else -> file.language.id.lowercase()
        }
    }

    /**
     * Estimates code complexity based on various metrics
     */
    fun estimateComplexity(code: String): ComplexityInfo {
        val lines = code.split("\n")
        val nonEmptyLines = lines.count { it.trim().isNotEmpty() }

        // Count cyclomatic complexity indicators
        val complexityKeywords = listOf(
            "if", "else", "elif", "while", "for", "foreach",
            "switch", "case", "catch", "except", "&&", "||",
            "?", "try", "finally"
        )

        val cyclomaticComplexity = complexityKeywords.sumOf { keyword ->
            code.split(Regex("\\b$keyword\\b", RegexOption.IGNORE_CASE)).size - 1
        }

        // Count nesting levels (simplified)
        val maxNesting = calculateMaxNesting(code)

        // Count function/method definitions
        val functionCount = countFunctions(code)

        // Calculate overall complexity score
        val complexityScore = (cyclomaticComplexity * 2) + maxNesting + (nonEmptyLines / 10)

        val level = when {
            complexityScore <= 5 -> ComplexityLevel.LOW
            complexityScore <= 15 -> ComplexityLevel.MEDIUM
            complexityScore <= 30 -> ComplexityLevel.HIGH
            else -> ComplexityLevel.VERY_HIGH
        }

        return ComplexityInfo(
            level = level,
            cyclomaticComplexity = cyclomaticComplexity,
            linesOfCode = nonEmptyLines,
            maxNesting = maxNesting,
            functionCount = functionCount,
            score = complexityScore
        )
    }

    private fun calculateMaxNesting(code: String): Int {
        var maxNesting = 0
        var currentNesting = 0

        for (char in code) {
            when (char) {
                '{', '(' -> {
                    currentNesting++
                    maxNesting = maxOf(maxNesting, currentNesting)
                }
                '}', ')' -> {
                    currentNesting = maxOf(0, currentNesting - 1)
                }
            }
        }

        return maxNesting
    }

    private fun countFunctions(code: String): Int {
        val functionPatterns = listOf(
            Regex("\\bdef\\s+\\w+", RegexOption.IGNORE_CASE), // Python
            Regex("\\bfunction\\s+\\w+", RegexOption.IGNORE_CASE), // JavaScript
            Regex("\\w+\\s*\\([^)]*\\)\\s*\\{", RegexOption.IGNORE_CASE), // Java/C-style
            Regex("\\bpublic\\s+\\w+\\s+\\w+\\s*\\(", RegexOption.IGNORE_CASE), // Java methods
            Regex("\\bprivate\\s+\\w+\\s+\\w+\\s*\\(", RegexOption.IGNORE_CASE), // Java methods
        )

        return functionPatterns.sumOf { pattern ->
            pattern.findAll(code).count()
        }
    }

    /**
     * Extracts imports and dependencies from code
     */
    fun extractImports(code: String, language: String): List<String> {
        return when (language.lowercase()) {
            "python" -> extractPythonImports(code)
            "java", "kotlin" -> extractJavaImports(code)
            "javascript", "typescript" -> extractJsImports(code)
            else -> emptyList()
        }
    }

    private fun extractPythonImports(code: String): List<String> {
        val imports = mutableListOf<String>()
        val lines = code.split("\n")

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("import ") -> {
                    imports.add(trimmed.substring(7).split(",").first().trim())
                }
                trimmed.startsWith("from ") && " import " in trimmed -> {
                    val module = trimmed.substring(5, trimmed.indexOf(" import ")).trim()
                    imports.add(module)
                }
            }
        }

        return imports
    }

    private fun extractJavaImports(code: String): List<String> {
        val imports = mutableListOf<String>()
        val pattern = Regex("import\\s+(static\\s+)?([\\w.]+);")

        pattern.findAll(code).forEach { match ->
            imports.add(match.groupValues[2])
        }

        return imports
    }

    private fun extractJsImports(code: String): List<String> {
        val imports = mutableListOf<String>()
        val patterns = listOf(
            Regex("import\\s+.*?from\\s+['\"]([^'\"]+)['\"]"), // ES6 imports
            Regex("require\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)"), // CommonJS requires
        )

        patterns.forEach { pattern ->
            pattern.findAll(code).forEach { match ->
                imports.add(match.groupValues[1])
            }
        }

        return imports
    }

    /**
     * Checks if code is likely a test file
     */
    fun isTestFile(file: PsiFile): Boolean {
        val fileName = file.name.lowercase()
        val filePath = file.virtualFile?.path?.lowercase() ?: ""

        return fileName.contains("test") ||
                fileName.contains("spec") ||
                filePath.contains("/test/") ||
                filePath.contains("/tests/") ||
                filePath.contains("/__tests__/") ||
                filePath.contains("/spec/")
    }

    /**
     * Extracts function/method signatures from code
     */
    fun extractFunctionSignatures(code: String, language: String): List<FunctionSignature> {
        return when (language.lowercase()) {
            "python" -> extractPythonFunctions(code)
            "java" -> extractJavaFunctions(code)
            "javascript", "typescript" -> extractJsFunctions(code)
            else -> emptyList()
        }
    }

    private fun extractPythonFunctions(code: String): List<FunctionSignature> {
        val functions = mutableListOf<FunctionSignature>()
        val pattern = Regex("def\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*:")

        pattern.findAll(code).forEach { match ->
            val name = match.groupValues[1]
            val params = match.groupValues[2].split(",").map { it.trim() }.filter { it.isNotEmpty() }
            functions.add(FunctionSignature(name, params, "python"))
        }

        return functions
    }

    private fun extractJavaFunctions(code: String): List<FunctionSignature> {
        val functions = mutableListOf<FunctionSignature>()
        val pattern = Regex("(public|private|protected)?\\s*(static)?\\s*\\w+\\s+(\\w+)\\s*\\(([^)]*)\\)")

        pattern.findAll(code).forEach { match ->
            val name = match.groupValues[3]
            val params = match.groupValues[4].split(",").map { it.trim() }.filter { it.isNotEmpty() }
            functions.add(FunctionSignature(name, params, "java"))
        }

        return functions
    }

    private fun extractJsFunctions(code: String): List<FunctionSignature> {
        val functions = mutableListOf<FunctionSignature>()
        val patterns = listOf(
            Regex("function\\s+(\\w+)\\s*\\(([^)]*)\\)"), // function declarations
            Regex("(\\w+)\\s*=\\s*function\\s*\\(([^)]*)\\)"), // function expressions
            Regex("(\\w+)\\s*=\\s*\\(([^)]*)\\)\\s*=>"), // arrow functions
        )

        patterns.forEach { pattern ->
            pattern.findAll(code).forEach { match ->
                val name = match.groupValues[1]
                val paramsIndex = if (pattern == patterns[0]) 2 else if (pattern == patterns[1]) 2 else 2
                val params = match.groupValues[paramsIndex].split(",").map { it.trim() }.filter { it.isNotEmpty() }
                functions.add(FunctionSignature(name, params, "javascript"))
            }
        }

        return functions
    }

    data class ComplexityInfo(
        val level: ComplexityLevel,
        val cyclomaticComplexity: Int,
        val linesOfCode: Int,
        val maxNesting: Int,
        val functionCount: Int,
        val score: Int
    )

    enum class ComplexityLevel {
        LOW, MEDIUM, HIGH, VERY_HIGH
    }

    data class FunctionSignature(
        val name: String,
        val parameters: List<String>,
        val language: String
    )
}
