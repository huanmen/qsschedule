package cn.qztc.timetable.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import androidx.work.OneTimeWorkRequestBuilder
import cn.qztc.timetable.core.data.db.TimetableDatabase
import cn.qztc.timetable.core.data.repository.TimetableRepository
import cn.qztc.timetable.core.data.repository.ImportOutcome
import cn.qztc.timetable.integration.qnu.android.HttpsQnuPageFetcher
import cn.qztc.timetable.integration.qnu.android.QnuAcquisitionCoordinator
import cn.qztc.timetable.integration.qnu.android.QnuAcquisitionFailure
import cn.qztc.timetable.integration.qnu.android.QnuAcquisitionResult
import cn.qztc.timetable.integration.qnu.android.QnuSessionStore
import cn.qztc.timetable.notifications.QztcNotifications
import cn.qztc.timetable.snapshot.SnapshotStore
import java.util.concurrent.TimeUnit

/** Performs a background comparison only while a previously authorized session remains valid. */
class QnuSyncWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {

    override suspend fun doWork(): Result {
        val database = TimetableDatabase.build(applicationContext)
        try {
            val repository = TimetableRepository(database)
            val semesterId = repository.settings().activeSemesterId ?: return Result.success()
            val semester = repository.semester(semesterId) ?: return Result.success()
            val sessionStore = QnuSessionStore(applicationContext)
            val session = sessionStore.load() ?: return Result.success()
            return when (val result = QnuAcquisitionCoordinator(HttpsQnuPageFetcher()).acquire(session, semesterId)) {
                is QnuAcquisitionResult.Ready -> {
                    val hint=result.timetable.semesterHint
                    if(hint?.year!=semester.schoolYear || hint.term!=semester.term) return Result.success()
                    val current=repository.state(semesterId)
                    val periods=repository.periodScheme(semesterId)?.periods.orEmpty()
                    val next=cn.qztc.timetable.core.domain.schedule.UpcomingScheduleChanges.merge(
                        semester,current,result.timetable.state,periods,java.time.LocalDateTime.now())
                    if (result.timetable.hasErrors) return Result.success()
                    val inbox = ScheduleChangeInbox(applicationContext, semesterId)
                    val previous = inbox.read().associateBy { it.id }
                    val upcoming = next.exceptions.filter {
                        cn.qztc.timetable.core.domain.schedule.UpcomingScheduleChanges.isUpcoming(
                            semester, it, periods, java.time.LocalDateTime.now())
                    }
                    inbox.receive(upcoming)
                    val changed = upcoming.count { previous[it.id] != it && current.exceptions.none { saved -> saved == it } }
                    if (changed > 0) QztcNotifications.notifyScheduleChange(applicationContext, changed, semesterId.hashCode())
                    Result.success()
                }
                QnuAcquisitionResult.ReauthenticationRequired -> {
                    // A stale cookie must never be retried with saved credentials in the background.
                    sessionStore.clear()
                    Result.success()
                }
                is QnuAcquisitionResult.Failed -> when (result.reason) {
                    QnuAcquisitionFailure.FETCH_FAILED -> {
                        if (result.fetchFailure == cn.qztc.timetable.integration.qnu.android.QnuFetchFailure.NETWORK) {
                            QztcNotifications.notifySchoolVpnRequired(
                                applicationContext,
                                "school-vpn-required".hashCode(),
                            )
                        }
                        Result.retry()
                    }
                    QnuAcquisitionFailure.INVALID_PAGE,
                    QnuAcquisitionFailure.EMPTY_TIMETABLE -> Result.success()
                }
            }
        } finally {
            database.close()
        }
    }
}

object QnuSyncScheduler {
    private const val PERIODIC_WORK_NAME = "qnu-timetable-periodic-sync"
    private const val IMMEDIATE_WORK_NAME = "qnu-timetable-immediate-sync"

    fun schedule(context: Context, intervalHours: Int = 2) {
        val request = PeriodicWorkRequestBuilder<QnuSyncWorker>(
            intervalHours.coerceIn(1, 24).toLong(),
            TimeUnit.HOURS,
        )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<QnuSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
