package app.pbbls.android.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Wire name for a mode — routed through the enum's own serializer so the
 * encode mapping can never drift from the `@SerialName` decode mapping.
 */
internal val CollectionMode.wireName: String
    get() = Json.encodeToJsonElement(CollectionMode.serializer(), this).jsonPrimitive.content

/**
 * `POST /collections` body — ports iOS `CollectionInsertPayload`. `user_id` is
 * explicit because the RLS `with check` compares it to `auth.uid()`. `mode` and
 * `glyph_id` are always present and JSON-null when unset: Postgres stores that
 * as SQL NULL, matching the nullable columns. The glyph rides the same insert
 * (R11); `set_collection_glyph` is never called, and the
 * `collections_glyph_usable` trigger guards the write. Pure builders so JVM tests can pin the exact
 * keysets (mirroring the iOS encoding suites).
 */
fun collectionInsertPayload(
    userId: String,
    name: String,
    mode: CollectionMode?,
    glyphId: String?,
): JsonObject =
    buildJsonObject {
        put("user_id", userId)
        put("name", name)
        put("mode", mode?.wireName)
        put("glyph_id", glyphId)
    }

/**
 * `PATCH /collections/:id` body — ports iOS `CollectionUpdatePayload`. `mode`
 * and `glyph_id` are explicitly JSON-null when cleared: PostgREST only touches keys that are
 * present, so an absent key would silently keep the old mode instead of
 * clearing the column.
 */
fun collectionUpdatePayload(
    name: String,
    mode: CollectionMode?,
    glyphId: String?,
): JsonObject =
    buildJsonObject {
        put("name", name)
        put("mode", mode?.wireName)
        put("glyph_id", glyphId)
    }
