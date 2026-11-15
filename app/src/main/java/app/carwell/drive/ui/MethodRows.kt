package app.carwell.drive.ui

import app.carwell.drive.domain.*
import java.time.ZoneId

/**
 * 오늘 이동 시트의 방법별 한 줄(시간·요금). 모두 거리 기반 어림이고, 직접 운전·택시는 오늘 내비 예상이 있으면 그 시간과 거리를 쓴다.
 * 요금표와 가정은 [TaxiFare]·[TransitFare] 한 곳에서 갱신한다.
 */
internal object MethodRows {
    fun of(
        est: TripEstimate?, t: RouteInfo.Typical?, lat: Double?, lon: Double?, nowMs: Long, zone: ZoneId,
        band: Pair<Int, Int>?, night: Boolean,
    ): Map<Alternative, String?> {
        val km = est?.km ?: t?.km
        val driveMin = est?.minutes ?: t?.min
        val destRegion = (if (lat != null && lon != null) TaxiFare.regionOf(lat, lon) else TaxiFare.Region.SEOUL)
        val seoul = TodayCompare.taxiRegionIsSeoul(est, lat, lon)
        val fare = TodayCompare.taxi(est, t, lat, lon, nowMs, zone)
        val self = est?.let { TodayCompare.selfLine(it, t) } ?: listOfNotNull(
            t?.let { "평소 ${RouteInfo.kmLabel(it.km)} · ${Dur.label(it.min)} · 주행비 약 ${won(FuelCost.won(it.km))}" },
            band?.let { "${if (night) "밤에는" else "낮에는"} 평소 ${Dur.label(it.first)}" },
        ).joinToString(" · ").ifBlank { null }
        fun line(vararg p: String?) = p.filterNotNull().joinToString(" · ").ifBlank { null }
        // 대중교통·셔클은 도로 정체의 영향을 덜 받는다고 보고, 평소(정체가 덜한 날)와 오늘 중 짧은 쪽을 기준으로 어림한다.
        val baseMin = if (driveMin != null && t != null) minOf(driveMin, t.min) else driveMin
        fun faster(min: Int) = driveMin?.takeIf { it - min >= 5 }?.let { "직접 운전보다 ${Dur.label(it - min)} 빨라요" }
        val transit = if (km != null && baseMin != null && destRegion != null) {
            val m = TransitFare.transitMinutes(baseMin)
            line(TransitFare.minutesLabel(m), "약 ${won(TransitFare.transitWon(km, destRegion))}", faster(m))
        } else null
        val drt = if (baseMin != null && destRegion != null) {
            val m = TransitFare.drtMinutes(baseMin)
            line(TransitFare.minutesLabel(m), "약 ${won(TransitFare.drtWon(destRegion))}", faster(m))
        } else null
        val taxi = line(
            driveMin?.let { "약 ${Dur.label(it)}" },
            fare?.let { "약 ${RouteInfo.wonLabel(it.won)}" + if (it.night || it.outside) " (할증 포함)" else "" },
            if (seoul) "02-120 → 6번" else null,
        )
        return mapOf(
            Alternative.SELF to self, Alternative.TAXI to taxi,
            Alternative.TRANSIT to transit, Alternative.DEMAND_BUS to drt,
        )
    }

    private fun won(w: Int) = "%,d원".format(w)
}
