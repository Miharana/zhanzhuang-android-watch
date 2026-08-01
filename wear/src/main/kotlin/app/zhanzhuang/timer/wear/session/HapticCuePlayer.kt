package app.zhanzhuang.timer.wear.session

import android.os.VibrationEffect
import android.os.Vibrator

enum class Cue { START, INTERVAL, COMPLETE, PAUSE, RESUME }

fun pattern(cue: Cue): LongArray = when (cue) {
    Cue.START -> longArrayOf(0, 70)
    Cue.INTERVAL -> longArrayOf(0, 65, 90, 65)
    Cue.COMPLETE -> longArrayOf(0, 220, 100, 65, 90, 65)
    Cue.PAUSE -> longArrayOf(0, 45)
    Cue.RESUME -> longArrayOf(0, 55)
}

/** A negative repeat index is Android's explicit one-shot setting. */
fun repeatIndex(cue: Cue): Int = -1

interface HapticCuePlayer {
    fun play(cue: Cue)
}

class AndroidHapticCuePlayer(private val vibrator: Vibrator) : HapticCuePlayer {
    override fun play(cue: Cue) {
        vibrator.vibrate(VibrationEffect.createWaveform(pattern(cue), repeatIndex(cue)))
    }
}
