package app.pbbls.android

import android.app.Application
import app.pbbls.android.services.EmotionPaletteService
import app.pbbls.android.services.PathService
import app.pbbls.android.services.SupabaseService
import app.rive.runtime.kotlin.core.Rive

/**
 * Application entry point — the `PebblesApp.swift` analog. Initializes Rive (D14)
 * and constructs the service graph exactly like `PebblesApp.swift`:
 * [SupabaseService] first, dependents taking it by constructor. `MainActivity`
 * reads the graph off this instance and provides it to Compose via
 * CompositionLocals (D4).
 *
 * [SupabaseService]'s constructor reads Supabase secrets through `AppEnvironment`,
 * which throws with setup instructions if they are blank — a setup bug that fails
 * loud at launch, never at build (D8).
 */
class PebblesApp : Application() {
    lateinit var supabase: SupabaseService
        private set

    lateinit var palettes: EmotionPaletteService
        private set

    lateinit var pathService: PathService
        private set

    override fun onCreate() {
        super.onCreate()
        Rive.init(this)
        supabase = SupabaseService()
        palettes = EmotionPaletteService(supabase)
        pathService = PathService(supabase)
    }
}
