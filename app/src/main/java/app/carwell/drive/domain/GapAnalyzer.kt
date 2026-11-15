package app.carwell.drive.domain

import java.time.ZoneId
import kotlin.math.roundToInt

data class WindowStats(
    val trips: Int,
    val nightRatio: Double,
    val longRatio: Double,
    val weeklyTrips: Double,
    val feelings: Int,
    val easyShare: Double,
    val tenseShare: Double,
)

enum class GapKind { RECORD_UP_FEELING_EASY, RECORD_SAME_FEELING_TENSE }

sealed interface GapResult {
    data class Collecting(val message: String = COLLECTING_TEXT) : GapResult
    /** 데이터는 충분하지만 보여 줄 간극이 없음 */
    data object Quiet : GapResult
    data class Card(val kind: GapKind, val title: String, val lines: List<String>) : GapResult

    companion object {
        const val COLLECTING_TEXT = "기록을 쌓는 중이에요"
        const val CARD_TITLE = "느낌과 기록이 달라요"
    }
}

object GapAnalyzer {
    private const val DAY = 86_400_000L

    fun stats(trips: List<Trip>, feelings: List<FeelingEntry>, fromMs: Long, toMs: Long): WindowStats {
        val t = trips.filter { it.startMs in fromMs until toMs }
        val f = feelings.filter { it.atMs in fromMs until toMs }
        fun ratio(n: Int, d: Int) = if (d == 0) 0.0 else n.toDouble() / d
        return WindowStats(
            trips = t.size,
            nightRatio = ratio(t.count { it.night }, t.size),
            longRatio = ratio(t.count { it.long }, t.size),
            weeklyTrips = t.size / (Rules.WINDOW_DAYS / 7.0),
            feelings = f.size,
            easyShare = ratio(f.count { it.feeling == Feeling.EASY }, f.size),
            tenseShare = ratio(f.count { it.feeling == Feeling.TENSE }, f.size),
        )
    }

    fun analyze(trips: List<Trip>, feelings: List<FeelingEntry>, nowMs: Long, @Suppress("UNUSED_PARAMETER") zone: ZoneId = ZoneId.systemDefault()): GapResult {
        val recent = stats(trips, feelings, nowMs - Rules.WINDOW_DAYS * DAY, nowMs + 1)
        val base = stats(trips, feelings, nowMs - 2 * Rules.WINDOW_DAYS * DAY, nowMs - Rules.WINDOW_DAYS * DAY)

        if (recent.trips < Rules.MIN_TRIPS_PER_WINDOW || base.trips < Rules.MIN_TRIPS_PER_WINDOW ||
            recent.feelings < Rules.MIN_FEELINGS_RECENT
        ) return GapResult.Collecting()

        val nightUp = recent.nightRatio - base.nightRatio >= Rules.NIGHT_RATIO_RISE - EPS
        val longUp = recent.longRatio - base.longRatio >= Rules.LONG_RATIO_RISE - EPS
        val weeklyUp = recent.weeklyTrips - base.weeklyTrips >= Rules.WEEKLY_TRIPS_RISE_MIN_ABS - EPS &&
            base.weeklyTrips > 0 &&
            (recent.weeklyTrips - base.weeklyTrips) / base.weeklyTrips >= Rules.WEEKLY_TRIPS_RISE_RATIO - EPS
        val recordUp = nightUp || longUp || weeklyUp

        val nightDown = base.nightRatio - recent.nightRatio >= Rules.NIGHT_RATIO_RISE - EPS
        val longDown = base.longRatio - recent.longRatio >= Rules.LONG_RATIO_RISE - EPS
        val weeklyDown = base.weeklyTrips - recent.weeklyTrips >= Rules.WEEKLY_TRIPS_RISE_MIN_ABS - EPS &&
            base.weeklyTrips > 0 &&
            (base.weeklyTrips - recent.weeklyTrips) / base.weeklyTrips >= Rules.WEEKLY_TRIPS_RISE_RATIO - EPS
        val recordSame = !recordUp && !nightDown && !longDown && !weeklyDown

        val easyDominant = recent.easyShare >= Rules.EASY_DOMINANT_SHARE - EPS && recent.tenseShare <= Rules.TENSE_LOW_SHARE + EPS

        if (recordUp && easyDominant) {
            val lines = buildList {
                if (nightUp) add("야간 운행이 ${pct(base.nightRatio)}에서 ${pct(recent.nightRatio)}로 늘었어요")
                if (longUp) add("장거리 운행이 ${pct(base.longRatio)}에서 ${pct(recent.longRatio)}로 늘었어요")
                if (weeklyUp) add("주간 운행이 ${one(base.weeklyTrips)}번에서 ${one(recent.weeklyTrips)}번으로 늘었어요")
                add("같은 기간 느낌은 '편안'이 ${pct(recent.easyShare)}였어요")
            }
            return card(GapKind.RECORD_UP_FEELING_EASY, lines)
        }

        val tenseUp = base.feelings >= Rules.MIN_FEELINGS_BASELINE &&
            recent.tenseShare - base.tenseShare >= Rules.TENSE_RISE - EPS &&
            recent.tenseShare >= Rules.TENSE_MIN_SHARE - EPS
        if (recordSame && tenseUp) {
            val lines = listOf(
                "최근 4주 운행 기록은 직전 4주와 비슷해요 (주 ${one(base.weeklyTrips)}번, ${one(recent.weeklyTrips)}번)",
                "느낌 '긴장'은 ${pct(base.tenseShare)}에서 ${pct(recent.tenseShare)}로 늘었어요",
            )
            return card(GapKind.RECORD_SAME_FEELING_TENSE, lines)
        }
        return GapResult.Quiet
    }

    private const val EPS = 1e-9
    private fun pct(x: Double) = "${(x * 100).roundToInt()}%"
    private fun one(x: Double) = String.format(java.util.Locale.US, "%.1f", x)

    private fun card(kind: GapKind, lines: List<String>): GapResult =
        if (PhraseGuard.containsBanned(GapResult.CARD_TITLE) || lines.any { PhraseGuard.containsBanned(it) }) GapResult.Quiet
        else GapResult.Card(kind, GapResult.CARD_TITLE, lines)
}
