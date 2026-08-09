package com.eliteprime.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface TmdbApiService {
    @GET("search/movie")
    suspend fun searchMovie(
        @Query("api_key") apiKey: String,
        @Query("query") query: String
    ): TmdbSearchResponse

    @GET("search/tv")
    suspend fun searchTv(
        @Query("api_key") apiKey: String,
        @Query("query") query: String
    ): TmdbSearchResponse

    @GET("trending/movie/week")
    suspend fun getTrendingMovies(
        @Query("api_key") apiKey: String
    ): TmdbTrendingResponse

    @GET("trending/tv/week")
    suspend fun getTrendingTv(
        @Query("api_key") apiKey: String
    ): TmdbTrendingResponse
}

data class TmdbSearchResponse(
    val results: List<TmdbSearchResult>
)

data class TmdbTrendingResponse(
    val results: List<TmdbTrendingResult>
)

data class TmdbTrendingResult(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val backdrop_path: String?,
    val poster_path: String?,
    val genre_ids: List<Int>,
    val release_date: String? = null,
    val first_air_date: String? = null
)

data class TmdbSearchResult(
    val backdrop_path: String?,
    val poster_path: String?,
    val genre_ids: List<Int>
)
