package app.zhanzhuang.timer.domain

import app.zhanzhuang.timer.model.HeartRateSample
import app.zhanzhuang.timer.model.SampleAccuracy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HeartRateSummaryTest {
    @Test
    fun fromCalculatesSummaryAcrossSamples() {
        val summary = requireNotNull(
            HeartRateSummary.from(
                listOf(
                    HeartRateSample(1_000, 80.0, SampleAccuracy.HIGH),
                    HeartRateSample(2_000, 100.0, SampleAccuracy.MEDIUM),
                    HeartRateSample(3_000, 90.0, SampleAccuracy.LOW),
                ),
            ),
        )

        assertEquals(90.0, summary.averageBpm)
        assertEquals(80.0, summary.minimumBpm)
        assertEquals(100.0, summary.maximumBpm)
    }

    @Test
    fun fromReturnsNullForNoSamples() {
        assertNull(HeartRateSummary.from(emptyList()))
    }
}
