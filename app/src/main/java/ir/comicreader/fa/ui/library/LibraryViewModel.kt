package ir.comicreader.fa.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.comicreader.fa.data.LibraryRepository
import ir.comicreader.fa.data.Ordering
import ir.comicreader.fa.data.Prefs
import ir.comicreader.fa.data.Recent
import ir.comicreader.fa.data.Saf
import ir.comicreader.fa.data.model.ComicItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SortMode { NAME_ASC, NAME_DESC, RECENT }

data class LibraryUiState(
    val loading: Boolean = false,
    val treeUri: Uri? = null,
    val allItems: List<ComicItem> = emptyList(),
    val items: List<ComicItem> = emptyList(),
    val recents: List<Recent> = emptyList(),
    val query: String = "",
    val error: String? = null,
    val hasFolder: Boolean = false,
    val sortOrdinal: Int = 0,
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = LibraryRepository(app)
    private val prefs = Prefs(app)

    private val _state = MutableStateFlow(
        LibraryUiState(sortOrdinal = prefs.sortOrdinal, recents = prefs.recents())
    )
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

    fun refreshRecents() {
        _state.update { it.copy(recents = prefs.recents()) }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        recompute()
    }

    fun setSort(ordinal: Int) {
        prefs.sortOrdinal = ordinal
        _state.update { it.copy(sortOrdinal = ordinal, allItems = sortItems(it.allItems)) }
        recompute()
    }

    private fun scan(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, treeUri = uri, hasFolder = true) }
            runCatching { repo.scan(uri) }
                .onSuccess { items ->
                    _state.update { it.copy(loading = false, allItems = sortItems(items)) }
                    recompute()
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "خطا در خواندن پوشه") }
                }
        }
    }

    private fun recompute() {
        _state.update { st ->
            val visible = if (st.query.isBlank()) {
                st.allItems
            } else {
                st.allItems.filter { it.name.contains(st.query, ignoreCase = true) }
            }
            st.copy(items = visible)
        }
    }

    private fun sortItems(items: List<ComicItem>): List<ComicItem> {
        val mode = SortMode.entries[prefs.sortOrdinal.coerceIn(0, SortMode.entries.size - 1)]
        return when (mode) {
            SortMode.NAME_ASC -> items.sortedWith(compareBy(Ordering.Natural) { it.name })
            SortMode.NAME_DESC -> items.sortedWith(compareBy(Ordering.Natural) { it.name }).asReversed()
            SortMode.RECENT -> items.sortedWith(
                compareByDescending<ComicItem> { prefs.lastOpened(it.uri.toString()) }
                    .thenBy(Ordering.Natural) { it.name }
            )
        }
    }
}
