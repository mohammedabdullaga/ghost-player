package com.eliteprime.app.ui.util

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusRequester
import com.eliteprime.app.data.local.entity.Category
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shared focus restoration utility for TV navigation.
 *
 * Call this once per screen that needs to restore focus when returning from
 * another screen (e.g. fullscreen player, detail screen). Focus position state
 * must live in the ViewModel so it survives navigation.
 *
 * ## Overload guide
 * | Screen             | Category scroll         | Item scroll       |
 * |--------------------|-------------------------|-------------------|
 * | TvLiveTvScreen     | categoryListState       | channelListState  |
 * | TvMovieScreen / TvSeriesScreen | sidebarListState  | gridState         |
 * | MobileLiveTvScreen | null                    | channelListState  |
 *
 * @param trigger When `true`, the focus restoration logic runs. Use this to
 *                fire restoration only when exiting fullscreen player: pass
 *                `globalMode != PlayerViewMode.FULLSCREEN`.
 */

// ── LazyListState overload (channels, sidebar items) ──────────────────────

@Composable
fun FocusRestoration(
    targetCategoryId: Long?,
    targetItemId: Long?,
    selectedCategory: Category?,
    onSelectCategory: (Long) -> Unit,
    categoryListState: LazyListState?,
    itemListState: LazyListState,
    categoryFocusRequesters: Map<Long, FocusRequester>,
    itemFocusRequesters: Map<Long, FocusRequester>,
    findItemIndex: (Long) -> Int,
    trigger: Boolean = true,
) {
    val scope = rememberCoroutineScope()

    LaunchedEffect(trigger) {
        if (!trigger) return@LaunchedEffect
        if (targetItemId != null || targetCategoryId != null) {
            // Capture itemId before category switch which may clear it
            val capturedItemId = targetItemId
            scope.launch {
                // 1. Switch category if needed
                if (targetCategoryId != null && targetCategoryId != selectedCategory?.id) {
                    onSelectCategory(targetCategoryId)
                    delay(50)
                }

                // Also scroll/focus the sidebar to the restored category
                if (categoryListState != null && targetCategoryId != null) {
                    categoryFocusRequesters[targetCategoryId]?.requestFocus()
                }

                // 2. Scroll to and focus the target item
                if (capturedItemId != null) {
                    // Retry loop: the item list may not have updated yet
                    var index = -1
                    repeat(15) {
                        index = findItemIndex(capturedItemId)
                        if (index >= 0) return@repeat
                        delay(50)
                    }
                    if (index >= 0) {
                        if (itemListState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                            itemListState.scrollToItem(index)
                        }
                        delay(50)
                        itemFocusRequesters[capturedItemId]?.requestFocus()
                    }
                }
            }
        }
    }
}

// ── LazyGridState overload (movie / series grids) ─────────────────────────

@Composable
fun FocusRestoration(
    targetCategoryId: Long?,
    targetItemId: Long?,
    selectedCategory: Category?,
    onSelectCategory: (Long) -> Unit,
    categoryListState: LazyListState?,
    gridState: LazyGridState,
    categoryFocusRequesters: Map<Long, FocusRequester>,
    itemFocusRequesters: Map<Long, FocusRequester>,
    findItemIndex: (Long) -> Int,
    trigger: Boolean = true,
) {
    val scope = rememberCoroutineScope()

    LaunchedEffect(trigger) {
        if (!trigger) return@LaunchedEffect
        if (targetItemId != null || targetCategoryId != null) {
            // Capture itemId before category switch which may clear it
            val capturedItemId = targetItemId
            scope.launch {
                // 1. Switch category if needed
                if (targetCategoryId != null && targetCategoryId != selectedCategory?.id) {
                    onSelectCategory(targetCategoryId)
                    delay(50)
                }

                // Also scroll/focus the sidebar to the restored category
                if (categoryListState != null && targetCategoryId != null) {
                    categoryFocusRequesters[targetCategoryId]?.requestFocus()
                }

                // 2. Scroll to and focus the target item
                if (capturedItemId != null) {
                    // Retry loop: the item list may not have updated yet
                    var index = -1
                    repeat(15) {
                        index = findItemIndex(capturedItemId)
                        if (index >= 0) return@repeat
                        delay(50)
                    }
                    if (index >= 0) {
                        if (gridState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                            gridState.scrollToItem(index)
                        }
                        delay(50)
                        itemFocusRequesters[capturedItemId]?.requestFocus()
                    }
                }
            }
        }
    }
}