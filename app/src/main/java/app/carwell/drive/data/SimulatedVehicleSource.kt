package app.carwell.drive.data

import app.carwell.drive.domain.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 차량 신호가 없는 기기(일반 에뮬레이터 등)에서 시연하기 위한 가상 소스. 시뮬레이터 패널 버튼이 이 메서드를 부른다. */
class SimulatedVehicleSource(startOdoKm: Double = 12_000.0) : VehicleSource {
    override val kind = SourceKind.SIMULATOR
    private val flow = MutableSharedFlow<VehicleEvent>(extraBufferCapacity = 256)
    override val events = flow.asSharedFlow()

    @Volatile var gear: GearState = GearState.P; private set
    @Volatile var odoKm: Double = startOdoKm; private set

    override fun start() {
        // 시작 직후 현재 상태를 한 번 알린다 (실제 소스가 get* 로 초기값을 읽는 것과 같은 역할)
        flow.tryEmit(VehicleEvent.OdometerChanged(odoKm))
        flow.tryEmit(VehicleEvent.GearChanged(gear))
    }

    override fun stop() {}

    fun setGear(g: GearState) {
        if (g == gear) return
        gear = g
        flow.tryEmit(VehicleEvent.GearChanged(g))
    }

    /** 주행거리 증가. 기어가 D 일 때만 의미가 있지만 막지는 않는다(앱이 꺼진 동안 이동 시연용). */
    fun drive(km: Double) {
        odoKm += km
        flow.tryEmit(VehicleEvent.OdometerChanged(odoKm))
    }

    fun blinkTurnSignal(left: Boolean = true) {
        flow.tryEmit(VehicleEvent.TurnSignalChanged(if (left) SignalState.LEFT else SignalState.RIGHT))
        flow.tryEmit(VehicleEvent.TurnSignalChanged(SignalState.OFF))
    }

    fun startGuidance(name: String, address: String? = null) {
        if (name.isNotBlank()) flow.tryEmit(VehicleEvent.DestinationSet(name.trim(), address))
    }
}
