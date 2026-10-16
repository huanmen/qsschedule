package cn.qztc.timetable.education

import org.junit.Assert.*
import org.junit.Test
import cn.qztc.timetable.integration.qnu.QnuCreditCourse
import cn.qztc.timetable.integration.qnu.QnuCreditGroup
import cn.qztc.timetable.integration.qnu.QnuStudyProgress

class ElectiveRecommendationTest {
    @Test fun artCanCoverBothGapsWithOneCourse() {
        assertTrue(electiveRecommendation(5.0, 1.0).contains("一门 2 学分的艺术课"))
    }
    @Test fun doesNotRecommendGoingAboveEightCredits() {
        assertTrue(electiveRecommendation(7.0, 0.0).contains("核对"))
    }
    @Test fun completedElectivesNeedNoMoreCourses() {
        assertTrue(electiveRecommendation(7.0, 2.0).contains("无需补选"))
    }
    @Test fun selectedCoursesCoverPendingGapWithoutRecommendingDuplicateSelection() {
        val art = QnuCreditCourse("art", "艺术课", 2.0, "已选课成绩未知")
        val progress = QnuStudyProgress(listOf(
            QnuCreditGroup("跨学科", 7.0, 5.0, null, null, false, listOf(art)),
            QnuCreditGroup("艺术", 2.0, 0.0, null, null, false, listOf(art))), null)
        val advice = currentElectiveAdvice(progress)
        assertTrue(advice.contains("暂不重复选课"))
        assertTrue(advice.contains("成绩未出"))
        assertTrue(advice.contains("已获跨学科 5 分"))
    }
    @Test fun unknownAndUnselectedCoursesAreNotAssumedToEarnCredits() {
        val courses = listOf(QnuCreditCourse("a", "未选", 2.0, "未选课"),
            QnuCreditCourse("b", "不及格", 2.0, "最高成绩不及格"),
            QnuCreditCourse("c", "通过", 2.0, "最高成绩已及格"),
            QnuCreditCourse("d", "已选", 1.0, "已选课"), QnuCreditCourse("d", "已选", 1.0, "已选课"))
        assertEquals(1.0, pendingElectiveCredits(courses), .0001)
    }
}
