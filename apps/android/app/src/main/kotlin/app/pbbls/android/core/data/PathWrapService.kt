package app.pbbls.android.core.data

import android.util.Log
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.model.wrap.MonthWrapDto
import app.pbbls.android.core.model.wrap.Strip
import app.pbbls.android.core.model.wrap.StripDto
import app.pbbls.android.core.model.wrap.WeekWrap
import app.pbbls.android.core.model.wrap.WeekWrapDto
import app.pbbls.android.core.model.wrap.WrapJson
import app.pbbls.android.core.model.wrap.toModel
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** The seam [PathViewModel][app.pbbls.android.features.path.PathViewModel]'s tests fake (#1207). */
interface PathWrapServicing {
    suspend fun strip(zone: ZoneId): Strip

    /** The device's last copy of each payload (spec §7.1), or null: display only, never instead of a fetch. */
    suspend fun cachedStrip(zone: ZoneId): Strip?

    suspend fun cachedWeekWrap(
        weekStart: LocalDate,
        zone: ZoneId,
    ): WeekWrap?

    suspend fun cachedMonthWrap(
        month: YearMonth,
        zone: ZoneId,
    ): MonthWrap?

    suspend fun weekWrap(
        weekStart: LocalDate,
        zone: ZoneId,
    ): WeekWrap

    suspend fun monthWrap(
        month: YearMonth,
        zone: ZoneId,
    ): MonthWrap
}

/**
 * The Path's strip, week wraps and month pages (#1207). The server aggregates them (`path_strip`,
 * `path_week_wrap`, `path_month_wrap`); this service only calls, decodes and maps. Each call sends
 * the zone the feed buckets its days in. Errors propagate; callers own their loading and error state.
 *
 * Each payload that decodes is kept on the device for the signed-in user ([WrapDiskCache]), so the
 * next cold start can show it while the fetch runs.
 */
@Singleton
class PathWrapService
    @Inject
    constructor(
        private val supabase: SupabaseService,
        private val disk: WrapDiskCaching,
    ) : PathWrapServicing {
        override suspend fun strip(zone: ZoneId): Strip = fetch(STRIP, zone, { rpc("path_strip", zone) }) { decode<StripDto>(it).toModel() }

        override suspend fun cachedStrip(zone: ZoneId): Strip? = cached(STRIP, zone) { decode<StripDto>(it).toModel() }

        override suspend fun cachedWeekWrap(
            weekStart: LocalDate,
            zone: ZoneId,
        ): WeekWrap? = cached(weekPage(weekStart), zone) { decode<WeekWrapDto>(it).toModel() }

        override suspend fun cachedMonthWrap(
            month: YearMonth,
            zone: ZoneId,
        ): MonthWrap? = cached(monthPage(month), zone) { decode<MonthWrapDto>(it).toModel() }

        override suspend fun weekWrap(
            weekStart: LocalDate,
            zone: ZoneId,
        ): WeekWrap =
            fetch(weekPage(weekStart), zone, { rpc("path_week_wrap", zone) { put("p_week_start", weekStart.toString()) } }) {
                decode<WeekWrapDto>(it).toModel()
            }

        override suspend fun monthWrap(
            month: YearMonth,
            zone: ZoneId,
        ): MonthWrap =
            fetch(monthPage(month), zone, { rpc("path_month_wrap", zone) { put("p_month", month.atDay(1).toString()) } }) {
                decode<MonthWrapDto>(it).toModel()
            }

        /** Fetches, decodes and, once the body has decoded, keeps it for the signed-in user. */
        private suspend fun <T> fetch(
            page: String,
            zone: ZoneId,
            call: suspend () -> String,
            map: (String) -> T,
        ): T {
            val body = call()
            val model = map(body)
            supabase.session
                ?.user
                ?.id
                ?.let { owner -> disk.write(owner, zone, page, body) }
            return model
        }

        /** The kept body for the signed-in user, decoded; a body this client cannot read is a miss. */
        private suspend fun <T> cached(
            page: String,
            zone: ZoneId,
            map: (String) -> T,
        ): T? {
            val owner = supabase.session?.user?.id ?: return null
            val body = disk.read(owner, zone, page) ?: return null
            return try {
                map(body)
            } catch (e: SerializationException) {
                Log.w(TAG, "kept wrap $page no longer decodes, read as a miss", e)
                null
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "kept wrap $page no longer decodes, read as a miss", e)
                null
            }
        }

        private inline fun <reified D> decode(body: String): D = WrapJson.decodeFromString<D>(body)

        // The body is decoded here rather than by the client's serializer, so the wraps' tolerance
        // (WrapJson) is the same in production and in the tests that replay the server's payloads.
        private suspend fun rpc(
            function: String,
            zone: ZoneId,
            params: JsonObjectBuilder.() -> Unit = {},
        ): String =
            supabase.client.postgrest
                .rpc(
                    function,
                    buildJsonObject {
                        put("p_tz", zone.id)
                        params()
                    },
                ).data

        private companion object {
            const val TAG = "path-wraps"
            const val STRIP = "strip"

            fun weekPage(weekStart: LocalDate) = "week-$weekStart"

            fun monthPage(month: YearMonth) = "month-$month"
        }
    }
