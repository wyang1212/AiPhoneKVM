// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package vip.wyang.aiphonekvm.capture

/** The deadline is measured from sending the lease request, including all network delay. */
object ExecutionLease {
    fun valid(sentAt: Long, now: Long, active: Boolean) = active && now - sentAt in 0..1000
}
