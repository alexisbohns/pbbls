package app.pbbls.android.di

import app.pbbls.android.features.pebblemedia.SnapProcessor
import app.pbbls.android.services.SnapURLCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Disk and network I/O. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** CPU-bound work. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** The UI thread, `immediate` so a call already on it does not post. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

/**
 * Process-lifetime scope for work that must outlive any screen, **dispatched on
 * `Dispatchers.Main.immediate`**. Four things a consumer has to know:
 *
 * - **It runs on the UI thread.** Compose state writes are free; wrap anything
 *   CPU- or I/O-bound in `withContext`. The name says "application", not
 *   "background" — don't launch a query straight onto it.
 * - **`SupabaseService`, `KarmaNotificationService` and `AchievementsService`
 *   share this one instance** (the `@Singleton` on its provider is what makes
 *   it one; drop that and each consumer silently gets its own).
 * - **`SupervisorJob` keeps their children independent** — a failed child never
 *   cancels its parent and so never reaches a sibling. That isolation was always
 *   per-child, never per-service, which is why collapsing three private scopes
 *   into this one took nothing away.
 * - **There is no `CoroutineExceptionHandler`**, so an uncaught throw in any
 *   child reaches the thread's handler and kills the process. Catch inside every
 *   `launch`. All three current consumers do.
 *
 * **Never cancel it.** Nothing does today, and a consumer that did would now
 * stop all three at once. It is NOT for view-scoped async either — that stays
 * `LaunchedEffect` / `rememberCoroutineScope` / `viewModelScope`, and
 * `GlobalScope` is still banned (`apps/android/CLAUDE.md`).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * The same contract as [ApplicationScope] but dispatched on IO — for a
 * process-lifetime consumer whose every launch is disk or network.
 *
 * It lives here rather than at its use site so that "what long-lived scopes does
 * this app have?" has exactly one answer: this file. [SnapURLCache]'s
 * coalescing sign-and-cache is the only consumer today.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoApplicationScope

/**
 * The only place `Dispatchers.*` is **used** in `app/src/main` (#848), and the
 * only place a long-lived [CoroutineScope] is built.
 *
 * A dispatcher named at a call site is a thread policy a test cannot replace
 * with a `TestDispatcher`, which is why they all live here. The check:
 *
 * ```
 * grep -rn "Dispatchers\." app/src/main/kotlin | grep -v '^\S*: *\*'
 * ```
 *
 * should return this file's three providers and nothing else. The `grep -v`
 * strips comment lines and is not optional — [SnapProcessor]'s KDoc names
 * `Dispatchers.IO` in prose to record what it replaced, and prose is not policy.
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @MainDispatcher main: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + main)

    @Provides
    @Singleton
    @IoApplicationScope
    fun provideIoApplicationScope(
        @IoDispatcher io: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + io)
}
