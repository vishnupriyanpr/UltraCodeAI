package com.ultracodeai.utils

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class OllamaClient {
    companion object {
        private val LOG = Logger.getInstance(OllamaClient::class.java)
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val GENERATE_ENDPOINT = "/api/generate"
        private const val LIST_ENDPOINT = "/api/tags"
        private const val SHOW_ENDPOINT = "/api/show"
        private const val VERSION_ENDPOINT = "/api/version"
        private const val HEALTH_ENDPOINT = "/"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(LoggingInterceptor())
        .build()

    private val gson = Gson()

    suspend fun generateCompletion(
        model: String,
        prompt: String,
        maxTokens: Int = 512,
        temperature: Double = 0.2,
        stream: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val settings = SettingsState.getInstance()
        val baseUrl = settings.ollamaUrl.trimEnd('/')

        val requestBody = JsonObject().apply {
            addProperty("model", model)
            addProperty("prompt", prompt)
            addProperty("stream", stream)
            add("options", JsonObject().apply {
                addProperty("num_predict", maxTokens)
                addProperty("temperature", temperature)
                addProperty("top_p", 0.9)
                addProperty("top_k", 40)
                addProperty("repeat_penalty", 1.1)
                addProperty("stop", "<|im_end|>")
                addProperty("num_ctx", 4096) // Context window
            })
        }

        val request = Request.Builder()
            .url("$baseUrl$GENERATE_ENDPOINT")
            .post(gson.toJson(requestBody).toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", "UltraCodeAI/1.0")
            .header("Accept", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val responseBody = response.body?.string() ?: throw IOException("Empty response body")
                        val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)

                        if (jsonResponse.has("error")) {
                            val error = jsonResponse.get("error").asString
                            LOG.warn("Ollama API error: $error")
                            throw IOException("Ollama API error: $error")
                        }

                        return@withContext jsonResponse.get("response")?.asString ?: ""
                    }
                    response.code == 404 -> {
                        throw IOException("Model not found. Please run: ollama pull $model")
                    }
                    response.code == 503 -> {
                        throw IOException("Ollama server is overloaded. Please try again later.")
                    }
                    else -> {
                        val error = "HTTP ${response.code}: ${response.message}"
                        LOG.warn("Ollama request failed: $error")
                        throw IOException("Ollama request failed: $error")
                    }
                }
            }
        } catch (e: ConnectException) {
            LOG.warn("Cannot connect to Ollama at $baseUrl")
            throw IOException("Cannot connect to Ollama server. Is it running?", e)
        } catch (e: SocketTimeoutException) {
            LOG.warn("Ollama request timed out")
            throw IOException("Request timed out. The model might be loading.", e)
        } catch (e: IOException) {
            LOG.warn("Network error communicating with Ollama: ${e.message}")
            throw e
        } catch (e: Exception) {
            LOG.warn("Unexpected error in Ollama client: ${e.message}")
            throw IOException("Unexpected error: ${e.message}", e)
        }
    }

    suspend fun listModels(): String = withContext(Dispatchers.IO) {
        val settings = SettingsState.getInstance()
        val baseUrl = settings.ollamaUrl.trimEnd('/')

        val request = Request.Builder()
            .url("$baseUrl$LIST_ENDPOINT")
            .get()
            .header("User-Agent", "UltraCodeAI/1.0")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    LOG.warn("Failed to list models: HTTP ${response.code}")
                    throw IOException("Failed to list models: HTTP ${response.code}")
                }

                return@withContext response.body?.string() ?: ""
            }
        } catch (e: IOException) {
            LOG.warn("Failed to connect to Ollama at $baseUrl: ${e.message}")
            throw e
        }
    }

    suspend fun checkModelExists(modelName: String): Boolean = withContext(Dispatchers.IO) {
        val settings = SettingsState.getInstance()
        val baseUrl = settings.ollamaUrl.trimEnd('/')

        val requestBody = JsonObject().apply {
            addProperty("name", modelName)
        }

        val request = Request.Builder()
            .url("$baseUrl$SHOW_ENDPOINT")
            .post(gson.toJson(requestBody).toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", "UltraCodeAI/1.0")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            LOG.debug("Model check failed for $modelName: ${e.message}")
            return@withContext false
        }
    }

    suspend fun getOllamaVersion(): String? = withContext(Dispatchers.IO) {
        val settings = SettingsState.getInstance()
        val baseUrl = settings.ollamaUrl.trimEnd('/')

        val request = Request.Builder()
            .url("$baseUrl$VERSION_ENDPOINT")
            .get()
            .header("User-Agent", "UltraCodeAI/1.0")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
                    return@withContext jsonResponse.get("version")?.asString
                }
                return@withContext null
            }
        } catch (e: Exception) {
            LOG.debug("Failed to get Ollama version: ${e.message}")
            return@withContext null
        }
    }

    fun isOllamaRunning(): Boolean {
        val settings = SettingsState.getInstance()
        val baseUrl = settings.ollamaUrl.trimEnd('/')

        val request = Request.Builder()
            .url("$baseUrl$HEALTH_ENDPOINT")
            .get()
            .header("User-Agent", "UltraCodeAI/1.0")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 404 // 404 is OK, means server is running
            }
        } catch (e: Exception) {
            LOG.debug("Ollama health check failed: ${e.message}")
            false
        }
    }

    suspend fun getServerInfo(): ServerInfo? = withContext(Dispatchers.IO) {
        try {
            val isRunning = isOllamaRunning()
            val version = if (isRunning) getOllamaVersion() else null
            val models = if (isRunning) {
                try {
                    val modelsJson = listModels()
                    val jsonObject = gson.fromJson(modelsJson, JsonObject::class.java)
                    jsonObject.getAsJsonArray("models")?.size() ?: 0
                } catch (e: Exception) {
                    0
                }
            } else 0

            return@withContext ServerInfo(isRunning, version, models)
        } catch (e: Exception) {
            LOG.debug("Failed to get server info: ${e.message}")
            return@withContext null
        }
    }

    data class ServerInfo(
        val isRunning: Boolean,
        val version: String?,
        val modelCount: Int
    )

    private class LoggingInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val startTime = System.currentTimeMillis()

            LOG.debug("Ollama request: ${request.method} ${request.url}")

            try {
                val response = chain.proceed(request)
                val duration = System.currentTimeMillis() - startTime

                LOG.debug("Ollama response: ${response.code} in ${duration}ms")
                return response
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                LOG.debug("Ollama request failed after ${duration}ms: ${e.message}")
                throw e
            }
        }
    }
}
