package app.pbbls.android.core.model.home

/**
 * The server's Home payloads (#1383): `packages/supabase/scripts/oracles/home/payloads/`, dumped
 * verbatim by `verify-home.ts` (HOME_DUMP=1) and put on the test classpath by `app/build.gradle.kts`.
 * iOS reads the same files (`HomePayloads.swift`). When a test here disagrees with one, fix the
 * mapping, never the dump.
 */
internal object HomePayloads {
    const val WRAPS = "home-wraps"

    fun read(name: String): String =
        requireNotNull(HomePayloads::class.java.getResource("/$name.json")) { "missing payload fixture $name.json" }
            .readText()
}
