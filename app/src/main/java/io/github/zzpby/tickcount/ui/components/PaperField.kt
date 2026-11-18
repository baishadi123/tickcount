package io.github.zzpby.tickcount.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextFieldColors
import androidx.compose.ui.unit.dp

/**
 * A field on paper: no box, no fill — the label a small note above, the text writing
 * itself on the page, and one hairline underneath as the line it is written on.
 *
 * The whole app's fields go through here, so "a form" reads as filling in a printed
 * page rather than facing a row of boxes: the world supplies the paper, this supplies
 * the rule you write on. Focus says so with the accent, quietly.
 */
@Composable
fun PaperField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    supportingText: @Composable (() -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = label?.let { text -> { Text(text) } },
        placeholder = placeholder?.let { text -> { Text(text) } },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions ?: androidx.compose.foundation.text.KeyboardActions.Default,
        visualTransformation = visualTransformation,
        supportingText = supportingText,
        // Flat bottom, softened shoulders: the indicator stays a ruled line, and
        // the rounded top corners only whisper the shape language the cards speak.
        shape = RoundedCornerShape(
            topStart = 14.dp,
            topEnd = 14.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp,
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
            disabledIndicatorColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            errorIndicatorColor = MaterialTheme.colorScheme.error,
        ),
    )
}

/** Exposed for callers that need to compose with the same recipe's colors. */
@Composable
fun paperFieldColors(): TextFieldColors = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
    unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
)
