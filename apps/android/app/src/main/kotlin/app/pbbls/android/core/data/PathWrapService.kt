package app.pbbls.android.core.data

import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.model.wrap.MonthWrapDto
import app.pbbls.android.core.model.wrap.Strip
import app.pbbls.android.core.model.wrap.StripDto
import app.pbbls.android.core.model.wrap.WeekWrap
import app.pbbls.android.core.model.wrap.WeekWrapDto
import app.pbbls.android.core.model.wrap.WrapJson
import app.pbbls.android.core.model.wrap.toModel
import io.github.jan.supabase.postgrest.postgrest
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
 */
@Singleton
class PathWrapService
    @Inject
    constructor(
        private val supabase: SupabaseService,
    ) : PathWrapServicing {
        override suspend fun strip(zone: ZoneId): Strip = WrapJson.decodeFromString<StripDto>(rpc("path_strip", zone)).toModel()

        override suspend fun weekWrap(
            weekStart: LocalDate,
            zone: ZoneId,
        ): WeekWrap =
            WrapJson.decodeFromString<WeekWrapDto>(rpc("path_week_wrap", zone) { put("p_week_start", weekStart.toString()) }).toModel()

        override suspend fun monthWrap(
            month: YearMonth,
            zone: ZoneId,
        ): MonthWrap =
            WrapJson.decodeFromString<MonthWrapDto>(rpc("path_month_wrap", zone) { put("p_month", month.atDay(1).toString()) }).toModel()

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
    }
