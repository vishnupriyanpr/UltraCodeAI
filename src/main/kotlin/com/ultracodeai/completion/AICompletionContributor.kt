package com.ultracodeai.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.ultracodeai.services.CodeCompletionService
import com.ultracodeai.utils.SettingsState
import com.intellij.icons.AllIcons
import kotlinx.coroutines.runBlocking

class AICompletionContributor : CompletionContributor() {
    companion object {
        private val LOG = Logger.getInstance(AICompletionContributor::class.java)
    }

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            AICompletionProvider()
        )
    }
}

class AICompletionProvider : CompletionProvider<CompletionParameters>() {
    companion object {
        private val LOG = Logger.getInstance(AICompletionProvider::class.java)
    }
    
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val settings = SettingsState.getInstance()
        if (!settings.enableCompletion) return

        try {
            val editor = parameters.editor
            val document = editor.document
            val offset = parameters.offset
            val psiFile = parameters.originalFile
            val project = editor.project ?: return
            
            val language = psiFile.language.id.lowercase()
            val lineNumber = document.getLineNumber(offset)
            val lineStart = document.getLineStartOffset(lineNumber)
            val currentLine = document.getText().substring(lineStart, document.getLineEndOffset(lineNumber))
            val prefix = currentLine.substring(0, offset - lineStart).trimEnd()
            
            if (prefix.length < 2) return
            
            val completionService = project.service<CodeCompletionService>()
            
            runBlocking {
                val completion = completionService.getCompletion("", prefix, language, psiFile.name)
                completion?.let { completionResult ->
                    if (completionResult.confidence > 0.4) {
                        val lookupElement = LookupElementBuilder.create(completionResult.suggestion)
                            .withPresentableText(completionResult.suggestion.take(50))
                            .withTailText(" (AI ${(completionResult.confidence * 100).toInt()}%)", true)
                            .withIcon(AllIcons.General.Information)
                        
                        result.addElement(lookupElement)
                    }
                }
            }
        } catch (e: Exception) {
            LOG.warn("AI completion failed: ${e.message}")
        }
    }
}
