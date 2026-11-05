// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package vip.wyang.aiphonekvm.capture

/** Selection indices are UTF-16, as in AccessibilityNodeInfo; never split an emoji pair. */
object TextEditPlan {
    fun needsSelection(start: Int, end: Int, cursor: Int) = start!=cursor || end!=cursor
    fun cursor(oldLength: Int, start: Int, text: String, replace: Boolean, space: Boolean): Int =
        (if(replace)0 else start.takeIf {it in 0..oldLength} ?: oldLength)+text.length+(if(space)1 else 0)
    fun apply(old: String, start: Int, end: Int, text: String, replace: Boolean, space: Boolean): String {
        require(text.length in 1..256 && old.length<=10000 && '\u0000' !in text)
        var index=0
        while(index<text.length) {
            val c=text[index++]
            if(c.isHighSurrogate()) { require(index<text.length && text[index].isLowSurrogate());index++ }
            else require(!c.isLowSurrogate())
        }
        val first=if(replace)0 else start.takeIf {it in 0..old.length} ?: old.length
        val last=if(replace)old.length else end.takeIf {it in first..old.length} ?: first
        for(i in listOf(first,last)) require(i==0 || i==old.length || !(old[i-1].isHighSurrogate() && old[i].isLowSurrogate()))
        return old.substring(0,first)+text+(if(space) " " else "")+old.substring(last)
    }
}
