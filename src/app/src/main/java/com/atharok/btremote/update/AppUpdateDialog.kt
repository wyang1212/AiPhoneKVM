// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atharok.btremote.common.extensions.getActivity

@Composable
fun AppUpdateDialog(onDismiss:()->Unit) {
    val locale=LocalConfiguration.current.locales[0]
    val context=LocalContext.current
    val updater=remember { AppUpdater.get(context) }
    val state by updater.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { updater.check() }
    AlertDialog(onDismissRequest=onDismiss,title={Text("应用更新")},text={
        Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("当前版本："+updater.currentVersion)
            Text(state.message)
            state.release?.let { r->
                Text(r.title,style=MaterialTheme.typography.titleMedium)
                Text("安装包 "+String.format(locale,"%.1f",r.size/1048576.0)+" MB")
                if(r.notes.isNotBlank())Text(r.notes)
            }
            if(state.stage==UpdateStage.DOWNLOADING) {
                LinearProgressIndicator(progress={state.release?.let { (state.downloaded.toFloat()/it.size).coerceIn(0f,1f) }?:0f},modifier=Modifier.fillMaxWidth())
                Text(String.format(locale,"%.1f",state.downloaded/1048576.0)+" MB 已下载")
            }
            if(state.stage==UpdateStage.VERIFYING || state.stage==UpdateStage.CHECKING)LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("安装前核对包名、版本、SHA-256和现有签名；安装仍需系统确认。",style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={
        when(state.stage) {
            UpdateStage.AVAILABLE -> TextButton(onClick=updater::download) { Text("下载更新") }
            UpdateStage.ERROR -> TextButton(onClick={if(state.release!=null)updater.download() else updater.check()}) { Text("重试") }
            UpdateStage.READY -> TextButton(onClick={context.getActivity()?.let(updater::install)}) { Text("安装更新") }
            UpdateStage.CURRENT,UpdateStage.IDLE -> TextButton(onClick=updater::check) { Text("重新检查") }
            else -> Unit
        }
    },dismissButton={
        Row {
            if(state.stage==UpdateStage.DOWNLOADING)TextButton(onClick=updater::cancelDownload) { Text("取消下载") }
            TextButton(onClick=onDismiss) { Text(if(state.stage==UpdateStage.DOWNLOADING)"后台下载" else "关闭") }
        }
    })
}
