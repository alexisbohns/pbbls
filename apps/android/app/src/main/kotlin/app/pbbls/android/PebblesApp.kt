package app.pbbls.android

import android.app.Application

/**
 * Application entry point — the `PebblesApp.swift` analog. Intentionally empty
 * for sub-project A: the service graph (`SupabaseService` and friends, wired the
 * way `PebblesApp.swift` constructs and injects them) lands with the entry funnel
 * (sub-project C). It exists now so the manifest's `android:name` is real and the
 * Application subclass is proven to load.
 */
class PebblesApp : Application()
