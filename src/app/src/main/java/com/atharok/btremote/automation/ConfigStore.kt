// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

// Check commit() explicitly: the UI must not report a failed pairing/configuration save as successful.
@SuppressLint("UseKtx")
class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("automation_private", Context.MODE_PRIVATE)
    val state = MutableStateFlow(runCatching {
        AutomationConfig().merge(Json.parseToJsonElement(prefs.getString("config", "{}")!!).jsonObject, true)
    }.getOrElse { AutomationConfig() })
    init { save(state.value) }
    @Synchronized fun bindCaptureTo(address: String) {
        val token = nextCapturePairingToken(prefs.getString("capture_target", null), address, state.value.captureToken)
        if (token == state.value.captureToken) return
        val next = state.value.copy(captureToken = token).validate()
        check(prefs.edit().putString("config", next.json(true).toString()).putString("capture_target", address).commit()) { "保存截图配对失败" }
        state.value = next
    }

    @Synchronized fun save(config: AutomationConfig) {
        config.validate()
        check(prefs.edit().putString("config", config.json(true).toString()).commit()) { "保存设置失败" }
        state.value = config
    }
}

/** Each selected target has a distinct screenshot pairing code, including the first migration. */
internal fun nextCapturePairingToken(previousAddress: String?, address: String, token: String): String =
    if (!previousAddress.isNullOrBlank() && previousAddress.equals(address, true)) token
    else java.util.UUID.randomUUID().toString().replace("-", "")
