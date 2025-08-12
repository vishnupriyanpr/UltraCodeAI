package com.ultracodeai.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.*

class ChatToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val chatPanel = UltraSimpleChatPanel()
        val content = ContentFactory.getInstance().createContent(chatPanel, "", false)
        content.isCloseable = false
        toolWindow.contentManager.addContent(content)
    }
}

class UltraSimpleChatPanel : JPanel(BorderLayout()) {
    private val chatArea = JTextArea()
    private val inputField = JTextField()
    private val sendButton = JButton("Send")
    
    init {
        setupUI()
    }
    
    private fun setupUI() {
        // Chat area
        chatArea.isEditable = false
        chatArea.lineWrap = true
        chatArea.wrapStyleWord = true
        chatArea.text = "🤖 UltraCodeAI - Now with Ollama Integration!\n\nAsk me for Python code, explanations, or debugging help!\n\n"
        
        // Input area
        val inputPanel = JPanel(BorderLayout())
        inputPanel.add(inputField, BorderLayout.CENTER)
        inputPanel.add(sendButton, BorderLayout.EAST)
        
        // Layout
        add(JScrollPane(chatArea), BorderLayout.CENTER)
        add(inputPanel, BorderLayout.SOUTH)
        
        // Button action
        sendButton.addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent) {
                handleMessage()
            }
        })
        
        // Enter key support
        inputField.addActionListener {
            handleMessage()
        }
    }
    
    private fun handleMessage() {
        val message = inputField.text.trim()
        if (message.isEmpty()) return
        
        chatArea.append("👤 You: $message\n")
        inputField.text = ""
        
        // Show thinking indicator
        chatArea.append("🤖 AI: ⏳ Thinking...\n")
        chatArea.caretPosition = chatArea.document.length
        
        // Disable input while processing
        sendButton.isEnabled = false
        inputField.isEnabled = false
        
        // Connect to Ollama in background thread
        Thread {
            try {
                val response = callOllamaAPI(message)
                
                // Update UI on EDT thread
                SwingUtilities.invokeLater {
                    // Remove thinking indicator
                    val text = chatArea.text
                    val lastThinking = text.lastIndexOf("🤖 AI: ⏳ Thinking...\n")
                    if (lastThinking != -1) {
                        chatArea.text = text.substring(0, lastThinking)
                    }
                    
                    // Add AI response
                    chatArea.append("🤖 AI: $response\n\n")
                    chatArea.caretPosition = chatArea.document.length
                    
                    // Re-enable input
                    sendButton.isEnabled = true
                    inputField.isEnabled = true
                    inputField.requestFocus()
                }
                
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    // Remove thinking indicator and show error
                    val text = chatArea.text
                    val lastThinking = text.lastIndexOf("🤖 AI: ⏳ Thinking...\n")
                    if (lastThinking != -1) {
                        chatArea.text = text.substring(0, lastThinking)
                    }
                    
                    chatArea.append("🤖 AI: ❌ Error: ${e.message}\n")
                    chatArea.append("💡 Make sure Ollama is running: 'ollama serve'\n\n")
                    chatArea.caretPosition = chatArea.document.length
                    
                    // Re-enable input
                    sendButton.isEnabled = true
                    inputField.isEnabled = true
                    inputField.requestFocus()
                }
            }
        }.start()
    }
    
    private fun callOllamaAPI(message: String): String {
        val url = java.net.URL("http://127.0.0.1:11434/api/generate")
        val connection = url.openConnection() as java.net.HttpURLConnection
        
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 10000 // 10 seconds
        connection.readTimeout = 30000    // 30 seconds
        
        // Create JSON request
        val jsonRequest = """
        {
            "model": "qwen2.5-coder:7b",
            "prompt": "$message",
            "stream": false
        }
        """.trimIndent()
        
        // Send request
        connection.outputStream.use { output ->
            output.write(jsonRequest.toByteArray())
        }
        
        // Read response
        val responseCode = connection.responseCode
        if (responseCode != 200) {
            throw Exception("HTTP $responseCode: ${connection.responseMessage}")
        }
        
        val responseText = connection.inputStream.bufferedReader().use { it.readText() }
        
        // Parse JSON response to extract the actual AI response
        return parseOllamaResponse(responseText)
    }
    
    private fun parseOllamaResponse(jsonResponse: String): String {
    return try {
        // More robust JSON parsing
        val responseStart = jsonResponse.indexOf("\"response\":\"")
        if (responseStart == -1) {
            return "Error: Could not find response in JSON"
        }
        
        val actualStart = responseStart + 12
        var responseEnd = actualStart
        var escapeNext = false
        var braceCount = 0
        
        // Parse until we find the actual end of the response string
        while (responseEnd < jsonResponse.length) {
            val char = jsonResponse[responseEnd]
            when {
                escapeNext -> {
                    escapeNext = false
                }
                char == '\\' -> {
                    escapeNext = true
                }
                char == '"' && braceCount == 0 -> {
                    break // Found the end of the response string
                }
                char == '{' -> braceCount++
                char == '}' -> braceCount--
            }
            responseEnd++
        }
        
        if (responseEnd <= actualStart) {
            return "Error: Could not parse response properly"
        }
        
        val rawResponse = jsonResponse.substring(actualStart, responseEnd)
        
        // Unescape JSON characters properly
        rawResponse
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\u003c", "<")
            .replace("\\u003e", ">")
            .replace("\\u003d", "=")
            .replace("\\u0026", "&")
            .trim()
            
    } catch (e: Exception) {
        "Error parsing AI response: ${e.message}"
    }
}

}
