package app.pbbls.android.features.path.crop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.data.TapHaptic
import app.pbbls.android.core.data.rememberTapHaptics
import app.pbbls.android.core.designsystem.PbblsGlyph
import app.pbbls.android.core.designsystem.PbblsGlyphImage
import app.pbbls.android.core.designsystem.PbblsGlyphSize
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.designsystem.readableWidth
import app.pbbls.android.core.designsystem.rememberReduceMotion
import app.pbbls.android.core.model.crop.CropRatio

/** The tool panel's height, the same for every tool so the canvas never jumps. */
private val ToolPanelHeight = 96.dp

/**
 * The crop editor (#1659, iOS `PhotoCropEditor`, spec "Layout"): it follows the app theme, with a top
 * bar (Cancel · Reset · Done), the canvas, the current tool's panel, and the tool group in thumb reach.
 * Shown over the flow that opened it, which owns [session] and decides what Done and Cancel do: Done
 * keeps exactly what the window shows. Stateless beyond [session]'s own state, so previews render it.
 */
@Composable
fun PhotoCropEditor(
    session: CropSession,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    hapticsOn: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val state = session.state
    val colors = MaterialTheme.colorScheme
    val reduceMotion = rememberReduceMotion()
    val haptic = rememberTapHaptics(hapticsOn)
    val image = remember(session.source.image) { session.source.image.asImageBitmap() }

    Box(modifier.fillMaxSize().background(colors.surface)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().readableWidth()) {
            CropTopBar(hasChanges = state.hasChanges, onCancel = onCancel, onReset = state::reset, onDone = onDone)
            CropCanvas(
                state = state,
                image = image,
                reduceMotion = reduceMotion,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().height(ToolPanelHeight).padding(horizontal = Spacing.lg),
            ) {
                when (state.tool) {
                    CropTool.RATIO ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally)) {
                            CropRatio.entries.forEach { ratio ->
                                CropRatioChip(
                                    ratio = ratio,
                                    selected = state.geometry.ratio == ratio,
                                    onClick = {
                                        haptic(TapHaptic.SELECTION)
                                        state.select(ratio)
                                    },
                                )
                            }
                        }
                    CropTool.ROTATE ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            IconButton(onClick = {
                                haptic(TapHaptic.SELECTION)
                                state.turnQuarter()
                            }) {
                                PbblsGlyphImage(
                                    glyph = PbblsGlyph.AGAIN_BACKWARD,
                                    size = PbblsGlyphSize.TOOLBAR,
                                    contentDescription = stringResource(R.string.crop_rotate_quarter),
                                    tint = colors.onSurface,
                                )
                            }
                            AngleRuler(
                                angle = state.geometry.angle,
                                onChange = state::setAngle,
                                onEditingChange = { state.isRotating = it },
                                onDegreeTick = { haptic(TapHaptic.SELECTION) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                }
            }
            CropToolGroup(
                selection = state.tool,
                onSelect = {
                    if (it != state.tool) haptic(TapHaptic.SELECTION)
                    state.tool = it
                },
                modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.sm),
            )
        }
    }
}

/** Cancel (start) · Reset (centre, once something changed) · Done (end, primary). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CropTopBar(
    hasChanges: Boolean,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = Spacing.sm)) {
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterStart)) {
            Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        }
        if (hasChanges) {
            TextButton(onClick = onReset, modifier = Modifier.align(Alignment.Center)) {
                Text(stringResource(R.string.crop_reset), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            }
        }
        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterEnd)) {
            Text(
                stringResource(R.string.action_done),
                style = MaterialTheme.typography.bodyLargeEmphasized,
                color = colors.primary,
            )
        }
    }
}
