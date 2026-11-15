package app.carwell.drive.domain

import java.time.ZoneId
import java.util.UUID

sealed interface RecorderAction {
    data class TripFinished(val trip: Trip) : RecorderAction
    /** 내비 안내 시작으로 확인된 목적지 (방문 집계용). 운행 여부와 무관하게 낸다. */
    data class DestinationNoted(val name: String, val address: String?, val lat: Double? = null, val lon: Double? = null) : RecorderAction
    /** 앱이 켜지기 전에 이미 올라가 있던 주행거리 (앱이 꺼진 동안 움직인 만큼). */
    data class UnrecordedDistance(val km: Double) : RecorderAction
}

/**
 * 기어 P→(R/N/D)→D→…→P 를 한 번의 운행으로 묶는 상태 기계. 안드로이드 의존 없음.
 *
 * 규칙
 * - P 를 떠난 시각이 운행 시작 시각. 그 사이 D 를 한 번도 거치지 않고 P 로 돌아오면(후진만 하고 복귀 등) 운행이 아님.
 * - 앱이 켜졌을 때 이미 P 가 아닌 상태였다면, 다음 P 까지는 기록하지 않는다(시작 시점을 모르므로).
 * - 거리 = 종료 시점 Odometer − 시작 시점 Odometer. [Rules.MIN_TRIP_KM] 미만이면 버린다.
 * - 방향지시등 = P 를 떠난 뒤 OFF→LEFT/RIGHT 로 바뀐 횟수.
 */
class TripRecorder(
    private val zone: ZoneId = ZoneId.systemDefault(),
    initialOdoKm: Double? = null,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private var gear = GearState.UNKNOWN
    private var departed = false
    private var sawDrive = false
    private var ignoreUntilPark = false
    private var departMs = 0L
    private var startOdo: Double? = null
    private var signals = 0
    private var lastSignal = SignalState.OFF
    private var pendingDestination: Pair<String, Long>? = null // name, atMs

    /** 마지막으로 본 누적 주행거리. 저장해 두었다가 다음 실행 때 [initialOdoKm] 으로 넘긴다. */
    var lastOdoKm: Double? = initialOdoKm
        private set
    private var odoSynced = false

    val isDeparted: Boolean get() = departed

    fun onEvent(event: VehicleEvent, nowMs: Long): List<RecorderAction> = when (event) {
        is VehicleEvent.OdometerChanged -> onOdometer(event.km)
        is VehicleEvent.TurnSignalChanged -> { onSignal(event.state); emptyList() }
        is VehicleEvent.DestinationSet -> {
            val name = event.name.trim()
            if (name.isEmpty()) emptyList() else {
                pendingDestination = name to nowMs
                listOf(RecorderAction.DestinationNoted(name, event.address?.trim()?.ifEmpty { null }, event.lat, event.lon))
            }
        }
        is VehicleEvent.GearChanged -> onGear(event.gear, nowMs)
    }

    private fun onOdometer(km: Double): List<RecorderAction> {
        val out = mutableListOf<RecorderAction>()
        val prev = lastOdoKm
        if (!odoSynced) {
            odoSynced = true
            if (prev != null && km - prev >= Rules.MIN_TRIP_KM && !departed && gear != GearState.D) {
                out += RecorderAction.UnrecordedDistance(km - prev)
            }
        }
        if (prev == null || km >= prev) lastOdoKm = km // 계기 초기화(감소)는 무시
        if (departed && startOdo == null) startOdo = km
        return out
    }

    private fun onSignal(state: SignalState) {
        if (departed && lastSignal == SignalState.OFF && state != SignalState.OFF) signals++
        lastSignal = state
    }

    private fun onGear(next: GearState, nowMs: Long): List<RecorderAction> {
        val prev = gear
        gear = next
        if (next == GearState.UNKNOWN) return emptyList()

        if (next == GearState.P) {
            val out = mutableListOf<RecorderAction>()
            if (departed && sawDrive && !ignoreUntilPark) {
                finish(nowMs)?.let { out += RecorderAction.TripFinished(it) }
            }
            reset()
            return out
        }

        // P 가 아닌 기어
        if (prev == GearState.UNKNOWN && !departed) {
            ignoreUntilPark = true // 이미 주행 중인 상태에서 앱이 시작됨
            departed = true
            departMs = nowMs
            return emptyList()
        }
        if (!departed) {
            departed = true
            departMs = nowMs
            startOdo = lastOdoKm
            signals = 0
            lastSignal = SignalState.OFF
        }
        if (next == GearState.D) sawDrive = true
        return emptyList()
    }

    private fun finish(endMs: Long): Trip? {
        val start = startOdo
        val end = lastOdoKm
        val distance = if (start != null && end != null && end >= start) end - start else 0.0
        if (distance < Rules.MIN_TRIP_KM) return null
        val dest = pendingDestination?.takeIf { departMs - it.second <= Rules.DESTINATION_STALE_HOURS * 3_600_000L }?.first
        return Trip.create(newId(), departMs, endMs, distance, signals, dest, zone)
    }

    private fun reset() {
        departed = false
        sawDrive = false
        ignoreUntilPark = false
        startOdo = null
        signals = 0
        lastSignal = SignalState.OFF
        pendingDestination = null
    }
}
