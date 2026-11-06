package app.pbbls.android.core.ui.souls

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme

private const val TAG = "SoulChooserSearch"

/**
 * The search field that replaces the top bar while looking someone up (mock 11): back closes and
 * clears, the cross clears the query. A failed create shows its line under the field with the
 * query kept (S4). Takes focus when it opens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoulChooserSearchField(
    state: SoulChooserState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // After a frame the field is attached, so the request has a node to land on.
        withFrameNanos { }
        try {
            focusRequester.requestFocus()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "search field not attached when asked for focus", e)
        }
    }
    Column(modifier = modifier.padding(horizontal = PebblesTheme.spacing.lg)) {
        SearchBar(
            modifier = Modifier.fillMaxWidth(),
            expanded = false,
            onExpandedChange = {},
            inputField = {
                SearchBarDefaults.InputField(
                    modifier = Modifier.focusRequester(focusRequester),
                    query = state.query,
                    onQueryChange = state::updateQuery,
                    onSearch = {},
                    expanded = false,
                    onExpandedChange = {},
                    placeholder = { Text(stringResource(R.string.souls_search)) },
                    leadingIcon = {
                        IconButton(onClick = onClose) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.onboarding_back),
                            )
                        }
                    },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = state::clearQuery) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_x_circle),
                                    contentDescription = stringResource(R.string.carve_clear),
                                )
                            }
                        }
                    },
                )
            },
            content = {},
        )
        state.createErrorRes?.let { error ->
            Text(
                text = stringResource(error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = PebblesTheme.spacing.lg, vertical = PebblesTheme.spacing.xs),
            )
        }
    }
}

/** The lens in a host's top bar that opens the field (mock 10, 13): [SoulChooserState.openSearch]. */
@Composable
fun SoulChooserSearchButton(
    state: SoulChooserState,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = state::openSearch, modifier = modifier) {
        Icon(painter = painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.souls_search))
    }
}
