package com.ghost.tv.data.model

import com.google.gson.annotations.SerializedName

data class HomeFeedResponse(
    @SerializedName("version") val version: Any? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("generated_at", alternate = ["generatedAt"]) val generatedAt: String? = null,
    @SerializedName("shelves") val shelves: List<ShelfItem>? = null
)

data class ShelfItem(
    @SerializedName("id") val id: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("title_ar", alternate = ["titleAr"]) val titleAr: String? = null,
    @SerializedName("display_title", alternate = ["displayTitle"]) val displayTitle: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("sort_order", alternate = ["sortOrder"]) val sortOrder: Int? = null,
    @SerializedName("items") val items: List<EventItem>? = null
) {
    val resolvedTitle: String
        get() = displayTitle?.ifBlank { null } ?: titleAr?.ifBlank { null } ?: title.orEmpty()
}

data class EventItem(
    @SerializedName("id") val idRaw: Any? = null,
    @SerializedName("event_mode", alternate = ["eventMode"]) val eventMode: String? = null,
    @SerializedName("image_url", alternate = ["imageUrl"]) val imageUrl: String? = null,
    @SerializedName("competition_bg", alternate = ["competitionBg"]) val competitionBg: String? = null,
    @SerializedName("competition_background", alternate = ["competitionBackground"]) val competitionBackground: String? = null,
    @SerializedName("competition_logo", alternate = ["competitionLogo"]) val competitionLogo: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("title_ar", alternate = ["titleAr"]) val titleAr: String? = null,
    @SerializedName("display_title", alternate = ["displayTitle"]) val displayTitle: String? = null,
    @SerializedName("tournament") val tournament: String? = null,
    @SerializedName("tournament_ar", alternate = ["tournamentAr"]) val tournamentAr: String? = null,
    @SerializedName("display_tournament", alternate = ["displayTournament"]) val displayTournament: String? = null,
    @SerializedName("sport") val sport: String? = null,
    @SerializedName("venue") val venue: String? = null,
    @SerializedName("venue_ar", alternate = ["venueAr"]) val venueAr: String? = null,
    @SerializedName("display_venue", alternate = ["displayVenue"]) val displayVenue: String? = null,
    @SerializedName("home_team", alternate = ["homeTeam"]) val homeTeam: TeamItem? = null,
    @SerializedName("away_team", alternate = ["awayTeam"]) val awayTeam: TeamItem? = null,
    @SerializedName("start_time", alternate = ["startTime"]) val startTime: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("streams") val streams: List<StreamItem>? = null
) {
    val id: String
        get() = idRaw?.toString().orEmpty()

    val resolvedBackgroundUrl: String?
        get() = competitionBackground?.ifBlank { null }
            ?: competitionBg?.ifBlank { null }
            ?: imageUrl?.ifBlank { null }

    val resolvedTournamentName: String
        get() = displayTournament?.ifBlank { null }
            ?: tournamentAr?.ifBlank { null }
            ?: tournament?.ifBlank { null }
            ?: displayTitle?.ifBlank { null }
            ?: titleAr?.ifBlank { null }
            ?: title.orEmpty()
}

data class TeamItem(
    @SerializedName("name") val name: String? = null,
    @SerializedName("name_ar", alternate = ["nameAr"]) val nameAr: String? = null,
    @SerializedName("display_name", alternate = ["displayName"]) val displayName: String? = null,
    @SerializedName("logo") val logo: String? = null
) {
    val resolvedName: String
        get() = displayName?.ifBlank { null } ?: nameAr?.ifBlank { null } ?: name.orEmpty()
}

data class StreamItem(
    @SerializedName("id") val idRaw: Any? = null,
    @SerializedName("stream_id", alternate = ["streamId"]) val streamIdRaw: Any? = null,
    @SerializedName("channel_name", alternate = ["channelName"]) val channelName: String? = null,
    @SerializedName("channel_logo", alternate = ["channelLogo"]) val channelLogo: String? = null,
    @SerializedName("label") val label: String? = null,
    @SerializedName("label_ar", alternate = ["labelAr"]) val labelAr: String? = null,
    @SerializedName("display_label", alternate = ["displayLabel"]) val displayLabel: String? = null,
    @SerializedName("resolution") val resolution: String? = null,
    @SerializedName("primary") val primary: Boolean = false,
    @SerializedName("channel") val channel: String? = null
) {
    val id: String
        get() = idRaw?.toString().orEmpty()

    val streamId: String
        get() = streamIdRaw?.toString().orEmpty().ifBlank { id }

    val resolvedChannelName: String
        get() = displayLabel?.ifBlank { null }
            ?: labelAr?.ifBlank { null }
            ?: label?.ifBlank { null }
            ?: channelName?.ifBlank { null }
            ?: channel.orEmpty()
}
