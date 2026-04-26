package com.oxygennotes.app.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.oxygennotes.app.OxygenNotesApplication
import com.oxygennotes.app.data.Folder
import com.oxygennotes.app.data.Note
import com.oxygennotes.app.data.NotesRepository
import com.oxygennotes.app.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "OxygenNotesVM"
private val DARK_THEME_KEY = booleanPreferencesKey("dark_theme")

data class OxygenNotesUiState(
    val selectedFolder: Folder? = null,
    val folderName: String = "Default",
    val isSearchActive: Boolean = false,
    val searchQuery: String = ""
)

class OxygenNotesViewModel(
    private val app: Application,
    private val notesRepository: NotesRepository
) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(OxygenNotesUiState())
    val uiState: StateFlow<OxygenNotesUiState> = _uiState.asStateFlow()

    val rootFolders = notesRepository.getRootFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFolders = notesRepository.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allNotes = notesRepository.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<Note>> = _uiState
        .flatMapLatest { state ->
            val baseFlow = when {
                state.selectedFolder != null ->
                    notesRepository.getNotesInFolder(state.selectedFolder.id)
                state.folderName == "Default" ->
                    notesRepository.getUncategorizedNotes()
                else ->
                    notesRepository.getAllNotes()
            }
            if (state.searchQuery.isNotEmpty()) {
                val query = state.searchQuery.lowercase()
                baseFlow.map { list ->
                    list.filter {
                        it.title.lowercase().contains(query) ||
                        it.content.lowercase().contains(query)
                    }
                }
            } else {
                baseFlow
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Theme preference ────────────────────────────────────────────────────────

    val isDarkTheme: StateFlow<Boolean> = app.dataStore.data
        .map { prefs -> prefs[DARK_THEME_KEY] ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setDarkTheme(dark: Boolean) {
        viewModelScope.launch {
            app.dataStore.edit { prefs -> prefs[DARK_THEME_KEY] = dark }
        }
    }

    // ── Folder selection ────────────────────────────────────────────────────────

    fun selectFolder(folder: Folder?) {
        _uiState.value = _uiState.value.copy(
            selectedFolder = folder,
            folderName = folder?.name ?: "All Notes"
        )
    }

    fun selectUncategorized() {
        _uiState.value = _uiState.value.copy(selectedFolder = null, folderName = "Default")
    }

    fun selectAllNotes() {
        _uiState.value = _uiState.value.copy(selectedFolder = null, folderName = "All Notes")
    }

    // ── Search ──────────────────────────────────────────────────────────────────

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun toggleSearch() {
        val next = !_uiState.value.isSearchActive
        _uiState.value = _uiState.value.copy(isSearchActive = next)
        if (!next) onSearchQueryChanged("")
    }

    // ── Note CRUD ───────────────────────────────────────────────────────────────

    suspend fun getNoteById(id: Long): Note? = notesRepository.getNoteById(id)

    fun addNote(title: String, content: String) {
        viewModelScope.launch {
            try {
                notesRepository.insertNote(
                    Note(title = title, content = content, folderId = _uiState.value.selectedFolder?.id)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert note", e)
            }
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            try {
                notesRepository.updateNote(note.copy(modifiedAt = System.currentTimeMillis()))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update note id=${note.id}", e)
            }
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            try {
                notesRepository.deleteNote(note)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete note id=${note.id}", e)
            }
        }
    }

    fun renameNote(note: Note, newTitle: String) {
        viewModelScope.launch {
            try {
                notesRepository.updateNote(note.copy(title = newTitle, modifiedAt = System.currentTimeMillis()))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to rename note id=${note.id}", e)
            }
        }
    }

    fun togglePin(noteId: Long) {
        viewModelScope.launch {
            try {
                val note = notesRepository.getNoteById(noteId) ?: return@launch
                notesRepository.updateNote(note.copy(isPinned = !note.isPinned, modifiedAt = System.currentTimeMillis()))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle pin for note id=$noteId", e)
            }
        }
    }

    fun setNoteFolder(noteId: Long, folderId: Long?) {
        viewModelScope.launch {
            try {
                val note = notesRepository.getNoteById(noteId) ?: return@launch
                val now = System.currentTimeMillis()
                notesRepository.updateNote(note.copy(folderId = folderId, sortOrder = now, modifiedAt = now))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to move note id=$noteId to folder $folderId", e)
            }
        }
    }

    fun saveNoteOrder(noteOrders: Map<Long, Long>) {
        if (noteOrders.isEmpty()) return
        viewModelScope.launch {
            try {
                noteOrders.forEach { (noteId, sortOrder) ->
                    val note = notesRepository.getNoteById(noteId) ?: return@forEach
                    notesRepository.updateNote(note.copy(sortOrder = sortOrder))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save note sort orders", e)
            }
        }
    }

    fun setNoteFullWidth(noteId: Long, isFullWidth: Boolean) {
        viewModelScope.launch {
            try {
                val note = notesRepository.getNoteById(noteId) ?: return@launch
                notesRepository.updateNote(note.copy(isFullWidth = isFullWidth, modifiedAt = System.currentTimeMillis()))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resize note id=$noteId", e)
            }
        }
    }

    // ── Folder CRUD ─────────────────────────────────────────────────────────────

    fun createFolder(name: String, parentId: Long?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                notesRepository.insertFolder(Folder(name = name.trim(), parentId = parentId))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create folder '$name'", e)
            }
        }
    }

    fun deleteFolder(folder: Folder) {
        viewModelScope.launch {
            try {
                notesRepository.deleteFolder(folder)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete folder id=${folder.id}", e)
            }
        }
    }

    fun renameFolder(folder: Folder, newName: String) {
        viewModelScope.launch {
            try {
                notesRepository.updateFolder(folder.copy(name = newName))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to rename folder id=${folder.id}", e)
            }
        }
    }

    fun moveFolderToParent(folder: Folder, newParentId: Long?) {
        if (newParentId != null && wouldCreateCycle(folder.id, newParentId, allFolders.value)) {
            Log.w(TAG, "Blocked circular move: folder ${folder.id} → parent $newParentId")
            return
        }
        viewModelScope.launch {
            try {
                notesRepository.updateFolder(folder.copy(parentId = newParentId))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to move folder id=${folder.id}", e)
            }
        }
    }

    private fun wouldCreateCycle(folderId: Long, newParentId: Long, allFolders: List<Folder>): Boolean {
        if (newParentId == folderId) return true
        var current = allFolders.firstOrNull { it.id == newParentId }
        while (current != null) {
            val node = current
            if (node.parentId == folderId) return true
            current = allFolders.firstOrNull { it.id == node.parentId }
        }
        return false
    }

    fun saveReorderedFolders(folders: List<Folder>) {
        viewModelScope.launch {
            try {
                folders.forEachIndexed { index, folder ->
                    notesRepository.updateFolder(folder.copy(sortOrder = index))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save reordered folders", e)
            }
        }
    }

    fun saveAssignModeResult(
        folders: List<Folder>,
        noteSortOrders: Map<Long, Long>,
        noteAssignments: Map<Long, Long?>
    ) {
        viewModelScope.launch {
            try {
                notesRepository.saveAssignModeResult(folders, noteSortOrders, noteAssignments)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save assign mode result", e)
            }
        }
    }

    fun saveFolderStructure(folders: List<Folder>) {
        viewModelScope.launch {
            try {
                folders.forEach { notesRepository.updateFolder(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save folder structure", e)
            }
        }
    }

    // ── Export / Import ─────────────────────────────────────────────────────────

    fun exportData(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val folders = notesRepository.getAllFoldersOnce()
                val notes   = notesRepository.getAllNotesOnce()
                val json    = buildExportJson(folders, notes)
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
            }
        }
    }

    fun importData(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val raw  = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: return@launch
                val root        = JSONObject(raw)
                val foldersJson = root.optJSONArray("folders") ?: JSONArray()
                val notesJson   = root.optJSONArray("notes")   ?: JSONArray()

                val folders = (0 until foldersJson.length()).map { i ->
                    val o = foldersJson.getJSONObject(i)
                    Folder(
                        id        = o.getLong("id"),
                        name      = o.getString("name"),
                        parentId  = o.optLong("parentId", -1L).takeIf { it != -1L },
                        sortOrder = o.optInt("sortOrder", 0)
                    )
                }
                val notes = (0 until notesJson.length()).map { i ->
                    val o = notesJson.getJSONObject(i)
                    Note(
                        id          = o.getLong("id"),
                        title       = o.optString("title", ""),
                        content     = o.optString("content", ""),
                        folderId    = o.optLong("folderId", -1L).takeIf { it != -1L },
                        isPinned    = o.optBoolean("isPinned", false),
                        isFullWidth = o.optBoolean("isFullWidth", false),
                        createdAt   = o.optLong("createdAt", System.currentTimeMillis()),
                        modifiedAt  = o.optLong("modifiedAt", System.currentTimeMillis()),
                        sortOrder   = o.optLong("sortOrder", System.currentTimeMillis())
                    )
                }

                notesRepository.importData(topologicalSortFolders(folders), notes)
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
            }
        }
    }

    private fun buildExportJson(folders: List<Folder>, notes: List<Note>): String {
        val root = JSONObject()
        root.put("version", 1)
        val fa = JSONArray()
        folders.forEach { f ->
            fa.put(JSONObject().apply {
                put("id", f.id); put("name", f.name)
                put("parentId", f.parentId ?: JSONObject.NULL)
                put("sortOrder", f.sortOrder)
            })
        }
        root.put("folders", fa)
        val na = JSONArray()
        notes.forEach { n ->
            na.put(JSONObject().apply {
                put("id", n.id); put("title", n.title); put("content", n.content)
                put("folderId", n.folderId ?: JSONObject.NULL)
                put("isPinned", n.isPinned); put("isFullWidth", n.isFullWidth)
                put("createdAt", n.createdAt); put("modifiedAt", n.modifiedAt)
                put("sortOrder", n.sortOrder)
            })
        }
        root.put("notes", na)
        return root.toString(2)
    }

    private fun topologicalSortFolders(folders: List<Folder>): List<Folder> {
        val result    = mutableListOf<Folder>()
        val remaining = folders.toMutableList()
        val inserted  = mutableSetOf<Long>()
        while (remaining.isNotEmpty()) {
            val batch = remaining.filter { it.parentId == null || it.parentId in inserted }
            if (batch.isEmpty()) { result.addAll(remaining); break }
            result.addAll(batch)
            batch.forEach { inserted.add(it.id) }
            remaining.removeAll(batch.toSet())
        }
        return result
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as OxygenNotesApplication
                OxygenNotesViewModel(app, app.container.notesRepository)
            }
        }
    }
}
