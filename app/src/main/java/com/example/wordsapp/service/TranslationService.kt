package com.example.wordsapp.service

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface TranslationService {
    //is a shortcut for -make an HTTP GET request  and parse the response
    //retrofit builds the full request URL
    @GET("get")
    suspend fun translate(
        //add this value to the URL as a query parameter
        //q - APIs required parameter name - the text I want to translate
        //API call needs two query parameters in the URL
        @Query("q") text: String,
        @Query("langpair") langpair: String = "en|ru"  // langpair APIs required
    ): TranslationResponse

    //in Kotlin, companion is like static in Java.
    companion object {
        private const val BASE_URL = "https://api.mymemory.translated.net/"

        fun create(): TranslationService {
            return Retrofit.Builder()
                //tells Retrofit where the API lives
                .baseUrl(BASE_URL)
                //parse JSON into Kotlin objects
                .addConverterFactory(GsonConverterFactory.create())
                //build Retrofit
                .build()
                // generate a working implementation of my interface
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