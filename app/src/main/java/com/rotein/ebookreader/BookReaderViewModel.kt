package com.rotein.ebookreader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
