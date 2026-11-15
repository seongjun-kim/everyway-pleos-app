package app.carwell.drive.domain

import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 택시 어림 요금. 요금표는 이 파일 한 곳에만 있고, 요금이 바뀌면 [SEOUL]·[GYEONGGI] 값과 [AS_OF]만 고치면 된다(앱 업데이트마다 최신화).
 * 반영: 기본요금·거리요금, 심야 할증(출발 시각 기준), 시계외 할증(출발·도착 지역이 다를 때), 정체 구간의 시간요금(어림).
 * 반영하지 않음: 통행료·호출료, 정확한 행정경계(좌표 상자로 어림), 서울·경기 밖 지역(요금 표시 없음).
 */
object TaxiFare {
    enum class Region { SEOUL, GYEONGGI }

    /** [fromHour, toHour) 시 구간의 할증률. toHour < fromHour 이면 자정을 넘는다. */
    data class Band(val fromHour: Int, val toHour: Int, val rate: Double)

    data class Policy(
        val baseWon: Int, val baseKm: Double, val stepM: Double, val stepWon: Int, val timeStepSec: Int,
        val night: List<Band>, val outsideRate: Double,
    )

    const val AS_OF = "서울 2023.2.1 · 경기 2023.7.1 고시"

    // 서울시 고시: 심야 22~23·02~04시 20%, 23~02시 40%, 시계외 20%, 겹치면 합산(최대 60%)
    val SEOUL = Policy(4800, 1.6, 131.0, 100, 30, listOf(Band(22, 23, .2), Band(23, 2, .4), Band(2, 4, .2)), .2)
    // 경기도 표준형: 심야 23~04시 30%. 시계외 20%는 서울과 같다고 보았다(확인 필요).
    val GYEONGGI = Policy(4800, 1.6, 131.0, 100, 30, listOf(Band(23, 4, .3)), .2)

    fun policy(r: Region) = if (r == Region.SEOUL) SEOUL else GYEONGGI

    /** 좌표로 지역을 어림한다. 서울 밖의 수도권(경기·인천 포함)은 경기, 수도권 밖은 null. 행정경계가 아니라 상자다. */
    fun regionOf(lat: Double, lon: Double): Region? = when {
        lat in 37.43..37.70 && lon in 126.76..127.19 -> Region.SEOUL
        lat in 36.9..38.1 && lon in 126.4..127.9 -> Region.GYEONGGI
        else -> null
    }

    private fun nightRate(p: Policy, hour: Int): Double =
        p.night.firstOrNull { b -> if (b.fromHour <= b.toHour) hour in b.fromHour until b.toHour else hour >= b.fromHour || hour < b.toHour }?.rate ?: 0.0

    data class Result(val won: Int, val night: Boolean, val outside: Boolean)

    /**
     * @param heavyPct 경로 중 정체 구간 비율(0~100). 그 비율만큼은 거리 대신 시간으로 요금이 붙는다고 어림한다.
     * @param origin 출발 지역(모르면 도착과 같다고 본다), @param dest 도착 지역. 도착을 모르면 null 을 돌려준다.
     */
    fun estimate(km: Double, minutes: Int, heavyPct: Int, departMs: Long, zone: ZoneId, origin: Region?, dest: Region?): Result? {
        dest ?: return null
        val from = origin ?: dest
        val p = policy(from)
        val heavy = heavyPct.coerceIn(0, 100) / 100.0
        val distKm = km * (1 - heavy)
        var won = p.baseWon.toDouble()
        if (distKm > p.baseKm) won += ceil((distKm - p.baseKm) * 1000.0 / p.stepM) * p.stepWon
        if (heavy > 0) won += ceil(minutes * 60 * heavy / p.timeStepSec) * p.stepWon
        val hour = Instant.ofEpochMilli(departMs).atZone(zone).hour
        val night = nightRate(p, hour)
        val outside = if (from != dest) p.outsideRate else 0.0
        won *= 1 + night + outside
        return Result(((won / 10.0).roundToInt()) * 10, night > 0, outside > 0)
    }
}

