package cn.qztc.timetable

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.qztc.timetable.education.HomeworkPayload
import cn.qztc.timetable.education.HomeworkStore
import cn.qztc.timetable.education.HomeworkChoices
import cn.qztc.timetable.education.HomeworkReminderOptions
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeworkAcceptanceTest {
    @After fun restoreUserReminderAccount() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = cn.qztc.timetable.qnu.QnuCredentialStore(context).load()?.username.orEmpty().trim()
        val key = java.security.MessageDigest.getInstance("SHA-256").digest(name.toByteArray()).joinToString("") { "%02x".format(it) }
        cn.qztc.timetable.education.HomeworkReminderScheduler.activate(context, key)
    }
    private fun payload(task: String, complete: Boolean = true) = JSONObject().put("rows", JSONArray().put(
        JSONObject().put("id", "10:$task").put("courseId", "10").put("taskId", task).put("title", "测试作业")
            .put("courseName", "测试课程").put("status", "pending").put("content", if (complete) "完整正文" else "")
            .put("detailComplete", complete).put("detailUrl", "https://eol.qztc.edu.cn/meol/common/homework.html")
            .put("attachments", JSONArray()).put("multiSubmit", JSONObject.NULL)))

    @Test fun consecutivePartialRefreshesMergeLatestSnapshotAndPreserveDetails() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "homework-acceptance-${System.nanoTime()}"
        try {
            val store = HomeworkStore(context, name)
            store.mergeAndSave(payload("1"));store.mergeAndSave(payload("2"))
            val partial = payload("1", false)
            partial.getJSONArray("rows").getJSONObject(0).put("status", "submitted")
            val items = HomeworkPayload.read(store.mergeAndSave(partial))
            assertEquals(2, items.size)
            val changed = items.first { it.taskId == "1" }
            assertEquals("submitted", changed.status); assertEquals("完整正文", changed.content); assertTrue(changed.detailComplete)
            assertNull(changed.multiSubmit)
            assertNull(HomeworkStore(context, name + "-other").snapshot())
        } finally {
            // Remove only this synthetic account's cache, leaving user results untouched.
            val key = java.security.MessageDigest.getInstance("SHA-256").digest(name.toByteArray()).joinToString("") { "%02x".format(it) }
            context.getSharedPreferences("homework-results", 0).edit().remove(key).commit()
        }
    }

    @Test fun archiveIdentityCannotOverwriteCurrentCourseRecord() {
        val current = payload("1")
        val archive = payload("1").also { it.getJSONArray("rows").getJSONObject(0).put("id", "10:1:archive:200").put("archiveTerm", "200") }
        val items = HomeworkPayload.read(HomeworkPayload.merge(current, archive))
        assertEquals(2, items.map { it.id }.distinct().size)
        assertEquals("未参与 · 归档", items.first { it.archiveTerm.isNotEmpty() }.label())
    }

    @Test fun deletionAndReminderChoicesSurviveRefreshAndCourseListKeepsEmptyCourses() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = HomeworkStore(context, "homework-choices-test")
        val choices = HomeworkChoices(context, store.key)
        val alarms = context.getSharedPreferences("homework-alarms", 0)
        val oldAccount = alarms.getString("account", null)
        try {
            val first = payload("1").put("courses", JSONArray().put(JSONObject().put("id", "10").put("name", "测试课程"))
                .put(JSONObject().put("id", "11").put("name", "没有作业的课程")))
            store.mergeAndSave(first)
            choices.hide("10:1", true)
            val options = HomeworkReminderOptions(once = java.time.LocalDateTime.of(2030, 1, 1, 10, 15), daily = java.time.LocalTime.of(8, 30), beforeMinutes = 90)
            choices.save("10:1", options)
            val merged = store.mergeAndSave(payload("1", false))
            assertEquals(2, merged.getJSONArray("courses").length())
            assertTrue(HomeworkChoices(context, store.key).hidden().contains("10:1"))
            assertEquals(options, HomeworkChoices(context, store.key).options("10:1"))
            assertFalse(HomeworkChoices(context, store.key + "other").hidden().contains("10:1"))
            choices.hide("10:1", false)
            assertTrue(choices.hidden().isEmpty())
        } finally {
            context.getSharedPreferences("homework-results", 0).edit().remove(store.key).commit()
            context.getSharedPreferences("homework-choices", 0).edit().remove("${store.key}:hidden").remove("${store.key}:reminder:10:1").commit()
            if (oldAccount != null) cn.qztc.timetable.education.HomeworkReminderScheduler.activate(context, oldAccount)
        }
    }

    @Test fun threeAlarmModesAreScheduledAndSubmissionCancelsAllOfThem() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = HomeworkStore(context, "homework-alarms-test")
        val choices = HomeworkChoices(context, store.key)
        val now = java.time.LocalDateTime.now().withSecond(0).withNano(0)
        try {
            context.getSharedPreferences("homework-alarms", 0).edit().remove("sent:${store.key}:10:1").commit()
            val root = payload("1").also { it.getJSONArray("rows").getJSONObject(0).put("deadline", now.plusDays(4).toString()) }
            store.mergeAndSave(root)
            choices.save("10:1", HomeworkReminderOptions(once = now.plusHours(2), daily = now.plusHours(1).toLocalTime(), beforeMinutes = 90))
            cn.qztc.timetable.education.HomeworkReminderScheduler.reschedule(context)
            val alarms = context.getSharedPreferences("homework-alarms", 0)
            assertEquals(3, alarms.getStringSet("scheduled", emptySet())!!.size)
            root.getJSONArray("rows").getJSONObject(0).put("status", "submitted")
            store.mergeAndSave(root)
            assertTrue(alarms.getStringSet("scheduled", emptySet())!!.isEmpty())
        } finally {
            context.getSharedPreferences("homework-results", 0).edit().remove(store.key).commit()
            context.getSharedPreferences("homework-choices", 0).edit().remove("${store.key}:reminder:10:1").commit()
        }
    }

    @Test fun reminderDeliveryPostsNotificationAndIgnoresDeletedAssignment() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        org.junit.Assume.assumeTrue(cn.qztc.timetable.notifications.QztcNotifications.canPost(context))
        val store = HomeworkStore(context, "homework-delivery-test")
        val choices = HomeworkChoices(context, store.key)
        val now = java.time.LocalDateTime.now().withSecond(0).withNano(0)
        var postedId: Int? = null
        try {
            val root = payload("1").also { it.getJSONArray("rows").getJSONObject(0).put("deadline", now.plusDays(4).toString()) }
            store.mergeAndSave(root)
            val key = context.getSharedPreferences("homework-alarms", 0).getStringSet("scheduled", emptySet())!!.single()
            cn.qztc.timetable.education.HomeworkReminderScheduler.fire(context, key)
            postedId = cn.qztc.timetable.education.HomeworkReminderScheduler.notificationId(store.key, "10:1")
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            assertTrue(manager.activeNotifications.any { it.id == postedId && it.notification.extras.getString("android.title") == "待提交：测试作业" })
            manager.cancel(postedId)
            choices.hide("10:1", true)
            cn.qztc.timetable.education.HomeworkReminderScheduler.reschedule(context)
            assertTrue(context.getSharedPreferences("homework-alarms", 0).getStringSet("scheduled", emptySet())!!.isEmpty())
            cn.qztc.timetable.education.HomeworkReminderScheduler.fire(context, key)
            repeat(10) { if (manager.activeNotifications.any { it.id == postedId }) android.os.SystemClock.sleep(100) }
            assertFalse(manager.activeNotifications.any { it.id == postedId })
        } finally {
            postedId?.let { context.getSystemService(android.app.NotificationManager::class.java).cancel(it) }
            context.getSharedPreferences("homework-results", 0).edit().remove(store.key).commit()
            context.getSharedPreferences("homework-choices", 0).edit().remove("${store.key}:hidden").commit()
            context.getSharedPreferences("homework-alarms", 0).edit().remove("sent:${store.key}:10:1").commit()
        }
    }
}
