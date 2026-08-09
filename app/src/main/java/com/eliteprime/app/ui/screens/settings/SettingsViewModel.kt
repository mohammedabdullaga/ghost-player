package com.eliteprime.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.data.local.entity.XtreamAccount
import com.eliteprime.app.data.local.entity.XtreamAccount_
import com.eliteprime.app.data.repository.XtreamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.objectbox.BoxStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val boxStore: BoxStore,
    private val xtreamRepository: XtreamRepository
) : ViewModel() {

    private val _account = MutableStateFlow<XtreamAccount?>(null)
    val account: StateFlow<XtreamAccount?> = _account.asStateFlow()

    init {
        loadAccount()
    }

    private fun loadAccount() {
        val accountBox = boxStore.boxFor(XtreamAccount::class.java)
        _account.value = accountBox.query().equal(XtreamAccount_.isActive, true).build().findFirst()
    }

    fun syncAll() {
        viewModelScope.launch {
            xtreamRepository.syncCategories()
            xtreamRepository.syncAllStreams(
                onLiveProgress = {},
                onMovieProgress = {},
                onSeriesProgress = {}
            )
        }
    }

    fun logout() {
        boxStore.boxFor(XtreamAccount::class.java).removeAll()
        _account.value = null
    }
}
