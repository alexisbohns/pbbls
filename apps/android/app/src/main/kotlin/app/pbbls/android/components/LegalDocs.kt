package app.pbbls.android.components

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * The two legal documents linked from the funnel. URLs match iOS
 * (`LegalDocumentSheet.swift`).
 */
enum class LegalDoc(
    val url: String,
) {
    TERMS("https://www.pbbls.app/docs/terms"),
    PRIVACY("https://www.pbbls.app/docs/privacy"),
}

/**
 * Opens a legal document in a Custom Tab — the in-app-browser analog of iOS's
 * `SFSafariViewController` sheet (D15). The `pebbles://legal/...` scheme never
 * reaches the OS: links are intercepted in-Compose (see [LegalDisclaimer]) and
 * routed here.
 */
fun openLegalDoc(
    context: Context,
    doc: LegalDoc,
) {
    CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(doc.url))
}
