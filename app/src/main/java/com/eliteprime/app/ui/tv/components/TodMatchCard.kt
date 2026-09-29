package com.eliteprime.app.ui.tv.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import coil3.compose.AsyncImage
import com.eliteprime.app.R
import com.eliteprime.app.data.model.EventItem
import com.eliteprime.app.data.model.TeamItem
import java.text.SimpleDateFormat
import java.util.*

private object TodCardColors {
    val FocusBorder = Color(0xFFFFC107) // Bright yellow/white highlight border
    val FrostedBarBg = Color(0xFF070E1E).copy(alpha = 0.65f)
    val FrostedBorder = Color.White.copy(alpha = 0.15f)
    val LiveRed = Color(0xFFE50914)
    val LiveGlow = Color(0x66E50914)
    val ShadowBottom = Color(0xE6000000)
    val ShadowTop = Color(0x99000000)
}

@Composable
fun TodMatchCard(
    event: EventItem,
    modifier: Modifier = Modifier,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        label = "tod_card_scale"
    )

    val backgroundUrl = event.competitionBackground?.ifBlank { null } ?: event.imageUrl
    val isLive = event.status.equals("LIVE", ignoreCase = true) || event.eventMode.equals("LIVE", ignoreCase = true)

    val todayLabel = stringResource(R.string.sports_today)
    val tomorrowLabel = stringResource(R.string.sports_tomorrow)

    val formattedTimeDate = remember(event.startTime, todayLabel, tomorrowLabel) {
        formatEventKickoff(event.startTime, todayLabel, tomorrowLabel)
    }

    TvSurface(
        onClick = onClick,
        modifier = modifier
            .width(260.dp)
            .height(146.dp)
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusChanged?.invoke(it.isFocused)
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, TodCardColors.FocusBorder),
                shape = RoundedCornerShape(10.dp)
            ),
            border = Border(
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(10.dp)
            )
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF10141D),
            focusedContainerColor = Color(0xFF10141D)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
        ) {
            // 1. Full-bleed Artwork Layer
            if (!backgroundUrl.isNullOrBlank()) {
                AsyncImage(
                    model = backgroundUrl,
                    contentDescription = event.title ?: event.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF1A2238), Color(0xFF0B0E17))
                            )
                        )
                )
            }

            // 2. Dark contrast gradient overlays (Top vignette, bottom heavy shadow)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                TodCardColors.ShadowTop,
                                Color.Transparent,
                                TodCardColors.ShadowBottom
                            )
                        )
                    )
            )

            // Live ambient red glow on top corner if LIVE
            if (isLive) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .align(Alignment.TopEnd)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(TodCardColors.LiveGlow, Color.Transparent)
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 3. Top Header Bar (~36.dp height)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        val tournamentLogo = event.competitionLogo?.ifBlank { null }
                        if (!tournamentLogo.isNullOrBlank()) {
                            AsyncImage(
                                model = tournamentLogo,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        val tournamentName = event.displayTournament?.ifBlank { null }
                            ?: event.tournament?.ifBlank { null }
                            ?: event.displayTitle
                            ?: event.title
                            ?: ""
                        if (tournamentName.isNotBlank()) {
                            Text(
                                text = tournamentName,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Status Indicator: LIVE badge vs Kickoff Time/Date Card
                    if (isLive) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(TodCardColors.LiveRed)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.sports_live_badge),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    } else if (formattedTimeDate != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF070E1E).copy(alpha = 0.85f))
                                .border(
                                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.25f)),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = formattedTimeDate.first, // HH:mm (e.g. 21:00)
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 12.sp
                                )
                                if (formattedTimeDate.second.isNotBlank()) {
                                    Text(
                                        text = formattedTimeDate.second, // "اليوم" / "غداً" / "26/05"
                                        color = Color(0xFFFFC107), // Accent Amber/Yellow for high contrast visibility
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        lineHeight = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Team Rows (The Split Frosted Bars)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val home = event.homeTeam
                    val away = event.awayTeam

                    if (home != null) {
                        FrostedTeamRow(team = home)
                    }
                    if (away != null) {
                        FrostedTeamRow(team = away)
                    }
                }
            }
        }
    }
}

@Composable
private fun FrostedTeamRow(team: TeamItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TodCardColors.FrostedBarBg)
            .border(
                border = BorderStroke(0.5.dp, TodCardColors.FrostedBorder),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val crestUrl = team.logo?.ifBlank { null }
            if (!crestUrl.isNullOrBlank()) {
                AsyncImage(
                    model = crestUrl,
                    contentDescription = team.displayName ?: team.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                )
            }

            val teamName = team.displayName?.ifBlank { null } ?: team.name ?: ""
            Text(
                text = teamName,
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatEventKickoff(
    startTime: String?,
    todayStr: String,
    tomorrowStr: String
): Pair<String, String>? {
    if (startTime.isNullOrBlank()) return null
    val raw = startTime.trim()

    val date: Date? = run {
        // 1. Try parsing numeric epoch (seconds or millis)
        val num = raw.toLongOrNull()
        if (num != null) {
            val millis = if (num < 100_000_000_000L) num * 1000L else num
            return@run Date(millis)
        }

        // 2. Try parsing formatted string date patterns (UTC)
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm"
        )

        for (pattern in patterns) {
            try {
                val parser = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = parser.parse(raw)
                if (parsed != null) return@run parsed
            } catch (_: Exception) { }
        }

        // 3. Sanitized fallback
        try {
            val cleanStr = raw.replace("T", " ")
                .replace("Z", "")
                .substringBefore(".")
                .trim()
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            parser.parse(cleanStr)
        } catch (_: Exception) {
            null
        }
    }

    if (date == null) return null

    val localZone = TimeZone.getDefault()
    val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
        timeZone = localZone
    }

    // Check if match date is Today or Tomorrow relative to local device time
    val now = Date()
    val calEvent = Calendar.getInstance(localZone).apply { time = date }
    val calNow = Calendar.getInstance(localZone).apply { time = now }
    val calTomorrow = Calendar.getInstance(localZone).apply {
        time = now
        add(Calendar.DAY_OF_YEAR, 1)
    }

    val isToday = calEvent.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
            calEvent.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)

    val isTomorrow = calEvent.get(Calendar.YEAR) == calTomorrow.get(Calendar.YEAR) &&
            calEvent.get(Calendar.DAY_OF_YEAR) == calTomorrow.get(Calendar.DAY_OF_YEAR)

    val dateStr = when {
        isToday -> todayStr       // "اليوم" in Arabic / "Today" in English
        isTomorrow -> tomorrowStr // "غداً" in Arabic / "Tomorrow" in English
        else -> {
            SimpleDateFormat("dd/MM", Locale.getDefault()).apply {
                timeZone = localZone
            }.format(date)
        }
    }

    return timeFormatter.format(date) to dateStr
}
