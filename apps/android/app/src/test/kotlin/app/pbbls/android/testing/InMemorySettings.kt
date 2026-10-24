package app.pbbls.android.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.pbbls.android.core.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/** A Preferences DataStore in memory (#1207): what a write puts in, a read gets back. */
class InMemoryPreferencesStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also {
            state.value =
                it
        }
}

/** A [SettingsStore] over [store], loaded at once: its collector runs eagerly. */
@OptIn(ExperimentalCoroutinesApi::class)
fun inMemorySettings(store: InMemoryPreferencesStore = InMemoryPreferencesStore()): SettingsStore =
    SettingsStore(store, CoroutineScope(UnconfinedTestDispatcher()))
