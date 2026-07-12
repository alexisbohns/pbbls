package app.pbbls.android.features.karma

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * Bottom-center overlay hosting the "+N karma" pastille — the iOS
 * `KarmaOverlayRoot` analog (D9). Drawn last in `RootScreen`'s authed branch so
 * it floats above the create/detail surfaces. Observes
 * [KarmaNotificationService.activeCapsule]; the service owns the auto-dismiss.
 */
@Composable
fun KarmaOverlayHost(
    service: KarmaNotificationService,
    modifier: Modifier = Modifier,
) {
    val content = service.activeCapsule
    // Retain the last content during the exit animation so it doesn't blank out.
    var lastContent by remember { mutableStateOf(KarmaEarnedContent(0, KarmaReason.PEBBLE_CREATED)) }
    if (content != null) lastContent = content

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = content != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            val shown = content ?: lastContent
            KarmaEarnedCapsule(
                content = shown,
                onTap = { service.dismiss() },
                modifier = Modifier.padding(bottom = 44.dp),
            )
        }
    }
}

/**
 * The pastille itself: a tonal-elevation [Surface] capsule with a sparkle, the
 * "+N karma" label, and a countdown ring that drains over
 * [KarmaNotificationService.CAPSULE_DURATION_MS]. Fires one
 * [HapticFeedbackConstants.CONFIRM] per fresh [content] (needs a `View`, so it
 * lives here rather than in the service). Ports `KarmaEarnedCapsule.swift`.
 */
@Composable
fun KarmaEarnedCapsule(
    content: KarmaEarnedContent,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    val view = LocalView.current
    val ring = remember { Animatable(1f) }
    val reasonLabel = stringResource(content.reason.labelRes)
    val a11y = stringResource(R.string.karma_flash_a11y, content.amount, reasonLabel)

    LaunchedEffect(content) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }
    LaunchedEffect(content) {
        ring.snapTo(1f)
        ring.animateTo(
            targetValue = 0f,
            animationSpec = tween(KarmaNotificationService.CAPSULE_DURATION_MS.toInt(), easing = LinearEasing),
        )
    }

    Surface(
        shape = CircleShape,
        color = system.background,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier =
            modifier
                .semantics { contentDescription = a11y }
                .clickable(onClick = onTap)
                .drawWithContent {
                    drawContent()
                    drawKarmaRing(ring.value, accent.primary)
                },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_sparkle),
                contentDescription = null,
                tint = accent.primary,
                modifier = Modifier.size(16.dp),
            )
            PebblesText(
                text = stringResource(R.string.karma_flash_amount, content.amount),
                style = PebblesTypography.buttonLabel,
                color = system.foreground,
            )
        }
    }
}

/**
 * Draws the capsule-outline countdown stroke trimmed to [progress] (1 → 0)
 * via [PathMeasure]. A full-perimeter round-rect matching the pastille's pill
 * shape, drained clockwise from the top.
 */
private fun DrawScope.drawKarmaRing(
    progress: Float,
    color: Color,
) {
    if (progress <= 0f) return
    val stroke = 2.5.dp.toPx()
    val inset = stroke / 2f + 1.25.dp.toPx()
    val rect = Rect(inset, inset, size.width - inset, size.height - inset)
    val path = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(rect.height / 2f))) }
    val measure = PathMeasure().apply { setPath(path, false) }
    val partial = Path()
    measure.getSegment(0f, progress * measure.length, partial, true)
    drawPath(partial, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
}
