package com.eliteprime.app.data.remote

import com.eliteprime.app.data.model.HomeFeedResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface SportsApiService {
    @GET("api/v1/home")
    suspend fun getHomeFeed(
        @Header("Accept") accept: String = "application/json",
        @Header("Accept-Language") acceptLanguage: String = "ar",
        @Query("lang") lang: String? = "ar"
    ): HomeFeedResponse
}
