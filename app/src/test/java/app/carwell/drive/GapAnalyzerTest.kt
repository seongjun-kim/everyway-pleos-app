package app.carwell.drive

import app.carwell.drive.domain.*
import org.junit.Assert.*
import org.junit.Test

class GapAnalyzerTest {
    private val now = ms(2026, 11, 14, 12)
    // 최근 4주 구간: now-28d..now, 기준 구간: now-56d..now-28d
    private fun recentTs(i: Int) = now - 1 * DAY - i * 2 * DAY / 3 // 28일 안쪽
    private fun baseTs(i: Int) = now - 29 * DAY - i * 2 * DAY / 3

    private data class Spec(val n: Int, val night: Int, val long: Int)

    private fun trips(spec: Spec, ts: (Int) -> Long) =
        (0 until spec.n).map { i -> trip(ts(i), night = i < spec.night, long = i < spec.long, id = "x${ts(i)}-$i") }

    private fun feelings(ts: (Int) -> Long, easy: Int, normal: Int, tense: Int): List<FeelingEntry> {
        var i = 0
        return buildList {
            repeat(easy) { add(feeling(ts(i++), Feeling.EASY)) }
            repeat(normal) { add(feeling(ts(i++), Feeling.NORMAL)) }
            repeat(tense) { add(feeling(ts(i++), Feeling.TENSE)) }
        }
    }

    @Test fun `기록이 모자라면 기록을 쌓는 중이에요만 나온다`() {
        val few = trips(Spec(5, 0, 0), ::recentTs) + trips(Spec(5, 0, 0), ::baseTs)
        assertEquals(GapResult.Collecting(), GapAnalyzer.analyze(few, feelings(::recentTs, 6, 0, 0), now))
        assertEquals("기록을 쌓는 중이에요", GapResult.COLLECTING_TEXT)
        assertTrue(GapAnalyzer.analyze(emptyList(), emptyList(), now) is GapResult.Collecting)
    }

    @Test fun `직전 기간 기록이 없으면 비교하지 않는다`() {
        val onlyRecent = trips(Spec(12, 4, 0), ::recentTs)
        assertTrue(GapAnalyzer.analyze(onlyRecent, feelings(::recentTs, 10, 0, 0), now) is GapResult.Collecting)
    }

    @Test fun `최근 느낌 기록이 6개 미만이면 비교하지 않는다`() {
        val t = trips(Spec(12, 6, 0), ::recentTs) + trips(Spec(12, 0, 0), ::baseTs)
        assertTrue(GapAnalyzer.analyze(t, feelings(::recentTs, 5, 0, 0), now) is GapResult.Collecting)
    }

    @Test fun `야간이 늘었는데 느낌은 편안 위주면 간극 카드`() {
        val t = trips(Spec(12, 6, 0), ::recentTs) + trips(Spec(12, 1, 0), ::baseTs) // 50% vs 8%
        val r = GapAnalyzer.analyze(t, feelings(::recentTs, 8, 2, 0), now)
        val card = r as GapResult.Card
        assertEquals(GapKind.RECORD_UP_FEELING_EASY, card.kind)
        assertEquals("느낌과 기록이 달라요", card.title)
        assertTrue(card.lines.any { it.contains("야간 운행이 8%에서 50%로 늘었어요") })
        assertTrue(card.lines.any { it.contains("'편안'이 80%") })
    }

    @Test fun `기록이 늘어도 느낌이 긴장 위주면 카드 없음(일치)`() {
        val t = trips(Spec(12, 6, 0), ::recentTs) + trips(Spec(12, 1, 0), ::baseTs)
        assertEquals(GapResult.Quiet, GapAnalyzer.analyze(t, feelings(::recentTs, 2, 2, 6), now))
    }

    @Test fun `편안이 60퍼센트 미만이면 편안 위주가 아니다`() {
        val t = trips(Spec(12, 6, 0), ::recentTs) + trips(Spec(12, 1, 0), ::baseTs)
        assertEquals(GapResult.Quiet, GapAnalyzer.analyze(t, feelings(::recentTs, 5, 5, 0), now)) // 50%
        assertTrue(GapAnalyzer.analyze(t, feelings(::recentTs, 6, 4, 0), now) is GapResult.Card) // 정확히 60%
    }

