package app.pbbls.android.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * Rounded-rectangle text input with a static 1dp border (no focus state, to
 * match iOS 1:1). White fill, `system.secondary` for both placeholder and
 * typed content. Ports `apps/ios/Pebbles/Components/Inputs/PebblesTextInput.swift`.
 */
@Composable
fun PebblesTextInput(
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isSecure: Boolean = false,
    contentType: ContentType? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    maxLines: Int = 1,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    val minHeight = 52.dp
    val horizontalPadding = 16.dp
    val shape = RoundedCornerShape(12.dp)

    val fieldModifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .let { base -> if (contentType != null) base.semantics { this.contentType = contentType } else base }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .background(Color.White, shape)
                .border(1.dp, system.muted, shape),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier,
            textStyle = PebblesTypography.body.copy(color = system.secondary),
            singleLine = singleLine,
            maxLines = maxLines,
            cursorBrush = SolidColor(accent.primary),
            visualTransformation = if (isSecure) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(text = placeholder, style = PebblesTypography.body, color = system.secondary)
                    }
                    innerTextField()
                }
            },
        )
    }
}
