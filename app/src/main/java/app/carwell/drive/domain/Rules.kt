package app.carwell.drive.domain

import java.time.Instant
import java.time.ZoneId

/**
 * 판정에 쓰는 모든 임계값은 이 한 곳에만 둔다. (RulesTest 가 값을 고정한다 — 바꾸면 테스트도 함께 의도적으로 수정)
 * 값은 MVP 시연용 초기값이며, 실제 시니어 운전 데이터로 검증된 기준이 아니다.
 */
object Rules {
    // 운행 파생값
    const val NIGHT_START_HOUR = 20      // 시작 시각 20:00 이상 또는
    const val NIGHT_END_HOUR = 5         // 05:00 미만이면 야간 운행
    const val LONG_TRIP_KM = 30.0        // 이 거리 이상이면 장거리
    const val MIN_TRIP_KM = 0.1          // 이보다 덜 움직인 P→D→P 는 운행으로 치지 않음
    const val DESTINATION_STALE_HOURS = 6 // 목적지 설정 후 이 시간이 지나 출발하면 그 목적지는 쓰지 않음

    // 단골 길
    const val TOP_PLACES = 12
    const val PLACE_DEDUPE_MINUTES = 30  // 같은 목적지를 이 시간 안에 다시 안내하면 방문 1회로만 센다

    // 간극 카드
    const val WINDOW_DAYS = 28           // 최근 4주 / 직전 기준 4주
    const val MIN_TRIPS_PER_WINDOW = 8   // 두 기간 모두 이 횟수 이상 있어야 비교
    const val MIN_FEELINGS_RECENT = 6    // 최근 4주 느낌 기록이 이 개수 이상 있어야 비교
    const val MIN_FEELINGS_BASELINE = 4  // 기준 기간 느낌이 이 개수 이상일 때만 '긴장 늘어남' 비교
    const val NIGHT_RATIO_RISE = 0.15    // 야간 비율이 이 만큼(비율 포인트) 이상 늘면 '눈에 띄게 늘어남'
    const val LONG_RATIO_RISE = 0.15     // 장거리 비율
    const val WEEKLY_TRIPS_RISE_RATIO = 0.30 // 주간 운행 횟수가 30% 이상 늘고
    const val WEEKLY_TRIPS_RISE_MIN_ABS = 1.0 // 주 1회 이상 늘면
    const val EASY_DOMINANT_SHARE = 0.60 // '편안' 위주: 최근 느낌 중 편안이 60% 이상이고
    const val TENSE_LOW_SHARE = 0.20     // 긴장이 20% 이하
    const val TENSE_RISE = 0.20          // 긴장 비율이 이 만큼 이상 늘고
    const val TENSE_MIN_SHARE = 0.30     // 최근 긴장 비율이 30% 이상

    fun isNight(startMs: Long, zone: ZoneId): Boolean {
        val h = Instant.ofEpochMilli(startMs).atZone(zone).hour
        return h >= NIGHT_START_HOUR || h < NIGHT_END_HOUR
    }

    fun isLong(distanceKm: Double): Boolean = distanceKm >= LONG_TRIP_KM
}
