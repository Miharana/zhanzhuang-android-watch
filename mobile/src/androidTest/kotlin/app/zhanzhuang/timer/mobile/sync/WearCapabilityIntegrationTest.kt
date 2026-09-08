package app.zhanzhuang.timer.mobile.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.zhanzhuang.timer.model.CAPABILITY_ZHAN_ZHUANG_WEAR
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Requires a paired physical Wear OS device with the matching debug app installed. */
@RunWith(AndroidJUnit4::class)
class WearCapabilityIntegrationTest {
    @Test
    fun pairedWatchAdvertisesTheSyncCapability() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val completed = CountDownLatch(1)
        var nodes: Set<Node> = emptySet()
        var failure: Exception? = null
        Wearable.getCapabilityClient(context)
                .getCapability(CAPABILITY_ZHAN_ZHUANG_WEAR, CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { capability -> nodes = capability.nodes; completed.countDown() }
            .addOnFailureListener { error -> failure = error; completed.countDown() }

        assertTrue("Capability query timed out", completed.await(5, TimeUnit.SECONDS))
        assertTrue(
            "Capability query failed: $failure; no reachable watch advertises $CAPABILITY_ZHAN_ZHUANG_WEAR",
            nodes.isNotEmpty(),
        )
    }
}
