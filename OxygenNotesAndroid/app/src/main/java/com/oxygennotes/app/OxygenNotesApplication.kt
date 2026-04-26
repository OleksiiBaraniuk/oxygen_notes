package com.oxygennotes.app

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.oxygennotes.app.data.AppContainer
import com.oxygennotes.app.data.AppDataContainer

val Application.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class OxygenNotesApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppDataContainer(this)
    }
}
