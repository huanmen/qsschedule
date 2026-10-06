package cn.qztc.timetable.notifications

import org.junit.Assert.*
import org.junit.Test

class ReminderNotificationIssueTest {
    @Test fun disabledAppAndChannelProduceActionableFailures() {
        assertNotNull(reminderNotificationIssue(false, 4, true, false, false))
        assertNotNull(reminderNotificationIssue(true, 0, false, false, false))
        assertNotNull(reminderNotificationIssue(true, 4, false, true, false))
    }
    @Test fun popupRequiresHighImportanceButNotificationBarDoesNot() {
        assertNotNull(reminderNotificationIssue(true, 3, true, false, false))
        assertNull(reminderNotificationIssue(true, 3, false, false, false))
        assertNull(reminderNotificationIssue(true, 4, true, false, false))
    }
    @Test fun doNotDisturbIsReportedAsPossibleInterference() {
        assertNotNull(reminderNotificationIssue(true, 4, true, false, true))
    }
}
