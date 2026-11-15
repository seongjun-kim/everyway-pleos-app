package app.carwell.drive

import app.carwell.drive.domain.*
import org.junit.Assert.*
import org.junit.Test

class DemoAndTextTest {
    private val now = ms(2026, 11, 14, 15, 30)

    @Test fun `가상 이력은 8주치이고 모두 demo 표시가 붙는다`() {
        val d = DemoData.generate(now, ZONE)
        assertEquals(32, d.trips.size)
        assertTrue(d.trips.all { it.demo } && d.feelings.all { it.demo } && d.visits.all { it.demo })
        assertTrue(d.trips.all { it.startMs < now && it.startMs >= now - 56 * DAY })
        assertTrue(d.feelings.size >= 20)
    }

    @Test fun `가상 이력의 파생값은 규칙과 같다`() {
        DemoData.generate(now, ZONE).trips.forEach {
            assertEquals(Rules.isNight(it.startMs, ZONE), it.night)
            assertEquals(Rules.isLong(it.distanceKm), it.long)
        }
    }

    @Test fun `가상 이력을 넣으면 간극 카드가 나오고 종합병원이 단골 상위에 있다`() {
        val d = DemoData.generate(now, ZONE)
        val r = GapAnalyzer.analyze(d.trips, d.feelings, now, ZONE)
        assertTrue(r.toString(), r is GapResult.Card)
        assertEquals(GapKind.RECORD_UP_FEELING_EASY, (r as GapResult.Card).kind)
        val top = PlaceStats.topPlaces(d.visits)
        assertEquals(12, top.size)
        assertTrue(top.any { it.name == "종합병원" })
        assertEquals("솔밭한정식", d.trips.maxBy { it.startMs }.destination)
        assertTrue(top.none { it.name == "시장" || it.name == "경로당" })
        // 같은 곳은 같은 거리(평소 길 계산이 안정적이도록)
        d.trips.groupBy { it.destination }.forEach { (n, l) -> assertTrue(n, l.maxOf { it.distanceKm } - l.minOf { it.distanceKm } < 1.0) }
    }

    @Test fun `가상 이력은 어느 시각에 불러와도 같은 결과(카드)가 나온다`() {
        for (h in listOf(0, 3, 9, 14, 21, 23)) {
            val t = ms(2026, 11, 14, h, 5)
            val d = DemoData.generate(t, ZONE)
            assertTrue("hour $h", GapAnalyzer.analyze(d.trips, d.feelings, t, ZONE) is GapResult.Card)
        }
    }

    @Test fun `한 줄 요약과 매너 한 줄`() {
        val t = Trip.create("a", ms(2026, 11, 14, 10), ms(2026, 11, 14, 10, 25), 12.34, 3, "서울병원", ZONE)
        assertEquals("12.3km · 25분 · 서울병원", TripText.summaryLine(t))
        assertEquals("방향지시등을 3번 켜고 달렸어요", TripText.mannerLine(t))
        assertNull(TripText.mannerLine(t.copy(turnSignalCount = 0)))
        assertEquals("12.3km · 25분", TripText.summaryLine(t.copy(destination = null)))
        assertTrue(TripText.spoken(t).startsWith("이번 운전은 12.3킬로미터, 25분이었어요."))
        assertEquals("12.3km · 1분 미만", TripText.summaryLine(t.copy(destination = null, endMs = t.startMs + 20_000)))
    }

    @Test fun `목적지 이름이 금지어를 포함해도 요약에는 그대로 나온다`() {
        val t = Trip.create("a", ms(2026, 11, 14, 10), ms(2026, 11, 14, 10, 25), 5.0, 0, "치매안심센터", ZONE)
        assertTrue(TripText.summaryLine(t).contains("치매안심센터"))
    }
}
