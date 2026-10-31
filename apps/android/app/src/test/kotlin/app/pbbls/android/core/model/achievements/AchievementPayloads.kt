package app.pbbls.android.core.model.achievements

/**
 * The server's payloads (#1307): `packages/supabase/scripts/oracles/achievements/payloads/`,
 * dumped verbatim by `verify-achievements.ts` (ACHIEVEMENTS_DUMP=1) and put on the test classpath
 * by `app/build.gradle.kts`. One copy, owned by the harness that produced it: when a test here
 * disagrees with one, fix the mapping, never the dump.
 */
internal object AchievementPayloads {
    /** Alice after the harness's section 2: three pebbles, the six firsts whole, offered tomorrow. */
    const val PAGE = "get-achievements"

    /** Bob: the oracle's 2025 and the current year. */
    const val YEAR = "get-achievements-year"

    /** Alice after section 3: explorer and emotions seen, one emotion met since. */
    const val SEEN = "get-achievements-seen"

    /** Alice with the six firsts' completion pulled (a run with the service key). */
    const val PULLED = "get-achievements-pulled"

    /** The first `check_achievements()` over Alice's heavy first pebble. */
    const val CHECK = "check-achievements"

    fun read(name: String): String =
        requireNotNull(AchievementPayloads::class.java.getResource("/$name.json")) { "missing payload fixture $name.json" }
            .readText()

    fun dto(name: String): AchievementsPageDto = AchievementsJson.decodeFromString<AchievementsPageDto>(read(name))
}
