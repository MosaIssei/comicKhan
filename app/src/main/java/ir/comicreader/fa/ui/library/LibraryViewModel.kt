package ir.comicreader.fa.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.comicreader.fa.data.LibraryRepository
import ir.comicreader.fa.data.Prefs
import ir.comicreader.fa.data.Saf
import ir.comicreader.fa.data.model.ComicItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val loading: Boolean = false,
    val treeUri: Uri? = null,
    val items: List<ComicItem> = emptyList(),
    val error: String? = null,
    val hasFolder: Boolean = false,
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = LibraryRepository(app)
    private val prefs = Prefs(app)

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        prefs.treeUri?.let { scan(Uri.parse(it)) }
    }

    fun onFolderPicked(uri: Uri) {
        Saf.takePermission(getApplication(), uri)
        prefs.treeUri = uri.toString()
        scan(uri)
    }

    fun refresh() {
        _state.value.treeUri?.let { scan(it) }
    }

    private fun scan(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, treeUri = uri, hasFolder = true) }
            runCatching { repo.scan(uri) }
                .onSuccess { items -> _state.update { it.copy(loading = false, items = items) } }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "خطا در خواندن پوشه") }
                }
        }
    }
}
