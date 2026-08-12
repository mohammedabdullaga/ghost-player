package com.eliteprime.app.ui.screens.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.data.local.entity.*
import com.eliteprime.app.data.remote.XtreamUrlFactory
import com.eliteprime.app.data.repository.PreferenceRepository
import com.eliteprime.app.data.repository.XtreamRepository
import com.eliteprime.app.domain.model.StreamInfo
import com.eliteprime.app.domain.model.StreamType
import com.eliteprime.app.player.GlobalPlayerState
import com.eliteprime.app.player.PlayerEngine
import com.eliteprime.app.player.PlayerViewMode
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
import kotlinx.coroutines.withContext
import java.util.Collections
import javax.inject.Inject

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val boxStore: BoxStore,
    val playerEngine: PlayerEngine,
    val globalPlayerState: GlobalPlayerState,
    private val xtreamRepository: XtreamRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    companion object {
        const val CAT_ID_FAVORITES = -1L
        const val CAT_ID_RECENT = -2L
        const val CAT_ID_ALL = -3L
    }

    private val categoryBox = boxStore.boxFor(Category::class.java)
    private val liveBox = boxStore.boxFor(LiveStream::class.java)
    private val epgBox = boxStore.boxFor(EPGProgram::class.java)
    private val accountBox = boxStore.boxFor(XtreamAccount::class.java)
    private val customGroupBox = boxStore.boxFor(CustomGroup::class.java)
    private val groupMembershipBox = boxStore.boxFor(GroupMembership::class.java)

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _previewChannel = MutableStateFlow<LiveStream?>(null)
    val previewChannel = _previewChannel.asStateFlow()

    private val _isReorderMode = MutableStateFlow(false)
    val isReorderMode = _isReorderMode.asStateFlow()

    private val _reorderCategories = MutableStateFlow<List<Category>>(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val categoriesFlow = combine(
        categoryBox.query().build().flow(),
        customGroupBox.query().build().flow()
    ) { providerCats, customGroups ->
        val virtualCats = listOf(
            Category(id = CAT_ID_FAVORITES, categoryName = "★ Favorites", isVirtual = true),
            Category(id = CAT_ID_RECENT, categoryName = "🕒 Recent", isVirtual = true),
            Category(id = CAT_ID_ALL, categoryName = "All Channels", isVirtual = true)
        )
        val customGroupCats = customGroups
            .filter { it.groupType == "live" }
            .map { cg ->
                Category(id = cg.id + 1000000, categoryName = cg.name, isVirtual = true, order = cg.groupOrder)
            }
        (virtualCats + customGroupCats + providerCats.filter { it.type == "live" && !it.isHidden }).sortedBy { it.order }
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val filteredChannelsFlow = combine(
        _selectedCategoryId,
        _searchQuery.debounce(300),
        categoriesFlow
    ) { selectedId, query, allCategories ->
        val currentId = selectedId ?: CAT_ID_ALL
        val currentSelected = allCategories.find { it.id == currentId } ?: allCategories.find { it.id == CAT_ID_ALL } ?: allCategories.first()

        val baseChannels = withContext(Dispatchers.IO) {
            when (currentId) {
                CAT_ID_ALL -> liveBox.query().build().find()
                CAT_ID_FAVORITES -> liveBox.query().equal(LiveStream_.isFavorite, true).build().find()
                CAT_ID_RECENT -> liveBox.query().notEqual(LiveStream_.lastWatchedAt, 0L).orderDesc(LiveStream_.lastWatchedAt).build().find(0, 50)
                else -> {
                    if (currentId >= 1000000) {
                        val realGroupId = currentId - 1000000
                        val streamIds = groupMembershipBox.query().equal(GroupMembership_.groupId, realGroupId).build().find().map { it.streamId }
                        liveBox.get(streamIds).filterNotNull()
                    } else {
                        liveBox.query().equal(LiveStream_.remoteCategoryId, currentSelected.categoryId, StringOrder.CASE_INSENSITIVE).build().find()
                    }
                }
            }
        }
        baseChannels.filter { 
            !it.isHidden && (query.isEmpty() || it.name.contains(query, ignoreCase = true)) 
        }
    }.flowOn(Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LiveUiState> = combine(
        categoriesFlow,
        _selectedCategoryId,
        filteredChannelsFlow,
        _isReorderMode,
        _reorderCategories
    ) { allCategories, selectedId, channels, reorderMode, reorderList ->
        
        val categories = if (reorderMode) reorderList else allCategories
        val currentId = selectedId ?: CAT_ID_ALL
        val currentSelected = categories.find { it.id == currentId } ?: allCategories.find { it.id == CAT_ID_ALL } ?: allCategories.first()

        LiveUiState(
            categories = categories,
            selectedCategory = currentSelected,
            filteredChannels = if (reorderMode) emptyList() else channels,
            isLoading = false,
            isCategoryReorderMode = reorderMode
        )
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LiveUiState(isLoading = true))

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

    fun selectCategory(category: Category) {
        _selectedCategoryId.value = category.id
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private var lastStableChannel: LiveStream? = null
    private var zapRevertJob: Job? = null

    fun previewChannel(channel: LiveStream) {
        if (_previewChannel.value?.id == channel.id && playerEngine.isPlaying.value) return
        
        _previewChannel.value = channel
        
        // Handle Zap Auto Revert
        zapRevertJob?.cancel()
        if (preferenceRepository.zapAutoRevert.value) {
            zapRevertJob = viewModelScope.launch {
                delay(5000)
                // If the user hasn't confirmed this channel (which would set lastStableChannel)
                // revert to the last stable one
                if (lastStableChannel != null && lastStableChannel!!.id != channel.id) {
                    previewChannel(lastStableChannel!!)
                }
            }
        }

        viewModelScope.launch {
            xtreamRepository.fetchEpgForChannel(channel)
            val account = accountBox.query().equal(XtreamAccount_.isActive, true).build().findFirst()
            if (account != null) {
                val url = XtreamUrlFactory.buildPlaybackUrl(
                    serverUrl = account.serverUrl,
                    username = account.username,
                    password = account.password,
                    kind = "live",
                    streamId = channel.id
                )
                playerEngine.prepare(StreamInfo(channel.id, channel.name, url, StreamType.MPEG_TS))
                playerEngine.play()
            }
        }
    }

    fun confirmChannel(channel: LiveStream) {
        zapRevertJob?.cancel()
        lastStableChannel = channel
    }

    fun toggleFavorite(channel: LiveStream) {
        channel.isFavorite = !channel.isFavorite
        liveBox.put(channel)
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
            val memberships = groupMembershipBox.query().equal(GroupMembership_.groupId, groupId).build().find()
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
            val memberships = groupMembershipBox.query()
                .equal(GroupMembership_.groupId, groupId)
                .equal(GroupMembership_.streamId, channel.id)
                .build().find()
            groupMembershipBox.remove(memberships)
        }
    }

    fun enterCategoryReorderMode() {
        _reorderCategories.value = uiState.value.categories
        _isReorderMode.value = true
    }

    fun exitCategoryReorderMode() {
        _isReorderMode.value = false
    }

    fun moveCategoryUp(category: Category) {
        val list = _reorderCategories.value.toMutableList()
        val index = list.indexOfFirst { it.id == category.id }
        if (index > 3) { // Skip Favorites, Recent, All
            Collections.swap(list, index, index - 1)
            _reorderCategories.value = list
        }
    }

    fun moveCategoryDown(category: Category) {
        val list = _reorderCategories.value.toMutableList()
        val index = list.indexOfFirst { it.id == category.id }
        if (index >= 3 && index < list.size - 1) {
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
    val selectedCategory: Category? = null,
    val filteredChannels: List<LiveStream> = emptyList(),
    val isLoading: Boolean = true,
    val isCategoryReorderMode: Boolean = false
)
