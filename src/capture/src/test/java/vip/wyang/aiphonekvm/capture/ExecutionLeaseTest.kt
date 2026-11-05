// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package vip.wyang.aiphonekvm.capture

import org.junit.Assert.*
import org.junit.Test

class ExecutionLeaseTest {
    @Test fun activeFreshLeaseCanStart() {assertTrue(ExecutionLease.valid(1000,1500,true))}
    @Test fun delayedResponseCannotStartAfterLeaseDeadline() {assertFalse(ExecutionLease.valid(1000,2001,true));assertFalse(ExecutionLease.valid(2000,1000,true))}
    @Test fun stoppedSharingCannotStartQueuedMainThreadWork() {assertFalse(ExecutionLease.valid(1000,1100,false))}
}
