package com.ghost.tv.data.model

import com.google.gson.annotations.SerializedName

data class HomeFeedResponse(
    @SerializedName("version") val version: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("generated_at", alternate = ["generatedAt"]) val generatedAt: String? = null,
    @SerializedName("shelves") val shelves: List<ShelfItem>? = null
)

data class ShelfItem(
    @SerializedName("id") val id: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("display_title", alternate = ["displayTitle"]) val displayTitle: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("sort_order", alternate = ["sortOrder"]) val sortOrder: Int? = null,
    @SerializedName("items") val items: List<EventItem>? = null
)

data class EventItem(
    @SerializedName("id") val id: String? = null,
    @SerializedName("event_mode", alternate = ["eventMode"]) val eventMode: String? = null,
    @SerializedName("image_url", alternate = ["imageUrl"]) val imageUrl: String? = null,
    @SerializedName("competition_background", alternate = ["competitionBackground"]) val competitionBackground: String? = null,
    @SerializedName("competition_logo", alternate = ["competitionLogo"]) val competitionLogo: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("display_title", alternate = ["displayTitle"]) val displayTitle: String? = null,
    @SerializedName("tournament") val tournament: String? = null,
    @SerializedName("display_tournament", alternate = ["displayTournament"]) val displayTournament: String? = null,
    @SerializedName("home_team", alternate = ["homeTeam"]) val homeTeam: TeamItem? = null,
    @SerializedName("away_team", alternate = ["awayTeam"]) val awayTeam: TeamItem? = null,
    @SerializedName("start_time", alternate = ["startTime"]) val startTime: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("streams") val streams: List<StreamItem>? = null
)

data class TeamItem(
    @SerializedName("name") val name: String? = null,
    @SerializedName("display_name", alternate = ["displayName"]) val displayName: String? = null,
    @SerializedName("logo") val logo: String? = null
)

data class StreamItem(
    @SerializedName("id") val id: String? = null,
    @SerializedName("stream_id", alternate = ["streamId"]) val streamId: String? = null,
    @SerializedName("channel_name", alternate = ["channelName"]) val channelName: String? = null,
    @SerializedName("channel_logo", alternate = ["channelLogo"]) val channelLogo: String? = null,
    @SerializedName("label") val label: String? = null,
    @SerializedName("display_label", alternate = ["displayLabel"]) val displayLabel: String? = null,
    @SerializedName("resolution") val resolution: String? = null,
    @SerializedName("primary") val primary: Boolean = false
)
