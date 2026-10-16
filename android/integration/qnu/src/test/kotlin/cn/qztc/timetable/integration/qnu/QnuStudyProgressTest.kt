package cn.qztc.timetable.integration.qnu

import org.junit.Test
import org.junit.Assert.*

class QnuStudyProgressTest {
    @Test fun readsOfficialPlanSummaryWhenGroupNameIsBlank() {
        val p = QnuStudyProgressParser.parse("""<table><tr><th>教学计划</th><th>课组名称</th><th>属性</th><th>学分要求</th><th>已获学分</th><th>是否合格</th></tr>
            <tr><td>专业教学计划</td><td></td><td></td><td>170.0</td><td>142.5</td><td>未通过</td></tr>
            <tr><td></td><td>集中实践课程</td><td>必修</td><td>37.0</td><td>7.0</td><td>未通过</td></tr></table>""")
        assertEquals(170.0, p.total?.required)
        assertEquals(142.5, p.total?.earned)
        assertEquals(27.5, p.total!!.required!! - p.total.earned!!, .001)
        assertEquals(1, p.groups.size)
    }
    @Test fun preservesCourseOwnershipForSameNameAndPlanUsingOfficialGroupId() {
        val p = QnuStudyProgressParser.parse("""<table><tr><th>课组</th><th>学分要求</th><th>已获学分</th></tr>
            <tr><td>通识课程（必修）</td><td>40</td><td>40</td></tr>
            <tr><td>通识课程（必修）</td><td>1</td><td>1</td></tr></table>
            <section data-qnu-plan data-qnu-group-id="|通识课程（必修）|0"><table><caption>通识课程（必修）</caption>
            <tr><th>课程号</th><th>课程名称</th><th>学分</th><th>修读状态</th></tr>
            <tr><td>001</td><td>合成通识课程</td><td>2</td><td>最高成绩已及格</td></tr></table></section>
            <section data-qnu-plan data-qnu-group-id="|通识课程（必修）|1"><table><caption>通识课程（必修）</caption>
            <tr><th>课程号</th><th>课程名称</th><th>学分</th></tr><tr><td>002</td><td>合成限选课程</td><td>1</td></tr></table></section>""")
        assertEquals("001", p.groups[0].courses.single().code)
        assertEquals("最高成绩已及格", p.groups[0].courses.single().status)
        assertEquals("002", p.groups[1].courses.single().code)
    }
    @Test fun assignsDuplicateGroupDetailsByExplicitPlan() {
        val p = QnuStudyProgressParser.parse("""<table><tr><th>教学计划</th><th>课组</th><th>学分要求</th><th>已获学分</th></tr>
            <tr><td>主修</td><td>通识课程（必修）</td><td>36</td><td>32</td></tr>
            <tr><td>辅修</td><td>通识课程（必修）</td><td>6</td><td>1</td></tr></table>
            <section data-qnu-plan><table><tr><th>教学计划</th><th>课组</th><th>课程号</th><th>课程名称</th><th>学分</th></tr>
            <tr><td>主修</td><td>通识课程</td><td>1</td><td>主修课程</td><td>2</td></tr>
            <tr><td>辅修</td><td>通识课程</td><td>2</td><td>辅修课程</td><td>1</td></tr></table></section>""")
        assertEquals(listOf("主修课程"), p.groups[0].courses.map { it.name })
        assertEquals(listOf("辅修课程"), p.groups[1].courses.map { it.name })
    }
    @Test fun preservesDuplicateNamesAndSeparatesPlans() {
        val p = QnuStudyProgressParser.parse("""<table><tr><th>教学计划</th><th>课组</th><th>学分要求</th><th>已获学分</th></tr>
            <tr><td rowspan="2">主修</td><td>通识课程（必修）</td><td>36</td><td>32</td></tr>
            <tr><td>通识课程（必修）</td><td>4</td><td>2</td></tr>
            <tr><td>辅修</td><td>通识课程（必修）</td><td>6</td><td>1</td></tr></table>
            <section data-qnu-plan><table><tr><th>课组</th><th>课程号</th><th>课程名称</th><th>学分</th></tr>
            <tr><td>通识课程</td><td>1</td><td>同名课组不能任意归属</td><td>2</td></tr></table></section>""")
        assertEquals(3, p.groups.size)
        assertEquals(3, p.groups.map { it.id }.distinct().size)
        assertEquals(listOf("主修", "主修", "辅修"), p.groups.map { it.plan })
        assertEquals(listOf(32.0, 2.0, 1.0), p.groups.map { it.earned })
        assertTrue(p.groups.all { it.courses.isEmpty() })
    }
    @Test fun doesNotSumNestedCreditsAndPreservesCountRequirements() {
        val p = QnuStudyProgressParser.parse("""<table><tr><th>课组</th><th>学分要求</th><th>已获学分</th><th>门数要求</th><th>已通过门数</th><th>是否合格</th></tr>
            <tr><td>跨学科课程</td><td>7</td><td>7</td><td></td><td>4</td><td>是</td></tr>
            <tr><td>艺术类校选课</td><td>2</td><td>2</td><td>1</td><td>1</td><td>是</td></tr>
            <tr><td>素质拓展</td><td>0</td><td>0</td><td>8</td><td>6</td><td>否</td></tr></table>""")
        assertNull(p.total)
        assertEquals(3, p.groups.size)
        assertFalse(p.groups.last().qualified!!)
        assertEquals(8, p.groups.last().requiredCount)
    }
    @Test(expected = IllegalStateException::class) fun rejectsChangedSchema() { QnuStudyProgressParser.parse("<table><tr><td>课组</td></tr></table>") }
    @Test fun zeroCreditGroupsUseCourseCountProgress() {
        val g = QnuCreditGroup("素质拓展", 0.0, 0.0, 9, 7, false)
        assertEquals(7f / 9f, g.progressRatio!!, .001f)
    }
    @Test fun readsExplicitSummaryAndCourseDetailsWithoutSummingGroups() {
        val p = QnuStudyProgressParser.parse("""<div>要求总学分：160</div><div>已获总学分：120</div>
            <table><tr><th>课组名称</th><th>学分要求</th><th>已获学分</th><th>是否合格</th></tr>
            <tr><td>集中实践课程（必修）</td><td>37</td><td>7</td><td>未通过</td></tr></table>
            <section data-qnu-plan><table><tr><th>课组</th><th>课程号</th><th>课程名称</th><th>学分</th><th>修读情况</th></tr>
            <tr><td rowspan="2">集中实践课程</td><td>001</td><td>实习</td><td>6</td><td>未选课</td></tr>
            <tr><td>002</td><td>实践</td><td>1</td><td>通过</td></tr></table></section>""")
        assertEquals(120.0, p.total?.earned)
        assertEquals(160.0, p.total?.required)
        assertEquals(2, p.groups.single().courses.size)
        assertEquals("未选课", p.groups.single().courses.first().status)
        assertEquals(1.0, p.groups.single().courses.last().credits)
    }
    @Test fun readsOfficialGroupNameAndRowspans() {
        val p = QnuStudyProgressParser.parse("""<table><tr><th>教学计划</th><th>课组名称</th><th>属性</th><th>学分要求</th><th>已获学分</th><th>门数要求</th><th>已通过门数</th><th>是否合格</th></tr>
            <tr><td rowspan="2">合成方案 学分要求：160 已获学分：100</td><td>通识</td><td>必修</td><td>36</td><td>36</td><td>10</td><td>10</td><td><em>通过</em></td></tr>
            <tr><td>实践</td><td>必修</td><td>32</td><td>12</td><td>8</td><td>4</td><td><em>未通过</em></td></tr></table>""")
        assertEquals(2, p.groups.size)
        assertEquals(160.0, p.total?.required)
        assertEquals(100.0, p.total?.earned)
        assertEquals(true, p.groups.first().qualified)
        assertEquals(false, p.groups.last().qualified)
    }
}
