package com.ghost.tv.ui.screens.live

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import android.util.Log
import com.ghost.tv.R
import com.ghost.tv.data.local.entity.*
import com.ghost.tv.data.remote.XtreamUrlFactory
import com.ghost.tv.data.repository.PreferenceRepository
import com.ghost.tv.data.repository.XtreamRepository
import com.ghost.tv.domain.model.StreamInfo
import com.ghost.tv.domain.model.StreamType
import com.ghost.tv.player.GlobalPlayerState
import com.ghost.tv.player.PlayerEngine
import com.ghost.tv.player.PlayerViewMode
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace
import dagger.hilt.android.lifecycle.HiltViewModel
import io.objectbox.BoxStore
import io.objectbox.kotlin.flow
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Collections
import javax.inject.Inject

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val boxStore: BoxStore,
    val playerEngine: PlayerEngine,
    val globalPlayerState: GlobalPlayerState,
    private val xtreamRepository: XtreamRepository,
    private val preferenceRepository: PreferenceRepository,
    private val application: Application,
    private val savedHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        const val CAT_ID_FAVORITES = -1L
        const val CAT_ID_RECENT = -2L
    }

    private val categoryBox = boxStore.boxFor(Category::class.java)
    private val liveBox = boxStore.boxFor(LiveStream::class.java)
    private val epgBox = boxStore.boxFor(EPGProgram::class.java)
    private val customGroupBox = boxStore.boxFor(CustomGroup::class.java)
    private val groupMembershipBox = boxStore.boxFor(GroupMembership::class.java)

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    private val _searchQuery = MutableStateFlow(savedHandle["search"] ?: "")
    private val _page = MutableStateFlow(0)

    private val _previewChannel = MutableStateFlow<LiveStream?>(null)
    val previewChannel = _previewChannel.asStateFlow()
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isReorderMode = MutableStateFlow(false)
    val isReorderMode = _isReorderMode.asStateFlow()

    private val _isChannelReorderMode = MutableStateFlow(false)
    val isChannelReorderMode = _isChannelReorderMode.asStateFlow()

    private val _reorderCategories = MutableStateFlow<List<Category>>(emptyList())
    private val _reorderChannels = MutableStateFlow<List<LiveStream>>(emptyList())

    // True lazy: counts are loaded per-category only when item scrolls into view
    private val _categoryCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    private val loadedCountIds = mutableSetOf<Long>()

    // Focus position state — backed by SavedStateHandle so focus survives
    // ViewModel destruction in Lite mode (hub-and-spoke popUpTo)
    private val _lastFocusedCategoryId = MutableStateFlow<Long?>(savedHandle["focusCat"])
    private val _lastFocusedItemId = MutableStateFlow<Long?>(savedHandle["focusItem"])

    private val _selectedReorderCategoryIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedReorderCategoryIds = _selectedReorderCategoryIds.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val categoriesFlow = combine(
        categoryBox.query().build().flow(),
        customGroupBox.query().build().flow()
    ) { providerCats, customGroups ->
        val virtualCats = listOf(
            Category(id = CAT_ID_FAVORITES, categoryName = application.getString(R.string.nav_favorites), isVirtual = true),
            Category(id = CAT_ID_RECENT, categoryName = application.getString(R.string.nav_recent), isVirtual = true)
        )
        val customGroupCats = customGroups
            .filter { it.groupType == "live" }
            .map { cg ->
                Category(id = cg.id + 1000000, categoryName = cg.name, isVirtual = true, order = cg.groupOrder)
            }
        (virtualCats + customGroupCats + providerCats.filter { it.type == "live" && !it.isHidden }).sortedBy { it.order }
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    // Debounce only non-empty queries; empty (initial/default) emits immediately
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val debouncedSearchQuery = _searchQuery
        .flatMapLatest { query ->
            if (query.isEmpty()) flowOf(query)
            else flowOf(query).debounce(300)
        }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val filteredChannelsFlow = combine(
        _selectedCategoryId,
        debouncedSearchQuery,
        categoriesFlow,
        liveBox.query().build().flow()
    ) { selectedId, query, allCategories, _ ->
        val currentId = selectedId 
            ?: allCategories.find { it.id == CAT_ID_FAVORITES }?.id 
            ?: allCategories.firstOrNull()?.id
            ?: return@combine emptyList<LiveStream>()

        val currentSelected = allCategories.find { it.id == currentId } ?: allCategories.firstOrNull() ?: return@combine emptyList<LiveStream>()
        if (query.isNotEmpty()) {
            runCatching {
                liveBox.query()
                    .contains(LiveStream_.name, query, StringOrder.CASE_INSENSITIVE)
                    .order(LiveStream_.order)
                    .build()
                    .use { it.find() }
                    .filter { !it.isHidden }
            }.getOrDefault(emptyList())
        } else {
            val baseChannels = runCatching {
                when (currentId) {
                    CAT_ID_FAVORITES -> {
                        val favs = liveBox.query()
                            .equal(LiveStream_.isFavorite, true)
                            .order(LiveStream_.order)
                            .build().use { it.find() }
                        if (favs.isEmpty() && selectedId == null) {
                            val fallback = allCategories.firstOrNull { !it.isVirtual }
                            if (fallback != null) {
                                liveBox.query()
                                    .equal(LiveStream_.remoteCategoryId, fallback.categoryId, StringOrder.CASE_INSENSITIVE)
                                    .order(LiveStream_.order)
                                    .build().use { it.find() }
                            } else favs
                        } else favs
                    }
                    CAT_ID_RECENT -> {
                        liveBox.query()
                            .greater(LiveStream_.lastWatchedAt, 0L)
                            .orderDesc(LiveStream_.lastWatchedAt)
                            .build()
                            .use { it.find(0, 100) }
                    }
                    else -> {
                        if (currentId >= 1000000) {
                            val realGroupId = currentId - 1000000
                            val memberships = groupMembershipBox.query()
                                .equal(GroupMembership_.groupId, realGroupId)
                                .order(GroupMembership_.order)
                                .build().use { it.find() }
                            val streamIds = memberships.map { it.streamId }
                            liveBox.get(streamIds).filterNotNull()
                        } else {
                            liveBox.query()
                                .equal(LiveStream_.remoteCategoryId, currentSelected.categoryId, StringOrder.CASE_INSENSITIVE)
                                .order(LiveStream_.order)
                                .build().use { it.find() }
                        }
                    }
                }
            }.getOrDefault(emptyList())
            baseChannels.filter { !it.isHidden }
        }
    }.flowOn(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LiveUiState> = combine(
        categoriesFlow,
        _selectedCategoryId,
        filteredChannelsFlow,
        _categoryCounts,
        combine(_isReorderMode, _reorderCategories, _isChannelReorderMode, _reorderChannels) { a, b, c, d -> Quad(a, b, c, d) }
    ) { allCategories, selectedId, channels, counts, quad ->
        if (channels.isNotEmpty() && browsingTrace != null) {
            browsingTrace?.stop()
            browsingTrace = null
        }
        val (reorderMode, reorderList, channelReorderMode, channelReorderList) = quad
        
        val categories = if (reorderMode) reorderList else allCategories
        val currentId = selectedId 
            ?: allCategories.find { it.id == CAT_ID_FAVORITES }?.id 
            ?: allCategories.firstOrNull()?.id
            ?: 0L
            
        val currentSelected = categories.find { it.id == currentId } ?: categories.firstOrNull()

        LiveUiState(
            categories = categories,
            categoryCounts = counts,
            selectedCategory = currentSelected,
            filteredChannels = if (channelReorderMode) channelReorderList else (if (reorderMode) emptyList() else channels),
            isLoading = false,
            isCategoryReorderMode = reorderMode,
            isChannelReorderMode = channelReorderMode
        )
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LiveUiState(isLoading = true))

    // Focus position state — survives navigation because it lives in the ViewModel
    val lastFocusedCategoryId: StateFlow<Long?> = _lastFocusedCategoryId.asStateFlow()
    val lastFocusedItemId: StateFlow<Long?> = _lastFocusedItemId.asStateFlow()

    fun recordFocusedCategory(categoryId: Long) {
        _lastFocusedCategoryId.value = categoryId
        savedHandle["focusCat"] = categoryId
    }

    fun recordFocusedItem(itemId: Long) {
        _lastFocusedItemId.value = itemId
        savedHandle["focusItem"] = itemId
    }

    fun clearFocusedItem() {
        _lastFocusedItemId.value = null
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentEpg: StateFlow<List<EPGProgram>> = _previewChannel.flatMapLatest { channel ->
        if (channel == null) flowOf(emptyList())
        else epgBox.query()
            .equal(EPGProgram_.channelId, channel.id)
            .order(EPGProgram_.startTimestamp)
            .build()
            .flow()
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var browsingTrace: Trace? = null

    fun selectCategory(category: Category) {
        if (_selectedCategoryId.value == category.id && _lastFocusedItemId.value != null) {
            return
        }
        browsingTrace = FirebasePerformance.getInstance().newTrace("live_category_load_time")
        browsingTrace?.start()
        
        _page.value = 0
        _selectedCategoryId.value = category.id
        if (_searchQuery.value.isNotEmpty()) {
            _searchQuery.value = ""
            savedHandle["search"] = ""
        }
        _lastFocusedItemId.value = null
    }

    /** Like selectCategory but preserves lastFocusedItemId for focus restoration. */
    fun selectCategoryForRestore(category: Category) {
        browsingTrace = FirebasePerformance.getInstance().newTrace("live_category_load_time")
        browsingTrace?.start()
        
        _page.value = 0
        _selectedCategoryId.value = category.id
        if (_searchQuery.value.isNotEmpty()) {
            _searchQuery.value = ""
            savedHandle["search"] = ""
        }
    }

    /** Lazy: call this from the compose item — runs count() only for this category, one-shot */
    fun loadCategoryCount(category: Category) {
        if (!loadedCountIds.add(category.id)) return
        viewModelScope.launch(Dispatchers.IO) {
            val count = runCatching {
                when (category.id) {
                    CAT_ID_FAVORITES -> liveBox.query()
                        .equal(LiveStream_.isFavorite, true)
                        .build().use { it.count().toInt() }
                    CAT_ID_RECENT -> liveBox.query()
                        .greater(LiveStream_.lastWatchedAt, 0L)
                        .build().use { it.count().toInt() }
                    else -> {
                        if (category.id >= 1000000) {
                            val realGroupId = category.id - 1000000
                            groupMembershipBox.query()
                                .equal(GroupMembership_.groupId, realGroupId)
                                .build().use { it.count().toInt() }
                        } else {
                            liveBox.query()
                                .equal(LiveStream_.remoteCategoryId, category.categoryId, StringOrder.CASE_INSENSITIVE)
                                .build().use { it.count().toInt() }
                        }
                    }
                }
            }.getOrElse { e ->
                Log.e("LiveViewModel", "Error loading category count for ${category.categoryName}", e)
                loadedCountIds.remove(category.id)
                0
            }
            if (count > 0) {
                _categoryCounts.update { it + (category.id to count) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        if (_searchQuery.value != query) {
            _page.value = 0
            _searchQuery.value = query
            savedHandle["search"] = query
        }
    }

    fun loadMore() {
        _page.value++
    }

    private var lastStableChannel: LiveStream? = null
    private var zapRevertJob: Job? = null

    fun previewChannel(channel: LiveStream) {
        if (_previewChannel.value?.id == channel.id && playerEngine.isPlaying.value) return
        
        _previewChannel.value = channel
        
        // Update last watched timestamp so it shows in Recents immediately
        viewModelScope.launch(Dispatchers.IO) {
            val freshChannel = liveBox.get(channel.id)
            if (freshChannel != null) {
                freshChannel.lastWatchedAt = System.currentTimeMillis()
                liveBox.put(freshChannel)
            }
        }
        
        // Handle Zap Auto Revert
        zapRevertJob?.cancel()
        if (preferenceRepository.zapAutoRevert.value) {
            zapRevertJob = viewModelScope.launch {
                delay(5000)
                if (lastStableChannel != null && lastStableChannel!!.id != channel.id) {
                    previewChannel(lastStableChannel!!)
                }
            }
        }

        viewModelScope.launch {
            xtreamRepository.fetchEpgForChannel(channel)
            val account = xtreamRepository.getAccount()
            if (account != null) {
                val url = XtreamUrlFactory.buildPlaybackUrl(
                    serverUrl = account.serverUrl,
                    username = account.username,
                    password = account.password,
                    kind = "live",
                    streamId = channel.id
                )
                playerEngine.prepare(StreamInfo(channel.id, channel.name, url, StreamType.MPEG_TS, isLive = true))
                playerEngine.play()
            }
        }
    }

    fun confirmChannel(channel: LiveStream) {
        zapRevertJob?.cancel()
        lastStableChannel = channel
        
        // Update last watched timestamp for Recents
        viewModelScope.launch(Dispatchers.IO) {
            channel.lastWatchedAt = System.currentTimeMillis()
            liveBox.put(channel)
        }
    }

    fun toggleFavorite(channel: LiveStream) {
        channel.isFavorite = !channel.isFavorite
        liveBox.put(channel)
        
        // Ensure preview state updates if it's the same channel
        if (_previewChannel.value?.id == channel.id) {
            _previewChannel.value = channel.copy() 
        }
    }

    fun hideChannel(channel: LiveStream) {
        channel.isHidden = true
        liveBox.put(channel)
        if (_previewChannel.value?.id == channel.id) {
            clearPreview()
        }
    }

    fun addCustomGroup(name: String) {
        customGroupBox.put(CustomGroup(name = name))
    }

    fun deleteCustomGroup(category: Category) {
        if (category.id >= 1000000) {
            val groupId = category.id - 1000000
            customGroupBox.remove(groupId)
            val memberships = runCatching {
                groupMembershipBox.query().equal(GroupMembership_.groupId, groupId).build().use { it.find() }
            }.getOrDefault(emptyList())
            groupMembershipBox.remove(memberships)
        }
    }

    fun addToGroup(channel: LiveStream, category: Category) {
        if (category.id >= 1000000) {
            val groupId = category.id - 1000000
            groupMembershipBox.put(GroupMembership(groupId = groupId, streamId = channel.id))
        }
    }

    fun removeFromGroup(channel: LiveStream, category: Category) {
        if (category.id >= 1000000) {
            val groupId = category.id - 1000000
            val memberships = runCatching {
                groupMembershipBox.query()
                    .equal(GroupMembership_.groupId, groupId)
                    .equal(GroupMembership_.streamId, channel.id)
                    .build().use { it.find() }
            }.getOrDefault(emptyList())
            groupMembershipBox.remove(memberships)
        }
    }

    fun enterCategoryReorderMode() {
        _reorderCategories.value = uiState.value.categories
        _isReorderMode.value = true
        _selectedReorderCategoryIds.value = emptySet()
    }

    fun exitCategoryReorderMode() {
        _isReorderMode.value = false
        _selectedReorderCategoryIds.value = emptySet()
    }

    fun toggleReorderSelection(categoryId: Long) {
        _selectedReorderCategoryIds.update { current ->
            if (current.contains(categoryId)) current - categoryId
            else current + categoryId
        }
    }

    fun moveSelectedCategoriesUp() {
        val selectedIds = _selectedReorderCategoryIds.value
        if (selectedIds.isEmpty()) return

        val list = _reorderCategories.value.toMutableList()
        // Sort selected indices to move them in order
        val selectedIndices = list.indices.filter { list[it].id in selectedIds }.sorted()
        
        selectedIndices.forEach { index ->
            if (index > 2) { // Skip Favorites (0) and Recent (1)
                Collections.swap(list, index, index - 1)
            }
        }
        _reorderCategories.value = list
    }

    fun moveSelectedCategoriesDown() {
        val selectedIds = _selectedReorderCategoryIds.value
        if (selectedIds.isEmpty()) return

        val list = _reorderCategories.value.toMutableList()
        val selectedIndices = list.indices.filter { list[it].id in selectedIds }.sortedDescending()

        selectedIndices.forEach { index ->
            if (index >= 2 && index < list.size - 1) {
                Collections.swap(list, index, index + 1)
            }
        }
        _reorderCategories.value = list
    }

    fun moveSelectedCategoriesToTop() {
        val selectedIds = _selectedReorderCategoryIds.value
        if (selectedIds.isEmpty()) return

        val list = _reorderCategories.value.toMutableList()
        val selectedItems = list.filter { it.id in selectedIds }
        list.removeAll { it.id in selectedIds }
        
        // Insert after virtual categories (Favorites/Recent)
        list.addAll(2, selectedItems)
        _reorderCategories.value = list
    }

    fun moveSelectedCategoriesToBottom() {
        val selectedIds = _selectedReorderCategoryIds.value
        if (selectedIds.isEmpty()) return

        val list = _reorderCategories.value.toMutableList()
        val selectedItems = list.filter { it.id in selectedIds }
        list.removeAll { it.id in selectedIds }
        list.addAll(selectedItems)
        _reorderCategories.value = list
    }

    fun moveCategoryUp(category: Category) {
        val list = _reorderCategories.value.toMutableList()
        val index = list.indexOfFirst { it.id == category.id }
        if (index > 2) { // Skip Favorites, Recent (Indices 0, 1)
            Collections.swap(list, index, index - 1)
            _reorderCategories.value = list
        }
    }

    fun moveCategoryDown(category: Category) {
        val list = _reorderCategories.value.toMutableList()
        val index = list.indexOfFirst { it.id == category.id }
        if (index >= 2 && index < list.size - 1) {
            Collections.swap(list, index, index + 1)
            _reorderCategories.value = list
        }
    }

    fun saveCategoryOrder() {
        val list = _reorderCategories.value
        boxStore.runInTx {
            list.forEachIndexed { index, cat ->
                if (cat.id >= 1000000) {
                    val cg = customGroupBox.get(cat.id - 1000000)
                    if (cg != null) {
                        cg.groupOrder = index
                        customGroupBox.put(cg)
                    }
                } else if (!cat.isVirtual) {
                    cat.order = index
                    categoryBox.put(cat)
                }
            }
        }
        _isReorderMode.value = false
    }

    // --- CHANNEL REORDER LOGIC ---

    fun enterChannelReorderMode() {
        _reorderChannels.value = uiState.value.filteredChannels
        _isChannelReorderMode.value = true
    }

    fun exitChannelReorderMode() {
        _isChannelReorderMode.value = false
    }

    fun moveChannelUp(channel: LiveStream) {
        val list = _reorderChannels.value.toMutableList()
        val index = list.indexOfFirst { it.id == channel.id }
        if (index > 0) {
            Collections.swap(list, index, index - 1)
            _reorderChannels.value = list
        }
    }

    fun moveChannelDown(channel: LiveStream) {
        val list = _reorderChannels.value.toMutableList()
        val index = list.indexOfFirst { it.id == channel.id }
        if (index >= 0 && index < list.size - 1) {
            Collections.swap(list, index, index + 1)
            _reorderChannels.value = list
        }
    }

    fun saveChannelOrder() {
        val list = _reorderChannels.value
        val currentCatId = _selectedCategoryId.value ?: CAT_ID_FAVORITES
        
        boxStore.runInTx {
            list.forEachIndexed { index, channel ->
                if (currentCatId >= 1000000) {
                    val groupId = currentCatId - 1000000
                    val membership = runCatching {
                        groupMembershipBox.query()
                            .equal(GroupMembership_.groupId, groupId)
                            .equal(GroupMembership_.streamId, channel.id)
                            .build().use { it.findFirst() }
                    }.getOrNull()
                    if (membership != null) {
                        membership.order = index
                        groupMembershipBox.put(membership)
                    }
                } else {
                    val stream = liveBox.get(channel.id)
                    if (stream != null) {
                        stream.order = index
                        liveBox.put(stream)
                    }
                }
            }
        }
        _isChannelReorderMode.value = false
    }

    fun clearPreview() {
        _previewChannel.value = null
        playerEngine.stop()
    }

    override fun onCleared() {
        super.onCleared()
        if (globalPlayerState.mode.value != PlayerViewMode.FULLSCREEN) {
            playerEngine.stop()
        }
    }
}

data class LiveUiState(
    val categories: List<Category> = emptyList(),
    val categoryCounts: Map<Long, Int> = emptyMap(),
    val selectedCategory: Category? = null,
    val filteredChannels: List<LiveStream> = emptyList(),
    val isLoading: Boolean = true,
    val isCategoryReorderMode: Boolean = false,
    val isChannelReorderMode: Boolean = false
)

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
