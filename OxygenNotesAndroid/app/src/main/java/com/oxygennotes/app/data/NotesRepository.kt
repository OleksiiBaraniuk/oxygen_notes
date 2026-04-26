package com.oxygennotes.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class NotesRepository(
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val db: AppDatabase
) {

    // Notes
    fun getUncategorizedNotes(): Flow<List<Note>> = noteDao.getUncategorizedNotes()
    fun getNotesInFolder(folderId: Long): Flow<List<Note>> = noteDao.getNotesInFolder(folderId)
    fun getAllNotes(): Flow<List<Note>> = noteDao.getAllNotes()
    suspend fun getAllNotesOnce(): List<Note> = noteDao.getAllNotesOnce()
    suspend fun getNoteById(id: Long): Note? = noteDao.getNoteById(id)
    suspend fun insertNote(note: Note) = noteDao.insertNote(note)
    suspend fun updateNote(note: Note) = noteDao.updateNote(note)
    suspend fun deleteNote(note: Note) = noteDao.deleteNote(note)

    // Folders
    fun getRootFolders(): Flow<List<Folder>> = folderDao.getRootFolders()
    fun getSubfolders(parentId: Long): Flow<List<Folder>> = folderDao.getSubfolders(parentId)
    fun getAllFolders(): Flow<List<Folder>> = folderDao.getAllFolders()
    suspend fun getAllFoldersOnce(): List<Folder> = folderDao.getAllFoldersOnce()

    suspend fun getFolderById(id: Long): Folder? = folderDao.getFolderById(id)
    suspend fun insertFolder(folder: Folder) = folderDao.insertFolder(folder)
    suspend fun updateFolder(folder: Folder) = folderDao.updateFolder(folder)
    suspend fun deleteFolder(folder: Folder) = folderDao.deleteFolder(folder)

    suspend fun importData(folders: List<Folder>, notes: List<Note>) = db.withTransaction {
        folders.forEach { folderDao.insertFolder(it) }
        notes.forEach { noteDao.insertNote(it) }
    }

    // Saves entire Assign Mode result in one DB transaction — Room emits one update, no intermediate states.
    suspend fun saveAssignModeResult(
        folders: List<Folder>,
        noteSortOrders: Map<Long, Long>,   // noteId → global sortOrder index
        noteAssignments: Map<Long, Long?>  // noteId → new folderId (null = uncategorized)
    ) = db.withTransaction {
        folders.forEach { folderDao.updateFolder(it) }
        noteSortOrders.forEach { (noteId, sortOrder) ->
            val note = noteDao.getNoteById(noteId) ?: return@forEach
            noteDao.updateNote(note.copy(sortOrder = sortOrder))
        }
        val now = System.currentTimeMillis()
        noteAssignments.forEach { (noteId, folderId) ->
            val note = noteDao.getNoteById(noteId) ?: return@forEach
            noteDao.updateNote(note.copy(folderId = folderId, sortOrder = now, modifiedAt = now))
        }
    }
}
