package app.pbbls.android.core.model

/**
 * An uncompleted moment's stone colours (R4): the web `BLANK_PALETTE` ramp,
 * fixed greys shared by every surface, never theme roles or an emotion palette.
 * Surface is the primary at 10 % alpha, the rule the emotion palettes follow.
 */
val BlankPalette: EmotionPalette =
    requireNotNull(
        EmotionPalette.fromHex(
            primaryHex = "#868686FF",
            secondaryHex = "#B9B9B9FF",
            lightHex = "#F3F3F3FF",
            surfaceHex = "#8686861A",
            darkHex = "#1B1B1BFF",
            shadedHex = "#5E5E5EFF",
        ),
    ) { "BLANK_PALETTE hexes must parse" }
