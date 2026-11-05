package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.example.data.model.CashMemoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

data class ExtractedBillResult(
    val customerName: String = "",
    val customerMobile: String = "",
    val customerAddress: String = "",
    val memoDate: String = "",
    val memoNumber: String = "",
    val items: List<CashMemoItem> = emptyList(),
    val discount: Double = 0.0,
    val taxPercentage: Double = 0.0,
    val notes: String = "",
    val isSuccess: Boolean = true,
    val rawResponse: String = ""
)

/**
 * Stabilized Gemini Flash Scanner Service:
 * - Downscales bitmaps to max 1024x1024 at 80% JPEG quality before base64 encoding.
 * - Uses 30-second connect/read/write timeouts.
 * - Implements automatic exponential backoff (max 2 retries) for transient HTTP 429/503 or network glitches.
 * - Enforces strict markdown JSON extraction guardrails.
 */
class GeminiService {

    companion object {
        const val DEFAULT_STABLE_MODEL = "gemini-2.5-flash"
        private const val MAX_IMAGE_DIMENSION = 1024
        private const val JPEG_COMPRESSION_QUALITY = 80
        private const val MAX_RETRIES = 2
        private const val INITIAL_BACKOFF_MS = 1000L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun analyzeRoughBillImage(
        bitmap: Bitmap,
        customApiKey: String? = null,
        provider: String = "gemini",
        modelName: String = DEFAULT_STABLE_MODEL
    ): Result<ExtractedBillResult> = withContext(Dispatchers.IO) {
        try {
            val base64Image = bitmapToBase64(bitmap)
            val prompt = buildAnalysisPrompt()

            when (provider.lowercase()) {
                "openai" -> executeOpenAiImageRequest(base64Image, prompt, customApiKey, modelName)
                "claude" -> executeClaudeImageRequest(base64Image, prompt, customApiKey, modelName)
                else -> executeGeminiImageRequest(base64Image, prompt, customApiKey, modelName)
            }
        } catch (e: Exception) {
            Result.failure(Exception("Could not process bill image: ${e.message ?: "Unknown error"}"))
        }
    }

    suspend fun analyzeRoughBillText(
        textDescription: String,
        customApiKey: String? = null,
        provider: String = "gemini",
        modelName: String = DEFAULT_STABLE_MODEL
    ): Result<ExtractedBillResult> = withContext(Dispatchers.IO) {
        try {
            val prompt = "${buildAnalysisPrompt()}\n\nHere is the rough bill text written by the shopkeeper/customer:\n$textDescription"

            when (provider.lowercase()) {
                "openai" -> executeOpenAiTextRequest(prompt, customApiKey, modelName)
                "claude" -> executeClaudeTextRequest(prompt, customApiKey, modelName)
                else -> executeGeminiTextRequest(prompt, customApiKey, modelName)
            }
        } catch (e: Exception) {
            Result.failure(Exception("Could not analyze bill text: ${e.message ?: "Unknown error"}"))
        }
    }

    private fun getCandidateGeminiModels(preferredModel: String): List<String> {
        val raw = preferredModel.trim().split(" ").firstOrNull().orEmpty()
        val normalized = if (raw.isBlank() || raw.startsWith("gemini-3.")) {
            DEFAULT_STABLE_MODEL
        } else {
            raw
        }
        val stableFallbacks = listOf(
            DEFAULT_STABLE_MODEL,
            "gemini-2.0-flash",
            "gemini-1.5-flash"
        )
        return (listOf(normalized) + stableFallbacks).distinct()
    }

    private suspend fun executeGeminiImageRequest(
        base64Image: String,
        prompt: String,
        customApiKey: String?,
        model: String
    ): Result<ExtractedBillResult> {
        val apiKey = resolveGeminiKey(customApiKey)
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("Gemini API key is not configured. Please add GEMINI_API_KEY in Secrets or Profile Settings."))
        }

