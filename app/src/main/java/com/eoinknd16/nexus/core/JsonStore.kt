package com.eoinknd16.nexus.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.KSerializer

val Context.nexusDataStore: DataStore<Preferences> by preferencesDataStore(name = "nexus")

/**
 * A single JSON-encoded value in DataStore, exposed as a hot StateFlow.
 * Every persisted thing in the app (addons, watch progress, library, PC
 * link, launcher pins) is small enough that one JSON blob per concern is
 * simpler than a database, and DataStore keeps writes off the main thread.
 */
class JsonStore<T>(
    private val dataStore: DataStore<Preferences>,
    keyName: String,
    private val serializer: KSerializer<T>,
    private val default: T,
    scope: CoroutineScope,
) {
    private val key = stringPreferencesKey(keyName)

    val state: StateFlow<T> = dataStore.data
        .map { prefs -> prefs[key]?.let { decode(it) } ?: default }
        .stateIn(scope, SharingStarted.Eagerly, default)

    /** Snapshot for UI code. May still be [default] for a moment at cold start. */
    val value: T get() = state.value

    /** Reads the real stored value, waiting for DataStore's first load if
     * needed -- use this anywhere a not-yet-loaded default would give a
     * wrong answer (e.g. "no addons installed"). */
    suspend fun get(): T = dataStore.data.first()[key]?.let { decode(it) } ?: default

    private fun decode(raw: String): T? = runCatching { Http.json.decodeFromString(serializer, raw) }.getOrNull()

    suspend fun update(transform: (T) -> T) {
        dataStore.edit { prefs ->
            val current = prefs[key]?.let { decode(it) } ?: default
            prefs[key] = Http.json.encodeToString(serializer, transform(current))
        }
    }

    suspend fun set(value: T) = update { value }
}
