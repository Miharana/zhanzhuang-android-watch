package app.zhanzhuang.timer.wear.session

import kotlin.test.Test
import kotlin.test.assertEquals

class HapticCuePlayerTest {
    @Test
    fun completionCueIsDistinct() {
        assertEquals(longArrayOf(0, 220, 100, 65, 90, 65).toList(), pattern(Cue.COMPLETE).toList())
    }

    @Test
    fun cuesNeverReplay() {
        assertEquals(-1, repeatIndex(Cue.INTERVAL))
    }
}
