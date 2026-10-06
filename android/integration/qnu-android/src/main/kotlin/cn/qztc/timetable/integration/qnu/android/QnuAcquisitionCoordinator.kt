package cn.qztc.timetable.integration.qnu.android

import cn.qztc.timetable.core.domain.imports.ImportedTimetable
import cn.qztc.timetable.integration.qnu.QnuPageClassifier
import cn.qztc.timetable.integration.qnu.QnuPageKind
import cn.qztc.timetable.integration.qnu.QnuTimetableImporter

enum class QnuAcquisitionFailure {
    FETCH_FAILED,
    INVALID_PAGE,
    EMPTY_TIMETABLE,
}

sealed interface QnuAcquisitionResult {
    data class Ready(val timetable: ImportedTimetable) : QnuAcquisitionResult
    data object ReauthenticationRequired : QnuAcquisitionResult
    data class Failed(
        val reason: QnuAcquisitionFailure,
        val fetchFailure: QnuFetchFailure? = null,
    ) : QnuAcquisitionResult
}

/**
 * 把会话、页面获取和现有解析器连成单一入口。调用方只处理明确状态，
 * 不需要接触 Cookie，也不会把失败页面当作空课表写入本地数据。
 */
class QnuAcquisitionCoordinator(private val fetcher: QnuPageFetcher) {

    suspend fun acquire(session: QnuSessionSnapshot, semesterId: String): QnuAcquisitionResult =
        when (val fetched = fetcher.fetch(session)) {
            QnuFetchResult.ReauthenticationRequired -> QnuAcquisitionResult.ReauthenticationRequired
            is QnuFetchResult.Failed -> QnuAcquisitionResult.Failed(
                reason = QnuAcquisitionFailure.FETCH_FAILED,
                fetchFailure = fetched.reason,
            )
            is QnuFetchResult.Page -> when (QnuPageClassifier.classify(fetched.bytes)) {
                QnuPageKind.AUTHENTICATION -> QnuAcquisitionResult.ReauthenticationRequired
                QnuPageKind.UNKNOWN -> QnuAcquisitionResult.Failed(QnuAcquisitionFailure.INVALID_PAGE)
                QnuPageKind.TIMETABLE -> {
                    val imported = QnuTimetableImporter.import(fetched.bytes, semesterId)
                    if (imported.diagnostics.any { it.code == "EMPTY_GRID" } && imported.state.exceptions.isEmpty()) {
                        QnuAcquisitionResult.Failed(QnuAcquisitionFailure.EMPTY_TIMETABLE)
                    } else if (imported.hasErrors) {
                        QnuAcquisitionResult.Failed(QnuAcquisitionFailure.INVALID_PAGE)
                    } else {
                        QnuAcquisitionResult.Ready(imported)
                    }
                }
            }
        }
}
