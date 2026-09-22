package app.pbbls.android.di

import app.pbbls.android.AppEnvironment
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import javax.inject.Singleton

/**
 * The one place `AppEnvironment` is read (#848, supersedes D4).
 *
 * Moving the client out of [app.pbbls.android.core.data.SupabaseService]'s
 * initializer is what makes the services constructible in a JVM test: nothing
 * reaches `BuildConfig` unless [provideSupabaseClient] is CALLED, and a test
 * never calls it. (`AppEnvironment`'s values are property getters, so merely
 * touching this `object` reads nothing.) `AppEnvironment` still throws with setup instructions when a
 * secret is blank — a setup bug that fails loud at first launch, never at build
 * (D8). The initializer performs no network I/O, so building it on the main
 * thread during launch stays safe.
 */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {
    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient =
        createSupabaseClient(
            supabaseUrl = AppEnvironment.supabaseUrl,
            supabaseKey = AppEnvironment.supabaseAnonKey,
        ) {
            // Google hosted OAuth returns via the pebbles://auth-callback deep
            // link; PKCE + this scheme/host are the D15/D16 contract. Shared
            // with iOS, so the dashboard allowlist is unchanged.
            install(Auth) {
                flowType = FlowType.PKCE
                scheme = "pebbles"
                host = "auth-callback"
            }
            install(Postgrest)
            // Storage signs the private pebbles-media snap URLs (sub-project D).
            install(Storage)
            // Functions registers the compose-pebble / compose-pebble-update
            // edge-function surface (M39 sub-project A). PebbleWriteService posts
            // via raw Ktor to read the 5xx soft-success body (D2), but the plugin
            // is installed so the standard functions surface is available.
            install(Functions)
        }
}
