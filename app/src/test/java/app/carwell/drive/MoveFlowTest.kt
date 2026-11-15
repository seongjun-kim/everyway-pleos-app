package app.carwell.drive

import app.carwell.drive.domain.*
import org.junit.Assert.*
import org.junit.Test

class MoveFlowTest {
    private val now = ms(2026, 11, 15, 15, 0)
    private fun choice(daysAgo: Int, hour: Int, alt: Alternative, name: String = "종합병원") =
        MoveChoice(ms(2026, 11, 15 - daysAgo, hour, 10), placeKey(name), name, alt)

    @Test fun `같은 날 여러 번 눌러도 모두 쌓인다`() {
        var d = AppData()
        d = DataOps.addChoice(d, "종합병원", "종합병원", Alternative.TAXI, now)
        d = DataOps.addChoice(d, "종합병원", "종합병원", Alternative.SELF, now + 3_600_000)
        d = DataOps.addChoice(d, "시골 본가", "시골 본가", Alternative.FAMILY, now + 7_200_000)
        assertEquals(3, d.choices.size)
        assertEquals(1, MoveFlow.byDay(d.choices, ZONE).size)
        assertEquals(3, MoveFlow.byDay(d.choices, ZONE).single().second.size)
    }

    @Test fun `날짜별로 묶이고 최신 날짜가 먼저`() {
        val list = listOf(choice(5, 9, Alternative.TAXI), choice(5, 18, Alternative.SELF), choice(1, 9, Alternative.FAMILY))
        val days = MoveFlow.byDay(list, ZONE)
        assertEquals(listOf("11월 14일", "11월 10일"), days.map { it.first })
        assertEquals(2, days[1].second.size)
    }

    @Test fun `이번 달 다른 방법 횟수는 직접 운전을 빼고 센다`() {
        val list = listOf(choice(1, 9, Alternative.TAXI), choice(2, 9, Alternative.SELF), choice(3, 9, Alternative.DEMAND_BUS), MoveChoice(ms(2026, 10, 26, 9), "종합병원", "종합병원", Alternative.TAXI))
        assertEquals(2, MoveFlow.otherCountThisMonth(list, now, ZONE))   // 10월 26일은 이번 달이 아님
    }

    @Test fun `택시는 전화 앱을 열고 다른 방법은 열지 않는다`() {
        assertEquals("02120", MoveFlow.dialNumber(Alternative.TAXI))
        assertNull(MoveFlow.dialNumber(Alternative.DEMAND_BUS))
        assertTrue(MoveFlow.steps(Alternative.TAXI).any { it.contains("6번") })
        assertTrue(Alternative.entries.all { MoveFlow.steps(it).isNotEmpty() })
    }




    @Test fun `가상 이력에는 이동 기록이 들어 있고 지우면 사라진다`() {
        val d = DataOps.loadDemo(AppData(), now)
        assertTrue(d.choices.isNotEmpty() && d.choices.all { it.demo })
        assertTrue(DataOps.clearDemo(d).choices.isEmpty())
        assertTrue(d.hasDemo)
    }
}
