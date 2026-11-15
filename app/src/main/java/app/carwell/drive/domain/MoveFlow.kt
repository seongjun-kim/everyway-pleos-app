package app.carwell.drive.domain

import java.time.Instant
import java.time.ZoneId

/**
 * "오늘은 다른 방법으로 갈래요"를 눌렀을 때 이어지는 안내와 기록.
 * 이 앱은 호출을 대신하지 않는다. 앱 없이 쓸 수 있는 방법의 다음 걸음(전화 앱 열기 등)만 알려 준다.
 */
object MoveFlow {
    /** 전화 앱에 미리 입력해 둘 번호(걸지는 않는다). 서울 120 동행콜택시: 02-120 후 6번. */
    fun dialNumber(alt: Alternative, seoul: Boolean = true): String? = when {
        alt == Alternative.TAXI && seoul -> "02120"
        else -> null
    }

    fun dialLabel(alt: Alternative, seoul: Boolean = true): String? = when {
        alt == Alternative.TAXI && seoul -> "02-120 열기"
        else -> null
    }

    /** 고른 뒤 보여 줄 "다음 걸음". 확인된 사실(서울 120 동행콜택시 호출 방식)만 구체적으로 쓴다. */
    fun steps(alt: Alternative, seoul: Boolean = true): List<String> = when (alt) {
        Alternative.TAXI -> if (!seoul) listOf("택시 호출 앱이나 지역 콜택시 번호로 불러요", "출발지와 목적지를 말해요") else listOf("전화 앱에서 02-120을 걸어요", "안내 음성이 나오면 6번을 눌러요", "출발지와 목적지를 말하면 차량 번호가 문자로 와요")
        Alternative.DEMAND_BUS -> listOf("셔클은 부르면 오는 수요응답 버스예요, 호출 방법은 지역마다 달라요", "사는 동네 지자체 안내에서 전화 호출 번호를 확인해요")
        Alternative.TRANSIT -> listOf("지도 앱에서 버스·지하철 길을 확인해요")
        Alternative.FAMILY -> listOf("가족에게 데려다 달라고 연락해요")
        Alternative.SELF -> listOf("평소 길로 안전하게 다녀오세요")
    }

    /** 이번 달에 직접 운전이 아닌 방법을 고른 횟수. */
    fun otherCountThisMonth(choices: List<MoveChoice>, nowMs: Long, zone: ZoneId): Int {
        val ym = Instant.ofEpochMilli(nowMs).atZone(zone).let { it.year to it.monthValue }
        return choices.count { c ->
            c.alt != Alternative.SELF &&
                Instant.ofEpochMilli(c.atMs).atZone(zone).let { (it.year to it.monthValue) == ym }
        }
    }

    /** 최신순으로 날짜 문자열("11월 12일") 별로 묶는다. */
    fun byDay(choices: List<MoveChoice>, zone: ZoneId): List<Pair<String, List<MoveChoice>>> =
        choices.sortedByDescending { it.atMs }
            .groupBy { c -> Instant.ofEpochMilli(c.atMs).atZone(zone).let { "${it.monthValue}월 ${it.dayOfMonth}일" } }
            .toList()
}

/** 오늘 이동 시트 위에 놓는 사실 말풍선(권유 없음). 앱이 아는 것만: 시각, 오늘 내비 예상의 서행, 먼 길. */
object TodayFacts {
    fun pills(nowMs: Long, zone: java.time.ZoneId, est: TripEstimate?, typical: RouteInfo.Typical?): List<String> = buildList {
        if (Rules.isNight(nowMs, zone)) add("밤이에요")
        if (est != null && est.slowPct >= 30) add("서행·정체 많음")
        val km = est?.km ?: typical?.km
        if (km != null && km >= Rules.LONG_TRIP_KM) add("먼 길이에요")
    }

    /** 같은 곳을 지금과 같은 시간대(밤/낮)에 다녀온 평소 소요(분)와 기록 수. 2건 미만이면 null. */
    fun bandMinutes(trips: List<Trip>, key: String, night: Boolean): Pair<Int, Int>? {
        val list = trips.filter { t ->
            t.destination != null && placeKey(t.destination) == key && t.night == night && t.distanceKm >= Rules.MIN_TRIP_KM
        }
        if (list.size < 2) return null
        val s = list.map { it.durationMin }.sorted()
        val m = if (s.size % 2 == 1) s[s.size / 2].toDouble() else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
        return Math.round(m).toInt() to list.size
    }
}
