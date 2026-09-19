package app.pbbls.android.di

import app.pbbls.android.services.PathService
import app.pbbls.android.services.PathServicing
import app.pbbls.android.services.PebbleWriteService
import app.pbbls.android.services.PebbleWriteServicing
import app.pbbls.android.services.ProfileService
import app.pbbls.android.services.ProfileServicing
import app.pbbls.android.services.ReferenceDataService
import app.pbbls.android.services.ReferenceDataServicing
import app.pbbls.android.services.SupabaseService
import app.pbbls.android.services.SupabaseServicing
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Interface → implementation for the five services a test needs to fake (#848).
 *
 * Only five, on purpose: the standing rule is "extract a `…Servicing` interface
 * for a fake only when a test needs one" (`apps/android/CLAUDE.md`). #849 pulls
 * the remaining fifteen as each screen gets a ViewModel and a test. Adding an
 * interface here with no fake and no test behind it is the thing that rule
 * exists to prevent.
 */
@Module
@InstallIn(SingletonComponent::class)
interface ServiceBindings {
    @Binds
    @Singleton
    fun bindSupabaseServicing(impl: SupabaseService): SupabaseServicing

    @Binds
    @Singleton
    fun bindPathServicing(impl: PathService): PathServicing

    @Binds
    @Singleton
    fun bindProfileServicing(impl: ProfileService): ProfileServicing

    @Binds
    @Singleton
    fun bindPebbleWriteServicing(impl: PebbleWriteService): PebbleWriteServicing

    @Binds
    @Singleton
    fun bindReferenceDataServicing(impl: ReferenceDataService): ReferenceDataServicing
}
