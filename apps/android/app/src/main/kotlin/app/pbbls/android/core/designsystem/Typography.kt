package app.pbbls.android.core.designsystem

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

private fun TextStyle.ysabeau() = copy(fontFamily = YsabeauFamily, fontFeatureSettings = YSABEAU_NUMBER_FEATURES)

private fun TextStyle.inclusiveSans() = copy(fontFamily = InclusiveSansFamily)

private val Baseline = Typography()

/**
 * The export's type (#853): M3's default scale, faces swapped — Ysabeau for
 * display/headline/title, Inclusive Sans for body/label — including every
 * Expressive `*Emphasized` style so stock components and app code agree.
 * Sizes are deliberately the baseline's: the old 17 sp iOS rhythm is gone.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val PebblesMaterialTypography: Typography =
    Typography(
        displayLarge = Baseline.displayLarge.ysabeau(),
        displayMedium = Baseline.displayMedium.ysabeau(),
        displaySmall = Baseline.displaySmall.ysabeau(),
        headlineLarge = Baseline.headlineLarge.ysabeau(),
        headlineMedium = Baseline.headlineMedium.ysabeau(),
        headlineSmall = Baseline.headlineSmall.ysabeau(),
        titleLarge = Baseline.titleLarge.ysabeau(),
        titleMedium = Baseline.titleMedium.ysabeau(),
        titleSmall = Baseline.titleSmall.ysabeau(),
        bodyLarge = Baseline.bodyLarge.inclusiveSans(),
        bodyMedium = Baseline.bodyMedium.inclusiveSans(),
        bodySmall = Baseline.bodySmall.inclusiveSans(),
        labelLarge = Baseline.labelLarge.inclusiveSans(),
        labelMedium = Baseline.labelMedium.inclusiveSans(),
        labelSmall = Baseline.labelSmall.inclusiveSans(),
        displayLargeEmphasized = Baseline.displayLargeEmphasized.ysabeau(),
        displayMediumEmphasized = Baseline.displayMediumEmphasized.ysabeau(),
        displaySmallEmphasized = Baseline.displaySmallEmphasized.ysabeau(),
        headlineLargeEmphasized = Baseline.headlineLargeEmphasized.ysabeau(),
        headlineMediumEmphasized = Baseline.headlineMediumEmphasized.ysabeau(),
        headlineSmallEmphasized = Baseline.headlineSmallEmphasized.ysabeau(),
        titleLargeEmphasized = Baseline.titleLargeEmphasized.ysabeau(),
        titleMediumEmphasized = Baseline.titleMediumEmphasized.ysabeau(),
        titleSmallEmphasized = Baseline.titleSmallEmphasized.ysabeau(),
        bodyLargeEmphasized = Baseline.bodyLargeEmphasized.inclusiveSans(),
        bodyMediumEmphasized = Baseline.bodyMediumEmphasized.inclusiveSans(),
        bodySmallEmphasized = Baseline.bodySmallEmphasized.inclusiveSans(),
        labelLargeEmphasized = Baseline.labelLargeEmphasized.inclusiveSans(),
        labelMediumEmphasized = Baseline.labelMediumEmphasized.inclusiveSans(),
        labelSmallEmphasized = Baseline.labelSmallEmphasized.inclusiveSans(),
    )
