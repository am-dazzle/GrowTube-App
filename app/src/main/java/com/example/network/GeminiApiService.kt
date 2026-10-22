package com.example.network

import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiPart(val text: String?)

@JsonClass(generateAdapter = true)
data class GeminiContent(val parts: List<GeminiPart>)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(val content: GeminiContent?)

@JsonClass(generateAdapter = true)
data class GeminiResponse(val candidates: List<GeminiCandidate>?)

interface RealGeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @retrofit2.http.Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

interface GeminiApiService {
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiRetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = com.squareup.moshi.Moshi.Builder()
        .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val realService: RealGeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RealGeminiApiService::class.java)
    }

    val service: GeminiApiService = object : GeminiApiService {
        override suspend fun generateContent(apiKey: String, request: GeminiRequest): GeminiResponse {
            val models = listOf("gemini-3.5-flash", "gemini-3.1-pro-preview")
            var lastException: Exception? = null
            for (model in models) {
                try {
                    return realService.generateContent(model = model, apiKey = apiKey, request = request)
                } catch (e: Exception) {
                    if (e is retrofit2.HttpException) {
                        val code = e.code()
                        // 400 (Bad request / invalid parameters), 401 (Unauthorized), 403 (Forbidden / bad API key), 429 (Quota exceeded)
                        if (code == 400 || code == 401 || code == 403 || code == 429) {
                            throw e
                        }
                    }
                    lastException = e
                    android.util.Log.w("GeminiClient", "Model $model failed, trying fallback...", e)
                }
            }
            throw lastException ?: Exception("Surgical model fallback failed")
        }
    }
}
