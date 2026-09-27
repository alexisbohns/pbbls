package app.pbbls.android.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.pbbls.android.R

/**
 * "Confirm it's you" (#976): the recent sign-in step before a high-harm account
 * action. [usesPassword] picks the proof: a password field for accounts with an
 * email identity, a Google round-trip otherwise. Stateless — the caller owns the
 * password text, the working flag and the error.
 *
 * Same chrome as [ConfirmDeleteDialog]. Cancel stays enabled while working:
 * that is how a user abandons the Google round-trip.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReauthDialog(
    usesPassword: Boolean,
    password: String,
    onPasswordChange: (String) -> Unit,
    isWorking: Boolean,
    errorText: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceContainerHigh,
        title = {
            Text(
                text = stringResource(R.string.reauth_title),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = colors.onSurface,
            )
        },
        text = {
            Column {
                Text(
                    text =
                        stringResource(
                            if (usesPassword) R.string.reauth_message_password else R.string.reauth_message_google,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                )
                if (usesPassword) {
                    Spacer(Modifier.height(Spacing.lg))
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = { Text(stringResource(R.string.reauth_password_label)) },
                        singleLine = true,
                        enabled = !isWorking,
                        isError = errorText != null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Done,
                            ),
                        keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                        // The saved password is exactly what this asks for.
                        modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
                    )
                }
                if (errorText != null) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                        // Appears after a submit, away from focus: announce it.
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isWorking && (!usesPassword || password.isNotEmpty())) {
                if (isWorking) {
                    LoadingIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text(
                        text = stringResource(if (usesPassword) R.string.reauth_confirm else R.string.reauth_continue_google),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                )
            }
        },
    )
}
