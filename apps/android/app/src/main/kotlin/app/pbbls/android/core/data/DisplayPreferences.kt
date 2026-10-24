package app.pbbls.android.core.data

import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton

/** How the month's day dots are drawn (#1207, D9): around a ring by default, or as a calendar. */
enum class DayView { CIRCLE, CALENDAR }

/**
 * Display settings (#1207, D9, D15): the day-dot view, switched once in the month sheet and
 * remembered. On [SettingsStore], read through the Path's ViewModel.
 */
@Singleton
class DisplayPreferences
    @Inject
    constructor(
        private val settings: SettingsStore,
    ) {
        val dayView: DayView get() = settings[DAY_VIEW]?.let { name -> DayView.entries.firstOrNull { it.name == name } } ?: DayView.CIRCLE

        fun setDayView(value: DayView) = settings.set(DAY_VIEW, value.name)

        private companion object {
            val DAY_VIEW = stringPreferencesKey("dayView")
        }
    }
