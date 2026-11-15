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
            t?.let { "평소 ${RouteInfo.kmLabel(it.km)} · ${it.min}분 · 주행비 약 ${won(FuelCost.won(it.km))}" },
            band?.let { "${if (night) "밤에는" else "낮에는"} 평소 ${it.first}분" },
        ).joinToString(" · ").ifBlank { null }
        fun line(vararg p: String?) = p.filterNotNull().joinToString(" · ").ifBlank { null }
        val transit = if (km != null && driveMin != null && destRegion != null) line(
            TransitFare.minutesLabel(TransitFare.transitMinutes(driveMin)),
            "약 ${won(TransitFare.transitWon(km, destRegion))}",
        ) else null
        val drt = if (driveMin != null && destRegion != null) line(
            TransitFare.minutesLabel(TransitFare.drtMinutes(driveMin)),
            "약 ${won(TransitFare.drtWon(destRegion))}",
        ) else null
        val taxi = line(
            driveMin?.let { "약 ${it}분" },
            fare?.let { "약 ${RouteInfo.wonLabel(it.won)}" + if (it.night || it.outside) " (할증 포함)" else "" },
            if (seoul) "02-120 → 6번" else null,
        )
        return mapOf(
            Alternative.SELF to self, Alternative.FAMILY to null, Alternative.TAXI to taxi,
            Alternative.TRANSIT to transit, Alternative.DEMAND_BUS to drt,
        )
    }

    private fun won(w: Int) = "%,d원".format(w)
}
