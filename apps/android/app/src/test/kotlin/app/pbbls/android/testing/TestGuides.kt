package app.pbbls.android.testing

import app.pbbls.android.core.model.home.Guide
import app.pbbls.android.core.model.home.GuideIllustration

/** About Pebbles readings for the JVM tests (#1383 D13): EN and FR words built from the [slug]. */
object TestGuides {
    fun guide(
        slug: String,
        sortOrder: Int,
        illustration: GuideIllustration? = null,
    ): Guide =
        Guide(
            id = "00000000-0000-0000-0000-00000000000$sortOrder",
            slug = slug,
            sortOrder = sortOrder,
            illustration = illustration,
            titleEn = "Title of $slug",
            titleFr = "Titre de $slug",
            summaryEn = "Summary of $slug",
            summaryFr = null,
            bodyEn = "Body of $slug.",
            bodyFr = null,
        )
}
