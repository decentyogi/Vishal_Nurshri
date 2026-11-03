package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.example.data.model.CashMemoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

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

class GeminiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun analyzeRoughBillImage(
        bitmap: Bitmap,
        customApiKey: String? = null,
        provider: String = "gemini",
        modelName: String = "gemini-2.5-flash"
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
            Result.failure(e)
        }
    }

    suspend fun analyzeRoughBillText(
        textDescription: String,
        customApiKey: String? = null,
        provider: String = "gemini",
        modelName: String = "gemini-2.5-flash"
    ): Result<ExtractedBillResult> = withContext(Dispatchers.IO) {
        try {
            val prompt = "${buildAnalysisPrompt()}\n\nHere is the rough bill text written by the shopkeeper/customer:\n$textDescription"

            when (provider.lowercase()) {
                "openai" -> executeOpenAiTextRequest(prompt, customApiKey, modelName)
                "claude" -> executeClaudeTextRequest(prompt, customApiKey, modelName)
                else -> executeGeminiTextRequest(prompt, customApiKey, modelName)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- GEMINI IMPLEMENTATION ---
    private fun getCandidateGeminiModels(preferredModel: String): List<String> {
        val clean = if (preferredModel.isBlank()) "gemini-3.5-flash" else preferredModel.split(" ").first()
        val allSupported = listOf(
            "gemini-3.5-flash",
            "gemini-3.8-flash",
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-2.0-flash-lite",
            "gemini-1.5-pro"
        )
        return (listOf(clean) + allSupported).distinct()
    }

    private fun executeGeminiImageRequest(
        base64Image: String,
        prompt: String,
        customApiKey: String?,
        model: String
    ): Result<ExtractedBillResult> {
        val apiKey = resolveGeminiKey(customApiKey)
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("Gemini API key is not configured. Please add GEMINI_API_KEY in Secrets or Settings."))
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
                put("temperature", 0.2)
                put("responseMimeType", "application/json")
            })
        }

        val candidateModels = getCandidateGeminiModels(model)
        var lastError: Exception? = null

        for (candidateModel in candidateModels) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$candidateModel:generateContent?key=$apiKey"
            val result = executeAndParseGemini(url, requestJson)
            if (result.isSuccess) {
                return result
            }
            lastError = result.exceptionOrNull() as? Exception
            val errorMsg = lastError?.message ?: ""

            // Non-recoverable auth errors should exit immediately
            if (errorMsg.contains("API key not valid", ignoreCase = true) || errorMsg.contains("PERMISSION_DENIED", ignoreCase = true)) {
                return result
            }

            // Retry next model with brief pause for high demand / rate limit / 404 / 503
            try { Thread.sleep(500) } catch (_: InterruptedException) {}
        }

        return Result.failure(lastError ?: Exception("Gemini servers are currently experiencing high demand. Please retry in a few moments."))
    }

    private fun executeGeminiTextRequest(
        prompt: String,
        customApiKey: String?,
        model: String
    ): Result<ExtractedBillResult> {
        val apiKey = resolveGeminiKey(customApiKey)
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("Gemini API key is not configured. Please add GEMINI_API_KEY in Secrets or Settings."))
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
                put("temperature", 0.2)
                put("responseMimeType", "application/json")
            })
        }

        val candidateModels = getCandidateGeminiModels(model)
        var lastError: Exception? = null

        for (candidateModel in candidateModels) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$candidateModel:generateContent?key=$apiKey"
            val result = executeAndParseGemini(url, requestJson)
            if (result.isSuccess) {
                return result
            }
            lastError = result.exceptionOrNull() as? Exception
            val errorMsg = lastError?.message ?: ""

            if (errorMsg.contains("API key not valid", ignoreCase = true) || errorMsg.contains("PERMISSION_DENIED", ignoreCase = true)) {
                return result
            }

            try { Thread.sleep(500) } catch (_: InterruptedException) {}
        }

        return Result.failure(lastError ?: Exception("Gemini servers are currently experiencing high demand. Please retry in a few moments."))
    }

    private fun executeAndParseGemini(url: String, requestJson: JSONObject): Result<ExtractedBillResult> {
        val requestBody = requestJson.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder().url(url).post(requestBody).build()
        val response = client.newCall(request).execute()
        val responseBodyString = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val errMsg = try {
                JSONObject(responseBodyString).optJSONObject("error")?.optString("message")
                    ?: "HTTP error ${response.code}"
            } catch (_: Exception) {
                "HTTP error ${response.code}: $responseBodyString"
            }
            return Result.failure(Exception(errMsg))
        }

        return try {
            val root = JSONObject(responseBodyString)
            val candidates = root.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            val content = candidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val rawText = parts?.optJSONObject(0)?.optString("text") ?: ""
            Result.success(parseGeminiJsonResponse(rawText))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse Gemini output: ${e.message}"))
        }
    }

    // --- OPENAI IMPLEMENTATION ---
    private fun executeOpenAiImageRequest(
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

    private fun executeOpenAiTextRequest(
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

    private fun executeOpenAiCommon(requestJson: JSONObject, apiKey: String): Result<ExtractedBillResult> {
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestJson.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            val errMsg = try {
                JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: "OpenAI HTTP error ${response.code}"
            } catch (_: Exception) {
                "OpenAI error ${response.code}: $responseBody"
            }
            return Result.failure(Exception(errMsg))
        }

        return try {
            val root = JSONObject(responseBody)
            val choices = root.optJSONArray("choices")
            val message = choices?.optJSONObject(0)?.optJSONObject("message")
            val content = message?.optString("content") ?: ""
            Result.success(parseGeminiJsonResponse(content))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse OpenAI output: ${e.message}"))
        }
    }

    // --- ANTHROPIC CLAUDE IMPLEMENTATION ---
    private fun executeClaudeImageRequest(
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

    private fun executeClaudeTextRequest(
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

    private fun executeClaudeCommon(requestJson: JSONObject, apiKey: String): Result<ExtractedBillResult> {
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(requestJson.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            val errMsg = try {
                JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: "Claude HTTP error ${response.code}"
            } catch (_: Exception) {
                "Claude error ${response.code}: $responseBody"
            }
            return Result.failure(Exception(errMsg))
        }

        return try {
            val root = JSONObject(responseBody)
            val contentArr = root.optJSONArray("content")
            val text = contentArr?.optJSONObject(0)?.optString("text") ?: ""
            Result.success(parseGeminiJsonResponse(text))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse Claude output: ${e.message}"))
        }
    }

    private fun resolveGeminiKey(customApiKey: String?): String {
        return when {
            !customApiKey.isNullOrBlank() -> customApiKey
            BuildConfig.GEMINI_API_KEY.isNotBlank() && !BuildConfig.GEMINI_API_KEY.startsWith("MY_") -> BuildConfig.GEMINI_API_KEY
            else -> ""
        }
    }

    private fun parseGeminiJsonResponse(rawJson: String): ExtractedBillResult {
        // Strip markdown backticks if any
        val clean = rawJson.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val jsonStartIndex = clean.indexOf('{')
        val jsonEndIndex = clean.lastIndexOf('}')
        val parsedJson = if (jsonStartIndex != -1 && jsonEndIndex != -1 && jsonEndIndex > jsonStartIndex) {
            clean.substring(jsonStartIndex, jsonEndIndex + 1)
        } else {
            clean
        }

        val obj = JSONObject(parsedJson)
        val customerName = obj.optString("customerName", "")
        val customerMobile = obj.optString("customerMobile", "")
        val customerAddress = obj.optString("customerAddress", "")
        val memoNumber = obj.optString("memoNumber", "")
        val memoDate = obj.optString("date", SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).format(Date()))
        val discount = obj.optDouble("discount", 0.0)
        val taxPercentage = obj.optDouble("taxPercentage", 0.0)
        val notes = obj.optString("notes", "Verified by AI")

        val itemsArray = obj.optJSONArray("items") ?: JSONArray()
        val itemsList = mutableListOf<CashMemoItem>()

        for (i in 0 until itemsArray.length()) {
            val itObj = itemsArray.getJSONObject(i)
            val desc = itObj.optString("description", "")
            val qty = itObj.optDouble("quantity", 1.0)
            val rate = itObj.optDouble("rate", 0.0)
            val amount = if (qty > 0 && rate > 0) qty * rate else itObj.optDouble("amount", 0.0)

            if (desc.isNotBlank() || amount > 0) {
                itemsList.add(
                    CashMemoItem(
                        serialNo = i + 1,
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

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val maxDim = 1600
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val (w, h) = if (ratio > 1) {
                Pair(maxDim, (maxDim / ratio).toInt())
            } else {
                Pair((maxDim * ratio).toInt(), maxDim)
            }
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
