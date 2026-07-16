package app.pbbls.android.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Shared chrome for Profile-screen cards (Stats, Collections, Lab) — the
 * `.profileCard()` analog (iOS `Theme/ProfileCard.swift`): clear background,
 * 1dp `system.muted` border, `Spacing.lg` corner radius, `Spacing.lg` inner
 * padding. Order matters: the border/clip own the card bounds, the padding is
 * inside them (the iOS modifier pads first because SwiftUI modifiers wrap
 * outward; Compose modifiers wrap inward, so the chain is reversed here).
 */
fun Modifier.profileCard(): Modifier =
    composed {
        val radius = PebblesTheme.spacing.lg
        val shape = RoundedCornerShape(radius)
        this
            .border(1.dp, PebblesTheme.colors.system.muted, shape)
            .clip(shape)
            .padding(PebblesTheme.spacing.lg)
    }
