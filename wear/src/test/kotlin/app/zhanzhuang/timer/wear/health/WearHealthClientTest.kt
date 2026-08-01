package app.zhanzhuang.timer.wear.health

import androidx.health.services.client.data.DataType
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.data.ExerciseCapabilities
import androidx.health.services.client.data.ExerciseInfo
import androidx.health.services.client.data.ExerciseTrackedStatus
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseTypeCapabilities
import com.google.common.util.concurrent.Futures
import java.lang.reflect.Proxy
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WearHealthClientTest {
    @Test
    fun meditationRequestsHeartRateOnly() {
        val config = createExerciseConfig()

        assertEquals(ExerciseType.MEDITATION, config.exerciseType)
        assertEquals(setOf(DataType.HEART_RATE_BPM), config.dataTypes)
    }

    @Test
    fun otherAppExerciseUsesDurationOnlyFallback() {
        assertIs<WearHealthStartResult.DurationOnly>(
            startResultFor(ExistingExercise.OTHER_APP),
        )
    }

    @Test
    fun unavailableHeartRateUsesDurationOnlyFallback() {
        assertIs<WearHealthStartResult.DurationOnly>(
            startResultFor(ExistingExercise.NONE, heartRateAvailable = false),
        )
    }

    @Suppress("RestrictedApi")
    @Test
    fun lifecycleIsNoOpAfterAnotherAppExerciseFallback() = kotlinx.coroutines.test.runTest {
        val calls = mutableListOf<String>()
        val client = AndroidWearHealthClient(fakeExerciseClient(ExerciseTrackedStatus.OTHER_APP_IN_PROGRESS, calls))

        assertIs<WearHealthStartResult.DurationOnly>(client.start())
        client.pause()
        client.resume()
        client.end()

        assertTrue(calls.none { it in setOf("pauseExerciseAsync", "resumeExerciseAsync", "endExerciseAsync") })
    }

    private fun fakeExerciseClient(status: Int, calls: MutableList<String>): ExerciseClient = Proxy.newProxyInstance(
        ExerciseClient::class.java.classLoader,
        arrayOf(ExerciseClient::class.java),
    ) { _, method, _ ->
        calls += method.name
        when (method.name) {
            "getCurrentExerciseInfoAsync" -> Futures.immediateFuture(ExerciseInfo(status, ExerciseType.MEDITATION))
            "getCapabilitiesAsync" -> Futures.immediateFuture(
                ExerciseCapabilities(
                    mapOf(
                        ExerciseType.MEDITATION to ExerciseTypeCapabilities(
                            supportedDataTypes = setOf(DataType.HEART_RATE_BPM),
                            supportedGoals = emptyMap(),
                            supportedMilestones = emptyMap(),
                            supportsAutoPauseAndResume = false,
                        ),
                    ),
                ),
            )
            "setUpdateCallback" -> null
            else -> Futures.immediateFuture(null)
        }
    } as ExerciseClient
}
