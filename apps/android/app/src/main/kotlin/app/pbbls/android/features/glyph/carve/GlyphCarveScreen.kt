package app.pbbls.android.features.glyph.carve

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.features.glyph.models.GlyphStroke
import app.pbbls.android.features.glyph.services.LocalGlyphService
import app.pbbls.android.features.path.render.GlyphImage
import app.pbbls.android.theme.PebblesDestructive
import app.pbbls.android.theme.PebblesScreen
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTopBarTextButton
import app.pbbls.android.theme.PebblesTypography
import kotlinx.coroutines.launch

private const val TAG = "glyph-carve"

/** The iOS carve constants (M43 D1/D2): canvas side in dp-space, RDP epsilon, stored stroke width. */
private const val CANVAS_SIDE = 280.0
private const val EPSILON = 1.5
private const val STORED_WIDTH = 6.0
private const val VIEW_BOX_SIDE = 200.0

/**
 * The carve studio — ports iOS `GlyphCarveSheet` as a full-screen cover
 * (M43 D2: a downward stroke must never dismiss it; exits only via
 * Cancel/Save). Optional name field → hard-white canvas (both themes) →
 * Undo/Clear pills. Commit pipeline per stroke: clamp to the canvas → RDP
 * (ε 1.5, canvas space) → ×(200/side) → byte-parity serializer →
 * `GlyphStroke(d, 6.0)`. Cancel with strokes asks "Discard your glyph?";
 * save failure keeps the strokes with an inline error.
 */
