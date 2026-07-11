package app.pbbls.android

import android.app.Application
import app.rive.runtime.kotlin.core.Rive

/**
 * Application entry point — the `PebblesApp.swift` analog. The service graph
 * (`SupabaseService` and friends, wired the way `PebblesApp.swift` constructs
 * and injects them) lands with the entry funnel (sub-project C); for now this
 * only initializes Rive (D14) so `RiveLogo` can load `.riv` resources.
 */
class PebblesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Rive.init(this)
    }
}
