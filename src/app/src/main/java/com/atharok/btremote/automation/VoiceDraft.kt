// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

/** Final results only; empty/cancelled speech must not erase a typed goal. */
object VoiceDraft {
    fun text(candidates: List<String>?): String? = candidates?.firstOrNull { it.isNotBlank() }
        ?.trim()?.takeIf { it.length <= 2000 }
    fun merge(goal: String, speech: String, append: Boolean): String =
        (if (append && goal.isNotBlank()) goal.trimEnd() + "\n" + speech else speech)
            .also { require(it.length <= 2000) { "任务目标最多2000字，请精简后再输入" } }
}
