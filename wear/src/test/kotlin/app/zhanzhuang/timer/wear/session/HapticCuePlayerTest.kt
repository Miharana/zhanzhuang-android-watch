package app.zhanzhuang.timer.wear.session

import kotlin.test.Test
import kotlin.test.assertEquals

class HapticCuePlayerTest {
    @Test
    fun eachCueUsesItsSpecifiedOneShotPattern() {
        assertEquals(longArrayOf(0, 70).toList(), pattern(Cue.START).toList())
        assertEquals(longArrayOf(0, 65, 90, 65).toList(), pattern(Cue.INTERVAL).toList())
        assertEquals(longArrayOf(0, 220, 100, 65, 90, 65).toList(), pattern(Cue.COMPLETE).toList())
        assertEquals(longArrayOf(0, 45).toList(), pattern(Cue.PAUSE).toList())
        assertEquals(longArrayOf(0, 55).toList(), pattern(Cue.RESUME).toList())
    }

    @Test
    fun noCueRepeats() {
        Cue.entries.forEach { cue -> assertEquals(-1, repeatIndex(cue)) }
    }
}
