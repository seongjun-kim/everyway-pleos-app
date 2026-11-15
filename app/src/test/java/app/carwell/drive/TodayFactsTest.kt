package app.carwell.drive

import app.carwell.drive.domain.*
import org.junit.Assert.*
import org.junit.Test

class TodayFactsTest {
    private val day = ms(2026, 11, 15, 14, 0)
    private val night = ms(2026, 11, 15, 22, 0)

    @Test fun `낮의 가까운 길에는 말풍선이 없다`() {
        assertTrue(TodayFacts.pills(day, ZONE, null, RouteInfo.Typical(24.0, 44, 4)).isEmpty())
    }

    @Test fun `밤이면 밤이에요`() {
        assertEquals(listOf("밤이에요"), TodayFacts.pills(night, ZONE, null, RouteInfo.Typical(24.0, 44, 4)))
    }

    @Test fun `서행 구간이 30퍼센트 이상이면 알린다`() {
        val slow = TripEstimate.of(20000, 2400, listOf(Segment(Congestion.HEAVY, 0, 40), Segment(Congestion.FAST, 40, 100)))
        val calm = TripEstimate.of(20000, 1500, listOf(Segment(Congestion.FAST, 0, 100)))
        assertEquals(listOf("서행·정체 많음"), TodayFacts.pills(day, ZONE, slow, null))
        assertTrue(TodayFacts.pills(day, ZONE, calm, null).isEmpty())
    }

    @Test fun `먼 길이면 먼 길이에요`() {
        assertEquals(listOf("먼 길이에요"), TodayFacts.pills(day, ZONE, null, RouteInfo.Typical(46.0, 70, 3)))
    }

    @Test fun `문구에 권유·판정 단어가 없다`() {
        val all = TodayFacts.pills(night, ZONE, TripEstimate.of(46000, 4000, listOf(Segment(Congestion.HEAVY, 0, 100))), null)
        assertTrue(all.none { PhraseGuard.containsBanned(it) })
        assertEquals(3, all.size)
    }

    @Test fun `이 시간대 평소 소요는 같은 시간대 기록이 2건 이상일 때만`() {
        val t1 = Trip.create("a", ms(2026, 11, 1, 10), ms(2026, 11, 1, 10, 40), 24.0, 1, "종합병원", ZONE)
        val t2 = Trip.create("b", ms(2026, 11, 2, 10), ms(2026, 11, 2, 10, 50), 24.0, 1, "종합병원", ZONE)
        val n1 = Trip.create("c", ms(2026, 11, 3, 21), ms(2026, 11, 3, 22, 10), 24.0, 1, "종합병원", ZONE)
        assertEquals(45 to 2, TodayFacts.bandMinutes(listOf(t1, t2, n1), placeKey("종합병원"), night = false))
        assertNull(TodayFacts.bandMinutes(listOf(t1, t2, n1), placeKey("종합병원"), night = true))
    }
}
