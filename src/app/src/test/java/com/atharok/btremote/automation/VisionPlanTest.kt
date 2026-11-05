package com.atharok.btremote.automation

import org.junit.Assert.*
import org.junit.Test

class VisionPlanTest {
    @Test fun parseObservationOnly() { val plan=VisionPlan.parse("""{"summary":"设置页面","commands":[]}""");assertNull(plan.command);assertEquals("设置页面",plan.summary) }
    @Test fun parseSingleStepWithoutModelSuppliedId() {
        val plan=VisionPlan.parse("""{"summary":"点测试按钮","commands":[{"id":"untrusted","action":"tap","params":{"x":100,"y":200}}]}""")
        assertFalse(plan.command!!.containsKey("id"));assertEquals("\"TAP\"",plan.command["action"].toString())
    }
    @Test fun rejectsMultipleStepsFromOneScreenshot() { assertThrows(IllegalArgumentException::class.java) { VisionPlan.parse("""{"summary":"a","commands":[{"action":"HOME"},{"action":"BACK"}]}""") } }
    @Test fun rejectsConfigurationCommands() { assertThrows(IllegalArgumentException::class.java) { VisionPlan.parse("""{"summary":"a","commands":[{"action":"SET_CONFIG"}]}""") } }
    @Test fun rejectsMalformedModelResponse() { assertThrows(Exception::class.java) { VisionPlan.parse("not json") } }
    @Test fun acceptsFencedJson() { assertEquals("ok",VisionPlan.parse("```json\n{\"summary\":\"ok\",\"commands\":[]}\n```").summary) }
}
