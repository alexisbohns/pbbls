package app.pbbls.android.core.data

import android.content.Context
import app.pbbls.android.core.model.CrisisResources
import app.pbbls.android.core.model.decodeCrisisResources
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The crisis lines (#1461; iOS `CrisisResources.bundled`): read once from the copy bundled in
 * `assets/crisis-resources.json`. Deliberately offline-only, no network and no admin edit, since
 * a number on this screen is a number someone dials on their worst day; the maintainer verifies
 * each against its official source, and `CrisisResourcesTest` pins the copy to the canonical file.
 */
@Singleton
class CrisisResourcesService internal constructor(
    private val builtIn: () -> String,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        builtIn = {
            context.assets
                .open(BUILT_IN)
                .bufferedReader()
                .use { it.readText() }
        },
    )

    /** Decoded on first use, then kept. */
    val resources: CrisisResources by lazy { decodeCrisisResources(builtIn()) }

    private companion object {
        const val BUILT_IN = "crisis-resources.json"
    }
}
