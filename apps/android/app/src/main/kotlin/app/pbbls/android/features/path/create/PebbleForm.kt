package app.pbbls.android.features.path.create

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pbbls.android.R
import app.pbbls.android.components.PebblesTextInput
import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.features.path.create.pickers.EmotionPickerSheet
import app.pbbls.android.features.path.create.pickers.GlyphPickerSheet
import app.pbbls.android.features.path.create.pickers.SoulPickerSheet
import app.pbbls.android.features.path.create.pickers.ValencePickerSheet
import app.pbbls.android.features.path.models.Domain
import app.pbbls.android.features.path.models.EmotionWithPalette
import app.pbbls.android.features.path.models.PebbleCollection
import app.pbbls.android.features.path.models.PebbleDraft
import app.pbbls.android.features.path.models.Valence
import app.pbbls.android.features.path.models.ValencePolarity
import app.pbbls.android.features.path.render.GlyphImage
import app.pbbls.android.features.path.render.PebbleSvg
import app.pbbls.android.features.path.render.ValenceGlyph
import app.pbbls.android.features.profile.models.SoulWithGlyph
import app.pbbls.android.theme.PebblesDestructive
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import app.pbbls.android.theme.ReferenceStrings
import app.pbbls.android.theme.ReferenceType

/** Which picker sheet is open — exactly one `ModalBottomSheet` at a time (D5). */
enum class PickerKind { EMOTION, VALENCE, SOUL, GLYPH }

/**
 * The shared pebble form (D6) — ports iOS `PebbleFormView`. Pure UI: it takes
 * the draft plus reference lists and the already-resolved [selectedEmotion]
 * (the shell reads the palette service so this stays previewable), and reports
 * every edit through [onDraftChange]. It hosts the four picker sheets and the
 * `When` dialogs, opening exactly one at a time. Reused by C's create screen and
 * D's edit screen — the optional [renderSvg]/[strokeColor] header is D's.
 */
