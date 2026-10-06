package cn.qztc.timetable.feature.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusPlaceSearchTest {
    private val places = listOf(
        CampusPoint("chaoyang", "朝阳楼", CampusCoordinate(24.872, 118.668), setOf("朝阳", "黄朝阳教学楼")),
        CampusPoint("rongmao", "荣茂楼", CampusCoordinate(24.868, 118.663), setOf("荣茂")),
        CampusPoint("gate", "东海校区大门", CampusCoordinate(24.867, 118.662), setOf("校门", "正门")),
    )

    @Test
    fun `partial building names match as the query grows`() {
        val first = CampusPlaceSearch.match("朝", places)
        val fuller = CampusPlaceSearch.match("朝阳", places)
        val room = CampusPlaceSearch.match("朝阳404", places)

        assertEquals("朝阳楼", first.single().name)
        assertEquals("朝阳楼", fuller.single().name)
        assertEquals("朝阳楼", room.single().name)
    }

    @Test
    fun `aliases and subsequence still rank the intended place first`() {
        val byAlias = CampusPlaceSearch.match("正门", places)
        val bySubsequence = CampusPlaceSearch.match("阳楼", places)

        assertEquals("东海校区大门", byAlias.single().name)
        assertEquals("朝阳楼", bySubsequence.single().name)
    }

    @Test
    fun `pinyin and initials match without switching modes`() {
        val byFull = CampusPlaceSearch.match("rongmao", places)
        val byInitials = CampusPlaceSearch.match("rml", places)
        val byVoiceRoom = CampusPlaceSearch.match("chaoyang404", places)

        assertEquals("荣茂楼", byFull.single().name)
        assertEquals("荣茂楼", byInitials.single().name)
        assertEquals("朝阳楼", byVoiceRoom.single().name)
        assertEquals("荣茂楼", CampusPlaceSearch.match("荣茂楼", places).first().name)
        assertTrue(CampusPlaceSearch.match("荣茂楼", places).none { it.name == "朝阳楼" })
    }

    @Test
    fun `user aliases are searched with the same matcher`() {
        val withAlias = places + CampusPoint(
            "archive",
            "档案馆",
            CampusCoordinate(24.87, 118.66),
            setOf("档案楼", "dangan"),
        )
        assertEquals("档案馆", CampusPlaceSearch.match("dangan", withAlias).single().name)
        assertEquals("档案馆", CampusPlaceSearch.match("dag", withAlias).first().name)
    }

    @Test
    fun `merged results keep local hits ahead of remote duplicates`() {
        val local = listOf(places[0])
        val remote = listOf(
            CampusSearchResult("amap-1", "朝阳楼", "泉州师范学院", latitude = 24.87, longitude = 118.66),
            CampusSearchResult("amap-2", "文科楼", "泉州师范学院", latitude = 24.86, longitude = 118.66),
        )
        val merged = mergeCampusSearchResults(local, remote)

        assertEquals(listOf("朝阳楼", "文科楼"), merged.map { it.name })
    }
}
