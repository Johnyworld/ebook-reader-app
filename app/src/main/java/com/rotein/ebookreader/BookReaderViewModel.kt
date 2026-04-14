package com.rotein.ebookreader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PopupState(
    val showMenu: Boolean = false,
    val showTocPopup: Boolean = false,
    val showSearchPopup: Boolean = false,
    val showBookmarkPopup: Boolean = false,
    val showHighlightPopup: Boolean = false,
    val showMemoListPopup: Boolean = false,
    val showMemoEditor: Boolean = false,
    val showSettingsPopup: Boolean = false,
    val showFontPopup: Boolean = false
)

class BookReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val _popupState = MutableStateFlow(PopupState())
    val popupState: StateFlow<PopupState> = _popupState.asStateFlow()

    private val _readerSettings = MutableStateFlow(ReaderSettingsStore.load(getApplication()))
    val readerSettings: StateFlow<ReaderSettings> = _readerSettings.asStateFlow()

    private val _currentTime = MutableStateFlow("")
    val currentTime: StateFlow<String> = _currentTime.asStateFlow()

    init {
        // 설정 변경 시 자동 저장
        viewModelScope.launch {
            _readerSettings.collect { settings ->
                withContext(Dispatchers.IO) {
                    ReaderSettingsStore.save(getApplication(), settings)
                }
            }
        }

        // 시계 업데이트
        viewModelScope.launch {
            val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            while (true) {
                _currentTime.value = sdf.format(java.util.Date())
                delay(60_000L - System.currentTimeMillis() % 60_000L)
            }
        }
    }

    fun updateSettings(settings: ReaderSettings) {
        _readerSettings.value = settings
    }

    fun toggleMenu() {
        _popupState.update { it.copy(showMenu = !it.showMenu) }
    }

    fun setShowMenu(show: Boolean) {
        _popupState.update { it.copy(showMenu = show) }
    }

    fun setShowTocPopup(show: Boolean) {
        _popupState.update { it.copy(showTocPopup = show) }
    }

    fun setShowSearchPopup(show: Boolean) {
        _popupState.update { it.copy(showSearchPopup = show) }
    }

    fun setShowBookmarkPopup(show: Boolean) {
        _popupState.update { it.copy(showBookmarkPopup = show) }
    }

    fun setShowHighlightPopup(show: Boolean) {
        _popupState.update { it.copy(showHighlightPopup = show) }
    }

    fun setShowMemoListPopup(show: Boolean) {
        _popupState.update { it.copy(showMemoListPopup = show) }
    }

    fun setShowMemoEditor(show: Boolean) {
        _popupState.update { it.copy(showMemoEditor = show) }
    }

    fun setShowSettingsPopup(show: Boolean) {
        _popupState.update { it.copy(showSettingsPopup = show) }
    }

    fun setShowFontPopup(show: Boolean) {
        _popupState.update { it.copy(showFontPopup = show) }
    }
}
