package cn.qztc.timetable.feature.timetable

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ActivityTextRecognitionTest {
    private val day=LocalDate.of(2026,10,15)
    @Test fun chineseSimplifiedFinancialAndTraditionalNumbersAreRecognized() {
        for(text in listOf("二〇二六年十月二十一日下午两点三十分 在图书馆 开会",
            "貳仟零貳拾陸年拾月貳拾壹日下午兩點參拾分 在图书馆 开会")) {
            val result=recognizeActivityText(text,day)
            assertEquals(LocalDate.of(2026,10,21),result.date)
            assertEquals(870,result.minute);assertEquals("图书馆",result.location);assertEquals("开会",result.title)
        }
        assertEquals(21*60+30,recognizeActivityText("下週伍晚上玖點半 讨论第三章",day).minute)
        assertEquals(LocalDate.of(2026,10,23),recognizeActivityText("下週伍晚上玖點半 讨论第三章",day).date)
        assertEquals("讨论第三章",recognizeActivityText("下週伍晚上玖點半 讨论第三章",day).title)
        assertEquals(LocalDate.of(2026,10,23),recognizeActivityText("十月廿三日 读书",day).date)
        assertNull(recognizeActivityText("二月三十日 六十点 读书",day).date)
        assertNull(recognizeActivityText("六十点 读书",day).minute)
    }
    @Test fun relativeTimeLocationAndNoteAreRecognizedLocally() {
        val result=recognizeActivityText("明天下午2点 在图书馆 开会 备注：带电脑",day)
        assertEquals(day.plusDays(1),result.date)
        assertEquals(840,result.minute)
        assertEquals("图书馆",result.location)
        assertEquals("开会",result.title)
        assertEquals("带电脑",result.note)
    }
    @Test fun explicitDateAndNextWeekAreUnambiguous() {
        assertEquals(LocalDate.of(2027,2,1),recognizeActivityText("2027年2月1日 08:30 复习",day).date)
        assertEquals(LocalDate.of(2026,10,19),recognizeActivityText("下周一 复习",day).date)
        val title=recognizeActivityText("整理笔记",day)
        assertNull(title.date);assertNull(title.minute);assertEquals("整理笔记",title.title)
        assertNull(recognizeActivityText("2月30日 不确定",day).date)
    }
}
