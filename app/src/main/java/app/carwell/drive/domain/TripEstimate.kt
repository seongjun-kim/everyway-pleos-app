package app.carwell.drive.domain

import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class Congestion {
    FAST, MODERATE, SLOW, HEAVY, OTHER;
    companion object { fun of(name: String): Congestion = entries.firstOrNull { it.name == name.uppercase() } ?: OTHER }
}

/** 경로를 0~100 비율로 나눈 한 구간의 정체 정도. */
data class Segment(val kind: Congestion, val start: Int, val end: Int)

/**
 * 오늘 이 길의 내비 예상(공식 NaviHelper SDK requestTrip 응답을 앱 말로 옮긴 것).
 * 시간(초)·거리(m)와, 경로 중 서행·정체로 표시된 구간의 비율만 남긴다.
 */
data class TripEstimate(
    val distanceM: Int, val etaSec: Int, val slowPct: Int, val heavyPct: Int,
    val originLat: Double? = null, val originLon: Double? = null,
    val energyWh: Double? = null, val socAtDest: Int? = null,
) {
    /** 내비가 도착 시 배터리 잔량(SOC)을 알려 주면 전기차로 본다. */
    val isEv: Boolean get() = (socAtDest ?: 0) > 0
    val km: Double get() = distanceM / 1000.0
    val minutes: Int get() = ceil(etaSec / 60.0).toInt()

    companion object {
        fun of(
            distanceM: Int, etaSec: Int, segments: List<Segment>, originLat: Double? = null, originLon: Double? = null,
            energyWh: Double? = null, socAtDest: Int? = null,
        ): TripEstimate {
            val total = segments.sumOf { (it.end - it.start).coerceAtLeast(0) }.coerceAtLeast(1)
            fun share(vararg k: Congestion) = (segments.filter { it.kind in k }.sumOf { (it.end - it.start).coerceAtLeast(0) } * 100.0 / total).roundToInt()
            return TripEstimate(distanceM, etaSec, share(Congestion.SLOW, Congestion.HEAVY), share(Congestion.HEAVY), originLat, originLon, energyWh, socAtDest)
        }
    }
}

/** 오늘 이동 시트에 쓸 비교 문구. 판단·권유 없이 사실만, 한 줄로 짧게. */
object TodayCompare {
    /** 직접 운전 아래 한 줄: 오늘 내비 예상 시간, 평소와 차이, 서행이 많을 때만 그 사실. */
    fun selfLine(est: TripEstimate, usual: RouteInfo.Typical?): String = listOfNotNull(
        "오늘 ${est.minutes}분",
        "주행비 약 ${"%,d".format(FuelCost.won(est.km, est))}원",
        usual?.let { u ->
            val d = est.minutes - u.min
            when {
                d >= 3 -> "평소보다 ${d}분 더"
                d <= -3 -> "평소보다 ${-d}분 덜"
                else -> null
            }
        },
        if (est.slowPct >= 30) "서행·정체 많음" else null,
    ).joinToString(" · ")

    /** 택시 어림 요금(오늘 내비 거리·시간이 있으면 그것, 없으면 평소 길). 어림할 수 없으면 null. */
    fun taxi(est: TripEstimate?, usual: RouteInfo.Typical?, destLat: Double?, destLon: Double?, nowMs: Long, zone: ZoneId): TaxiFare.Result? {
        val km = est?.km ?: usual?.km ?: return null
        val min = est?.minutes ?: usual?.min ?: 0
        val dest = (if (destLat != null && destLon != null) TaxiFare.regionOf(destLat, destLon) else TaxiFare.Region.SEOUL) ?: return null
        val origin = if (est?.originLat != null && est.originLon != null) TaxiFare.regionOf(est.originLat, est.originLon) else null
        return TaxiFare.estimate(km, min, est?.heavyPct ?: 0, nowMs, zone, origin, dest)
    }

    fun taxiRegionIsSeoul(est: TripEstimate?, destLat: Double?, destLon: Double?): Boolean {
        val o = if (est?.originLat != null && est.originLon != null) TaxiFare.regionOf(est.originLat, est.originLon) else null
        val d = if (destLat != null && destLon != null) TaxiFare.regionOf(destLat, destLon) else null
        return (o ?: d ?: TaxiFare.Region.SEOUL) == TaxiFare.Region.SEOUL
    }
}
