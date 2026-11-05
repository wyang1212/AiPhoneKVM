// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package vip.wyang.aiphonekvm.capture

import org.junit.Assert.*
import org.junit.Test

class TextEditPlanTest {
    @Test fun preservesChineseDigitsCasePunctuationAndEmoji() {
        val text="中文 AiPhone42！😃";assertEquals(text,TextEditPlan.apply("",0,0,text,false,false))
    }
    @Test fun replacesOnlySelectedTextByDefault() {assertEquals("a中文z",TextEditPlan.apply("aOLDz",1,4,"中文",false,false))}
    @Test fun explicitReplaceDoesNotAppendOrKeepOldImePrediction() {assertEquals("AiPhone42",TextEditPlan.apply("Earphone2",9,9,"AiPhone42",true,false))}
    @Test fun unknownSelectionAppendsAndSpaceIsLiteral() {assertEquals("前缀42 ",TextEditPlan.apply("前缀",-1,-1,"42",false,true))}
    @Test fun rejectsBrokenSurrogatesAndInvalidLengthsBeforeWriting() {
        assertThrows(IllegalArgumentException::class.java){TextEditPlan.apply("",0,0,"\uD83D",true,false)}
        assertThrows(IllegalArgumentException::class.java){TextEditPlan.apply("",0,0,"",true,false)}
        assertThrows(IllegalArgumentException::class.java){TextEditPlan.apply("",0,0,"a".repeat(257),true,false)}
    }
    @Test fun neverInsertsInMiddleOfEmojiPair() {assertThrows(IllegalArgumentException::class.java){TextEditPlan.apply("😃",1,1,"x",false,false)}}
    @Test fun caretFollowsInsertedTextRatherThanKeepingPreviousImePosition() {
        assertEquals("中文 输入法42 Test!?😃".length,TextEditPlan.cursor(9,9,"中文 输入法42 Test!?😃",true,false))
        assertEquals(4,TextEditPlan.cursor(8,1,"中文",false,true))
    }
    @Test fun nativeSearchAlreadyPlacesCursorAndNeedsNoUnsupportedSelectionAction() {
        assertFalse(TextEditPlan.needsSelection(2,2,2))
        assertTrue(TextEditPlan.needsSelection(9,9,17))
    }
}
