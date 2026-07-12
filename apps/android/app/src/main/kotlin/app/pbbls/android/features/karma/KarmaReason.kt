package app.pbbls.android.features.karma

import androidx.annotation.StringRes
import app.pbbls.android.R

/**
 * Why karma was earned — mirrors iOS `KarmaReason.swift`. Only the two native
 * call sites that exist today: creating a pebble (create flash) and enriching
 * one (edit flash, delta > 0 only). Web has more reasons; add here when a real
 * caller lands (YAGNI).
 *
 * [labelRes] is the localized display label, wired through an explicit,
 * compile-checked constructor mapping to `R.string.*` (never `getIdentifier`),
 * so a missing label fails to compile rather than at runtime. The two string
 * keys live in both `strings.xml` files under `LocalizationParityTest`.
 */
enum class KarmaReason(
    @StringRes val labelRes: Int,
) {
    PEBBLE_CREATED(R.string.karma_reason_pebble_created),
    PEBBLE_ENRICHED(R.string.karma_reason_pebble_enriched),
}
