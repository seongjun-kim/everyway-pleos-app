package app.carwell.drive.domain

import kotlinx.coroutines.flow.Flow

enum class GearState { P, R, N, D, UNKNOWN }
enum class SignalState { OFF, LEFT, RIGHT }
enum class SourceKind { PLEOS, SIMULATOR }

/** 차량 신호. 소스(Pleos/시뮬레이터)가 달라도 앱 나머지는 이 이벤트만 본다. */
sealed interface VehicleEvent {
    data class GearChanged(val gear: GearState) : VehicleEvent
    /** 누적 주행거리(km) */
    data class OdometerChanged(val km: Double) : VehicleEvent
    data class TurnSignalChanged(val state: SignalState) : VehicleEvent
    /** 내비 안내가 시작되며 확인된 목적지 */
    data class DestinationSet(val name: String, val address: String?, val lat: Double? = null, val lon: Double? = null) : VehicleEvent
}

interface VehicleSource {
    val kind: SourceKind
    val events: Flow<VehicleEvent>
    fun start()
    fun stop()
    /** 목적지까지 오늘 내비 예상(시간·거리·정체)을 요청한다. 지원하지 않거나 실패하면 null. 콜백은 어느 스레드에서든 올 수 있다. */
    fun requestTrip(lat: Double, lon: Double, name: String, onResult: (TripEstimate?) -> Unit) = onResult(null)
}

interface Clock {
    fun nowMs(): Long
}
