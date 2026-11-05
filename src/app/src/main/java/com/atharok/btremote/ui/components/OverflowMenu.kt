package com.atharok.btremote.ui.components

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.atharok.btremote.R
import com.atharok.btremote.common.extensions.autoMirroredIcon
import com.atharok.btremote.common.utils.AppIcons
import com.atharok.btremote.ui.theme.surfaceElevationHigh

@Composable
private fun OverflowMenu(
    item: @Composable (showMenu: () -> Unit) -> Unit,
    content: @Composable (closeDropdownMenu: () -> Unit) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    item { showMenu = !showMenu }

    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false },
        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(surfaceElevationHigh())
    ) {
        content { showMenu = false }
    }
}

// ---- DropdownMenu ----

@Composable
fun BasicOverflowMenu(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable (closeDropdownMenu: () -> Unit) -> Unit
) {
    OverflowMenu(
        item = {
            BasicIconButton(
                onClick = it,
                icon = icon,
                contentDescription = contentDescription,
                modifier = modifier
            )
        },
        content = content
    )
}

@Composable
fun MoreOverflowMenu(
    modifier: Modifier = Modifier,
    content: @Composable (closeDropdownMenu: () -> Unit) -> Unit
) {
    BasicOverflowMenu(
        icon = AppIcons.MoreVert,
        contentDescription = stringResource(id = R.string.more),
        modifier = modifier,
        content = content
    )
}

// ---- DropdownMenuItem ----

@Composable
fun BasicDropdownMenuItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    DropdownMenuItem(
        text = {
            TextNormal(
                text = text,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        onClick = onClick,
        modifier = modifier,
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.autoMirroredIcon(icon)
            )
        }
    )
}