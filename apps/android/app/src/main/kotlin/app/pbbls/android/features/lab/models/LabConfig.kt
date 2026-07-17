package app.pbbls.android.features.lab.models

/**
 * Lab constants — ports iOS `LabConfig`. Cover assets live in the PUBLIC
 * `lab-assets` bucket, so URLs are plain object paths with no signing —
 * unlike the private `pebbles-media` snaps (M44 design D7).
 */
object LabConfig {
    const val ASSETS_BUCKET = "lab-assets"

    /** The community invite the featured card opens externally (design D8). */
    const val WHATSAPP_INVITE_URL = "https://chat.whatsapp.com/CA2MvH7035JDwM4VqnVg9M"

    /**
     * Public URL for a cover image, or null when [coverImagePath] is null OR
     * empty (iOS guards both before building the URL).
     */
    fun coverImageUrl(
        supabaseUrl: String,
        coverImagePath: String?,
    ): String? =
        coverImagePath
            ?.takeIf { it.isNotEmpty() }
            ?.let { "${supabaseUrl.trimEnd('/')}/storage/v1/object/public/$ASSETS_BUCKET/$it" }
}