    @Test fun `야간 비율 증가가 정확히 15퍼센트포인트면 늘어난 것, 미만이면 아님`() {
        // 20회 중 야간: 기준 2(10%) → 최근 5(25%) = +15pt
        val base = trips(Spec(20, 2, 0), ::baseTs)
        val at = GapAnalyzer.analyze(trips(Spec(20, 5, 0), ::recentTs) + base, feelings(::recentTs, 8, 0, 0), now)
        assertTrue(at is GapResult.Card)
        val below = GapAnalyzer.analyze(trips(Spec(20, 4, 0), ::recentTs) + base, feelings(::recentTs, 8, 0, 0), now)
        assertEquals(GapResult.Quiet, below)
    }

    @Test fun `주간 운행 횟수가 30퍼센트 이상 늘면 기록이 늘어난 것`() {
        // 기준 10회(주 2.5) → 최근 14회(주 3.5): +1.0, +40%
        val t = trips(Spec(14, 0, 0), ::recentTs) + trips(Spec(10, 0, 0), ::baseTs)
        val card = GapAnalyzer.analyze(t, feelings(::recentTs, 8, 0, 0), now) as GapResult.Card
        assertTrue(card.lines.any { it.contains("주간 운행이 2.5번에서 3.5번으로 늘었어요") })
        // 기준 20회(주 5) → 최근 24회(주 6): +1.0 이지만 +20% → 아님
        val t2 = trips(Spec(24, 0, 0), ::recentTs) + trips(Spec(20, 0, 0), ::baseTs)
        assertEquals(GapResult.Quiet, GapAnalyzer.analyze(t2, feelings(::recentTs, 8, 0, 0), now))
    }

    @Test fun `기록은 그대로인데 긴장이 늘면 간극 카드`() {
        val t = trips(Spec(12, 2, 1), ::recentTs) + trips(Spec(12, 2, 1), ::baseTs)
        val f = feelings(::recentTs, 3, 2, 5) + feelings(::baseTs, 8, 1, 1) // 긴장 50% vs 10%
        val card = GapAnalyzer.analyze(t, f, now) as GapResult.Card
        assertEquals(GapKind.RECORD_SAME_FEELING_TENSE, card.kind)
        assertTrue(card.lines.any { it.contains("'긴장'은 10%에서 50%로 늘었어요") })
    }

    @Test fun `기록은 그대로인데 긴장이 조금만 늘면 카드 없음`() {
        val t = trips(Spec(12, 2, 1), ::recentTs) + trips(Spec(12, 2, 1), ::baseTs)
        val f = feelings(::recentTs, 7, 2, 2) + feelings(::baseTs, 8, 1, 1) // 18% vs 10%
        assertEquals(GapResult.Quiet, GapAnalyzer.analyze(t, f, now))
    }

    @Test fun `기준 구간 느낌이 4개 미만이면 긴장 비교는 하지 않는다`() {
        val t = trips(Spec(12, 2, 1), ::recentTs) + trips(Spec(12, 2, 1), ::baseTs)
        val f = feelings(::recentTs, 2, 2, 6) + feelings(::baseTs, 3, 0, 0)
        assertEquals(GapResult.Quiet, GapAnalyzer.analyze(t, f, now))
    }

    @Test fun `기록이 줄었고 긴장이 늘어도 카드는 만들지 않는다`() {
        val t = trips(Spec(8, 0, 0), ::recentTs) + trips(Spec(24, 0, 0), ::baseTs) // 주 2 vs 6
        val f = feelings(::recentTs, 3, 2, 5) + feelings(::baseTs, 8, 1, 1)
        assertEquals(GapResult.Quiet, GapAnalyzer.analyze(t, f, now))
    }

    @Test fun `카드 문구에는 금지어가 없다`() {
        val t = trips(Spec(20, 12, 8), ::recentTs) + trips(Spec(12, 1, 0), ::baseTs)
        val card = GapAnalyzer.analyze(t, feelings(::recentTs, 9, 1, 0), now) as GapResult.Card
        (card.lines + card.title).forEach { assertFalse(it, PhraseGuard.containsBanned(it)) }
    }
}
