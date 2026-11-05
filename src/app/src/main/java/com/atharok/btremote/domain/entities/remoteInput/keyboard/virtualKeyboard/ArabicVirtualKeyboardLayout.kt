package com.atharok.btremote.domain.entities.remoteInput.keyboard.virtualKeyboard

import com.atharok.btremote.domain.entities.remoteInput.RemoteButtonProperties
import com.atharok.btremote.domain.entities.remoteInput.keyboard.KeyboardKey

class ArabicVirtualKeyboardLayout: VirtualKeyboardLayout() {

    protected override val keyboardInputs: Map<Char, ByteArray> by lazy {
        mapOf(
            ' ' to RemoteButtonProperties.KeyboardSpaceBarButton.input,

            'ذ' to byteArrayOf(0x00, KeyboardKey.ROW_1_KEY_00.byte),
            '١' to byteArrayOf(0x00, KeyboardKey.ROW_1_KEY_01.byte),
            '٢' to byteArrayOf(0x00, KeyboardKey.ROW_1_KEY_02.byte),
            '٣' to byteArrayOf(0x00, KeyboardKey.ROW_1_KEY_03.byte),
            '٤' to byteArrayOf(0x00, KeyboardKey.ROW_1_KEY_04.byte),
            '٥' to byteArrayOf(0x00, KeyboardKey.ROW_1_KEY_05.byte),
            '٦' to byteArrayOf(0x00, KeyboardKey.ROW_1_KEY_06.byte),
            '٧' to byteArrayOf(0x00, KeyboardKey.ROW_1_KEY_07.byte),
            '٨' to byteArrayOf(0x00, KeyboardKey.ROW_1_KEY_08.byte),
            '٩' to byteArrayOf(0x00, KeyboardKey.ROW_1_KEY_09.byte),
            '٠' to byteArrayOf(0x00, KeyboardKey.ROW_1_KEY_10.byte),
            '-' to byteArrayOf(0x00, KeyboardKey.ROW_1_KEY_11.byte),
            '=' to byteArrayOf(0x00, KeyboardKey.ROW_1_KEY_12.byte),

            'ض' to byteArrayOf(0x00, KeyboardKey.ROW_2_KEY_00.byte),
            'ص' to byteArrayOf(0x00, KeyboardKey.ROW_2_KEY_01.byte),
            'ث' to byteArrayOf(0x00, KeyboardKey.ROW_2_KEY_02.byte),
            'ق' to byteArrayOf(0x00, KeyboardKey.ROW_2_KEY_03.byte),
            'ف' to byteArrayOf(0x00, KeyboardKey.ROW_2_KEY_04.byte),
            'غ' to byteArrayOf(0x00, KeyboardKey.ROW_2_KEY_05.byte),
            'ع' to byteArrayOf(0x00, KeyboardKey.ROW_2_KEY_06.byte),
            'ه' to byteArrayOf(0x00, KeyboardKey.ROW_2_KEY_07.byte),
            'خ' to byteArrayOf(0x00, KeyboardKey.ROW_2_KEY_08.byte),
            'ح' to byteArrayOf(0x00, KeyboardKey.ROW_2_KEY_09.byte),
            'ج' to byteArrayOf(0x00, KeyboardKey.ROW_2_KEY_10.byte),
            'د' to byteArrayOf(0x00, KeyboardKey.ROW_2_KEY_11.byte),

            'ش' to byteArrayOf(0x00, KeyboardKey.ROW_3_KEY_00.byte),
            'س' to byteArrayOf(0x00, KeyboardKey.ROW_3_KEY_01.byte),
            'ي' to byteArrayOf(0x00, KeyboardKey.ROW_3_KEY_02.byte),
            'ب' to byteArrayOf(0x00, KeyboardKey.ROW_3_KEY_03.byte),
            'ل' to byteArrayOf(0x00, KeyboardKey.ROW_3_KEY_04.byte),
            'ا' to byteArrayOf(0x00, KeyboardKey.ROW_3_KEY_05.byte),
            'ت' to byteArrayOf(0x00, KeyboardKey.ROW_3_KEY_06.byte),
            'ن' to byteArrayOf(0x00, KeyboardKey.ROW_3_KEY_07.byte),
            'م' to byteArrayOf(0x00, KeyboardKey.ROW_3_KEY_08.byte),
            'ك' to byteArrayOf(0x00, KeyboardKey.ROW_3_KEY_09.byte),
            'ط' to byteArrayOf(0x00, KeyboardKey.ROW_3_KEY_10.byte),
            '\\' to byteArrayOf(0x00, KeyboardKey.ROW_3_KEY_11.byte),

            '\\' to byteArrayOf(0x00, KeyboardKey.ROW_4_KEY_00.byte),
            'ئ' to byteArrayOf(0x00, KeyboardKey.ROW_4_KEY_01.byte),
            'ء' to byteArrayOf(0x00, KeyboardKey.ROW_4_KEY_02.byte),
            'ؤ' to byteArrayOf(0x00, KeyboardKey.ROW_4_KEY_03.byte),
            'ر' to byteArrayOf(0x00, KeyboardKey.ROW_4_KEY_04.byte),
            'ﻻ' to byteArrayOf(0x00, KeyboardKey.ROW_4_KEY_05.byte),
            'ى' to byteArrayOf(0x00, KeyboardKey.ROW_4_KEY_06.byte),
            'ة' to byteArrayOf(0x00, KeyboardKey.ROW_4_KEY_07.byte),
            'و' to byteArrayOf(0x00, KeyboardKey.ROW_4_KEY_08.byte),
            'ز' to byteArrayOf(0x00, KeyboardKey.ROW_4_KEY_09.byte),
            'ظ' to byteArrayOf(0x00, KeyboardKey.ROW_4_KEY_10.byte),

            // ---- Shift ----

            'ّ' to byteArrayOf(0x02, KeyboardKey.ROW_1_KEY_00.byte),
            '!' to byteArrayOf(0x02, KeyboardKey.ROW_1_KEY_01.byte),
            '@' to byteArrayOf(0x02, KeyboardKey.ROW_1_KEY_02.byte),
            '#' to byteArrayOf(0x02, KeyboardKey.ROW_1_KEY_03.byte),
            '$' to byteArrayOf(0x02, KeyboardKey.ROW_1_KEY_04.byte),
            '٪' to byteArrayOf(0x02, KeyboardKey.ROW_1_KEY_05.byte),
            '^' to byteArrayOf(0x02, KeyboardKey.ROW_1_KEY_06.byte),
            '&' to byteArrayOf(0x02, KeyboardKey.ROW_1_KEY_07.byte),
            '*' to byteArrayOf(0x02, KeyboardKey.ROW_1_KEY_08.byte),
            ')' to byteArrayOf(0x02, KeyboardKey.ROW_1_KEY_09.byte),
            '(' to byteArrayOf(0x02, KeyboardKey.ROW_1_KEY_10.byte),
            '_' to byteArrayOf(0x02, KeyboardKey.ROW_1_KEY_11.byte),
            '+' to byteArrayOf(0x02, KeyboardKey.ROW_1_KEY_12.byte),

            'َ' to byteArrayOf(0x02, KeyboardKey.ROW_2_KEY_00.byte),
            'ً' to byteArrayOf(0x02, KeyboardKey.ROW_2_KEY_01.byte),
            'ُ' to byteArrayOf(0x02, KeyboardKey.ROW_2_KEY_02.byte),
            'ٌ' to byteArrayOf(0x02, KeyboardKey.ROW_2_KEY_03.byte),
            'ﻹ' to byteArrayOf(0x02, KeyboardKey.ROW_2_KEY_04.byte),
            'إ' to byteArrayOf(0x02, KeyboardKey.ROW_2_KEY_05.byte),
            '‘' to byteArrayOf(0x02, KeyboardKey.ROW_2_KEY_06.byte),
            '÷' to byteArrayOf(0x02, KeyboardKey.ROW_2_KEY_07.byte),
            '×' to byteArrayOf(0x02, KeyboardKey.ROW_2_KEY_08.byte),
            '؛' to byteArrayOf(0x02, KeyboardKey.ROW_2_KEY_09.byte),
            '>' to byteArrayOf(0x02, KeyboardKey.ROW_2_KEY_10.byte),
            '<' to byteArrayOf(0x02, KeyboardKey.ROW_2_KEY_11.byte),

            'ِ' to byteArrayOf(0x02, KeyboardKey.ROW_3_KEY_00.byte),
            'ٍ' to byteArrayOf(0x02, KeyboardKey.ROW_3_KEY_01.byte),
            ']' to byteArrayOf(0x02, KeyboardKey.ROW_3_KEY_02.byte),
            '[' to byteArrayOf(0x02, KeyboardKey.ROW_3_KEY_03.byte),
            'ﻷ' to byteArrayOf(0x02, KeyboardKey.ROW_3_KEY_04.byte),
            'أ' to byteArrayOf(0x02, KeyboardKey.ROW_3_KEY_05.byte),
            'ـ' to byteArrayOf(0x02, KeyboardKey.ROW_3_KEY_06.byte),
            '،' to byteArrayOf(0x02, KeyboardKey.ROW_3_KEY_07.byte),
            '/' to byteArrayOf(0x02, KeyboardKey.ROW_3_KEY_08.byte),
            ':' to byteArrayOf(0x02, KeyboardKey.ROW_3_KEY_09.byte),
            '"' to byteArrayOf(0x02, KeyboardKey.ROW_3_KEY_10.byte),
            '|' to byteArrayOf(0x02, KeyboardKey.ROW_3_KEY_11.byte),

            '|' to byteArrayOf(0x02, KeyboardKey.ROW_4_KEY_00.byte),
            '~' to byteArrayOf(0x02, KeyboardKey.ROW_4_KEY_01.byte),
            'ْ' to byteArrayOf(0x02, KeyboardKey.ROW_4_KEY_02.byte),
            '}' to byteArrayOf(0x02, KeyboardKey.ROW_4_KEY_03.byte),
            '{' to byteArrayOf(0x02, KeyboardKey.ROW_4_KEY_04.byte),
            'ﻵ' to byteArrayOf(0x02, KeyboardKey.ROW_4_KEY_05.byte),
            'آ' to byteArrayOf(0x02, KeyboardKey.ROW_4_KEY_06.byte),
            '’' to byteArrayOf(0x02, KeyboardKey.ROW_4_KEY_07.byte),
            ',' to byteArrayOf(0x02, KeyboardKey.ROW_4_KEY_08.byte),
            '.' to byteArrayOf(0x02, KeyboardKey.ROW_4_KEY_09.byte),
            '؟' to byteArrayOf(0x02, KeyboardKey.ROW_4_KEY_10.byte)
        )
    }

    protected override val extraInputs: Map<Char, ByteArray> by lazy { mapOf() }
}