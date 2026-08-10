package com.eliteprime.app.ui.screens.details

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import coil3.compose.AsyncImage
import com.eliteprime.app.data.local.entity.Episode
import com.eliteprime.app.data.local.entity.Season
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.ui.components.*
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.isTelevisionDevice

private enum class SeriesDetailTab { EPISODES, RELATED }

@Composable
fun SeriesDetailsScreen(
    onBack: () -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onSeriesClick: (Series) -> Unit = {},
    viewModel: SeriesDetailsViewModel = hiltViewModel()
) {
    val seriesState by viewModel.series.collectAsState()
    val series = seriesState
    val seasons by viewModel.seasons.collectAsState()
    val selectedSeason by viewModel.selectedSeason.collectAsState()
    val relatedSeries by viewModel.relatedSeries.collectAsState()
    val isTv = LocalContext.current.isTelevisionDevice()
    
    var currentTab by remember { mutableStateOf(SeriesDetailTab.EPISODES) }

    if (series == null) {
        Box(modifier = Modifier.fillMaxSize().background(DetailSurface.backdrop), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AppColors.ArenaOrange, strokeWidth = 2.5.dp)
        }
        return
    }

    AppBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            // Cinematic Backdrop
            AsyncImage(
                model = series.backdropUrl ?: series.cover,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.5f
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        listOf(DetailSurface.backdrop, DetailSurface.backdrop.copy(alpha = 0.75f), DetailSurface.backdrop.copy(alpha = 0.95f))
                    )
                )
            )

            Row(modifier = Modifier.fillMaxSize()) {
                // Left Panel: Poster & Back
                PosterPanel(
                    posterUrl = series.cover ?: series.backdropUrl, 
                    onBack = onBack, 
                    modifier = Modifier.weight(0.38f),
                    isTv = isTv
                )

                // Right Panel: Info & Episodes
                LazyColumn(
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxHeight()
                        .padding(start = DetailSpacing.md, end = DetailSpacing.xl, top = DetailSpacing.xl, bottom = DetailSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(DetailSpacing.sm)
                ) {
                    item {
                        InfoCard {
                            Text(
                                text = series.genre?.uppercase() ?: "SERIES", 
                                style = EyebrowStyle, 
                                color = AppColors.ArenaOrange
                            )

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                Text(
                                    text = series.name,
                                    color = DetailSurface.textPrimary,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    lineHeight = 28.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                DetailFavButton(
                                    isFavorite = series.isFavorite,
                                    onClick = { viewModel.toggleFavorite() },
                                    isTv = isTv
                                )
                            }

                            TagRow(genre = null, rating = series.rating)
                            
                            if (!series.plot.isNullOrBlank()) {
                                Text(
                                    text = series.plot!!,
                                    color = DetailSurface.textSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            MetadataGrid(
                                listOfNotNull(
                                    series.releaseDate?.take(4)?.let { "YEAR" to it },
                                    series.director?.takeIf { it.isNotBlank() }?.let { "DIRECTOR" to it },
                                    series.cast?.takeIf { it.isNotBlank() }?.let { "CAST" to it }
                                )
                            )
                        }
                    }

                    item {
                        DetailTabSection(
                            seasons = seasons,
                            selectedSeason = selectedSeason,
                            onSeasonSelected = { viewModel.selectSeason(it) },
                            currentTab = currentTab,
                            onTabChange = { currentTab = it },
                            isTv = isTv
                        )
                    }

                    if (currentTab == SeriesDetailTab.EPISODES) {
                        selectedSeason?.let { season ->
                            itemsIndexed(season.episodes) { index, episode ->
                                EpisodeRow(
                                    episode = episode,
                                    index = index + 1,
                                    fallbackImageUrl = series.cover ?: series.backdropUrl,
                                    onClick = { onPlayEpisode(episode) },
                                    isTv = isTv
                                )
                            }
                        }
                    } else {
                        // More Like This Grid
                        if (relatedSeries.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                    Text("No related series found", color = Color.White.copy(alpha = 0.5f))
                                }
                            }
                        } else {
                            item {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(20.dp),
                                    maxItemsInEachRow = if (isTv) 5 else 3
                                ) {
                                    relatedSeries.forEach { related ->
                                        RelatedPosterCard(
                                            imageUrl = related.cover ?: related.backdropUrl,
                                            title = related.name,
                                            onClick = { onSeriesClick(related) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailTabSection(
    seasons: List<Season>,
    selectedSeason: Season?,
    onSeasonSelected: (Season) -> Unit,
    currentTab: SeriesDetailTab,
    onTabChange: (SeriesDetailTab) -> Unit,
    isTv: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- 1. Episodes Tab ---
        Box(modifier = Modifier.wrapContentSize()) {
            var expanded by remember { mutableStateOf(false) }
            val isActive = currentTab == SeriesDetailTab.EPISODES
            
            val content = @Composable {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isActive && selectedSeason != null) selectedSeason.name else "EPISODES",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                    Icon(Icons.Default.ArrowDropDown, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            if (isTv) {
                TvSurface(
                    onClick = { 
                        if (isActive) expanded = true
                        else onTabChange(SeriesDetailTab.EPISODES)
                    },
                    modifier = Modifier.height(42.dp),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isActive) AppColors.FocusBrown.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.08f),
                        focusedContainerColor = AppColors.FocusBrown
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
                ) {
                    content()
                }
            } else {
                Surface(
                    onClick = { 
                        if (isActive) expanded = true
                        else onTabChange(SeriesDetailTab.EPISODES)
                    },
                    color = if (isActive) AppColors.ArenaOrange else Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    content()
                }
            }

            DropdownMenu(
                expanded = expanded, 
                onDismissRequest = { expanded = false }, 
                modifier = Modifier.background(Color(0xFF14171C))
            ) {
                seasons.forEach { season ->
                    DropdownMenuItem(
                        text = { Text(season.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                        onClick = { 
                            onSeasonSelected(season)
                            onTabChange(SeriesDetailTab.EPISODES)
                            expanded = false 
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // --- 2. More Like This Tab ---
        val isRelatedActive = currentTab == SeriesDetailTab.RELATED
        if (isTv) {
            TvSurface(
                onClick = { onTabChange(SeriesDetailTab.RELATED) },
                modifier = Modifier.height(42.dp),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isRelatedActive) AppColors.FocusBrown.copy(alpha = 0.9f) else Color.Transparent,
                    focusedContainerColor = AppColors.FocusBrown
                ),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
            ) {
                Box(modifier = Modifier.padding(horizontal = 16.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text("MORE LIKE THIS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }
        } else {
            Surface(
                onClick = { onTabChange(SeriesDetailTab.RELATED) },
                modifier = Modifier.height(42.dp),
                color = if (isRelatedActive) AppColors.ArenaOrange else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (isRelatedActive) AppColors.ArenaOrange else Color.White.copy(alpha = 0.1f))
            ) {
                Box(modifier = Modifier.padding(horizontal = 16.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text("MORE LIKE THIS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }
        }

        // --- 3. Episode Count (Fixed Horizontal Alignment) ---
        if (currentTab == SeriesDetailTab.EPISODES && selectedSeason != null) {
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(), 
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = "${selectedSeason.episodes.size} EPISODES",
                    color = AppColors.ArenaOrange,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PosterPanel(posterUrl: String?, onBack: () -> Unit, modifier: Modifier = Modifier, isTv: Boolean) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(DetailSpacing.md)
            .clip(RoundedCornerShape(DetailRadius.xl))
            .border(BorderStroke(1.dp, DetailSurface.hairline), RoundedCornerShape(DetailRadius.xl))
    ) {
        AsyncImage(
            model = posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.85f)
                )
            )
        )
        
        if (isTv) {
            TvSurface(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(DetailSpacing.md).size(40.dp),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Black.copy(alpha = 0.45f),
                    focusedContainerColor = AppColors.FocusBrown.copy(alpha = 0.8f)
                ),
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(BorderStroke(2.dp, Color.White))
                )
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        } else {
            MobileBackButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(DetailSpacing.md))
        }
    }
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = DetailSurface.cardFill,
        shape = RoundedCornerShape(DetailRadius.lg),
        border = BorderStroke(1.dp, DetailSurface.hairline)
    ) {
        Column(
            modifier = Modifier.padding(DetailSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DetailSpacing.md),
            content = content
        )
    }
}

@Composable
private fun DetailHeader(kicker: String, title: String, isFavorite: Boolean, onToggleFavorite: () -> Unit) {
    // Shared handled locally
}

@Composable
private fun DetailFavButton(isFavorite: Boolean, onClick: () -> Unit, isTv: Boolean) {
    if (isTv) {
        TvSurface(
            onClick = onClick,
            modifier = Modifier.size(44.dp),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.1f),
                focusedContainerColor = AppColors.FocusBrown.copy(alpha = 0.8f)
            ),
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White))
            )
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isFavorite) AppColors.ArenaOrange else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(40.dp)
                .background(DetailSurface.cardFillElevated, CircleShape)
                .border(BorderStroke(1.dp, DetailSurface.hairline), CircleShape)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = if (isFavorite) AppColors.ArenaOrange else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun TagRow(genre: String?, rating: Double) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DetailSpacing.xs)) {
        if (rating > 0) {
            Surface(
                color = DetailSurface.cardFillElevated,
                shape = RoundedCornerShape(DetailRadius.sm),
                border = BorderStroke(1.dp, DetailSurface.hairline)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = DetailSpacing.sm, vertical = DetailSpacing.xxs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Star, null, tint = AppColors.ArenaOrange, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(text = "%.1f".format(rating), color = DetailSurface.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MetadataGrid(items: List<Pair<String, String>>) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(DetailSpacing.xs)) {
        items.chunked(2).forEachIndexed { rowIndex, rowItems ->
            if (rowIndex > 0) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DetailSurface.hairline))
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                rowItems.forEach { (label, value) ->
                    Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                        Text(text = label, color = DetailSurface.textTertiary, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 0.6.sp)
                        Text(text = value, color = DetailSurface.textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    index: Int,
    fallbackImageUrl: String?,
    onClick: () -> Unit,
    isTv: Boolean
) {
    if (isTv) {
        TvSurface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = DetailSurface.cardFill,
                focusedContainerColor = AppColors.FocusBrown.copy(alpha = 0.8f)
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(DetailRadius.md)),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White))
            )
        ) {
            EpisodeRowContent(episode, index, fallbackImageUrl)
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = RoundedCornerShape(DetailRadius.md),
            color = DetailSurface.cardFill,
            border = BorderStroke(1.dp, DetailSurface.hairline)
        ) {
            EpisodeRowContent(episode, index, fallbackImageUrl)
        }
    }
}

@Composable
private fun EpisodeRowContent(episode: Episode, index: Int, fallbackImageUrl: String?) {
    Row(modifier = Modifier.fillMaxSize().padding(DetailSpacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DetailSpacing.sm)) {
        Box(modifier = Modifier.fillMaxHeight().aspectRatio(1.6f).clip(RoundedCornerShape(DetailRadius.sm)).background(Color.Black)) {
            AsyncImage(
                model = episode.coverUrl ?: fallbackImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(bottomEnd = DetailRadius.sm),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = index.toString(),
                    color = DetailSurface.textPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "E${episode.episodeNumber} · ${episode.title}",
                color = DetailSurface.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!episode.duration.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(text = episode.duration!!, color = DetailSurface.textTertiary, fontSize = 11.sp)
            }
        }
        Surface(onClick = {}, modifier = Modifier.size(32.dp), shape = CircleShape, color = AppColors.ArenaOrange) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun RelatedPosterCard(imageUrl: String?, title: String, onClick: () -> Unit) {
    val isTv = LocalContext.current.isTelevisionDevice()
    Column(
        modifier = Modifier.width(110.dp),
        verticalArrangement = Arrangement.spacedBy(DetailSpacing.xxs)
    ) {
        if (isTv) {
            TvSurface(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(DetailRadius.md)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Black.copy(alpha = 0.2f),
                    focusedContainerColor = AppColors.FocusBrown.copy(alpha = 0.8f)
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(BorderStroke(2.dp, Color.White))
                )
            ) {
                AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        } else {
            Surface(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                shape = RoundedCornerShape(DetailRadius.md),
                color = DetailSurface.cardFill,
                border = BorderStroke(1.dp, DetailSurface.hairline)
            ) {
                AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Text(text = title, color = DetailSurface.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
