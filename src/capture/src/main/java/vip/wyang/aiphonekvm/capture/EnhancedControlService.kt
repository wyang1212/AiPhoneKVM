// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package vip.wyang.aiphonekvm.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.*
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.json.JSONObject

/** Enabled only through Android's accessibility settings. No key filtering or UI-tree upload. */
class EnhancedControlService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { if(instance===this) instance=null;super.onDestroy() }

    fun execute(command: JSONObject, leaseSentAt: Long, authorized: () -> Boolean, complete: (JSONObject) -> Unit) {
        main.post {
            try {
                check(ExecutionLease.valid(leaseSentAt,SystemClock.elapsedRealtime(),authorized() && instance===this)) { "EXPIRED" }
                val expected=command.getJSONObject("params").optJSONObject("screen")
                if (expected!=null && !DisplaySnapshot.matches(expected,DisplaySnapshot.read(this))) {
                    complete(failed("SCREEN_CHANGED"));return@post
                }
                when(command.getString("action")) {
                    "TAP", "SWIPE" -> gesture(command, complete)
                    "INPUT" -> input(command.getJSONObject("params"), leaseSentAt, authorized, complete)
                    else -> complete(failed("BAD_ACTION"))
                }
            } catch(_: Exception) { complete(failed("INVALID_OR_EXPIRED")) }
        }
    }
    private fun gesture(command: JSONObject, complete: (JSONObject) -> Unit) {
        val p=command.getJSONObject("params");val tap=command.getString("action")=="TAP"
        val x=p.getInt(if(tap) "x" else "from_x");val y=p.getInt(if(tap) "y" else "from_y")
        val tx=if(tap)x else p.getInt("to_x");val ty=if(tap)y else p.getInt("to_y")
        val duration=p.optLong("duration",if(tap)50 else 300);require(duration in 1..2000)
        val m=android.util.DisplayMetrics()
        @Suppress("DEPRECATION") getSystemService(WindowManager::class.java).defaultDisplay.getRealMetrics(m)
        require(x in 0 until m.widthPixels && tx in 0 until m.widthPixels && y in 0 until m.heightPixels && ty in 0 until m.heightPixels)
        val path=Path().apply { moveTo(x.toFloat(),y.toFloat());if(!tap) lineTo(tx.toFloat(),ty.toFloat()) }
        val description=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path,0,duration)).build()
        val accepted=dispatchGesture(description,object:GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { complete(success()) }
            override fun onCancelled(gestureDescription: GestureDescription?) { complete(failed("GESTURE_CANCELLED")) }
        },main)
        if(!accepted) complete(failed("GESTURE_REJECTED"))
    }
    private fun input(p: JSONObject, leaseSentAt: Long, authorized: () -> Boolean, complete: (JSONObject) -> Unit) {
        val text=p.getString("text");require(text.length in 1..256)
        val confirm=p.optString("confirm","");require(confirm in setOf("","enter","space"))
        val node=focusedEditor()
        if(node==null) { complete(failed("NO_EDITABLE_FOCUS"));return }
        val old=if(node.isShowingHintText) "" else node.text?.toString().orEmpty()
        val replace=p.optBoolean("replace",false)
        val expected=TextEditPlan.apply(old,node.textSelectionStart,node.textSelectionEnd,text,replace,confirm=="space")
        val cursor=TextEditPlan.cursor(old.length,node.textSelectionStart,text,replace,confirm=="space")
        val args=Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,expected) }
        check(ExecutionLease.valid(leaseSentAt,SystemClock.elapsedRealtime(),authorized() && instance===this))
        if(!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)) { complete(failed("SET_TEXT_REJECTED"));return }
        fun finish() {
            var needsConfirm=false
            if(confirm=="enter") {
                val withinLease=ExecutionLease.valid(leaseSentAt,SystemClock.elapsedRealtime(),authorized() && instance===this)
                needsConfirm= !withinLease || Build.VERSION.SDK_INT<30 || !node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            }
            complete(success().put("textVerified",true).put("needsHidConfirm",needsConfirm))
        }
        fun verifyCursor(remaining: Int) {
            if(node.refresh() && node.isFocused && node.text?.toString()==expected && node.textSelectionStart==cursor && node.textSelectionEnd==cursor) finish()
            else if(remaining>0) main.postDelayed({verifyCursor(remaining-1)},50)
            else complete(failed("CURSOR_NOT_VERIFIED"))
        }
        // Verify, never rewrite on failure: a retry could target a newly focused field.
        fun verify(remaining: Int) {
            if(node.refresh() && node.isFocused && node.text?.toString()==expected) {
                if(!TextEditPlan.needsSelection(node.textSelectionStart,node.textSelectionEnd,cursor)) { finish();return }
                if(!ExecutionLease.valid(leaseSentAt,SystemClock.elapsedRealtime(),authorized() && instance===this)) { complete(failed("INPUT_LEASE_EXPIRED"));return }
                val selection=Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,cursor)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,cursor)
                }
                if(!node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION,selection)) { complete(failed("SET_SELECTION_REJECTED"));return }
                verifyCursor(4)
            } else if(remaining>0) main.postDelayed({ verify(remaining-1) },100)
            else complete(failed("TEXT_NOT_VERIFIED"))
        }
        verify(8)
    }
    private fun focusedEditor(): AccessibilityNodeInfo? {
        fun eligible(node: AccessibilityNodeInfo) = node.isFocused && node.isEnabled && !node.isPassword &&
            node.actionList.any { it.id==AccessibilityNodeInfo.ACTION_SET_TEXT }
        val focus=findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if(BuildConfig.DEBUG) android.util.Log.d("AiPhoneControl", "input focus type=${focus?.className} focused=${focus?.isFocused} editable=${focus?.isEditable} setText=${focus?.actionList?.any { it.id==AccessibilityNodeInfo.ACTION_SET_TEXT }}")
        if(focus!=null && focus.refresh() && eligible(focus)) return focus
        // A visible keyboard can be the active window. Only inspect the focused application,
        // and require input focus plus SET_TEXT capability instead of relying on WebView's editable flag.
        val appRoots=windows.filter { it.type==AccessibilityWindowInfo.TYPE_APPLICATION && (it.isFocused || it.isActive) }.mapNotNull { it.root }
        val roots=appRoots.ifEmpty { listOfNotNull(rootInActiveWindow) }
        for(root in roots) {
            root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { if(eligible(it)) return it }
            val queue=java.util.ArrayDeque<AccessibilityNodeInfo>();queue.add(root);var visited=0
            while(queue.isNotEmpty() && visited++<500) {
                val node=queue.removeFirst()
                if(eligible(node)) return node
                for(i in 0 until node.childCount) node.getChild(i)?.let { queue.addLast(it) }
            }
        }
        return null
    }
    companion object {
        @Volatile var instance: EnhancedControlService?=null;private set
        fun success()=JSONObject().put("status","SUCCESS")
        fun failed(code: String)=JSONObject().put("status","FAILED").put("code",code)
    }
}
