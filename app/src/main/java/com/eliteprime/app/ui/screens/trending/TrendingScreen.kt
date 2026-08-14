package com.eliteprime.app.ui.screens.trending

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.components.EyebrowStyle
import androidx.tv.material3.Surface as TvSurface
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Border
import com.eliteprime.app.ui.screens.dashboard.DashboardViewModel
import com.eliteprime.app.ui.screens.dashboard.TrendingItem
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.isTelevisionDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private enum class TrendingTab { MOVIES, SERIES }

// Landscape hero ratio (height relative to width) — the card's height is
// derived from its width using this, so it stays proportionate to the
// backdrop art it displays (which is itself landscape/16:9-ish) rather
// than being sized independently of it.
private const val HERO_ASPECT = 0.5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendingScreen(
    onBack: () -> Unit,
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(TrendingTab.MOVIES) }
    val context = LocalContext.current
    val isTv = context.isTelevisionDevice()

    val firstItemFocusRequester = remember { FocusRequester() }
    LaunchedEffect(uiState.isTrendingLoading, selectedTab) {
        if (isTv && !uiState.isTrendingLoading) {
            delay(500)
            try {
                firstItemFocusRequester.requestFocus()
            } catch (e: Exception) {}
        }
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(modifier = Modifier.background(Color.Black.copy(alpha = 0.4f))) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.home_trending_title), fontWeight = FontWeight.Black, fontSize = 18.sp) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = Color.White
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TrendingTabButton(
                            label = stringResource(R.string.filter_movies),
                            isSelected = selectedTab == TrendingTab.MOVIES,
                            onClick = { selectedTab = TrendingTab.MOVIES }
                        )
                        TrendingTabButton(
                            label = stringResource(R.string.filter_series),
                            isSelected = selectedTab == TrendingTab.SERIES,
                            onClick = { selectedTab = TrendingTab.SERIES }
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isTrendingLoading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AppColors.ArenaOrange)
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.trending_loading), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                    }
                } else {
                    val currentItems = if (selectedTab == TrendingTab.MOVIES) uiState.trendingMovies else uiState.trendingSeries

                    if (currentItems.isEmpty()) {
                        EmptyTrendingView()
                    } else {
                        TrendingCarousel(
                            items = currentItems,
                            firstItemFocusRequester = firstItemFocusRequester,
                            onItemClick = { item ->
                                if (item.localMatch != null) {
                                    if (selectedTab == TrendingTab.MOVIES) onMovieClick(item.localMatch as Movie)
                                    else onSeriesClick(item.localMatch as Series)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendingTabButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val isTv = LocalContext.current.isTelevisionDevice()
    var isFocused by remember { mutableStateOf(false) }
    
    if (isTv) {
        TvSurface(
            onClick = onClick,
            modifier = Modifier.onFocusChanged { isFocused = it.isFocused },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.2f) else Color.Transparent,
                focusedContainerColor = Color.White
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            border = ClickableSurfaceDefaults.border(
                border = if (isSelected && !isFocused) Border(BorderStroke(1.dp, AppColors.ArenaOrange)) else Border.None,
                focusedBorder = Border(BorderStroke(3.dp, Color.White))
            )
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                color = if (isFocused) Color.Black else if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                letterSpacing = 1.2.sp
            )
        }
    } else {
        val alpha by animateFloatAsState(if (isSelected) 1f else 0.5f, label = "tabAlpha")
        Surface(
            onClick = onClick,
            color = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.2f) else Color.Transparent,
            shape = RoundedCornerShape(12.dp),
            border = if (isSelected) BorderStroke(1.dp, AppColors.ArenaOrange) else null
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                color = Color.White.copy(alpha = alpha),
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun TrendingCarousel(
    items: List<TrendingItem<*>>,
    firstItemFocusRequester: FocusRequester,
    onItemClick: (TrendingItem<*>) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    val isTv = LocalContext.current.isTelevisionDevice()

    // Track target page to ensure consistent centering across all directions
    var targetPage by remember { mutableIntStateOf(0) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        // Large, wide hero card — width is the dominant dimension now
        // (most of the screen), with just enough left over on each side
        // for the neighboring card to peek in as a "there's more" cue.
        // Height is derived from that width as a landscape ratio, so the
        // card stays proportionate to the backdrop art it displays rather
        // than being sized independently of it.
        val cardWidth = screenWidth * 0.8f
        val cardHeight = (cardWidth * HERO_ASPECT).coerceAtMost(screenHeight * 0.82f)

        // This padding ensures the focused card is perfectly centered
        val horizontalPadding = (screenWidth - cardWidth) / 2

        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            pageSpacing = 14.dp,
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            beyondViewportPageCount = 2,
            userScrollEnabled = !isTv
        ) { page ->
            val item = items[page]
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue

            TrendingCard(
                item = item,
                onClick = { onItemClick(item) },
                scale = lerp(start = 0.92f, stop = 1f, fraction = 1f - pageOffset.coerceIn(0f, 1f)),
                alpha = lerp(start = 0.5f, stop = 1f, fraction = 1f - pageOffset.coerceIn(0f, 1f)),
                width = cardWidth,
                height = cardHeight,
                modifier = if (page == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                onFocused = {
                    if (isTv) {
                        targetPage = page
                    }
                }
            )
        }
    }

    // Effect to handle TV centering animation - handles both directions (L->R and R->L)
    LaunchedEffect(targetPage) {
        if (isTv && pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }
}

@Composable
private fun TrendingCard(
    item: TrendingItem<*>,
    onClick: () -> Unit,
    scale: Float,
    alpha: Float,
    width: Dp,
    height: Dp,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTv = LocalContext.current.isTelevisionDevice()
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(width)
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            },
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF161921),
        border = BorderStroke(
            width = if (isFocused) 3.dp else 1.dp,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.12f)
        ),
        tonalElevation = 12.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Backdrop art fills the whole card — landscape source image
            // for a landscape card, instead of a boxed-in poster crop.
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.backdropUrl ?: item.posterUrl)
                    .diskCachePolicy(CachePolicy.ENABLED) // ALLOWED for trending
                    .build(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Horizontal scrim: solid enough on the left for the text
            // block to stay readable over any backdrop, fading out toward
            // the right so the image itself stays visible and recognizable.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.92f),
                            0.5f to Color.Black.copy(alpha = 0.6f),
                            1f to Color.Black.copy(alpha = 0.05f)
                        )
                    )
            )
            // Extra bottom scrim so the button/description stay legible
            // even over a bright backdrop.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.6f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.55f)
                        )
                    )
            )

            // All info lives here, over the image — title, metadata,
            // full description, and the watch button, constrained to
            // roughly the left half so the right side of the backdrop
            // stays visible.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.56f)
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp,
                    lineHeight = 30.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = item.releaseDate?.take(4) ?: "N/A",
                        color = Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )

                    // Genre (from TMDB list, fallback to local if matched, else placeholder)
                    val genreText = item.genres.firstOrNull() 
                        ?: when (val match = item.localMatch) {
                            is Movie -> match.genre ?: ""
                            is Series -> match.genre ?: ""
                            else -> ""
                        }.takeIf { it.isNotBlank() } 
                        ?: stringResource(R.string.generic_recommended)

                    Surface(
                        color = AppColors.ArenaOrange.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(7.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = genreText,
                            color = AppColors.ArenaOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(7.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(13.dp))
                            Text("%.1f".format(item.voteAverage), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                Text(
                    text = item.overview,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Button(
                    onClick = onClick,
                    enabled = item.localMatch != null,
                    modifier = Modifier.height(46.dp),
                    contentPadding = PaddingValues(horizontal = 28.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.ArenaOrange,
                        disabledContainerColor = Color.White.copy(alpha = 0.12f)
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (item.localMatch != null) stringResource(R.string.generic_watch_now) else stringResource(R.string.trending_not_in_library),
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTrendingView() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Icon(Icons.Default.Movie, null, tint = Color.White.copy(alpha = 0.1f), modifier = Modifier.size(80.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.trending_empty),
            color = Color.White.copy(alpha = 0.5f),
            fontWeight = FontWeight.Bold
        )
    }
}
