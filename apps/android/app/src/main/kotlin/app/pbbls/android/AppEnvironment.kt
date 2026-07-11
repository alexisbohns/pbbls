package app.pbbls.android

/**
 * Typed access to build-time configuration injected via `secrets.properties` →
 * `BuildConfig` (see `app/build.gradle.kts`). Fails loud and early if a value is
 * missing so setup bugs don't become runtime mysteries — the Android analog of
 * the iOS `AppEnvironment` `fatalError` contract. A missing `secrets.properties`
 * never breaks the build; it surfaces here, at first access, instead.
 */
object AppEnvironment {
    val supabaseUrl: String
        get() = requireSecret("SUPABASE_URL", BuildConfig.SUPABASE_URL)

    val supabaseAnonKey: String
        get() = requireSecret("SUPABASE_ANON_KEY", BuildConfig.SUPABASE_ANON_KEY)

    /**
     * Returns [value] when it is non-blank, otherwise throws with copy-the-example
     * setup instructions. Extracted from the accessors (rather than reading
     * `BuildConfig` inline) so it can be unit-tested without a real
     * `secrets.properties` present on the machine.
     */
    internal fun requireSecret(
        name: String,
        value: String,
    ): String {
        check(value.isNotBlank()) {
            "$name is missing. Copy apps/android/secrets.example.properties to " +
                "secrets.properties and fill in real values."
        }
        return value
    }
}
