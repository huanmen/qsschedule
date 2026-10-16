package cn.qztc.timetable.adskip

import org.junit.Assert.*
import org.junit.Test

class AdSkipAbsenceEvidenceTest {
    @Test fun incompleteScanBetweenMissingTargetsCannotConfirmSuccess() {
        val state = AdSkipAbsenceEvidence()
        assertFalse(state.observe(7, true, false))
        assertFalse(state.observe(7, false, false))
        assertFalse(state.observe(7, true, false))
        assertTrue(state.observe(7, true, false))
    }
    @Test fun windowTransitionsAndUnknownRootsRequireFreshEvidence() {
        val state = AdSkipAbsenceEvidence()
        assertFalse(state.observe(7, true, false))
        assertFalse(state.observe(8, true, false))
        state.reset()
        assertFalse(state.observe(8, true, false))
        assertFalse(state.observe(-1, true, false))
        assertFalse(state.observe(8, true, false))
        assertTrue(state.observe(8, true, false))
    }
    @Test fun reappearingTargetBreaksAbsenceSequence() {
        val state = AdSkipAbsenceEvidence()
        assertFalse(state.observe(7, true, false))
        assertFalse(state.observe(7, true, true))
        assertFalse(state.observe(7, true, false))
        assertTrue(state.observe(7, true, false))
    }
}
