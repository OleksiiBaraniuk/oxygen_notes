package com.oxygennotes.app.data

import android.content.Context

interface AppContainer {
    val notesRepository: NotesRepository
}

class AppDataContainer(private val context: Context) : AppContainer {
    override val notesRepository: NotesRepository by lazy {
        val database = AppDatabase.getDatabase(context)
        NotesRepository(database.noteDao(), database.folderDao(), database)
    }
}