/**
 * 버스·지하철·부르면 오는 버스(DRT)의 거리 기반 어림 요금과 시간. 요금표와 가정은 이 파일 한 곳에서 갱신한다.
 * 수도권 교통카드 기준: 서울 지하철 1,550원(2025.6.28 인상, 10km까지, 10~50km 5km마다 +100, 50km 초과 8km마다 +100),
 * 서울 간선·지선버스 1,500원, 경기 일반형 시내버스 1,650원(2025.10.25 인상, 10km까지, 10~40km 5km마다 +100, 상한 2,350원).
 * DRT 요금은 지역마다 달라(세종 이응버스 성인 1,800원 등) 해당 지역 시내버스 기본요금과 같다고 가정한다.
 * 시간은 그날 내비 예상(자동차) 시간의 배수에 접근·대기 시간을 더한 어림이다(가정값).
 */
object TransitFare {
    const val AS_OF = "서울 2025.6 · 경기 2025.10 기준, DRT는 가정"

    private fun steps(km: Double, from: Double, per: Double) = if (km <= from) 0 else kotlin.math.ceil((km - from) / per).toInt()

    fun subwayWon(km: Double): Int = when {
        km <= 10.0 -> 1550
        km <= 50.0 -> 1550 + 100 * steps(km, 10.0, 5.0)
        else -> 1550 + 100 * 8 + 100 * steps(km, 50.0, 8.0)
    }

    fun gyeonggiBusWon(km: Double): Int = (1650 + 100 * steps(km, 10.0, 5.0)).coerceAtMost(2350)

    /** "버스·지하철" 한 줄에 쓸 대표 요금: 서울은 지하철 거리비례, 경기는 일반 시내버스 거리비례. */
    fun transitWon(km: Double, region: TaxiFare.Region): Int = if (region == TaxiFare.Region.SEOUL) subwayWon(km) else gyeonggiBusWon(km)

    fun drtWon(region: TaxiFare.Region): Int = if (region == TaxiFare.Region.SEOUL) 1500 else 1650

    /** 자동차 예상 시간(분) → 대중교통 어림(분): ×1.6 + 접근·대기 10분. */
    fun transitMinutes(driveMin: Int): Int = (driveMin * 1.6 + 10).toInt()

    /** DRT 어림(분): 우회·합승 ×1.4 + 호출 대기 10분. */
    fun drtMinutes(driveMin: Int): Int = (driveMin * 1.4 + 10).toInt()

    fun minutesLabel(min: Int): String = if (min < 60) "약 ${min}분" else "약 ${min / 60}시간" + (if (min % 60 >= 5) " ${min % 60}분" else "")
}


/**
 * 직접 운전 주행비(연료·전기) 어림. 통행료·주차비·감가·정비는 뺀다. 기준값은 이 파일 한 곳에서 갱신한다.
 * 휘발유: 전국 평균 1,855원/L(오피넷 2026.11.15) ÷ 연비 10km/L(Pleos 내비의 경로 요청 기본값). 차종·연료를 앱이 몰라 가정한다.
 * 전기차: 내비 응답(TripInfo)의 예상 소비 전력(Wh) × 공공 급속(50~100kW) 325.6원/kWh(2026.8 개편 단가). 응답이 없으면 km당 128Wh로 가정한다.
 */
object FuelCost {
    const val AS_OF = "휘발유 1,855원/L(2026.11.15), 전기 급속 325.6원/kWh(2026.8)"
    const val GASOLINE_WON_PER_L = 1855.0
    const val KM_PER_L = 10.0
    const val EV_WON_PER_KWH = 325.6
    const val EV_WH_PER_KM = 128.0

    /** 100원 단위로 어림한 주행비. [est] 가 있고 전기차(SOC 응답)면 내비의 소비 전력을 쓴다. */
    fun won(km: Double, est: TripEstimate? = null): Int {
        val raw = if (est != null && est.isEv) {
            val wh = est.energyWh?.takeIf { it > 0 } ?: (km * EV_WH_PER_KM)
            wh / 1000.0 * EV_WON_PER_KWH
        } else km / KM_PER_L * GASOLINE_WON_PER_L
        return (Math.round(raw / 100.0) * 100).toInt().coerceAtLeast(100)
    }
}
