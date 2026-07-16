package app.pbbls.android.features.profile

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.glyph.models.GlyphStroke
import app.pbbls.android.features.profile.components.ProfileBanner
import app.pbbls.android.features.profile.components.ProfileCollectionsCard
import app.pbbls.android.features.profile.components.ProfileLogoutButton
import app.pbbls.android.features.profile.components.ProfileShortcutsRow
import app.pbbls.android.features.profile.components.ProfileStatsCard
import app.pbbls.android.features.profile.models.Collection
import app.pbbls.android.services.LocalPathStatsService
import app.pbbls.android.services.LocalProfileService
import app.pbbls.android.services.LocalReferenceDataService
import app.pbbls.android.services.LocalSupabaseService
import app.pbbls.android.services.ProfileRow
import app.pbbls.android.theme.PebblesScreen
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTypography
import kotlinx.coroutines.launch

private const val TAG = "profile"

/**
 * The Profile screen — ports iOS `ProfileView.swift` (sub-project C): banner,
 * shortcuts row (tiles appear as their destinations land — D11), stats card,
 * collections carousel (header → list, card → detail, empty tile → the create
 * form as a cover), and log out, with the gear button opening [SettingsScreen]
 * as a full-screen cover (the D5 surface pattern). The Lab card arrives with
 * its own milestone. Navigating away disposes this destination, so returning
 * re-runs the load — the carousel stays fresh after edits in the pushed
 * screens.
 *
 * Deviation from iOS (design D13): a failed profile fetch shows the standard
 * error + Retry treatment instead of iOS's silent empty banner.
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onOpenSouls: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenCollection: (Collection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val profileService = LocalProfileService.current
    val stats = LocalPathStatsService.current
    val supabase = LocalSupabaseService.current
    val refs = LocalReferenceDataService.current
    val scope = rememberCoroutineScope()
    val system = PebblesTheme.colors.system

    var profile by remember { mutableStateOf<ProfileRow?>(null) }
    var glyphStrokes by remember { mutableStateOf<List<GlyphStroke>?>(null) }
    var collections by remember { mutableStateOf<List<Collection>>(emptyList()) }
    var collectionsLoaded by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isPresentingSettings by remember { mutableStateOf(false) }
    var isPresentingCreateCollection by remember { mutableStateOf(false) }
    var loadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(loadKey) {
        isLoading = true
        loadFailed = false
        try {
            val row = profileService.loadProfile()
            profile = row
            row.glyphId?.let { id ->
                try {
                    glyphStrokes = profileService.loadGlyphStrokes(id)
                } catch (e: Exception) {
                    Log.e(TAG, "glyph fetch failed", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "profile fetch failed", e)
            loadFailed = true
        } finally {
            isLoading = false
        }
        try {
            collections = profileService.loadCollections()
        } catch (e: Exception) {
            Log.e(TAG, "collections fetch failed", e)
        } finally {
            collectionsLoaded = true
        }
    }
    LaunchedEffect(Unit) { stats.load() }

    PebblesScreen(
        modifier = modifier,
        topBar = {
            PebblesTopBar(
                title = stringResource(R.string.profile_title),
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.profile_back_a11y),
                            tint = system.secondary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                trailing = {
                    IconButton(onClick = { isPresentingSettings = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_gear),
                            contentDescription = stringResource(R.string.settings_title),
                            tint = system.secondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
            )
        },
    ) {
        when {
            isLoading ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = PebblesTheme.colors.accent.primary)
                }

            loadFailed ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PebblesText(
                        text = stringResource(R.string.profile_load_error),
                        style = PebblesTypography.body,
                        color = system.secondary,
                    )
                    TextButton(onClick = { loadKey++ }) {
                        PebblesText(
                            text = stringResource(R.string.profile_retry),
                            style = PebblesTypography.buttonLabel,
                            color = PebblesTheme.colors.accent.primary,
                        )
                    }
                }

            else ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xl),
                ) {
                    ProfileBanner(
                        displayName = profile?.displayName,
                        memberSince = profile?.createdAt,
                        glyphStrokes = glyphStrokes,
                    )
                    ProfileShortcutsRow(
                        onOpenCollections = onOpenCollections,
                        onOpenSouls = onOpenSouls,
                    )
                    ProfileStatsCard(
                        ripple = stats.ripple,
                        assiduity = stats.assiduity,
                        daysPracticed = stats.daysPracticed,
                        pebbles = stats.pebbles,
                        karma = stats.karma,
                    )
                    ProfileCollectionsCard(
                        collections = collections,
                        hasLoaded = collectionsLoaded,
                        onOpenList = onOpenCollections,
                        onOpenCollection = onOpenCollection,
                        onCreate = { isPresentingCreateCollection = true },
                    )
                    ProfileLogoutButton(onClick = onSignOut)
                }
        }
    }

    if (isPresentingCreateCollection) {
        CollectionFormScreen(
            original = null,
            onDismiss = { isPresentingCreateCollection = false },
            onSaved = {
                isPresentingCreateCollection = false
                loadKey++
                scope.launch { refs.refreshCollections() }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (isPresentingSettings) {
        SettingsScreen(
            initialDisplayName = profile?.displayName.orEmpty(),
            initialGlyphId = profile?.glyphId,
            initialGlyphStrokes = glyphStrokes,
            email = supabase.session?.user?.email,
            providers =
                linkedProviders(
                    supabase.session
                        ?.user
                        ?.identities
                        ?.map { it.provider },
                ),
            onDismiss = { isPresentingSettings = false },
            onSaved = { newName, newGlyph ->
                profile = profile?.copy(displayName = newName, glyphId = newGlyph?.id ?: profile?.glyphId)
                newGlyph?.strokes?.let { glyphStrokes = it }
                isPresentingSettings = false
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * SSO provider labels from the session identities — mirrors
 * `SettingsSheet.linkedProviders`: brand names, rendered verbatim (never
 * localized); the implicit `email` identity is not a provider.
 */
internal fun linkedProviders(providers: List<String>?): List<String> =
    providers.orEmpty().mapNotNull {
        when (it) {
            "apple" -> "Apple"
            "google" -> "Google"
            else -> null
        }
    }
