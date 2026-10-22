package cn.qztc.timetable.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeekRuleTextTest {

    @Test
    fun `continuous range`() {
        assertEquals((1..16).toList(), WeekRuleText.parse("1-16周")!!.weeks)
        assertEquals((1..8).toList(), WeekRuleText.parse("1-8周")!!.weeks)
        assertEquals((3..18).toList(), WeekRuleText.parse("3-18周")!!.weeks)
    }

    @Test
    fun `single week`() {
        assertEquals(listOf(13), WeekRuleText.parse("第13周")!!.weeks)
        assertEquals(listOf(7), WeekRuleText.parse("第7周")!!.weeks)
    }

    @Test
    fun `parity without zhou suffix`() {
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14, 16), WeekRuleText.parse("1-16双")!!.weeks)
        assertEquals(listOf(1, 3, 5, 7, 9, 11, 13, 15), WeekRuleText.parse("1-16单")!!.weeks)
        assertEquals(listOf(4, 6, 8, 10, 12, 14, 16, 18), WeekRuleText.parse("3-18双")!!.weeks)
    }

    @Test
    fun `parity with zhou suffix`() {
        assertEquals(listOf(3, 5, 7, 9, 11, 13, 15, 17), WeekRuleText.parse("3-17周单")!!.weeks)
    }

    @Test
    fun `comma separated list`() {
        assertEquals(listOf(1, 3, 5, 9, 10, 11), WeekRuleText.parse("1,3,5,9-11周")!!.weeks)
        assertEquals(listOf(2, 4), WeekRuleText.parse("2、4周")!!.weeks)
    }

    @Test
    fun `short term chinese week list and range`() {
        assertEquals(listOf(16, 17), WeekRuleText.parse("第16、17周")!!.weeks)
        assertEquals(listOf(16, 17), WeekRuleText.parse("第十六、十七周")!!.weeks)
        assertEquals(listOf(16, 17), WeekRuleText.parse("第壹拾陆、壹拾柒周")!!.weeks)
        assertEquals(listOf(16, 17), WeekRuleText.parse("十六至十七周")!!.weeks)
    }

    @Test
    fun `raw text preserved`() {
        assertEquals("3-17周单", WeekRuleText.parse("3-17周单")!!.rawText)
    }

    @Test
    fun `unparseable text returns null`() {
        assertNull(WeekRuleText.parse(""))
        assertNull(WeekRuleText.parse("讲课学时"))
        assertNull(WeekRuleText.parse("16-1周"))
        assertNull(WeekRuleText.parse("0-5周"))
        assertNull(WeekRuleText.parse("第周"))
        assertNull(WeekRuleText.parse("1-99周"))
    }

    @Test
    fun `empty parity result returns null`() {
        assertNull(WeekRuleText.parse("2-2单"))
    }
}
