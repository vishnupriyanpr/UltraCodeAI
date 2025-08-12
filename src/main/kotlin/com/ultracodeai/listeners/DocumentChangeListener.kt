package com.ultracodeai.listeners

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.ultracodeai.services.ErrorDetectionService
import com.ultracodeai.utils.SettingsState
import kotlinx.coroutines.*

class DocumentChangeListener : DocumentListener {
    companion object {
        private val LOG = Logger.getInstance(DocumentChangeListener::class.java)
        private const val DEBOUNCE_DELAY = 500L // 500ms
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val pendingAnalysis = mutableMapOf<String, Job>()

    override fun documentChanged(event: DocumentEvent) {
        val settings = SettingsState.getInstance()
        if (!settings.enableErrorDetection || settings.lowPowerMode) {
            return
        }

        try {
            val document = event.document
            val file = FileDocumentManager.getInstance().getFile(document)

            // Only process supported file types
            if (file == null || !isSupportedFileType(file.extension)) {
                return
            }

            val lineNumber = document.getLineNumber(event.offset)
            val documentKey = "${file.path}:$lineNumber"

            // Cancel previous analysis for this document/line
            pendingAnalysis[documentKey]?.cancel()

            // Schedule new analysis with debouncing
            val job = scope.launch {
                delay(DEBOUNCE_DELAY)

                try {
                    // Find project from document
                    val projects = com.intellij.openapi.project.ProjectManager.getInstance().openProjects
                    val project = projects.firstOrNull { proj ->
                        com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
                            .getFile(document)?.let { docFile ->
                                com.intellij.openapi.vfs.VfsUtil.isAncestor(proj.baseDir, docFile, true)
                            } ?: false
                    }

                    project?.let { proj ->
                        val errorDetectionService = proj.service<ErrorDetectionService>()
                        errorDetectionService.analyzeDocumentChange(document, lineNumber)
                    }
                } catch (e: Exception) {
                    LOG.debug("Error in document change analysis: ${e.message}")
                } finally {
                    pendingAnalysis.remove(documentKey)
                }
            }

            pendingAnalysis[documentKey] = job

        } catch (e: Exception) {
            LOG.debug("Failed to process document change: ${e.message}")
        }
    }

    override fun beforeDocumentChange(event: DocumentEvent) {
        // Clear analysis for the line being modified
        try {
            val document = event.document
            val lineNumber = document.getLineNumber(event.offset)
            val file = FileDocumentManager.getInstance().getFile(document)

            if (file != null) {
                val documentKey = "${file.path}:$lineNumber"
                pendingAnalysis[documentKey]?.cancel()
                pendingAnalysis.remove(documentKey)
            }
        } catch (e: Exception) {
            LOG.debug("Error in beforeDocumentChange: ${e.message}")
        }
    }

    private fun isSupportedFileType(extension: String?): Boolean {
        if (extension == null) return false

        return when (extension.lowercase()) {
            "py", "pyw" -> true // Python
            "java", "kt", "kts" -> true // Java, Kotlin
            "js", "ts", "jsx", "tsx" -> true // JavaScript, TypeScript
            "cpp", "cc", "cxx", "c", "h", "hpp" -> true // C/C++
            "cs" -> true // C#
            "php" -> true // PHP
            "rb" -> true // Ruby
            "go" -> true // Go
            "rs" -> true // Rust
            "sql" -> true // SQL
            else -> false
        }
    }
}
