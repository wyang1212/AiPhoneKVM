package com.atharok.btremote.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.atharok.btremote.R
import com.atharok.btremote.common.extensions.getDimensionDp
import com.atharok.btremote.common.utils.AppIcons
import com.atharok.btremote.ui.components.AppScaffold
import com.atharok.btremote.ui.components.MaterialButton
import com.atharok.btremote.ui.components.MaterialOutlinedButton
import com.atharok.btremote.ui.components.NavigateUpIconButton
import com.atharok.btremote.ui.components.TextMedium
import com.atharok.btremote.ui.components.TextNormalSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val MAX_PAGES = 5

@Composable
fun DistantDevicePairScreen(
    localDeviceName: String,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current
) {
    val scrollState: ScrollState = rememberScrollState()
    val pagerState: PagerState = rememberPagerState(pageCount = { MAX_PAGES })
    val coroutineScope: CoroutineScope = rememberCoroutineScope()

    val pagerContents: Array<@Composable () -> Unit> = remember {
        createPages(
            context = context,
            localDeviceName = localDeviceName,
            verticalPadding = context.getDimensionDp(R.dimen.padding_large)
        )
    }

    StatelessDistantDevicePairScreen(
        pages = pagerContents,
        scrollState = scrollState,
        pagerState = pagerState,
        coroutineScope = coroutineScope,
        navigateUp = navigateUp,
        modifier = modifier
    )
}

private fun createPages(
    context: Context,
    localDeviceName: String,
    verticalPadding: Dp
): Array<@Composable () -> Unit> = arrayOf(
    { Page1(localDeviceName, Modifier.padding(vertical = verticalPadding)) },
    { Page2(context, localDeviceName, Modifier.padding(vertical = verticalPadding)) },
    { Page3(localDeviceName, Modifier.padding(vertical = verticalPadding)) },
    { Page4(context, Modifier.padding(vertical = verticalPadding)) },
    { Page5(context, Modifier.padding(vertical = verticalPadding)) }
)

@Composable
private fun StatelessDistantDevicePairScreen(
    pages: Array<@Composable () -> Unit>,
    scrollState: ScrollState,
    pagerState: PagerState,
    coroutineScope: CoroutineScope,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppScaffold(
        title = stringResource(id = R.string.pairing_a_device),
        modifier = modifier,
        navigateUp = {
            NavigateUpIconButton(navigateUp)
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier.fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = dimensionResource(id = R.dimen.padding_large)),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .weight(1f),
                verticalAlignment = Alignment.Top,
                userScrollEnabled = false
            ) { pageIndex: Int ->
                pages[pageIndex]()
            }

            DistantDevicePairBottomView(
                pageIndex = pagerState.currentPage,
                previous = {
                    if (pagerState.currentPage > 0) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    }
                },
                next = {
                    if (pagerState.currentPage < MAX_PAGES - 1) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        navigateUp()
                    }
                },
                modifier = Modifier
                    .padding(bottom = dimensionResource(id = R.dimen.padding_large))
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun Page1(
    localDeviceName: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(space = dimensionResource(id = R.dimen.padding_large))
    ) {
        TextMedium(text = stringResource(id = R.string.pairing_from_a_remote_device_intro_title))
        TextNormalSecondary(text = stringResource(id = R.string.pairing_from_a_remote_device_intro_content))
        TextNormalSecondary(text = stringResource(id = R.string.name_of_this_device, localDeviceName))
    }
}

@Composable
private fun Page2(
    context: Context,
    localDeviceName: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(space = dimensionResource(id = R.dimen.padding_large))
    ) {
        TextMedium(text = stringResource(id = R.string.pairing_from_a_remote_device_step_1_title))
        TextNormalSecondary(text = stringResource(id = R.string.pairing_from_a_remote_device_step_1_content_1))
        TextNormalSecondary(text = stringResource(id = R.string.pairing_from_a_remote_device_step_1_content_2, localDeviceName))
        MaterialButton(
            onClick = {
                val enableDiscoverableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                }
                context.startActivity(enableDiscoverableBtIntent)
            },
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(id = R.string.enabled_bluetooth_visibility),
            icon = AppIcons.Visibility
        )
    }
}

@Composable
private fun Page3(
    localDeviceName: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(space = dimensionResource(id = R.dimen.padding_large))
    ) {
        TextMedium(text = stringResource(id = R.string.pairing_from_a_remote_device_step_2_title))
        TextNormalSecondary(text = stringResource(id = R.string.pairing_from_a_remote_device_step_2_content_1))
        TextNormalSecondary(text = stringResource(id = R.string.pairing_from_a_remote_device_step_2_content_2, localDeviceName))
        TextNormalSecondary(text = stringResource(id = R.string.pairing_from_a_remote_device_step_2_content_3))
        TextNormalSecondary(text = stringResource(id = R.string.pairing_from_a_remote_device_step_2_content_4))
    }
}

@Composable
private fun Page4(
    context: Context,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(space = dimensionResource(id = R.dimen.padding_large))
    ) {
        TextMedium(text = stringResource(id = R.string.pairing_from_a_remote_device_step_3_title))
        TextNormalSecondary(text = stringResource(id = R.string.pairing_from_a_remote_device_step_3_content_1))
        TextNormalSecondary(text = stringResource(id = R.string.pairing_from_a_remote_device_step_3_content_2))
        MaterialButton(
            onClick = {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            },
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(id = R.string.open_bluetooth_settings),
            icon = AppIcons.Settings
        )
    }
}

@Composable
private fun Page5(
    context: Context,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(space = dimensionResource(id = R.dimen.padding_large))
    ) {
        TextMedium(text = stringResource(id = R.string.pairing_from_a_remote_device_step_4_title))
        TextNormalSecondary(text = stringResource(id = R.string.pairing_from_a_remote_device_step_4_content, context.getString(R.string.done)))
    }
}

@Composable
private fun DistantDevicePairBottomView(
    pageIndex: Int,
    previous: () -> Unit,
    next: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if(pageIndex > 0) {
            MaterialOutlinedButton(
                onClick = previous,
                modifier = Modifier.padding(end = dimensionResource(id = R.dimen.padding_normal)).weight(0.5f),
                text = stringResource(id = R.string.previous)
            )
        } else {
            Spacer(modifier = Modifier.padding(end = dimensionResource(id = R.dimen.padding_normal)).weight(0.5f))
        }

        MaterialButton(
            onClick = next,
            modifier = Modifier.padding(start = dimensionResource(id = R.dimen.padding_normal)).weight(0.5f),
            text = if(pageIndex < MAX_PAGES - 1) stringResource(id = R.string.next) else stringResource(id = R.string.done)
        )
    }
}