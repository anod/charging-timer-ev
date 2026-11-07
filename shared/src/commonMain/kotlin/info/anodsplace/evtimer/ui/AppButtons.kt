package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val ActionButtonMinHeight = 56.dp
private val TextButtonMinHeight = 48.dp
private val IconButtonMinSize = 48.dp

@Composable
fun AppPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = ActionButtonMinHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        content = content
    )
}

@Composable
fun AppSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = ActionButtonMinHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        content = content
    )
}

@Composable
fun AppDestructiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = ActionButtonMinHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        ),
        content = content
    )
}

@Composable
fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = TextButtonMinHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        content = content
    )
}

@Composable
fun AppIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.sizeIn(minWidth = IconButtonMinSize, minHeight = IconButtonMinSize),
        enabled = enabled,
        content = content
    )
}
