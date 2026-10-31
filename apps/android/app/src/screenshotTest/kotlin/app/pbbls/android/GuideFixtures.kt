package app.pbbls.android

import app.pbbls.android.core.model.home.Guide
import app.pbbls.android.core.model.home.GuideIllustration

/**
 * The nine published readings as the `guides` seed writes them (#1383 D13), in Kotlin: the screenshot source set
 * cannot read the unit tests' JSON. No French body, as the seed: a French render shows the field-by-field fallback.
 */
internal object GuideFixtures {
    private fun guide(
        slug: String,
        sortOrder: Int,
        titleEn: String,
        titleFr: String,
        summaryEn: String,
        summaryFr: String,
        body: String,
    ): Guide =
        Guide(
            id = "guide-$slug",
            slug = slug,
            sortOrder = sortOrder,
            illustration = GuideIllustration.fromWire(slug),
            titleEn = titleEn,
            titleFr = titleFr,
            summaryEn = summaryEn,
            summaryFr = summaryFr,
            bodyEn = body,
            bodyFr = null,
        )

    val all: List<Guide> =
        listOf(
            guide(
                "pebbles",
                10,
                "About Pebbles",
                "À propos de Pebbles",
                "The private memory of your life with the people in it.",
                "La mémoire privée de ta vie avec les gens qui la peuplent.",
                "Pebbles is a private journal of moments. A moment is a time and a name. A pebble is that moment " +
                    "completed with how it felt. Nothing here is shared unless you choose to.",
            ),
            guide(
                "stones",
                20,
                "About the stones",
                "À propos des pierres",
                "How a stone is built from intensity, positiveness and emotion.",
                "Comment une pierre naît de l'intensité, de la valence et de l'émotion.",
                "Every pebble is drawn as a stone. Its size is the intensity: small, medium or large. Its kind is the " +
                    "positiveness: a lava stone for a low moment, a river pebble for an even one, a gem for a bright one. " +
                    "Its colour is the **emotion**.",
            ),
            guide(
                "emotions",
                30,
                "About emotions",
                "À propos des émotions",
                "Seven families, and the finer emotions inside them.",
                "Sept familles, et les émotions plus fines qu'elles abritent.",
                "Each pebble carries one emotion. Emotions belong to seven families: joy, pride, anger, fear, shame, " +
                    "sadness and peace. The family gives the pebble its palette; the finer emotion gives it its name.",
            ),
            guide(
                "valence",
                40,
                "About valence",
                "À propos de la valence",
                "Bright, even or heavy: what a moment did to you.",
                "Lumineux, égal ou lourd : ce qu'un moment t'a fait.",
                "Positiveness says whether a moment lifted you, left you even, or weighed on you.",
            ),
            guide(
                "domains",
                50,
                "About domains",
                "À propos des domaines",
                "The areas of life a moment touches.",
                "Les domaines de la vie qu'un moment touche.",
                "A domain is an area of your life: family, work, health, friends, travel, and more.",
            ),
            guide(
                "collections",
                60,
                "About collections",
                "À propos des collections",
                "Stacks, packs and tracks: pebbles gathered along a thread.",
                "Piles, séries et parcours : des galets réunis par un fil.",
                "A collection gathers pebbles along a thread.",
            ),
            guide(
                "souls",
                70,
                "About souls",
                "À propos des âmes",
                "The people and beings in your life.",
                "Les personnes et les êtres de ta vie.",
                "A soul is someone in your life: a person, or a being like your dog.",
            ),
            guide(
                "glyphs",
                80,
                "About glyphs",
                "À propos des glyphes",
                "Marks you carve, instead of photos of people.",
                "Des marques que tu graves, plutôt que des photos de gens.",
                "A glyph is a small mark you carve by hand.",
            ),
            guide(
                "karma",
                90,
                "About karma",
                "À propos du karma",
                "A quiet count of what you keep.",
                "Un compte discret de ce que tu gardes.",
                "Karma grows as you record and complete moments and reach milestones.",
            ),
        )

    fun bySlug(slug: String): Guide = all.first { it.slug == slug }
}