@Composable
fun GlyphCarveScreen(
    onSaved: (Glyph) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glyphService = LocalGlyphService.current
    val system = PebblesTheme.colors.system
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var strokes by remember { mutableStateOf<List<GlyphStroke>>(emptyList()) }
    var isSaving by remember { mutableStateOf(false) }
    var showSaveError by remember { mutableStateOf(false) }
    var showDiscardAlert by remember { mutableStateOf(false) }

    fun cancel() {
        if (isSaving) return
        if (strokes.isEmpty()) onCancel() else showDiscardAlert = true
    }

    fun save() {
        if (strokes.isEmpty() || isSaving) return
        scope.launch {
            isSaving = true
            showSaveError = false
            try {
                val glyph = glyphService.create(strokes = strokes, name = name)
                onSaved(glyph)
            } catch (e: Exception) {
                Log.e(TAG, "glyph save failed", e)
                showSaveError = true
                isSaving = false
            }
        }
    }

    BackHandler(enabled = !isSaving) { cancel() }

    PebblesScreen(
        modifier = modifier.background(system.background),
        topBar = {
            PebblesTopBar(
                title = stringResource(R.string.carve_title),
                leading = {
                    PebblesTopBarTextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { cancel() },
                    )
                },
                trailing = {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = PebblesTheme.colors.accent.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        PebblesTopBarTextButton(
                            text = stringResource(R.string.action_save),
                            onClick = { save() },
                            enabled = strokes.isNotEmpty(),
                            color = if (strokes.isNotEmpty()) system.secondary else system.muted,
                        )
                    }
                },
            )
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle =
                    PebblesTypography.title.copy(
                        color = system.foreground,
                        textAlign = TextAlign.Center,
                    ),
                cursorBrush = SolidColor(PebblesTheme.colors.accent.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (name.isEmpty()) {
                            PebblesText(
                                text = stringResource(R.string.carve_name_placeholder),
                                style = PebblesTypography.title,
                                color = system.muted,
                                textAlign = TextAlign.Center,
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            CarveCanvas(
                strokes = strokes,
                onStrokeCommitted = { stroke -> strokes = strokes + stroke },
            )

            if (showSaveError) {
                PebblesText(
                    text = stringResource(R.string.carve_save_error),
                    style = PebblesTypography.callout,
                    color = PebblesDestructive,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                CarvePillButton(
                    iconRes = R.drawable.ic_undo,
                    label = stringResource(R.string.carve_undo),
                    enabled = strokes.isNotEmpty(),
                    onClick = { strokes = strokes.dropLast(1) },
                )
                CarvePillButton(
                    iconRes = R.drawable.ic_trash,
                    label = stringResource(R.string.carve_clear),
                    enabled = strokes.isNotEmpty(),
                    onClick = { strokes = emptyList() },
                )
            }
        }
    }

    if (showDiscardAlert) {
        AlertDialog(
            onDismissRequest = { showDiscardAlert = false },
            containerColor = system.background,
            title = {
                PebblesText(
                    text = stringResource(R.string.carve_discard_title),
                    style = PebblesTypography.headlineEmphasized,
                    color = system.foreground,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardAlert = false
                        onCancel()
                    },
                ) {
                    PebblesText(
                        text = stringResource(R.string.carve_discard),
                        style = PebblesTypography.buttonLabel,
                        color = PebblesDestructive,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardAlert = false }) {
                    PebblesText(
                        text = stringResource(R.string.carve_keep_editing),
                        style = PebblesTypography.buttonLabel,
                        color = PebblesTheme.colors.accent.primary,
                    )
                }
            },
        )
    }
}

/**
 * The 280dp drawing surface: hard-white in both themes (D2), committed
 * strokes re-rendered through the shared [GlyphImage] pipeline (the same
 * renderer every surface uses — no second parser), the in-progress stroke a
 * raw un-simplified polyline. Gesture capture converts px → dp so the RDP
 * epsilon applies in the iOS point space. `internal` for screenshots.
 */
@Composable
internal fun CarveCanvas(
    strokes: List<GlyphStroke>,
    onStrokeCommitted: (GlyphStroke) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = PebblesTheme.colors.accent
    val system = PebblesTheme.colors.system
    val density = LocalDensity.current
    var activePoints by remember { mutableStateOf<List<CarvePoint>>(emptyList()) }
    val strokesA11y = stringResource(R.string.carve_canvas_strokes_a11y, strokes.size)

    fun commit() {
        val committed = activePoints
        activePoints = emptyList()
        if (committed.isEmpty()) return
        val simplified = PathSimplification.simplify(committed, EPSILON)
        val scale = VIEW_BOX_SIDE / CANVAS_SIDE
        val scaled = simplified.map { CarvePoint(it.x * scale, it.y * scale) }
        val d = SvgPathSerializer.svgPathString(scaled)
        if (d.isNotEmpty()) onStrokeCommitted(GlyphStroke(d = d, width = STORED_WIDTH))
    }

    Box(
        modifier =
            modifier
                .size(CANVAS_SIDE.dp)
                .clip(RoundedCornerShape(PebblesTheme.spacing.xxl))
                .background(Color.White)
                .border(1.dp, system.muted, RoundedCornerShape(PebblesTheme.spacing.xxl))
                .clearAndSetSemantics { contentDescription = strokesA11y }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val toDp = { px: Float -> (px / density.density).toDouble().coerceIn(0.0, CANVAS_SIDE) }
                        activePoints = listOf(CarvePoint(toDp(down.position.x), toDp(down.position.y)))
                        drag(down.id) { change ->
                            activePoints =
                                activePoints + CarvePoint(toDp(change.position.x), toDp(change.position.y))
                            change.consume()
                        }
                        commit()
                    }
                },
    ) {
        GlyphImage(
            strokes = strokes,
            viewBox = "0 0 200 200",
            strokeColor = accent.primary,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (activePoints.isNotEmpty()) {
                val px = { v: Double -> (v * density.density).toFloat() }
                val path =
                    Path().apply {
                        moveTo(px(activePoints.first().x), px(activePoints.first().y))
                        activePoints.drop(1).forEach { lineTo(px(it.x), px(it.y)) }
                    }
                drawPath(
                    path = path,
                    color = accent.primary,
                    style =
                        Stroke(
                            width = px(STORED_WIDTH * (CANVAS_SIDE / VIEW_BOX_SIDE)),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                )
            }
        }
    }
}

/** Undo/Clear pill — accent label + icon on an accent-surface capsule (iOS chrome). */
@Composable
private fun CarvePillButton(
    iconRes: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val accent = PebblesTheme.colors.accent
    val system = PebblesTheme.colors.system
    val color = if (enabled) accent.primary else system.muted
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(accent.surface)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        PebblesText(
            text = label,
            style = PebblesTypography.buttonLabel,
            color = color,
        )
    }
}
