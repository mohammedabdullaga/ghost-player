package com.eliteprime.app.ui.screens.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.data.local.entity.*
import com.eliteprime.app.data.repository.XtreamRepository
import com.eliteprime.app.domain.model.StreamInfo
import com.eliteprime.app.domain.model.StreamType
import com.eliteprime.app.player.PlayerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import io.objectbox.BoxStore
import io.objectbox.kotlin.flow
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Collections
import javax.inject.Inject

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val boxStore: BoxStore,
    val playerEngine: PlayerEngine,
    private val xtreamRepository: XtreamRepository
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

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val uiState: StateFlow<LiveUiState> = combine(
        combine(
            categoryBox.query().build().flow(),
            customGroupBox.query().build().flow()
        ) { c, cg -> c to cg },
        _selectedCategoryId,
        _searchQuery.debounce(300),
        _isReorderMode,
        _reorderCategories
    ) { (providerCats, customGroups), selectedId, query, reorderMode, reorderList ->
        
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

        val allCategories = if (reorderMode) reorderList else {
            virtualCats + (customGroupCats + providerCats.filter { it.type == "live" && !it.isHidden }).sortedBy { it.order }
        }
        
        val currentId = selectedId ?: CAT_ID_ALL
        val currentSelected = allCategories.find { it.id == currentId } ?: virtualCats.first()

        val channels = when (currentId) {
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
        }.filter { !it.isHidden && (query.isEmpty() || it.name.contains(query, ignoreCase = true)) }

        LiveUiState(
            categories = allCategories,
            selectedCategory = currentSelected,
            filteredChannels = channels,
            isLoading = false,
            isCategoryReorderMode = reorderMode
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LiveUiState())

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentEpg: StateFlow<List<EPGProgram>> = _previewChannel.flatMapLatest { channel ->
        if (channel == null) flowOf(emptyList())
        else epgBox.query()
            .equal(EPGProgram_.channelId, channel.id)
            .order(EPGProgram_.startTimestamp)
            .build()
            .flow()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectCategory(category: Category) {
        _selectedCategoryId.value = category.id
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun previewChannel(channel: LiveStream) {
        if (_previewChannel.value?.id == channel.id) return
        _previewChannel.value = channel
        
        viewModelScope.launch {
            xtreamRepository.fetchEpgForChannel(channel)
            val account = accountBox.query().equal(XtreamAccount_.isActive, true).build().findFirst()
            if (account != null) {
                val url = "${account.serverUrl}live/${account.username}/${account.password}/${channel.id}.ts"
                playerEngine.prepare(StreamInfo(channel.id, channel.name, url, StreamType.LIVE))
                playerEngine.play()
            }
        }
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
        playerEngine.release()
    }
}

data class LiveUiState(
    val categories: List<Category> = emptyList(),
    val selectedCategory: Category? = null,
    val filteredChannels: List<LiveStream> = emptyList(),
    val isLoading: Boolean = true,
    val isCategoryReorderMode: Boolean = false
)
