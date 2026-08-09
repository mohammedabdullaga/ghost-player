package com.eliteprime.app.data.remote

import com.eliteprime.app.data.local.entity.Episode
import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface XtreamApi {
    @GET
    suspend fun authenticate(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String
    ): XtreamAuthResponse

    @GET
    suspend fun getCategories(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String
    ): List<XtreamCategoryResponse>

    @GET
    suspend fun getStreams(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String,
        @Query("category_id") categoryId: String? = null
    ): List<Map<String, Any>>

    @GET
    suspend fun getShortEpg(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_short_epg",
        @Query("stream_id") streamId: Long
    ): XtreamEpgResponse

    @GET
    suspend fun getSeriesInfo(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_info",
        @Query("series_id") seriesId: Long
    ): XtreamSeriesInfoResponse
}

data class XtreamSeriesInfoResponse(
    val info: Map<String, Any>?,
    val episodes: Map<String, List<Episode>>?,
    val seasons: List<XtreamSeasonResponse>?
)

data class XtreamSeasonResponse(
    @SerializedName("season_number") val seasonNumber: Int,
    val name: String?
)

data class XtreamEpgResponse(
    @SerializedName("epg_listings") val listings: List<XtreamEpgListing>?
)

data class XtreamEpgListing(
    val title: String,
    val description: String?,
    @SerializedName("start_timestamp") val startTimestamp: Long,
    @SerializedName("stop_timestamp") val stopTimestamp: Long
)

data class XtreamAuthResponse(
    @SerializedName("user_info") val userInfo: XtreamUserInfo?,
    @SerializedName("server_info") val serverInfo: XtreamServerInfo?
)

data class XtreamUserInfo(
    val username: String,
    val status: String,
    val exp_date: String?,
    val max_connections: String?
)

data class XtreamServerInfo(
    val url: String,
    val port: String,
    val https_port: String?,
    val server_protocol: String?,
    val rtmp_port: String?,
    val timezone: String?
)

data class XtreamCategoryResponse(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("category_name") val categoryName: String,
    @SerializedName("parent_id") val parentId: Int
)
