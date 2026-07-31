package app.zhanzhuang.timer.domain

import app.zhanzhuang.timer.model.HeartRateSample

data class HeartRateSummary(
    val averageBpm: Double,
    val minimumBpm: Double,
    val maximumBpm: Double,
) {
    companion object {
        fun from(samples: List<HeartRateSample>): HeartRateSummary? {
            if (samples.isEmpty()) return null
            return HeartRateSummary(
                averageBpm = samples.map(HeartRateSample::bpm).average(),
                minimumBpm = samples.minOf(HeartRateSample::bpm),
                maximumBpm = samples.maxOf(HeartRateSample::bpm),
            )
        }
    }
}
