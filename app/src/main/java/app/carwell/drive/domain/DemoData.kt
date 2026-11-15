package app.carwell.drive.domain

import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 시연용 가상 이력: 8주치. 앞 4주(기준)는 주 3회, 뒤 4주(최근)는 주 5회에 야간·장거리가 늘고
 * 느낌은 '편안'이 대부분이 되도록 정해진 패턴으로 만든다(무작위 아님 → 테스트로 고정).
 * 모든 항목은 demo=true 로 표시되어 화면 상단 배너와 '가상 이력 지우기'에 쓰인다.
 */
object DemoData {
    data class Result(
        val trips: List<Trip>,
        val feelings: List<FeelingEntry>,
        val visits: List<PlaceVisit>,
        val choices: List<MoveChoice> = emptyList(),
    )

    /** 은퇴를 앞둔 60대 초반 운전자의 가상 행선지. 거리는 장소마다 고정(평소 길 계산에 쓰임), 이름은 모두 가상이다. */
    private data class Spot(val name: String, val km: Double, val lat: Double, val lon: Double)
    private val HOSPITAL = Spot("종합병원", 24.0, 37.583, 127.1325)
    private val OFFICE = Spot("사무실", 18.0, 37.4961, 127.2198)
    private val GOLF = Spot("골프연습장", 13.0, 37.3964, 127.2109)
    private val CHURCH = Spot("교회", 9.0, 37.3565, 127.1355)
    private val MART = Spot("마트", 5.0, 37.3788, 127.0792)
    private val RESTAURANT = Spot("솔밭한정식", 11.0, 37.3856, 127.0009)   // 가장 최근 행선지(식당 예시)
    private val TRAIL = Spot("둘레길 주차장", 15.0, 37.4668, 126.9768)
    private val HOMETOWN = Spot("시골 본가", 46.0, 37.2469, 127.4562)
    private val DAEJEON = Spot("대전 가족 모임", 48.0, 37.0668, 127.0947)
    private val OUTLET = Spot("아울렛", 21.0, 37.5438, 126.9994)
    private val REUNION = Spot("동창회 모임", 27.0, 37.4793, 127.3249)
    private val CAMPING = Spot("가평 캠핑장", 36.0, 37.6565, 127.2064)

    private val NEAR_DAY_BASE = listOf(HOSPITAL, OFFICE, GOLF, CHURCH, MART)
    private val NEAR_DAY_RECENT = listOf(HOSPITAL, OFFICE, GOLF, HOSPITAL, TRAIL, OUTLET, MART)
    private val NEAR_NIGHT = listOf(CHURCH, REUNION, RESTAURANT)
    private val LONG_BASE = listOf(HOMETOWN)
    private val LONG_RECENT = listOf(HOMETOWN, DAEJEON, CAMPING)

    fun generate(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Result {
        val today = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMs), zone).toLocalDate()
        val trips = mutableListOf<Trip>()
        val feelings = mutableListOf<FeelingEntry>()
        val visits = mutableListOf<PlaceVisit>()

        // 기준 4주: today-55 .. today-29 (27일), 최근 4주: today-27 .. today-1 (27일). 경계(28일)에는 두지 않는다.
        fun build(firstOffset: Int, lastOffset: Int, tripCount: Int, nightMod: (Int) -> Boolean, longMod: (Int) -> Boolean,
                  feeling: (Int) -> Feeling?, idPrefix: String) {
            val span = firstOffset - lastOffset
            var longSeen = 0; var nightSeen = 0; var daySeen = 0
            for (i in 0 until tripCount) {
                val dayOffset = firstOffset - (i * span) / (tripCount - 1)
                val night = nightMod(i)
                val long = longMod(i)
                val hour = if (night) 20 + (i % 3) else 9 + (i % 7)
                val start = ZonedDateTime.of(today.minusDays(dayOffset.toLong()), java.time.LocalTime.of(hour, (i * 7) % 60), zone)
                val recent = idPrefix == "demo-r"
                val spot = when {
                    recent && i == tripCount - 1 -> RESTAURANT          // 가장 최근 행선지
                    long -> (if (recent) LONG_RECENT else LONG_BASE)[longSeen++ % (if (recent) LONG_RECENT else LONG_BASE).size]
                    night -> NEAR_NIGHT[nightSeen++ % NEAR_NIGHT.size]
                    else -> (if (recent) NEAR_DAY_RECENT else NEAR_DAY_BASE)[daySeen++ % (if (recent) NEAR_DAY_RECENT else NEAR_DAY_BASE).size]
                }
                val km = spot.km + (i % 3 - 1) * 0.4
                val min = (km * 1.6).toInt() + 6
                val end = start.plusMinutes(min.toLong())
                val dest = spot.name
                val signals = 2 + (i % 5)
                val trip = Trip.create("$idPrefix$i", start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli(),
                    km, signals, dest, zone, demo = true)
                trips += trip
                visits += PlaceVisit(dest, null, trip.startMs, demo = true, lat = spot.lat, lon = spot.lon)
                feeling(i)?.let { feelings += FeelingEntry(trip.endMs, it, trip.id, demo = true) }
            }
        }

        // 기준 12회: 야간 2(17%) · 장거리 1(8%), 느낌 10개(편안 6·보통 3·긴장 1)
        build(55, 29, 12, nightMod = { it == 4 || it == 9 }, longMod = { it == 6 },
            feeling = { i -> when {
                i == 11 || i == 5 -> null
                i == 0 -> Feeling.TENSE
                i % 3 == 1 -> Feeling.NORMAL
                else -> Feeling.EASY
            } }, idPrefix = "demo-b")
        // 최근 20회: 야간 8(40%) · 장거리 6(30%), 느낌 17개(편안 12·보통 4·긴장 1)
        build(27, 1, 20, nightMod = { it % 5 < 2 }, longMod = { it == 3 || it == 6 || it == 8 || it == 13 || it == 16 || it == 18 },
            feeling = { i -> when {
                i == 2 || i == 8 || i == 14 -> null
                i == 7 -> Feeling.TENSE
                i % 5 == 1 && i != 11 -> Feeling.NORMAL
                i == 13 -> Feeling.NORMAL
                else -> Feeling.EASY
            } }, idPrefix = "demo-r")

        // 오늘 이동 기록: 컨디션이 안 좋았던 날 정해 둔 방법으로 다녀온 예(가상)
        fun at(daysAgo: Int, hour: Int) = ZonedDateTime.of(today.minusDays(daysAgo.toLong()), java.time.LocalTime.of(hour, 10), zone).toInstant().toEpochMilli()
        val choices = listOf(
            MoveChoice(at(14, 9), placeKey(HOSPITAL.name), HOSPITAL.name, Alternative.TAXI, demo = true),
            MoveChoice(at(9, 20), placeKey(HOMETOWN.name), HOMETOWN.name, Alternative.TRANSIT, demo = true),
            MoveChoice(at(5, 9), placeKey(HOSPITAL.name), HOSPITAL.name, Alternative.TAXI, demo = true),
            MoveChoice(at(5, 18), placeKey(HOSPITAL.name), HOSPITAL.name, Alternative.SELF, demo = true),
        )
        return Result(trips, feelings, visits, choices)
    }
}
