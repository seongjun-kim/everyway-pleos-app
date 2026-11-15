package app.carwell.drive

import app.carwell.drive.domain.*
import org.junit.Assert.*
import org.junit.Test

class PlaceAndPlanTest {
    private fun v(name: String, at: Long) = PlaceVisit(name, null, at)
    private val t = ms(2026, 11, 1, 9)

    @Test fun `방문 횟수 상위 5곳, 동률이면 최근에 간 곳이 먼저`() {
        val visits = buildList {
            repeat(5) { add(v("병원", t + it * DAY)) }
            repeat(4) { add(v("시장", t + it * DAY)) }
            repeat(3) { add(v("교회", t + it * DAY)) }
            repeat(2) { add(v("마트", t + it * DAY)) }
            repeat(2) { add(v("경로당", t + (it + 1) * DAY)) }   // 마트와 동률, 더 최근
            add(v("약국", t))
        }
        val top = PlaceStats.topPlaces(visits, limit = 5)
        assertEquals(listOf("병원", "시장", "교회", "경로당", "마트"), top.map { it.name })
        assertEquals(5, top.size)
        assertEquals(5, top.first().count)
    }

    @Test fun `이름의 공백·대소문자 차이는 같은 곳으로 센다`() {
        val top = PlaceStats.topPlaces(listOf(v("서울 병원", t), v(" 서울  병원", t + 1), v("서울병원", t + 2)))
        assertEquals(2, top.size)
        assertEquals(2, top.first().count)
    }

    @Test fun `같은 목적지를 30분 안에 다시 안내하면 방문으로 세지 않는다`() {
        val existing = listOf(v("병원", t))
        assertFalse(PlaceStats.shouldRecordVisit(existing, "병원", t + 29 * 60_000))
        assertTrue(PlaceStats.shouldRecordVisit(existing, "병원", t + 31 * 60_000))
        assertTrue(PlaceStats.shouldRecordVisit(existing, "시장", t + 1000))
        assertFalse(PlaceStats.shouldRecordVisit(existing, "  ", t + DAY))
    }




}
