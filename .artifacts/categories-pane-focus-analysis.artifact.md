# Categories Pane Focus Analysis & Enhancement

## Current Architecture

Both `TvMovieScreen.kt` and `TvSeriesScreen.kt` share the same pattern:

```
Row (fillMaxSize)
├── Column (sidebar, weight=sidebarWeight, alpha=hidden?0:1)
│   └── LazyColumn (categories)
│       └── TvCategoryItem × N
└── Column (grid, weight=gridWeight)
    └── LazyVerticalGrid
        └── VodPosterCard / SeriesPosterCard × N
```

**Key state:**
- `isSidebarHidden` → controls animation (`0f` weight when hidden, `0.28f` when open)
- `isGridFocused` → tracks if focus is in grid
- `lastSidebarFocusKey` → remembers which category had focus last

---

## Root Cause: Why Focus Gets "Stuck"

There are **4 interrelated issues** that together cause the intermittent stuck state:

### Issue 1 — `SIDEBAR_WEIGHT_CLOSED = 0f` causes LazyColumn item decomposition

When the sidebar is hidden, its Column has `weight(0f + 0.001f)` ≈ 0 px. The inner `LazyColumn` sees a zero-size viewport and **recycles/decomposes** all child items. This means every `categoryFocusRequesters[id]` becomes **unattached** — calling `requestFocus()` on them produces the `FocusRequester is not initialized` warning and silently fails.

```
SIDEBAR_WEIGHT_CLOSED = 0f  →  Column width ≈ 0  →  LazyColumn items not composed
```

### Issue 2 — `BackHandler` guard blocks retries

```kotlin
BackHandler {
    if (isGridFocused) {          // ← guard
        returnFocusToSidebar()    // ← sets isGridFocused = false immediately
    } else {
        onBack()
    }
}
```

First press: `isGridFocused=true` → calls `returnFocusToSidebar()` → sets `isGridFocused=false` → 300ms coroutine starts.
Second press: `isGridFocused=false` → `onBack()` fires instead! User is thrown out of the screen.

### Issue 3 — `returnFocusToSidebar()` has no retry, no scroll-to-item

```kotlin
val returnFocusToSidebar: () -> Unit = {
    isGridFocused = false
    isSidebarHidden = false
    scope.launch {
        delay(300)
        try {
            requester.requestFocus()   // ← single attempt, no retry
        } catch (_: Exception) {}      // ← silent failure, focus stays on grid
    }
}
```

If the 300ms delay isn't enough for the LazyColumn to recompose items at the scrolled-to position, focus silently fails.

### Issue 4 — No `focusProperties` on grid items

The Compose focus system has no idea there's a leftward navigation path from the grid. Without `focusProperties`, even if the sidebar were composed, the system wouldn't know where to send focus.

---

## Proposed Fix

### Approach: Hybrid — `focusProperties` as primary path + manual fallback

The fix uses `focusProperties` on grid items pointing to an **always-composed** sidebar container `FocusRequester`. When Left is pressed, we let the focus system move focus natively (via `focusProperties`) while also having a manual fallback that works even if the system path fails.

### Changes (both `TvMovieScreen.kt` and `TvSeriesScreen.kt`)

#### 1. Add `sidebarContainerRequester` + `sidebarListState`

```kotlin
val sidebarContainerRequester = remember { FocusRequester() }
val sidebarListState = rememberLazyListState()
```

`sidebarContainerRequester` goes on the **Column** (always composed, even with 0 weight) — not on the LazyColumn.

#### 2. Keep `SIDEBAR_WEIGHT_CLOSED = 0f` and use `sizeIn` minimum

Instead of changing the weight constant, add `Modifier.widthIn(min = 1.dp)` to the sidebar Column so LazyColumn always has a non-zero viewport:

```kotlin
Column(
    modifier = Modifier
        .weight(sidebarWeight + 0.001f)
        .widthIn(min = 1.dp)          // ← always non-zero viewport
        .fillMaxHeight()
        .focusRequester(sidebarContainerRequester)  // ← always attached
        ...
)
```

Wait — `widthIn` won't work with `weight`. The weight system will override it. Let me think...

Actually the better approach: change `SIDEBAR_WEIGHT_CLOSED` to a tiny non-zero value like `0.001f`, and use `alpha(0f)` for visual hiding. This keeps items composed.

#### 3. Add `focusProperties` on grid items

```kotlin
.focusProperties {
    if (isStartColumn) {
        if (isRtl) this.right = sidebarContainerRequester
        else this.left = sidebarContainerRequester
    }
}
```

#### 4. Rewrite `returnFocusToSidebar` — no guard, scroll + retry

```kotlin
val returnFocusToSidebar: () -> Unit = {
    isGridFocused = false
    isSidebarHidden = false
    scope.launch {
        // Scroll to the remembered category
        val key = lastSidebarFocusKey
        val index = uiState.categories.indexOfFirst { it.id == key }
        if (index >= 0) sidebarListState.scrollToItem(index)

        // Wait for sidebar animation (220ms) + layout pass
        delay(RETURN_FOCUS_DELAY_MS)
        try {
            (categoryFocusRequesters[key] ?: firstCategoryRequester).requestFocus()
        } catch (_: Exception) {
            delay(100)
            try {
                (categoryFocusRequesters[key] ?: firstCategoryRequester).requestFocus()
            } catch (_: Exception) {
                sidebarContainerRequester.requestFocus()
            }
        }
    }
}
```

#### 5. Fix `BackHandler` — use `isSidebarHidden || isGridFocused`

```kotlin
BackHandler {
    if (isSidebarHidden || isGridFocused) {
        returnFocusToSidebar()
    } else {
        onBack()
    }
}
```

This allows retry presses since `isSidebarHidden` remains `false` until `returnFocusToSidebar` actually sets it.

#### 6. Remove `isSidebarHidden = true` from grid item `onFocusChanged`

This causes redundant state writes when navigating between grid items. Only set it when entering the grid.

### Complete diff summary

```
SIDEBAR_WEIGHT_CLOSED:  0f → 0.001f

ADD:  sidebarContainerRequester, sidebarListState
MOD:  Sidebar Column gets focusRequester(sidebarContainerRequester)
MOD:  LazyColumn gets state = sidebarListState
ADD:  Grid items get focusProperties redirecting to sidebarContainerRequester
MOD:  returnFocusToSidebar: remove guard, add scrollToItem, add retry
MOD:  BackHandler guard: isGridFocused → isSidebarHidden || isGridFocused
DEL:  isSidebarHidden = true from grid item onFocusChanged
```

---

## Flow After Fix

```
User presses Left on grid item (start column)
  ↓
Option A (focusProperties path — when sidebar is composed):
  focusProperties: this.left = sidebarContainerRequester
  → focus moves to sidebar Column container
  → D-pad Right on container → first LazyColumn item
  ✓

Option B (manual path — fallback):
  onPreviewKeyEvent: consume key → returnFocusToSidebar()
  → isSidebarHidden = false, isGridFocused = false
  → delay(340ms)
  → requestFocus(category requester) — now attached because viewport exists
  ✓

BackHandler retry (if first attempt fails):
  isSidebarHidden || isGridFocused → returnFocusToSidebar() again
  ✓
```