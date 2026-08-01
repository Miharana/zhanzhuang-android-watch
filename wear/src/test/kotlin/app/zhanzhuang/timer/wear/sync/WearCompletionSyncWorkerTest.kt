package app.zhanzhuang.timer.wear.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class WearCompletionSyncWorkerTest {
    @Test
    fun failedDeliveryRequestsRetryAndSuccessfulDeliveryFinishes() {
        assertEquals(WearCompletionDelivery.RETRY, WearCompletionRetryPolicy.resultFor(sent = false))
        assertEquals(WearCompletionDelivery.DONE, WearCompletionRetryPolicy.resultFor(sent = true))
    }
}
