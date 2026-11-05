// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.ui.views

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.atharok.btremote.R
import com.atharok.btremote.common.utils.AppIcons
import com.atharok.btremote.ui.components.TemplateModalBottomSheet
import com.atharok.btremote.ui.components.TextMedium
import com.atharok.btremote.ui.components.TextNormalSecondary

@Composable
private fun HelpModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (ColumnScope.() -> Unit)
) {
    TemplateModalBottomSheet(
        title = stringResource(id = R.string.help),
        icon = AppIcons.Help,
        iconDescription = stringResource(id = R.string.help),
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        content = content
    )
}

@Composable
fun DevicesSelectionScreenHelpModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HelpModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {

        // Instruction
        Section(
            title = stringResource(id = R.string.connection),
            message = "先在首页底部选择已配对设备，再点击 AI、手柄、键鼠、键盘、媒体、鼠标或 TV。选择设备不会直接进入控制；已有连接可以复用。返回首页会停止自动化、取消排队动作并松开按键。",
            modifier = Modifier.padding(top = dimensionResource(id = R.dimen.padding_medium))
        )

        Section(
            title = "语音输入任务目标",
            message = "在主控端 AI 自动化页面点击“语音输入”，按系统提示允许麦克风，再说出任务。识别文字可修改，核对后点“开始自动执行”。已有目标可选择语音替换或追加，取消和识别失败会保留原目标。识别由手机系统语音服务提供，可能需要联网。"
        )
        Section(
            title = "自动获取被控端屏幕",
            message = "更新并连接被控端截图助手、授权整屏共享后，设备型号、屏幕宽高和方向会自动获取。开启增强控制后，点击与滑动直接使用触屏坐标，无需鼠标系数；旋转后须以新截图为准。纯蓝牙鼠标的参数保留在“蓝牙鼠标高级校准”，受系统鼠标加速影响。"
        )

        // Missing device
        Section(
            title = stringResource(id = R.string.help_missing_device_title),
            message = stringResource(id = R.string.help_missing_device_message)
        )

        // Connection failure
        Section(
            title = stringResource(id = R.string.help_device_failed_connection_title),
            message = buildString {
                append(stringResource(id = R.string.help_device_failed_connection_message_1))
                append("\n\n")
                append(stringResource(id = R.string.help_device_failed_connection_check_1))
                append("\n")
                append(stringResource(id = R.string.help_device_failed_connection_check_2))
                append("\n")
                append(stringResource(id = R.string.help_device_failed_connection_check_3))
                append("\n")
                append(stringResource(id = R.string.help_device_failed_connection_check_4))
                append("\n\n")
                append(stringResource(id = R.string.help_device_failed_connection_message_2))
                append("\n\n")
                append(stringResource(id = R.string.help_device_failed_connection_check_5))
                append("\n")
                append(stringResource(id = R.string.help_device_failed_connection_check_6))
                append("\n\n")
                append(stringResource(id = R.string.help_device_failed_connection_message_3))
            }
        )

        // HID Service Not Initialized
        Section(
            title = stringResource(id = R.string.help_hid_service_not_initialized_title),
            message = buildString {
                append(stringResource(id = R.string.help_hid_service_not_initialized_message_1))
                append("\n\n")
                append(stringResource(id = R.string.help_hid_service_not_initialized_message_2))
                append("\n")
                append(stringResource(id = R.string.help_hid_service_not_initialized_message_3))
            }
        )

        // Remote control buttons
        Section(
            title = stringResource(id = R.string.help_remote_control_buttons_are_not_working_title),
            message = buildString {
                append(stringResource(id = R.string.help_remote_control_buttons_are_not_working_message))
                append("\n\n")
                append(stringResource(id = R.string.help_remote_control_buttons_are_not_working_check_1))
                append("\n")
                append(stringResource(id = R.string.help_remote_control_buttons_are_not_working_check_2))
                append("\n")
                append(stringResource(id = R.string.help_remote_control_buttons_are_not_working_check_3))
                append("\n")
                append(stringResource(id = R.string.help_device_failed_connection_check_6))
                append("\n\n")
                append(stringResource(id = R.string.help_remote_control_buttons_are_not_working_message_2))
            },
            modifier = Modifier.padding(top = dimensionResource(id = R.dimen.padding_medium))
        )

        // Keyboard
        Section(
            title = stringResource(id = R.string.keyboard),
            message = stringResource(id = R.string.help_keyboard_wrong_character_sent_message)
        )
    }
}

@Composable
fun BluetoothScanningScreenHelpModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HelpModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {

        // Instruction
        Section(
            title = stringResource(id = R.string.pairing_a_device),
            message = stringResource(id = R.string.help_paring_select_device_from_list),
            modifier = Modifier.padding(top = dimensionResource(id = R.dimen.padding_medium))
        )

        // Missing device
        Section(
            title = stringResource(id = R.string.help_missing_device_title),
            message = buildString {
                append(stringResource(id = R.string.help_pairing_missing_device_message_1))
                append("\n\n")
                append(stringResource(id = R.string.help_pairing_missing_device_check_1))
                append("\n")
                append(stringResource(id = R.string.help_pairing_missing_device_check_2))
                append("\n")
                append(stringResource(id = R.string.help_pairing_missing_device_check_3))
                if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                    append("\n")
                    append(stringResource(id = R.string.help_pairing_missing_device_check_location_for_android_11_or_less))
                }
            }
        )

        // Connection failure
        Section(
            title = stringResource(id = R.string.help_device_failed_connection_title),
            message = buildString {
                append(stringResource(id = R.string.help_pairing_device_failed_connection_message_1))
                append("\n\n")
                append(stringResource(id = R.string.help_device_failed_connection_check_1))
                append("\n")
                append(stringResource(id = R.string.help_device_failed_connection_check_2))
                append("\n")
                append(stringResource(id = R.string.help_device_failed_connection_check_3))
                append("\n")
                append(stringResource(id = R.string.help_device_failed_connection_check_4))
            }
        )
    }
}

@Composable
fun RemoteScreenHelpModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HelpModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {

        // Remote control buttons
        Section(
            title = stringResource(id = R.string.help_remote_control_buttons_are_not_working_title),
            message = buildString {
                append(stringResource(id = R.string.help_remote_control_buttons_are_not_working_message))
                append("\n\n")
                append(stringResource(id = R.string.help_remote_control_buttons_are_not_working_check_1))
                append("\n")
                append(stringResource(id = R.string.help_remote_control_buttons_are_not_working_check_2))
                append("\n")
                append(stringResource(id = R.string.help_remote_control_buttons_are_not_working_check_3))
                append("\n")
                append(stringResource(id = R.string.help_remote_control_buttons_are_not_working_check_4))
                append("\n\n")
                append(stringResource(id = R.string.help_remote_control_buttons_are_not_working_message_2))
            },
            modifier = Modifier.padding(top = dimensionResource(id = R.dimen.padding_medium))
        )

        // Keyboard
        Section(
            title = stringResource(id = R.string.keyboard),
            message = stringResource(id = R.string.help_keyboard_wrong_character_sent_message)
        )
    }
}

@Composable
private fun Section(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        TextMedium(
            text = title
        )
        TextNormalSecondary(
            text = message,
            modifier = Modifier.padding(vertical = dimensionResource(id = R.dimen.padding_max))
        )
    }
}
