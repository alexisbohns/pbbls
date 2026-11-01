package app.pbbls.android.core.designsystem

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import app.pbbls.android.core.model.SettingsLink

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

/**
 * Opens one of Settings' admin-edited links (#1489, iOS `SettingsSheet`): `https` in a Custom Tab,
 * like [openLegalDoc]; `mailto:` handed to the system's mail app. The funnel keeps [LegalDoc]
 * (spec D2): consent is bound to a document version and never depends on admin data.
 */
fun openSettingsLink(
    context: Context,
    link: SettingsLink,
) {
    val uri = link.url.toUri()
    try {
        if (link.opensInBrowser) {
            CustomTabsIntent.Builder().build().launchUrl(context, uri)
        } else {
            context.startActivity(Intent(Intent.ACTION_SENDTO, uri))
        }
    } catch (e: ActivityNotFoundException) {
        Log.w("settings-links", "nothing on this device opens ${link.key}", e)
    }
}
