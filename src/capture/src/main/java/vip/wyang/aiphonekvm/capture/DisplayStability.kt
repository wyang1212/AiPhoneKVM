// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package vip.wyang.aiphonekvm.capture

/** Display metrics change before the rotation animation finishes. Request a fresh buffer only
 * after a quiet interval; observing the same display must not postpone capture indefinitely. */
class DisplayStability(private val quietMs: Long = 1000) {
    private var signature: String? = null
    private var changedAt = 0L
    @Synchronized fun observe(value: String, now: Long) {
        if (signature != value) { signature = value; changedAt = now }
    }
    @Synchronized fun remaining(now: Long): Long =
        if (signature == null) quietMs else (quietMs - (now - changedAt)).coerceIn(0,quietMs)
    @Synchronized fun stable(value: String, now: Long): Boolean = signature == value && remaining(now) == 0L
}
