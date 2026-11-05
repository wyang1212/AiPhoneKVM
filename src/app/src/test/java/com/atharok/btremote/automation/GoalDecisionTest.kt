// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import org.junit.Assert.*
import org.junit.Test

class GoalDecisionTest {
    @Test fun acceptsOneActionAndExplicitScreenEvidence() {
        val decision=GoalDecision.parse("""{"taskStatus":"continue","summary":"按钮","evidence":"按钮在画面中","commands":[{"action":"TAP","params":{"x":12,"y":34}}]}""")
        assertEquals(GoalOutcome.CONTINUE,decision.outcome);assertNotNull(decision.command)
    }
    @Test fun acceptsCompletedWithoutAction() {
        assertEquals(GoalOutcome.COMPLETED,GoalDecision.parse("""{"taskStatus":"completed","summary":"结果页","evidence":"当前显示完成","commands":[]}""").outcome)
    }
    @Test fun rejectsCompletedWithAction() {
        assertThrows(IllegalArgumentException::class.java){GoalDecision.parse("""{"taskStatus":"completed","summary":"结果页","evidence":"已完成","commands":[{"action":"HOME"}]}""")}
    }
    @Test fun rejectsContinueWithoutAction() {
        assertThrows(IllegalArgumentException::class.java){GoalDecision.parse("""{"taskStatus":"continue","summary":"页面","evidence":"观察","commands":[]}""")}
    }
    @Test fun rejectsAmbiguousMissingStatusOrEvidence() {
        assertThrows(Exception::class.java){GoalDecision.parse("""{"summary":"页面","commands":[]}""")}
        assertThrows(Exception::class.java){GoalDecision.parse("""{"taskStatus":"completed","summary":"页面","evidence":"","commands":[]}""")}
    }
    @Test fun rejectsModelGeneratedAdministrativeActions() {
        assertThrows(IllegalArgumentException::class.java){GoalDecision.parse("""{"taskStatus":"continue","summary":"配置","evidence":"页面","commands":[{"action":"SET_CONFIG"}]}""")}
    }
    @Test fun rejectsFunctionStringInsteadOfCommandObject() {
        val error=assertThrows(IllegalArgumentException::class.java){GoalDecision.parse("""{"taskStatus":"continue","summary":"按钮","evidence":"可见按钮","commands":["TAP(100,200)"]}""")}
        assertTrue(error.message!!.contains("不能是字符串"))
    }
    @Test fun rejectsStringParametersRatherThanGuessingHowToExecute() {
        assertThrows(IllegalArgumentException::class.java){GoalDecision.parse("""{"taskStatus":"continue","summary":"按钮","evidence":"可见按钮","commands":[{"action":"TAP","params":"100,200"}]}""")}
    }
}
