package app.pbbls.android.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * What one tab asks another to do on arrival (#1383, spec D10; iOS `nav.go(_, intent:)`): Home's week
 * tap focuses that week on the Path, and the first-five tasks open a create flow on their tab.
 */
sealed interface TabIntent {
    data class FocusWeek(
        val weekStart: LocalDate,
    ) : TabIntent

    data object CreateCollection : TabIntent

    data object CreateSoul : TabIntent

    data object CarveGlyph : TabIntent
}

/**
 * The one pending [TabIntent], a cross-screen notification the target screen's ViewModel consumes
 * exactly once on resume. The poster calls [post] then navigates; the target calls [take] with its
 * own type, so a resume elsewhere never eats it. A newer post replaces one nobody took.
 */
@Singleton
class TabIntents
    @Inject
    constructor() {
        private val state = MutableStateFlow<TabIntent?>(null)

        val pending: StateFlow<TabIntent?> = state.asStateFlow()

        fun post(intent: TabIntent) {
            state.value = intent
        }

        /** The pending intent if it is a [type], cleared as it is returned; null (and nothing cleared) otherwise. */
        fun <T : TabIntent> take(type: KClass<T>): T? {
            while (true) {
                val current = state.value
                if (!type.isInstance(current)) return null
                if (state.compareAndSet(current, null)) return type.java.cast(current)
            }
        }

        inline fun <reified T : TabIntent> take(): T? = take(T::class)
    }
