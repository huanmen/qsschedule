package cn.qztc.timetable.adskip

import org.junit.Assert.*
import org.junit.Test

class AdSkipDetectionRoundTest {
    @Test fun sameTargetIsAttemptedOnlyOncePerRound() {
        val round = AdSkipDetectionRound()
        assertTrue(round.allows("a", 0))
        round.attempted("a", 0)
        assertFalse(round.allows("a", 1000))
        assertFalse(round.allows("b", 500))
        assertTrue(round.allows("b", 1000))
    }

    @Test fun thirdDifferentTargetFinishesRound() {
        val round = AdSkipDetectionRound()
        round.attempted("a", 0)
        round.attempted("b", 800)
        assertTrue(round.allows("c", 1600))
        round.attempted("c", 1600)
        assertTrue(round.finished())
        assertFalse(round.allows("d", 3000))
    }

    @Test fun absenceAfterAttemptRearmsAfterGap() {
        val round = AdSkipDetectionRound()
        round.attempted("a", 0)
        assertFalse(round.presence(false, 100))
        assertFalse(round.presence(false, 1200))
        assertTrue(round.presence(false, 2500))
        assertTrue(round.allows("a", 2500))
    }

    @Test fun visibleTargetCancelsAbsenceEvidence() {
        val round = AdSkipDetectionRound()
        round.attempted("a", 0)
        round.presence(false, 100)
        assertFalse(round.presence(true, 900))
        assertFalse(round.presence(false, 1200))
    }
    @Test fun nextAdCanBeProcessedWithoutReclickingDismissedAd() {
        val round = AdSkipDetectionRound()
        round.attempted("popup-close", 0)
        assertFalse(round.finished())
        assertTrue(round.allows("feed-close", 800))
        assertFalse(round.allows("popup-close", 800))
        round.attempted("feed-close", 800)
        assertFalse(round.allows("feed-close", 1800))
        assertTrue(round.allows("other-popup-close", 1800))
    }
}
