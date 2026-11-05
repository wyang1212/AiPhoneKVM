// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import org.junit.Assert.*
import org.junit.Test

class VoiceDraftTest {
    @Test fun cancelledAndEmptyResultsDoNotReplaceGoal() {
        assertNull(VoiceDraft.text(null)); assertNull(VoiceDraft.text(listOf(" ","")))
    }
    @Test fun usesFirstUsefulFinalCandidateAndPreservesChinese() {
        assertEquals("打开设置，搜索显示",VoiceDraft.text(listOf(" "," 打开设置，搜索显示 ","另一个候选")))
    }
    @Test fun replacingAndAppendingAreExplicit() {
        assertEquals("读取休眠时长",VoiceDraft.merge("旧目标","读取休眠时长",false))
        assertEquals("打开设置\n读取休眠时长",VoiceDraft.merge("打开设置  ","读取休眠时长",true))
        assertEquals("新目标",VoiceDraft.merge(" ","新目标",true))
    }
    @Test fun oversizedSpeechAndAppendAreRejectedWithoutTruncation() {
        assertNull(VoiceDraft.text(listOf("字".repeat(2001))))
        assertTrue(runCatching { VoiceDraft.merge("字".repeat(1999),"字",true) }.isFailure)
        assertEquals(2000,VoiceDraft.merge("","字".repeat(2000),false).length)
    }
}