@Composable
fun PebbleForm(
    draft: PebbleDraft,
    onDraftChange: (PebbleDraft) -> Unit,
    domains: List<Domain>,
    souls: List<SoulWithGlyph>,
    collections: List<PebbleCollection>,
    selectedEmotion: EmotionWithPalette?,
    selectedGlyph: Glyph?,
    onGlyphPicked: (Glyph?) -> Unit,
    saveError: String?,
    modifier: Modifier = Modifier,
    renderSvg: String? = null,
    strokeColor: String? = null,
    renderHeight: Dp = 260.dp,
) {
    var activePicker by remember { mutableStateOf<PickerKind?>(null) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (renderSvg != null && strokeColor != null) {
            PebbleSvg(
                svg = renderSvg,
                strokeHex = strokeColor,
                modifier = Modifier.fillMaxWidth().height(renderHeight),
            )
        }
        WhenRow(
            happenedAt = draft.happenedAt,
            onChange = { onDraftChange(draft.copy(happenedAt = it)) },
        )
        PebblesTextInput(
            placeholder = stringResource(R.string.create_name_placeholder),
            value = draft.name,
            onValueChange = { onDraftChange(draft.copy(name = it)) },
        )
        PebblesTextInput(
            placeholder = stringResource(R.string.create_description_placeholder),
            value = draft.description,
            onValueChange = { onDraftChange(draft.copy(description = it)) },
            singleLine = false,
            maxLines = 5,
        )
        FormSectionHeader(stringResource(R.string.create_mood_header))
        EmotionRow(selectedEmotion = selectedEmotion, onTap = { activePicker = PickerKind.EMOTION })
        DomainRow(
            domains = domains,
            selectedId = draft.domainId,
            onSelect = { onDraftChange(draft.copy(domainId = it)) },
        )
        ValenceRow(valence = draft.valence, onTap = { activePicker = PickerKind.VALENCE })
        FormSectionHeader(stringResource(R.string.create_glyph_header))
        GlyphRow(
            glyph = selectedGlyph,
            onTap = { activePicker = PickerKind.GLYPH },
            onRemove =
                if (draft.glyphId != null) {
                    {
                        onDraftChange(draft.copy(glyphId = null))
                        onGlyphPicked(null)
                    }
                } else {
                    null
                },
        )
        FormSectionHeader(stringResource(R.string.create_souls_header))
        SelectedSoulsRow(
            soulIds = draft.soulIds,
            allSouls = souls,
            onTap = { activePicker = PickerKind.SOUL },
        )
        FormSectionHeader(stringResource(R.string.create_optional_header))
        CollectionRow(
            collections = collections,
            selectedId = draft.collectionId,
            onSelect = { onDraftChange(draft.copy(collectionId = it)) },
        )
        if (saveError != null) {
            PebblesText(
                text = saveError,
                style = PebblesTypography.callout,
                color = PebblesDestructive,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    when (activePicker) {
        PickerKind.EMOTION ->
            EmotionPickerSheet(
                currentEmotionId = draft.emotionId,
                valence = draft.valence,
                onDismiss = { activePicker = null },
                onSelected = {
                    onDraftChange(draft.copy(emotionId = it))
                    activePicker = null
                },
            )
        PickerKind.VALENCE ->
            ValencePickerSheet(
                current = draft.valence,
                onDismiss = { activePicker = null },
                onSelected = {
                    onDraftChange(draft.copy(valence = it))
                    activePicker = null
                },
            )
        PickerKind.SOUL ->
            SoulPickerSheet(
                currentSelection = draft.soulIds,
                onDismiss = { activePicker = null },
                onConfirm = {
                    onDraftChange(draft.copy(soulIds = it))
                    activePicker = null
                },
            )
        PickerKind.GLYPH ->
            GlyphPickerSheet(
                currentGlyphId = draft.glyphId,
                onDismiss = { activePicker = null },
                onSelected = {
                    onDraftChange(draft.copy(glyphId = it.id))
                    onGlyphPicked(it)
                    activePicker = null
                },
            )
        null -> Unit
    }
}

/**
 * One tap-target form row: a 32dp leading indicator, a label, an optional
 * trailing value, and a chevron. `internal` so [WhenRow] (a sibling file in this
 * package) reuses it.
 */
@Composable
internal fun FormRow(
    leading: @Composable () -> Unit,
    label: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        PebblesText(label, PebblesTypography.body, color = system.foreground)
        Spacer(Modifier.weight(1f))
        if (value != null) {
            PebblesText(value, PebblesTypography.body, color = system.secondary, maxLines = 1)
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = system.secondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun FormSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    PebblesText(
        text = text,
        style = PebblesTypography.cardHeading,
        color = PebblesTheme.colors.system.secondary,
        modifier = modifier,
    )
}

@Composable
private fun EmotionRow(
    selectedEmotion: EmotionWithPalette?,
    onTap: () -> Unit,
) {
    val value =
        selectedEmotion?.let {
            ReferenceStrings.referenceName(ReferenceType.EMOTION, it.slug, it.name)
        } ?: stringResource(R.string.create_choose)
    FormRow(
        leading = {
            if (selectedEmotion != null) {
                Text(text = selectedEmotion.emoji, fontSize = 24.sp, modifier = Modifier.size(32.dp))
            } else {
                DashedPlaceholder()
            }
        },
        label = stringResource(R.string.create_emotion_label),
        value = value,
        onClick = onTap,
    )
}

@Composable
private fun ValenceRow(
    valence: Valence?,
    onTap: () -> Unit,
) {
    val system = PebblesTheme.colors.system
    val value =
        valence?.let {
            stringResource(valencePolarityLabelRes(it.polarity))
        } ?: stringResource(R.string.create_choose)
    FormRow(
        leading = {
            if (valence != null) {
                ValenceGlyph(
                    size = valence.sizeGroup,
                    polarity = valence.polarity,
                    tintColor = system.secondary,
                    modifier = Modifier.size(32.dp),
                )
            } else {
                DashedPlaceholder()
            }
        },
        label = stringResource(R.string.create_valence_label),
        value = value,
        onClick = onTap,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlyphRow(
    glyph: Glyph?,
    onTap: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val system = PebblesTheme.colors.system
    var menuExpanded by remember { mutableStateOf(false) }
    val label =
        if (glyph == null) {
            stringResource(R.string.create_glyph_placeholder)
        } else {
            glyph.name ?: stringResource(R.string.create_glyph_untitled)
        }
    Box {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(
                        onClick = onTap,
                        onLongClick = { if (onRemove != null) menuExpanded = true },
                    )
                    .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (glyph != null) {
                GlyphImage(
                    strokes = glyph.strokes,
                    viewBox = glyph.viewBox,
                    strokeColor = system.secondary,
                    modifier = Modifier.size(32.dp),
                )
            } else {
                DashedPlaceholder()
            }
            PebblesText(label, PebblesTypography.body, color = system.foreground)
            Spacer(Modifier.weight(1f))
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = system.secondary,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = {
                    PebblesText(
                        text = stringResource(R.string.create_glyph_remove),
                        style = PebblesTypography.buttonLabel,
                        color = PebblesDestructive,
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_trash),
                        contentDescription = null,
                        tint = PebblesDestructive,
                    )
                },
                onClick = {
                    menuExpanded = false
                    onRemove?.invoke()
                },
            )
        }
    }
}

@Composable
private fun DomainRow(
    domains: List<Domain>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    var expanded by remember { mutableStateOf(false) }
    val selected = domains.firstOrNull { it.id == selectedId }
    val value =
        selected?.let {
            ReferenceStrings.referenceName(ReferenceType.DOMAIN, it.slug, it.name)
        } ?: stringResource(R.string.create_choose)
    Box {
        FormRow(
            leading = {
                Icon(
                    painter = painterResource(R.drawable.ic_pebble_domain),
                    contentDescription = null,
                    tint = accent.primary,
                    modifier = Modifier.size(28.dp),
                )
            },
            label = stringResource(R.string.create_domain_label),
            value = value,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            domains.forEach { domain ->
                DropdownMenuItem(
                    text = {
                        PebblesText(
                            text = ReferenceStrings.referenceName(ReferenceType.DOMAIN, domain.slug, domain.name),
                            style = PebblesTypography.body,
                            color = system.foreground,
                        )
                    },
                    onClick = {
                        onSelect(domain.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CollectionRow(
    collections: List<PebbleCollection>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    var expanded by remember { mutableStateOf(false) }
    val selected = collections.firstOrNull { it.id == selectedId }
    val value = selected?.name ?: stringResource(R.string.create_none)
    Box {
        FormRow(
            leading = {
                Icon(
                    painter = painterResource(R.drawable.ic_pebble_collection),
                    contentDescription = null,
                    tint = accent.primary,
                    modifier = Modifier.size(28.dp),
                )
            },
            label = stringResource(R.string.create_collection_label),
            value = value,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    PebblesText(
                        text = stringResource(R.string.create_none),
                        style = PebblesTypography.body,
                        color = system.foreground,
                    )
                },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            collections.forEach { collection ->
                DropdownMenuItem(
                    text = {
                        PebblesText(
                            text = collection.name,
                            style = PebblesTypography.body,
                            color = system.foreground,
                        )
                    },
                    onClick = {
                        onSelect(collection.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedSoulsRow(
    soulIds: List<String>,
    allSouls: List<SoulWithGlyph>,
    onTap: () -> Unit,
) {
    val selectedSouls = allSouls.filter { it.id in soulIds }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        selectedSouls.forEach { soul ->
            SoulPill(soul = soul, onClick = onTap)
        }
        AddSoulPill(onClick = onTap)
    }
}

@Composable
private fun SoulPill(
    soul: SoulWithGlyph,
    onClick: () -> Unit,
) {
    val system = PebblesTheme.colors.system
    Column(
        modifier =
            Modifier
                .width(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GlyphImage(
            strokes = soul.glyph.strokes,
            viewBox = soul.glyph.viewBox,
            strokeColor = system.secondary,
            modifier = Modifier.size(40.dp),
        )
        PebblesText(soul.name, PebblesTypography.bodyLeadHand, color = system.secondary, maxLines = 1)
    }
}

@Composable
private fun AddSoulPill(
    onClick: () -> Unit,
) {
    val system = PebblesTheme.colors.system
    Column(
        modifier =
            Modifier
                .width(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).border(1.dp, system.secondary, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            PebblesText("+", PebblesTypography.title, color = system.secondary)
        }
        PebblesText(
            text = stringResource(R.string.create_soul_add),
            style = PebblesTypography.meta,
            color = system.secondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun DashedPlaceholder(modifier: Modifier = Modifier) {
    val color = PebblesTheme.colors.system.secondary
    Box(modifier = modifier.size(32.dp).border(1.dp, color, RoundedCornerShape(6.dp)))
}

/** Polarity → localized label; `internal` so [ValencePickerSheet] reuses it. */
internal fun valencePolarityLabelRes(polarity: ValencePolarity): Int =
    when (polarity) {
        ValencePolarity.HIGHLIGHT -> R.string.valence_highlight
        ValencePolarity.NEUTRAL -> R.string.valence_neutral
        ValencePolarity.LOWLIGHT -> R.string.valence_lowlight
    }
