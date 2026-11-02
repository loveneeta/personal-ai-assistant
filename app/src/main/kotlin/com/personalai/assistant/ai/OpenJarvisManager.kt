package com.personalai.assistant.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object OpenJarvisManager {

    private const val PREFS_NAME = "openjarvis_prefs"
    private const val KEY_ENABLED = "openjarvis_enabled"
    private const val KEY_SERVER_URL = "openjarvis_server_url"
    private const val KEY_API_KEY = "openjarvis_api_key"
    private const val KEY_MODEL = "openjarvis_model"

    const val DEFAULT_MODEL = "nvidia/nemotron-3.5-lightning-30b-a3b"
    const val DEFAULT_SERVER_URL = "http://10.0.2.2:8000"

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getServerUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL)?.trim() ?: DEFAULT_SERVER_URL
    }

    fun setServerUrl(context: Context, url: String) {
        val normalized = url.trim().removeSuffix("/")
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SERVER_URL, normalized).apply()
    }

    fun getApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_API_KEY, "")?.trim() ?: ""
    }

    fun setApiKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    fun getModel(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_MODEL, DEFAULT_MODEL)?.trim() ?: DEFAULT_MODEL
    }

    fun setModel(context: Context, model: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_MODEL, model.trim()).apply()
    }

    /**
     * Test connection to the OpenJarvis server.
     */
    suspend fun testConnection(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val baseUrl = getServerUrl(context)
        val apiKey = getApiKey(context)
        val model = getModel(context)
        val startTime = System.currentTimeMillis()

        try {
            val url = URL("$baseUrl/health")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                if (apiKey.isNotEmpty()) {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
            }

            val responseCode = connection.responseCode
            val latency = System.currentTimeMillis() - startTime

            if (responseCode in 200..299) {
                Result.success("Connected successfully (${latency}ms) • Model: $model")
            } else {
                Result.failure(Exception("HTTP $responseCode from OpenJarvis ($baseUrl)"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Cannot reach OpenJarvis at $baseUrl: ${e.message}"))
        }
    }

    /**
     * Query OpenJarvis OpenAI-compatible endpoint with optional local context.
     */
    suspend fun query(
        context: Context,
        userPrompt: String,
        groundingContext: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val baseUrl = getServerUrl(context)
        val apiKey = getApiKey(context)
        val model = getModel(context)

        try {
            val url = URL("$baseUrl/v1/chat/completions")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 45000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                if (apiKey.isNotEmpty()) {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
            }

            val messages = JSONArray()
            val systemInstruction = buildString {
                append("You are JARVIS, Loveneet's personal assistant.")
                if (groundingContext.isNotBlank()) {
                    append("\nHere is relevant context from on-device meeting transcripts, action items, and voice notes:\n")
                    append(groundingContext)
                    append("\nUse this context to answer accurately. If context is not relevant, answer using your general intelligence.")
                }
            }
            messages.put(JSONObject().put("role", "system").put("content", systemInstruction))
            messages.put(JSONObject().put("role", "user").put("content", userPrompt))

            val payload = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("temperature", 0.7)
                put("max_tokens", 800)
            }

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseText = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use {
                    it.readText()
                }
                val json = JSONObject(responseText)
                val choices = json.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val message = choices.getJSONObject(0).optJSONObject("message")
                    val content = message?.optString("content", "") ?: ""
                    Result.success(content)
                } else {
                    Result.failure(Exception("Empty choices in response"))
                }
            } else {
                val errorStream = connection.errorStream
                val errorText = errorStream?.let {
                    BufferedReader(InputStreamReader(it, "UTF-8")).use { r -> r.readText() }
                } ?: "HTTP $responseCode"
                Result.failure(Exception("OpenJarvis returned $responseCode: $errorText"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Ingest a document or meeting transcript into OpenJarvis Knowledge store.
     */
    suspend fun syncKnowledge(
        context: Context,
        title: String,
        content: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        val baseUrl = getServerUrl(context)
        val apiKey = getApiKey(context)

        try {
            val url = URL("$baseUrl/v1/connectors/upload/ingest")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 20000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (apiKey.isNotEmpty()) {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
            }

            val payload = JSONObject().apply {
                put("title", title)
                put("content", content)
            }

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val resp = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use { it.readText() }
                val json = JSONObject(resp)
                val chunks = json.optInt("chunks_added", 1)
                Result.success(chunks)
            } else {
                Result.failure(Exception("Sync failed with HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
