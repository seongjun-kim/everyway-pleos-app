package app.carwell.drive.domain

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 단골 목적지로 실제 다녀온 기록(거리·시간)에서 "평소 이 길"을 뽑고, 택시로 가면 대략 얼마인지 어림한다.
 * 외부 서버·실시간 교통 정보는 쓰지 않는다(그래서 막히는지는 알려 주지 못한다).
 * 요금은 서울시 중형택시 신고요금(2023.2.1 기준)을 주간 기준으로 계산한 어림값이며, 시간요금·할증·지역 차이는 반영하지 않으며, 30km 이상 장거리는 어림이 맞지 않아 요금을 빼고 보여 준다.
 */
object RouteInfo {
    data class Typical(val km: Double, val min: Int, val samples: Int)

    private const val SEOUL_BASE_WON = 4800
    private const val SEOUL_BASE_KM = 1.6
    private const val SEOUL_STEP_M = 131.0
    private const val SEOUL_STEP_WON = 100

    /** 같은 목적지로 간 운행의 가운데 값(중앙값). 운행이 없으면 null. */
    fun typical(trips: List<Trip>, key: String): Typical? {
        val list = trips.filter { t ->
            t.destination != null && placeKey(t.destination) == key && t.distanceKm >= Rules.MIN_TRIP_KM
        }
        if (list.isEmpty()) return null
        return Typical(median(list.map { it.distanceKm }), median(list.map { it.durationMin.toDouble() }).roundToInt(), list.size)
    }

    private fun median(v: List<Double>): Double {
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    /** 서울 중형택시 주간 요금 어림값(원). 시간요금·할증 제외. */
    fun seoulTaxiFare(km: Double): Int {
        if (km <= SEOUL_BASE_KM) return SEOUL_BASE_WON
        val steps = ceil((km - SEOUL_BASE_KM) * 1000.0 / SEOUL_STEP_M).toInt()
        return SEOUL_BASE_WON + steps * SEOUL_STEP_WON
    }

    /** 1,000원 단위로 어림해 "1만 3천원" 꼴로. */
    fun wonLabel(won: Int): String {
        val r = ((won / 1000.0).roundToInt() * 1000).coerceAtLeast(1000)
        val man = r / 10000
        val chon = (r % 10000) / 1000
        return when {
            man > 0 && chon > 0 -> "${man}만 ${chon}천원"
            man > 0 -> "${man}만원"
            else -> "${chon}천원"
        }
    }

    fun kmLabel(km: Double): String = if (km >= 10) "${km.roundToInt()}km" else "%.1fkm".format(km)

    const val SEOUL_PHONE_HOW = "서울은 02-120 → 6번"
    const val DEMAND_BUS_HOW = "전화 호출은 지역마다 달라요"

    /** 고른 방법 아래에 보여 줄 한 줄. 알려 줄 게 없으면 null. */
    fun infoLine(alt: Alternative, typical: Typical?): String? {
        val route = typical?.let { "평소 ${kmLabel(it.km)}·${it.min}분" }
        return when (alt) {
            Alternative.TAXI -> listOfNotNull(
                route,
                typical?.takeIf { it.km < Rules.LONG_TRIP_KM }?.let { "서울 요금표로 약 ${wonLabel(seoulTaxiFare(it.km))}" },
                "전화로 부르기: $SEOUL_PHONE_HOW",
            ).joinToString(" · ")
            Alternative.DEMAND_BUS -> listOfNotNull(route, DEMAND_BUS_HOW).joinToString(" · ")
            else -> null
        }
    }
}
