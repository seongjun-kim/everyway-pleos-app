package app.carwell.drive.domain

import kotlinx.serialization.Serializable
import java.time.ZoneId

@Serializable
data class Trip(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val distanceKm: Double,
    val turnSignalCount: Int,
    val destination: String?,
    val night: Boolean,
    val long: Boolean,
    val demo: Boolean = false,
) {
    val durationMin: Int get() = ((endMs - startMs) / 60_000L).toInt().coerceAtLeast(0)

    companion object {
        /** 야간·장거리 파생값은 항상 여기서 계산한다. */
        fun create(
            id: String, startMs: Long, endMs: Long, distanceKm: Double, turnSignalCount: Int,
            destination: String?, zone: ZoneId, demo: Boolean = false,
        ) = Trip(
            id, startMs, endMs, distanceKm, turnSignalCount, destination,
            night = Rules.isNight(startMs, zone), long = Rules.isLong(distanceKm), demo = demo,
        )
    }
}

enum class Feeling(val label: String) {
    EASY("편안"), NORMAL("보통"), TENSE("긴장")
}

@Serializable
data class FeelingEntry(
    val atMs: Long,
    val feeling: Feeling,
    val tripId: String? = null,
    val demo: Boolean = false,
)

@Serializable
data class PlaceVisit(
    val name: String,
    val address: String? = null,
    val atMs: Long,
    val demo: Boolean = false,
    val lat: Double? = null,
    val lon: Double? = null,
) {
    val key: String get() = placeKey(name)
}

fun placeKey(name: String): String = name.trim().lowercase().replace(Regex("\\s+"), " ")

/** 이동 대안. 호출·결제 같은 외부 연동은 없고, 본인이 고른 값만 저장한다. */
@Serializable
enum class Alternative(val label: String) {
    FAMILY("가족이 데려다줌"),
    TAXI("택시"),
    TRANSIT("버스·지하철"),
    DEMAND_BUS("셔클"),
    SELF("직접 운전"),
}

@Serializable
data class AppData(
    val trips: List<Trip> = emptyList(),
    val feelings: List<FeelingEntry> = emptyList(),
    val visits: List<PlaceVisit> = emptyList(),
    val pendingFeelingTripId: String? = null,
    val lastOdoKm: Double? = null,
    val unrecordedKm: Double = 0.0,
    val readAloud: Boolean = true,
    val choices: List<MoveChoice> = emptyList(),
) {
    val hasDemo: Boolean get() = trips.any { it.demo } || visits.any { it.demo } || feelings.any { it.demo } || choices.any { it.demo }
}

/** "오늘은 이렇게 갈게요"를 누른 기록. 하루에 여러 번 쌓일 수 있고, 호출·결제는 하지 않는다. */
@Serializable
data class MoveChoice(val atMs: Long, val key: String, val name: String, val alt: Alternative, val demo: Boolean = false)

data class PlaceCount(val key: String, val name: String, val address: String?, val count: Int, val lastAtMs: Long, val lat: Double? = null, val lon: Double? = null)