        val requestJson = JSONObject().apply {
            val contents = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                        put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                put("data", base64Image)
                            })
                        })
                    }
                    put("parts", parts)
                }
                put(contentObj)
            }
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.15)
                put("responseMimeType", "application/json")
            })
        }

        return executeGeminiWithModelFallbackAndRetry(apiKey, model, requestJson)
    }

    private suspend fun executeGeminiTextRequest(
        prompt: String,
        customApiKey: String?,
        model: String
    ): Result<ExtractedBillResult> {
        val apiKey = resolveGeminiKey(customApiKey)
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("Gemini API key is not configured. Please add GEMINI_API_KEY in Secrets or Profile Settings."))
        }

        val requestJson = JSONObject().apply {
            val contents = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    }
                    put("parts", parts)
                }
                put(contentObj)
            }
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.15)
                put("responseMimeType", "application/json")
            })
        }

        return executeGeminiWithModelFallbackAndRetry(apiKey, model, requestJson)
    }

    /**
     * Executes Gemini API request with exponential backoff retry (max 2 retries per endpoint)
     * for HTTP 429 / 503 or transient IOExceptions.
     */
    private suspend fun executeGeminiWithModelFallbackAndRetry(
        apiKey: String,
        preferredModel: String,
        requestJson: JSONObject
    ): Result<ExtractedBillResult> {
        val candidateModels = getCandidateGeminiModels(preferredModel)
        var lastError: Exception? = null

        for (candidateModel in candidateModels) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$candidateModel:generateContent?key=$apiKey"

            for (attempt in 0..MAX_RETRIES) {
                val callResult = executeSingleGeminiCall(url, requestJson)
                if (callResult.result.isSuccess) {
                    return callResult.result
                }

                lastError = callResult.result.exceptionOrNull() as? Exception
                val errorMsg = lastError?.message.orEmpty()

                // Non-recoverable authentication / key errors exit immediately
                if (callResult.httpCode == 400 && errorMsg.contains("API key not valid", ignoreCase = true) ||
                    callResult.httpCode == 403 ||
                    errorMsg.contains("PERMISSION_DENIED", ignoreCase = true)
                ) {
                    return callResult.result
                }

                // Model not found (404): break retry loop and try next stable fallback model immediately
                if (callResult.httpCode == 404) {
                    break
                }

                // Transient error (HTTP 429 / 503 / 500 or network IOException): exponential backoff
                val isTransient = callResult.isNetworkGlitch ||
                    callResult.httpCode == 429 ||
                    callResult.httpCode == 503 ||
                    callResult.httpCode == 500

                if (isTransient && attempt < MAX_RETRIES) {
                    val backoffMs = INITIAL_BACKOFF_MS * (1L shl attempt) // 1000ms, 2000ms
                    delay(backoffMs)
                } else {
                    break
                }
            }
        }

        return Result.failure(
            lastError ?: Exception("Unable to scan bill right now. Please check your connection and try again.")
        )
    }

    private data class HttpCallOutcome(
        val result: Result<ExtractedBillResult>,
        val httpCode: Int = 0,
        val isNetworkGlitch: Boolean = false
    )

    private fun executeSingleGeminiCall(url: String, requestJson: JSONObject): HttpCallOutcome {
        val requestBody = requestJson.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder().url(url).post(requestBody).build()

        return try {
            client.newCall(request).execute().use { response ->
                val responseBodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errMsg = try {
                        JSONObject(responseBodyString).optJSONObject("error")?.optString("message")
                            ?: "HTTP ${response.code}"
                    } catch (_: Exception) {
                        "HTTP ${response.code}"
                    }
                    return HttpCallOutcome(
                        result = Result.failure(Exception(errMsg)),
                        httpCode = response.code
                    )
                }

                val root = JSONObject(responseBodyString)
                val candidates = root.optJSONArray("candidates")
                val candidate = candidates?.optJSONObject(0)
                val content = candidate?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val rawText = parts?.optJSONObject(0)?.optString("text").orEmpty()

                if (rawText.isBlank()) {
                    return HttpCallOutcome(
                        result = Result.failure(Exception("AI returned an empty response. Please try scanning a clearer photo.")),
                        httpCode = response.code
                    )
                }

                HttpCallOutcome(
                    result = Result.success(parseGeminiJsonResponse(rawText)),
                    httpCode = response.code
                )
            }
        } catch (ioe: IOException) {
            HttpCallOutcome(
                result = Result.failure(Exception("Network timeout or connection error: ${ioe.message}")),
                isNetworkGlitch = true
            )
        } catch (e: Exception) {
            HttpCallOutcome(
                result = Result.failure(Exception("Could not parse scanned bill JSON: ${e.message}"))
            )
        }
    }

    // --- OPENAI IMPLEMENTATION ---
    private suspend fun executeOpenAiImageRequest(
        base64Image: String,
        prompt: String,
        apiKey: String?,
        model: String
    ): Result<ExtractedBillResult> {
        if (apiKey.isNullOrBlank()) {
            return Result.failure(IllegalStateException("OpenAI API key is missing. Please enter your OpenAI key in Profile Settings."))
        }
        val effectiveModel = if (model.isBlank()) "gpt-4o-mini" else model
        val requestJson = JSONObject().apply {
            put("model", effectiveModel)
            val messages = JSONArray().apply {
                val userMsg = JSONObject().apply {
                    put("role", "user")
                    val contentArr = JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", prompt)
                        })
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:image/jpeg;base64,$base64Image")
                            })
                        })
                    }
                    put("content", contentArr)
                }
                put(userMsg)
            }
            put("messages", messages)
            put("temperature", 0.2)
        }

        return executeOpenAiCommon(requestJson, apiKey)
    }

    private suspend fun executeOpenAiTextRequest(
        prompt: String,
        apiKey: String?,
        model: String
    ): Result<ExtractedBillResult> {
        if (apiKey.isNullOrBlank()) {
            return Result.failure(IllegalStateException("OpenAI API key is missing. Please enter your OpenAI key in Profile Settings."))
        }
        val effectiveModel = if (model.isBlank()) "gpt-4o-mini" else model
        val requestJson = JSONObject().apply {
            put("model", effectiveModel)
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            put("messages", messages)
            put("temperature", 0.2)
        }

        return executeOpenAiCommon(requestJson, apiKey)
    }

    private suspend fun executeOpenAiCommon(requestJson: JSONObject, apiKey: String): Result<ExtractedBillResult> {
        for (attempt in 0..MAX_RETRIES) {
            try {
                val request = Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(requestJson.toString().toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        if ((response.code == 429 || response.code == 503) && attempt < MAX_RETRIES) {
                            delay(INITIAL_BACKOFF_MS * (1L shl attempt))
                            return@use
                        }
                        val errMsg = try {
                            JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: "OpenAI HTTP ${response.code}"
                        } catch (_: Exception) {
                            "OpenAI HTTP ${response.code}"
                        }
                        return Result.failure(Exception(errMsg))
                    }

                    val root = JSONObject(responseBody)
                    val choices = root.optJSONArray("choices")
                    val message = choices?.optJSONObject(0)?.optJSONObject("message")
                    val content = message?.optString("content").orEmpty()
                    return Result.success(parseGeminiJsonResponse(content))
                }
            } catch (ioe: IOException) {
                if (attempt < MAX_RETRIES) {
                    delay(INITIAL_BACKOFF_MS * (1L shl attempt))
                } else {
                    return Result.failure(Exception("Network timeout: ${ioe.message}"))
                }
            } catch (e: Exception) {
                return Result.failure(Exception("Failed to parse OpenAI output: ${e.message}"))
            }
        }
        return Result.failure(Exception("OpenAI request failed after retries."))
    }

    // --- ANTHROPIC CLAUDE IMPLEMENTATION ---
    private suspend fun executeClaudeImageRequest(
        base64Image: String,
        prompt: String,
        apiKey: String?,
        model: String
    ): Result<ExtractedBillResult> {
        if (apiKey.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Anthropic Claude API key is missing. Please enter your Claude key in Profile Settings."))
        }
        val effectiveModel = if (model.isBlank()) "claude-3-5-haiku-20241022" else model
        val requestJson = JSONObject().apply {
            put("model", effectiveModel)
            put("max_tokens", 2048)
            val messages = JSONArray().apply {
                val userMsg = JSONObject().apply {
                    put("role", "user")
                    val contentArr = JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "image")
                            put("source", JSONObject().apply {
                                put("type", "base64")
                                put("media_type", "image/jpeg")
                                put("data", base64Image)
                            })
                        })
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", prompt)
                        })
                    }
                    put("content", contentArr)
                }
                put(userMsg)
            }
            put("messages", messages)
        }

        return executeClaudeCommon(requestJson, apiKey)
    }

    private suspend fun executeClaudeTextRequest(
        prompt: String,
        apiKey: String?,
        model: String
    ): Result<ExtractedBillResult> {
        if (apiKey.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Anthropic Claude API key is missing. Please enter your Claude key in Profile Settings."))
        }
        val effectiveModel = if (model.isBlank()) "claude-3-5-haiku-20241022" else model
        val requestJson = JSONObject().apply {
            put("model", effectiveModel)
            put("max_tokens", 2048)
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            put("messages", messages)
        }

        return executeClaudeCommon(requestJson, apiKey)
    }

    private suspend fun executeClaudeCommon(requestJson: JSONObject, apiKey: String): Result<ExtractedBillResult> {
        for (attempt in 0..MAX_RETRIES) {
            try {
                val request = Request.Builder()
                    .url("https://api.anthropic.com/v1/messages")
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .post(requestJson.toString().toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        if ((response.code == 429 || response.code == 503) && attempt < MAX_RETRIES) {
                            delay(INITIAL_BACKOFF_MS * (1L shl attempt))
                            return@use
                        }
                        val errMsg = try {
                            JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: "Claude HTTP ${response.code}"
                        } catch (_: Exception) {
                            "Claude HTTP ${response.code}"
                        }
                        return Result.failure(Exception(errMsg))
                    }

                    val root = JSONObject(responseBody)
                    val contentArr = root.optJSONArray("content")
                    val text = contentArr?.optJSONObject(0)?.optString("text").orEmpty()
                    return Result.success(parseGeminiJsonResponse(text))
                }
            } catch (ioe: IOException) {
                if (attempt < MAX_RETRIES) {
                    delay(INITIAL_BACKOFF_MS * (1L shl attempt))
                } else {
                    return Result.failure(Exception("Network timeout: ${ioe.message}"))
                }
            } catch (e: Exception) {
                return Result.failure(Exception("Failed to parse Claude output: ${e.message}"))
            }
        }
        return Result.failure(Exception("Claude request failed after retries."))
    }

    private fun resolveGeminiKey(customApiKey: String?): String {
        return when {
            !customApiKey.isNullOrBlank() -> customApiKey.trim()
            BuildConfig.GEMINI_API_KEY.isNotBlank() && !BuildConfig.GEMINI_API_KEY.startsWith("MY_") -> BuildConfig.GEMINI_API_KEY.trim()
            else -> ""
        }
    }

    /**
     * Strict Markdown & JSON Extraction Guardrails:
     * Strips any leading ```json / ```JSON / ``` fences and trailing ``` before extracting the JSON object.
     */
    internal fun extractCleanJsonString(rawResponse: String): String {
        var cleaned = rawResponse.trim()
        // Strip markdown code block fences using regex if present
        val fenceRegex = Regex("```(?:json|JSON)?\\s*([\\s\\S]*?)\\s*```")
        val match = fenceRegex.find(cleaned)
        if (match != null) {
            cleaned = match.groupValues[1].trim()
        } else {
            cleaned = cleaned
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        }

        val jsonStartIndex = cleaned.indexOf('{')
        val jsonEndIndex = cleaned.lastIndexOf('}')
        if (jsonStartIndex == -1 || jsonEndIndex == -1 || jsonEndIndex <= jsonStartIndex) {
            throw IllegalArgumentException("Response did not contain a valid JSON object.")
        }
        return cleaned.substring(jsonStartIndex, jsonEndIndex + 1)
    }

    internal fun parseGeminiJsonResponse(rawJson: String): ExtractedBillResult {
        val parsedJson = extractCleanJsonString(rawJson)
        val obj = JSONObject(parsedJson)

        val customerName = obj.optString("customerName", "").trim()
        val customerMobile = obj.optString("customerMobile", "").trim()
        val customerAddress = obj.optString("customerAddress", "").trim()
        val memoNumber = obj.optString("memoNumber", "").trim()
        val memoDate = obj.optString("date", SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).format(Date())).trim()
        val discount = obj.optDouble("discount", 0.0).takeIf { !it.isNaN() && it >= 0.0 } ?: 0.0
        val taxPercentage = obj.optDouble("taxPercentage", 0.0).takeIf { !it.isNaN() && it >= 0.0 } ?: 0.0
        val notes = obj.optString("notes", "Verified by AI").trim()

        val itemsArray = obj.optJSONArray("items") ?: JSONArray()
        val itemsList = mutableListOf<CashMemoItem>()

        for (i in 0 until itemsArray.length()) {
            val itObj = itemsArray.optJSONObject(i) ?: continue
            val desc = itObj.optString("description", "").trim()
            val qty = itObj.optDouble("quantity", 1.0).takeIf { !it.isNaN() && it > 0.0 } ?: 1.0
            val rate = itObj.optDouble("rate", 0.0).takeIf { !it.isNaN() && it >= 0.0 } ?: 0.0
            val amount = if (qty > 0 && rate > 0) qty * rate else (itObj.optDouble("amount", 0.0).takeIf { !it.isNaN() } ?: 0.0)

            if (desc.isNotBlank() || amount > 0) {
                itemsList.add(
                    CashMemoItem(
                        serialNo = itemsList.size + 1,
                        description = desc,
                        quantity = qty,
                        rate = rate,
                        amount = amount
                    )
                )
            }
        }

        return ExtractedBillResult(
            customerName = customerName,
            customerMobile = customerMobile,
            customerAddress = customerAddress,
            memoDate = memoDate,
            memoNumber = memoNumber,
            items = itemsList,
            discount = discount,
            taxPercentage = taxPercentage,
            notes = notes,
            isSuccess = true,
            rawResponse = rawJson
        )
    }

    private fun buildAnalysisPrompt(): String {
        return """
You are an expert bilingual Indian nursery & shop invoice assistant.
Your task is to analyze this handwritten rough bill, scratchpad notes, or text bill.
Extract:
1. CUSTOMER DETAILS: customerName, customerMobile (10 digits in India), customerAddress.
2. DATE & MEMO NO if available (otherwise current date dd/MM/yyyy).
3. ITEMS: Plant or item name in clean Hindi/English (e.g. 'गुलाब का पौधा', 'गेंदा', 'गमला 12 इंच', 'वर्मीकम्पोस्ट').
4. QUANTITY & RATE: Validate that amount = quantity * rate. Correct arithmetic mistakes if the rough slip has calculation errors!
5. DISCOUNT or TAX if mentioned.
6. NOTES: Brief summary of what you extracted/validated.

Return ONLY a valid JSON object matching this schema:
{
  "customerName": "",
  "customerMobile": "",
  "customerAddress": "",
  "date": "dd/MM/yyyy",
  "memoNumber": "",
  "items": [
    {
      "description": "Plant or Item Name",
      "quantity": 1.0,
      "rate": 0.0,
      "amount": 0.0
    }
  ],
  "discount": 0.0,
  "taxPercentage": 0.0,
  "notes": "Validation notes"
}
        """.trimIndent()
    }

    /**
     * Pre-processes bitmaps before base64 encoding:
     * Downscales to max 1024x1024 and compresses at 80% JPEG quality to prevent OOM and payload timeouts.
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val largestDim = max(bitmap.width, bitmap.height)
        val scaled = if (largestDim > MAX_IMAGE_DIMENSION) {
            val scale = MAX_IMAGE_DIMENSION.toFloat() / largestDim.toFloat()
            val targetW = max(1, (bitmap.width * scale).toInt())
            val targetH = max(1, (bitmap.height * scale).toInt())
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        } else {
            bitmap
        }

        val bytes = ByteArrayOutputStream().use { outputStream ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_COMPRESSION_QUALITY, outputStream)
            outputStream.toByteArray()
        }
        if (scaled !== bitmap) {
            scaled.recycle()
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
