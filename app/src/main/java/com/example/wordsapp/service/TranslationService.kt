package com.example.wordsapp.service

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface TranslationService {
    @GET("get")
    suspend fun translate(
        @Query("q") text: String,               // note: q, not text
        @Query("langpair") langpair: String = "en|ru"  // note: langpair, not lang
    ): TranslationResponse

    companion object {
        private const val BASE_URL = "https://api.mymemory.translated.net/"

        fun create(): TranslationService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(TranslationService::class.java)
        }
    }
}

data class TranslationResponse(
    val responseData: ResponseData,
    val matches: List<Match>?
)

data class ResponseData(
    val translatedText: String,
    val match: Double?
)

data class Match(
    val translation: String
)