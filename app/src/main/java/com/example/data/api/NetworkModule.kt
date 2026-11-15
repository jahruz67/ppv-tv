package com.example.data.api

import com.example.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun createOkHttpClient(enableLogging: Boolean = BuildConfig.DEBUG): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Android TV; PPVstTV/1.0)")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }

        if (enableLogging) {
            val logger = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(logger)
        }

        return builder.build()
    }

    fun createPpvApiService(
        baseUrl: String = BuildConfig.PPV_API_BASE_URL,
        okHttpClient: OkHttpClient = createOkHttpClient(),
    ): PpvApiService {
        val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(normalizedBase)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(PpvApiService::class.java)
    }
}
