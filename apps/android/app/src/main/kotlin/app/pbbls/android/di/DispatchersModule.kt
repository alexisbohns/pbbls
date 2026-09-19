package app.pbbls.android.di

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
 * Process-lifetime scope for work that must outlive any screen. A
 * `SupervisorJob` so one failed child never cancels the rest.
 *
 * This is NOT for view-scoped async — that stays `LaunchedEffect` /
 * `rememberCoroutineScope` / `viewModelScope`, and `GlobalScope` is still
 * banned (`apps/android/CLAUDE.md`).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * The ONLY place `Dispatchers.*` is named in `app/src/main` (#848).
 *
 * `grep -rn "Dispatchers\." app/src/main/kotlin` returning only this file is
 * the acceptance criterion — a literal anywhere else is a hard-coded thread
 * policy a test cannot replace with a `TestDispatcher`.
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
}
