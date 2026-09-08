package app.zhanzhuang.timer.wear.health

import androidx.health.services.client.data.DataType
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.HealthServicesException
import androidx.health.services.client.data.ExerciseCapabilities
import androidx.health.services.client.data.ExerciseInfo
import androidx.health.services.client.data.ExerciseTrackedStatus
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseTypeCapabilities
import com.google.common.util.concurrent.Futures
import java.lang.reflect.Proxy
import kotlinx.coroutines.CancellationException
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

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

    @Suppress("RestrictedApi")
    @Test
    fun callbackRegistrationSecurityFailureAfterStartingCleansUpOurExercise() = kotlinx.coroutines.test.runTest {
        val calls = mutableListOf<String>()
        val client = AndroidWearHealthClient(
            fakeExerciseClient(
                status = ExerciseTrackedStatus.NO_EXERCISE_IN_PROGRESS,
                calls = calls,
                callbackFailure = SecurityException("callback registration denied"),
            ),
        )

        assertEquals(
            WearHealthStartResult.DurationOnly(DurationOnlyReason.PERMISSION_DENIED),
            client.start(),
        )
        client.end()

        assertEquals(1, calls.count { it == "startExerciseAsync" })
        assertEquals(1, calls.count { it == "endExerciseAsync" })
    }

    @Suppress("RestrictedApi")
    @Test
    fun callbackRegistrationFailureRetainsOwnershipWhenImmediateCleanupFails() = kotlinx.coroutines.test.runTest {
        val calls = mutableListOf<String>()
        val failures = FakeExerciseFailures(remainingEndFailures = 1)
        val client = AndroidWearHealthClient(
            fakeExerciseClient(
                status = ExerciseTrackedStatus.NO_EXERCISE_IN_PROGRESS,
                calls = calls,
                callbackFailure = SecurityException("callback registration denied"),
                failures = failures,
            ),
        )

        assertEquals(
            WearHealthStartResult.StartedWithoutUpdates(DurationOnlyReason.PERMISSION_DENIED),
            client.start(),
        )
        client.end()

        assertEquals(2, calls.count { it == "endExerciseAsync" })
    }

    @Suppress("RestrictedApi")
    @Test
    fun reattachCallbackFailureRetainsOurExerciseForRecovery() = kotlinx.coroutines.test.runTest {
        val calls = mutableListOf<String>()
        val client = AndroidWearHealthClient(
            fakeExerciseClient(
                status = ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS,
                calls = calls,
                callbackFailure = SecurityException("callback registration denied"),
            ),
        )

        assertEquals(
            WearHealthStartResult.StartedWithoutUpdates(DurationOnlyReason.PERMISSION_DENIED),
            client.start(),
        )
        client.end()

        assertEquals(0, calls.count { it == "startExerciseAsync" })
        assertEquals(1, calls.count { it == "endExerciseAsync" })
    }

    @Suppress("RestrictedApi")
    @Test
    fun ownedExerciseCapabilityPermissionFailureRetainsOwnershipForRecovery() = kotlinx.coroutines.test.runTest {
        val calls = mutableListOf<String>()
        val client = AndroidWearHealthClient(
            fakeExerciseClient(
                status = ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS,
                calls = calls,
                capabilitiesFailure = SecurityException("capability lookup denied"),
            ),
        )

        assertEquals(
            WearHealthStartResult.StartedWithoutUpdates(DurationOnlyReason.PERMISSION_DENIED),
            client.start(),
        )
        client.end()

        assertEquals(0, calls.count { it == "startExerciseAsync" })
        assertEquals(1, calls.count { it == "endExerciseAsync" })
    }

    @Suppress("RestrictedApi")
    @Test
    fun ownedExerciseCapabilityHealthServicesFailureRetainsOwnershipForRecovery() = kotlinx.coroutines.test.runTest {
        val calls = mutableListOf<String>()
        val client = AndroidWearHealthClient(
            fakeExerciseClient(
                status = ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS,
                calls = calls,
                capabilitiesFailure = HealthServicesException("capability lookup unavailable"),
            ),
        )

        assertEquals(
            WearHealthStartResult.StartedWithoutUpdates(DurationOnlyReason.HEALTH_SERVICES_UNAVAILABLE),
            client.start(),
        )
        client.end()

        assertEquals(0, calls.count { it == "startExerciseAsync" })
        assertEquals(1, calls.count { it == "endExerciseAsync" })
    }

    @Suppress("RestrictedApi")
    @Test
    fun ownedExerciseWithoutHeartRateRetainsOwnershipForRecovery() = kotlinx.coroutines.test.runTest {
        val calls = mutableListOf<String>()
        val client = AndroidWearHealthClient(
            fakeExerciseClient(
                status = ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS,
                calls = calls,
                heartRateAvailable = false,
            ),
        )

        assertEquals(
            WearHealthStartResult.StartedWithoutUpdates(DurationOnlyReason.HEART_RATE_UNAVAILABLE),
            client.start(),
        )
        client.end()

        assertEquals(0, calls.count { it == "startExerciseAsync" })
        assertEquals(1, calls.count { it == "endExerciseAsync" })
    }

    @Suppress("RestrictedApi")
    @Test
    fun ownedExerciseCapabilityCancellationIsRethrownAndRetainsOwnershipForRecovery() = kotlinx.coroutines.test.runTest {
        val calls = mutableListOf<String>()
        val client = AndroidWearHealthClient(
            fakeExerciseClient(
                status = ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS,
                calls = calls,
                capabilitiesFailure = CancellationException("capability lookup cancelled"),
            ),
        )

        assertFailsWith<CancellationException> { client.start() }
        client.end()

        assertEquals(0, calls.count { it == "startExerciseAsync" })
        assertEquals(1, calls.count { it == "endExerciseAsync" })
    }

    @Suppress("RestrictedApi")
    @Test
    fun callbackRegistrationCancellationIsRethrownAndLeavesOurExerciseRecoverable() = kotlinx.coroutines.test.runTest {
        val calls = mutableListOf<String>()
        val client = AndroidWearHealthClient(
            fakeExerciseClient(
                status = ExerciseTrackedStatus.NO_EXERCISE_IN_PROGRESS,
                calls = calls,
                callbackFailure = CancellationException("cancelled"),
            ),
        )

        assertFailsWith<CancellationException> { client.start() }
        client.end()

        assertEquals(1, calls.count { it == "endExerciseAsync" })
    }

    @Suppress("RestrictedApi")
    @Test
    fun repeatedStartStatusLookupCancellationIsRethrownAndKeepsOurExerciseEndable() = kotlinx.coroutines.test.runTest {
        val calls = mutableListOf<String>()
        val failures = FakeExerciseFailures()
        val client = AndroidWearHealthClient(
            fakeExerciseClient(
                status = ExerciseTrackedStatus.NO_EXERCISE_IN_PROGRESS,
                calls = calls,
                failures = failures,
            ),
        )

        assertEquals(WearHealthStartResult.Started, client.start())
        failures.currentExerciseInfoFailure = CancellationException("status lookup cancelled")

        assertFailsWith<CancellationException> { client.start() }
        client.end()

        assertEquals(1, calls.count { it == "endExerciseAsync" })
    }

    @Suppress("RestrictedApi")
    @Test
    fun repeatedStartStatusLookupPermissionFailureKeepsOurExerciseEndable() = kotlinx.coroutines.test.runTest {
        val calls = mutableListOf<String>()
        val failures = FakeExerciseFailures()
        val client = AndroidWearHealthClient(
            fakeExerciseClient(
                status = ExerciseTrackedStatus.NO_EXERCISE_IN_PROGRESS,
                calls = calls,
                failures = failures,
            ),
        )

        assertEquals(WearHealthStartResult.Started, client.start())
        failures.currentExerciseInfoFailure = SecurityException("status lookup denied")

        assertEquals(
            WearHealthStartResult.StartedWithoutUpdates(DurationOnlyReason.PERMISSION_DENIED),
            client.start(),
        )
        client.end()

        assertEquals(1, calls.count { it == "endExerciseAsync" })
    }

    @Suppress("RestrictedApi")
    @Test
    fun repeatedStartStatusLookupHealthServicesFailureKeepsOurExerciseEndable() = kotlinx.coroutines.test.runTest {
        val calls = mutableListOf<String>()
        val failures = FakeExerciseFailures()
        val client = AndroidWearHealthClient(
            fakeExerciseClient(
                status = ExerciseTrackedStatus.NO_EXERCISE_IN_PROGRESS,
                calls = calls,
                failures = failures,
            ),
        )

        assertEquals(WearHealthStartResult.Started, client.start())
        failures.currentExerciseInfoFailure = HealthServicesException("status lookup unavailable")

        assertEquals(
            WearHealthStartResult.StartedWithoutUpdates(DurationOnlyReason.HEALTH_SERVICES_UNAVAILABLE),
            client.start(),
        )
        client.end()

        assertEquals(1, calls.count { it == "endExerciseAsync" })
    }

    private data class FakeExerciseFailures(
        var remainingEndFailures: Int = 0,
        var currentExerciseInfoFailure: Throwable? = null,
    )

    private fun fakeExerciseClient(
        status: Int,
        calls: MutableList<String>,
        callbackFailure: Throwable? = null,
        capabilitiesFailure: Throwable? = null,
        heartRateAvailable: Boolean = true,
        failures: FakeExerciseFailures = FakeExerciseFailures(),
    ): ExerciseClient = Proxy.newProxyInstance(
        ExerciseClient::class.java.classLoader,
        arrayOf(ExerciseClient::class.java),
    ) { _, method, _ ->
        calls += method.name
        when (method.name) {
            "getCurrentExerciseInfoAsync" -> failures.currentExerciseInfoFailure?.let { Futures.immediateFailedFuture(it) }
                ?: Futures.immediateFuture(ExerciseInfo(status, ExerciseType.MEDITATION))
            "getCapabilitiesAsync" -> capabilitiesFailure?.let { Futures.immediateFailedFuture(it) }
                ?: Futures.immediateFuture(
                    ExerciseCapabilities(
                        mapOf(
                            ExerciseType.MEDITATION to ExerciseTypeCapabilities(
                                supportedDataTypes = if (heartRateAvailable) setOf(DataType.HEART_RATE_BPM) else emptySet(),
                                supportedGoals = emptyMap(),
                                supportedMilestones = emptyMap(),
                                supportsAutoPauseAndResume = false,
                            ),
                        ),
                    ),
                )
            "setUpdateCallback" -> callbackFailure?.let { throw it }
            "endExerciseAsync" -> if (failures.remainingEndFailures-- > 0) {
                Futures.immediateFailedFuture(HealthServicesException("cleanup failed"))
            } else {
                Futures.immediateFuture(null)
            }
            else -> Futures.immediateFuture(null)
        }
    } as ExerciseClient
}
