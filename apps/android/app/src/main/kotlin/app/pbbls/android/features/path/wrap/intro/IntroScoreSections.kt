package app.pbbls.android.features.path.wrap.intro

/**
 * A section's head (iOS `IntroScore.head`): the insights head first when it leads tone or hours, then the
 * head, then the lead line if any. One arrival per card: the insights head ticks it when it leads, the head
 * otherwise. Both heads are drawn from the start (D8): their beats carry only the tick.
 */
fun IntroScore.head(
    hasLead: Boolean,
    insights: Boolean,
): List<IntroBeat> {
    val beats = mutableListOf<IntroBeat>()
    if (insights) beats += IntroBeat(IntroKey.Insights, 0.0, 0.0, IntroTick.arrive)
    val shift = shift(insights)
    beats += IntroBeat(IntroKey.Header, shift, 0.0, if (insights) null else IntroTick.arrive)
    if (hasLead) beats += IntroBeat(IntroKey.Lead, shift + IntroScore.LEAD_AT, IntroMotion.RISE)
    return beats
}

/** How much later a section's own beats come: the insights head's shift when it leads. */
fun IntroScore.shift(insights: Boolean): Double = if (insights) IntroScore.INSIGHTS_SHIFT else 0.0
