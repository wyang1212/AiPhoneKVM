package com.atharok.btremote.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.atharok.btremote.R
import com.atharok.btremote.common.extensions.autoMirroredIcon
import com.atharok.btremote.common.utils.AppIcons

// ---- Basic IconButton ----

@Composable
fun BasicIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    shape: Shape = IconButtonDefaults.standardShape,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        shape = shape
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.autoMirroredIcon(icon)
        )
    }
}

// ---- Some common IconButtons ----

@Composable
fun NavigateUpIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BasicIconButton(
        onClick = onClick,
        icon = AppIcons.Back,
        contentDescription = stringResource(id = R.string.back),
        modifier = modifier
    )
}

@Composable
fun SettingsIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BasicIconButton(
        onClick = onClick,
        icon = AppIcons.Settings,
        contentDescription = stringResource(id = R.string.settings),
        modifier = modifier
    )
}

@Composable
fun HelpIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BasicIconButton(
        onClick = onClick,
        icon = AppIcons.Help,
        contentDescription = stringResource(id = R.string.help),
        modifier = modifier
    )
}