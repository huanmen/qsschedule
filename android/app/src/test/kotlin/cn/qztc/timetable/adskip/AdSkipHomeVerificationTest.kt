package cn.qztc.timetable.adskip
import org.junit.Assert.*
import org.junit.Test
class AdSkipHomeVerificationTest {
    private val rule = AdSkipGenericRules.rule("com.cainiao.wireless")
    private val home = rule.successActivityNames.single()
    @Test fun requiresNewMatchingWindowAndFreshPostClickHomeEvent() {
        assertTrue(AdSkipVerification.confirmedHome(rule, rule.packageName, home, 1, 2, 2, 1200, 1000, 1300))
        assertFalse(AdSkipVerification.confirmedHome(rule, rule.packageName, home, 1, 1, 1, 1200, 1000, 1300))
        assertFalse(AdSkipVerification.confirmedHome(rule, rule.packageName, home, 1, 2, 3, 1200, 1000, 1300))
        assertFalse(AdSkipVerification.confirmedHome(rule, rule.packageName, home, 1, 2, 2, 900, 1000, 1300))
        assertFalse(AdSkipVerification.confirmedHome(rule, rule.packageName, home, 1, 2, 2, 1200, 1000, 4000))
        assertFalse(AdSkipVerification.confirmedHome(rule, "other.app", home, 1, 2, 2, 1200, 1000, 1300))
        assertFalse(AdSkipVerification.confirmedHome(rule, rule.packageName, "other.Activity", 1, 2, 2, 1200, 1000, 1300))
        assertFalse(AdSkipVerification.confirmedHome(rule, rule.packageName, home, 1, 2, 2, 1400, 1000, 1300))
        assertFalse(AdSkipVerification.confirmedHome(rule, rule.packageName, home, 1, -1, -1, 1200, 1000, 1300))
        assertFalse(AdSkipVerification.confirmedHome(rule, rule.packageName, home, -1, 2, 2, 1200, 1000, 1300))
    }
}
