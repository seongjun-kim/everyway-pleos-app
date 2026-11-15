package app.carwell.drive.data

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import app.carwell.drive.domain.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Pleos Connect SDK 소스. Vehicle SDK(Gear·Odometer·TurnSignal) + NaviHelper(목적지).
 * 실제 Pleos 서비스가 있는 기기에서만 만든다 ([isAvailable]). 일반 에뮬레이터에서는 실행 검증을 못 했다(README 참고).
 */
class PleosVehicleSource(private val context: Context) : VehicleSource {
    override val kind = SourceKind.PLEOS
    private val flow = MutableSharedFlow<VehicleEvent>(extraBufferCapacity = 256)
    override val events = flow.asSharedFlow()
    private val main = Handler(Looper.getMainLooper())

    private var vehicle: Vehicle? = null
    private var navi: NaviHelper? = null
    private var waitingDestination = false
    private var routeName: String? = null

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) { flow.tryEmit(VehicleEvent.GearChanged(map(gear))) }
        override fun onFailed(e: Exception) { Log.w(TAG, "gear listener failed: $e") }
    }
    private val odoListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(distance: Float?) { distance?.let { flow.tryEmit(VehicleEvent.OdometerChanged(it.toDouble())) } }
        override fun onFailed(e: Exception) { Log.w(TAG, "odometer listener failed: $e") }
    }
    private val signalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) { flow.tryEmit(VehicleEvent.TurnSignalChanged(map(state))) }
        override fun onFailed(e: Exception) { Log.w(TAG, "turn signal listener failed: $e") }
    }

    private val naviListener = object : NaviHelperEventListener {
        // 안내(경로 탐색) 시작 → 이름은 여기서, 주소는 getDestinationInfo() 결과에서 받는다.
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            routeName = routeStartInfo.name
            waitingDestination = true
            navi?.getDestinationInfo()
            main.postDelayed({ flushDestination(null) }, DESTINATION_WAIT_MS) // 응답이 없으면 이름만으로 기록
        }
        override fun onDestinationInfo(destinationInfo: DestinationInfo) { flushDestination(destinationInfo) }
        override fun onCurrentLocationInfo(i: ai.pleos.playground.navi.data.CurrentLocationInfo) { lastLat = i.latitude; lastLon = i.longitude }
        override fun onTripInfo(tripInfo: ai.pleos.playground.navi.data.TripInfo) {
            val cb = tripCb ?: return
            tripCb = null
            cb(TripEstimate.of(tripInfo.distance, tripInfo.eta,
                tripInfo.summary?.trafficConditions.orEmpty().map { Segment(Congestion.of(it.congestionType.name), it.startRatio, it.endRatio) }, lastLat, lastLon,
                tripInfo.energyConsumption, tripInfo.estimatedSocAtDest))
        }
        override fun onError(errorCode: ai.pleos.playground.navi.constants.NaviErrorCode) {
            Log.w(TAG, "navi error: $errorCode")
            tripCb?.let { tripCb = null; it(null) }
        }
    }

    private fun flushDestination(info: DestinationInfo?) {
        if (!waitingDestination) return
        waitingDestination = false
        val name = info?.name?.takeIf { it.isNotBlank() } ?: routeName
        if (!name.isNullOrBlank()) flow.tryEmit(VehicleEvent.DestinationSet(name, info?.address?.takeIf { it.isNotBlank() },
            info?.latitude?.takeIf { it != 0.0 }, info?.longitude?.takeIf { it != 0.0 }))
    }

    @Volatile private var tripCb: ((TripEstimate?) -> Unit)? = null
    @Volatile private var lastLat: Double? = null
    @Volatile private var lastLon: Double? = null

    /** NaviHelper requestTrip: 안내를 시작하지 않고 목적지까지 예상 시간·거리·구간별 정체를 받는다(에뮬레이터 확인). */
    override fun requestTrip(lat: Double, lon: Double, name: String, onResult: (TripEstimate?) -> Unit) {
        val n = navi ?: return onResult(null)
        tripCb?.let { it(null) }
        tripCb = onResult
        runCatching { n.getCurrentLocationInfo() }
        runCatching { n.requestTrip(ai.pleos.playground.navi.data.TripRequest(lat, lon, "", name, "")) }
            .onFailure { tripCb = null; onResult(null) }
        main.postDelayed({ if (tripCb === onResult) { tripCb = null; onResult(null) } }, TRIP_WAIT_MS)
    }

    override fun start() {
        runCatching {
            val v = Vehicle(context).also { it.initialize() }
            vehicle = v
            v.getGear().apply {
                getCurrentGearSelectionState(
                    { g -> flow.tryEmit(VehicleEvent.GearChanged(map(g))); Unit },
                    { e -> Log.w(TAG, "gear get failed: $e"); Unit })
                registerGearSelection(gearListener)
            }
            v.getOdometer().apply {
                getCurrentDistanceDriven(
                    { d -> d?.let { flow.tryEmit(VehicleEvent.OdometerChanged(it.toDouble())) }; Unit },
                    { e -> Log.w(TAG, "odometer get failed: $e"); Unit })
                registerDistanceDriven(odoListener)
            }
            v.getTurnSignal().apply {
                getCurrentTurnSignalState(
                    { s -> flow.tryEmit(VehicleEvent.TurnSignalChanged(map(s))); Unit },
                    { e -> Log.w(TAG, "turn signal get failed: $e"); Unit })
                registerTurnSignalState(signalListener)
            }
        }.onFailure { Log.e(TAG, "Vehicle SDK init failed", it) }

        runCatching {
            val n = NaviHelper(context)
            navi = n
            n.initialize({ n.addListener(naviListener) }, { t -> Log.w(TAG, "NaviHelper init failed: $t") })
        }.onFailure { Log.e(TAG, "NaviHelper init failed", it) }
    }

    override fun stop() {
        runCatching {
            vehicle?.getGear()?.unregisterGearSelection(gearListener)
            vehicle?.getOdometer()?.unregisterDistanceDriven(odoListener)
            vehicle?.getTurnSignal()?.unregisterTurnSignalState(signalListener)
            vehicle?.release()
        }
        runCatching { navi?.removeListener(naviListener); navi?.release() }
        vehicle = null; navi = null
    }

    private fun map(g: VehicleGear?): GearState = when (g) {
        VehicleGear.GEAR_PARK -> GearState.P
        VehicleGear.GEAR_REVERSE -> GearState.R
        VehicleGear.GEAR_NEUTRAL -> GearState.N
        VehicleGear.GEAR_UNKNOWN, null -> GearState.UNKNOWN
        else -> GearState.D // GEAR_DRIVE, GEAR_1..9
    }

    private fun map(s: TurnSignalValue?): SignalState = when (s) {
        TurnSignalValue.LEFT -> SignalState.LEFT
        TurnSignalValue.RIGHT -> SignalState.RIGHT
        else -> SignalState.OFF
    }

    companion object {
        private const val TAG = "PleosVehicleSource"
        private const val DESTINATION_WAIT_MS = 3_000L
        private const val TRIP_WAIT_MS = 8_000L

        /** AAR 매니페스트의 <queries> 에 선언된 Pleos 서비스 액션이 이 기기에서 풀리면 Pleos 환경으로 본다. */
        fun isAvailable(context: Context): Boolean = runCatching {
            val pm = context.packageManager
            val intent = Intent("ai.pleos.playground.vehicle.VEHICLE_START")
            pm.queryIntentServices(intent, 0).isNotEmpty() ||
                pm.queryIntentActivities(intent, 0).isNotEmpty() ||
                pm.queryBroadcastReceivers(intent, 0).isNotEmpty()
        }.getOrDefault(false)
    }
}
