package app.pbbls.android.features.achievements.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.achievements.AchievementCard
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.core.model.achievements.DomainsSection
import app.pbbls.android.core.ui.ReferenceStrings
import app.pbbls.android.core.ui.ReferenceType
import app.pbbls.android.features.achievements.AchievementLookups
import app.pbbls.android.features.achievements.copy.rememberAchievementsCopy
import app.pbbls.android.features.achievements.figures.DomainSealMark
import app.pbbls.android.features.achievements.figures.ShardBand
import app.pbbls.android.features.achievements.figures.domainBandItems

private val NUMBER_COLUMN = 42.dp
private val SEAL_COLUMN = 30.dp
private val BAND = 350.dp

/** A domain's name, or "A domain" for an id the server left out or this device does not know yet. */
@Composable
private fun domainName(
    id: String?,
    lookups: AchievementLookups,
    fallback: String,
): String = id?.let(lookups.domain)?.let { ReferenceStrings.referenceName(ReferenceType.DOMAIN, it.slug, it.name) } ?: fallback

/**
 * The Domains sheet (the board's `sheetDomains`, pick 3): the band in the order they came, the
 * insight, the register newest first as the server sends it (numbered by arrival), the not yet.
 */
@Composable
fun DomainsSheet(
    domains: DomainsSection,
    lookups: AchievementLookups,
    onOpenMoment: (String) -> Unit,
    onPull: (CompletionStack) -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = rememberAchievementsCopy()
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val openPebble = copy.string(R.string.achievements_open_pebble)
    val unknown = copy.string(R.string.achievements_unknown_domain)
    Column(modifier.fillMaxWidth()) {
        val ready: (@Composable () -> Unit)? =
            if (domains.completion?.ready == true) {
                {
                    ReadyMark(
                        copy.string(R.string.achievements_ready),
                        copy.readyDescription(CompletionStack.DOMAINS),
                        { onPull(CompletionStack.DOMAINS) },
                    )
                }
            } else {
                null
            }
        SheetBand(title = copy.cardTitle(AchievementCard.DOMAINS), hint = copy.domainsRegister(), trailing = ready)
        ShardBand(domainBandItems(domains), copy.domainsDescription(domains), modifier = Modifier.padding(top = 16.dp), width = BAND)
        copy.coverage(domains.coverage)?.let { Text(it, style = type.lead, color = colors.ink, modifier = Modifier.padding(top = 14.dp)) }

        SheetSection(copy.string(R.string.achievements_section_entered), copy.string(R.string.achievements_newest_first))
        domains.entered.forEachIndexed { i, entry ->
            val name = domainName(entry.domainId, lookups, unknown)
            // Numbered by arrival: the served list is newest first, so the first row is the last to come.
            val number = copy.registerNumber(domains.entered.size - i)
            val pebble = entry.pebble
            val pebbleName = pebble?.let { copy.momentName(it.name) }
            val date = entry.on?.let(copy::shortDate)
            val opens = pebble?.momentId
            RegisterRow(
                mark = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // One line at any font scale: "No. 14" shrinks to its column rather than wrap.
                        Text(
                            number,
                            style = type.registerDate,
                            color = colors.ink2,
                            maxLines = 1,
                            autoSize =
                                TextAutoSize.StepBased(
                                    minFontSize = type.registerDate.fontSize / 2,
                                    maxFontSize = type.registerDate.fontSize,
                                ),
                            modifier = Modifier.width(NUMBER_COLUMN),
                        )
                        DomainSealMark(box = SEAL_COLUMN, width = 17f * 28f / 30f)
                    }
                },
                title = name,
                subtitle = pebbleName,
                date = date,
                isNew = entry.isNew,
                description = copy.row(number, name, pebbleName, date, isNew = entry.isNew),
                onClick = opens?.let { id -> { onOpenMoment(id) } },
                clickLabel = opens?.let { openPebble },
                markWidth = NUMBER_COLUMN + SEAL_COLUMN,
            )
        }

        if (domains.notYet.isNotEmpty()) {
            SheetSection(copy.string(R.string.achievements_section_not_yet), hint = null)
            val notYet = domains.notYet.map { domainName(it, lookups, unknown) }
            NotYetNames(notYet, copy.notYetDescription(notYet))
        }
    }
}
