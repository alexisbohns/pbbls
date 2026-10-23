package app.pbbls.android.core.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import app.pbbls.android.di.ApplicationScope
import app.pbbls.android.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's settings on disk (#1207, D15): one Preferences DataStore, read once into Compose state so
 * the theme and the gates read it synchronously and recompose on a change. A write shows at once and
 * persists behind. The two flags that lived in `pebbles_prefs` (`hasSeenOnboarding`,
 * `useWallpaperColors`) migrate on the first read; the consent cache and the Complete autosaves
 * stay in that file.
 *
 * [isLoaded] turns true after the first read; the splash holds until then, so a first frame never
 * shows a default the disk is about to contradict.
 */
@Singleton
class SettingsStore internal constructor(
    private val store: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher io: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
    ) : this(SettingsDataStores.of(context, io), scope)

    var isLoaded: Boolean by mutableStateOf(false)
        private set

    var current: Preferences by mutableStateOf(emptyPreferences())
        private set

    init {
        // @ApplicationScope has no handler: an unreadable file is logged and read as defaults.
        scope.launch {
            store.data
                .catch { e ->
                    if (e !is IOException) throw e
                    Log.e(TAG, "settings unreadable, using defaults", e)
                    emit(emptyPreferences())
                }.collect {
                    current = it
                    isLoaded = true
                }
        }
    }

    operator fun <T> get(key: Preferences.Key<T>): T? = current[key]

    fun <T> set(
        key: Preferences.Key<T>,
        value: T,
    ) {
        current = current.toMutablePreferences().apply { this[key] = value }
        scope.launch {
            try {
                store.edit { it[key] = value }
            } catch (e: IOException) {
                Log.e(TAG, "could not save ${key.name}", e)
            }
        }
    }

    private companion object {
        const val TAG = "settings"
    }
}

/**
 * One DataStore per file for the whole process. DataStore refuses two live instances on one file,
 * and a Hilt test builds a new singleton component per test in the same process.
 */
internal object SettingsDataStores {
    private const val FILE = "pebbles_settings"

    /** The prefs file the migrated flags lived in; shared with consent and the Complete autosaves. */
    private const val LEGACY_PREFS = "pebbles_prefs"
    private val MIGRATED_KEYS = setOf("hasSeenOnboarding", "useWallpaperColors")

    private val stores = HashMap<File, DataStore<Preferences>>()

    @Synchronized
    fun of(
        context: Context,
        io: CoroutineDispatcher,
    ): DataStore<Preferences> {
        val file = context.preferencesDataStoreFile(FILE)
        return stores.getOrPut(file) { create(context, io, file) }
    }

    /** A store on [file] that migrates the legacy flags on its first read; [of] keeps one per file. */
    fun create(
        context: Context,
        io: CoroutineDispatcher,
        file: File,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            migrations = listOf(SharedPreferencesMigration(context, LEGACY_PREFS, MIGRATED_KEYS)),
            scope = CoroutineScope(io + SupervisorJob()),
            produceFile = { file },
        )
}
