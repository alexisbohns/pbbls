package app.pbbls.android.features.path.wrap.month

import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.intro.IntroBeat
import app.pbbls.android.features.path.wrap.intro.IntroScore
import app.pbbls.android.features.path.wrap.intro.IntroSequence
import app.pbbls.android.features.path.wrap.intro.head

/** The month's deck (#1242 D21; iOS `MonthDeck`'s statics): which cards it deals, and what each plays. */
internal object MonthDeck {
    /** The cover if shown, each section that shows in the server's order, then the end card; nothing for an empty month. */
    fun cards(
        wrap: MonthWrap,
        lookups: WrapLookups,
    ): List<IntroSequence> {
        val cover = if (wrap.cover != null) listOf(IntroSequence.Cover) else emptyList()
        val dealt = cover + wrap.sections.filter { MonthSections.isShown(it, wrap, lookups) }.map { IntroSequence.Section(it) }
        return if (dealt.isEmpty()) emptyList() else dealt + IntroSequence.End
    }

    /**
     * The first tone or hours card carries the insights head. Over the dealt [cards], so it agrees with
     * [MonthSections.leadsInsights], which reads the same condition off the wrap.
     */
    fun leadsInsights(
        card: IntroSequence,
        cards: List<IntroSequence>,
    ): Boolean =
        card ==
            cards.firstOrNull {
                it == IntroSequence.Section(MonthSection.TONE) || it == IntroSequence.Section(MonthSection.HOURS)
            }

    /**
     * What a card plays. The cover its garland; a section, for now, its arrival alone (its entrance lands in
     * the parts after this one); the end card nothing yet.
     */
    fun beats(
        card: IntroSequence,
        wrap: MonthWrap,
        lookups: WrapLookups,
        leadsInsights: Boolean,
    ): List<IntroBeat> =
        when (card) {
            IntroSequence.Cover -> coverBeats(wrap, lookups)
            is IntroSequence.Section -> IntroScore.head(hasLead = false, insights = leadsInsights)
            IntroSequence.End -> emptyList()
        }
}
