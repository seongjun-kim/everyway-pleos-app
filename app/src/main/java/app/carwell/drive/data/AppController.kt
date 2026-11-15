package app.carwell.drive.data

import android.content.Context
import app.carwell.drive.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.time.ZoneId
import java.util.concurrent.Executors

/** 시연 시 '야간 시각'으로 바꿔 볼 수 있는 시계. 시뮬레이터 소스일 때만 켜진다. */
class SimClock : Clock {
    @Volatile private var offsetMs = 0L
    @Volatile var night = false; private set
    override fun nowMs(): Long = System.currentTimeMillis() + offsetMs

    fun setNight(on: Boolean) {
        night = on
        offsetMs = if (on) {
            val now = java.time.ZonedDateTime.now()
            val target = now.toLocalDate().atTime(22, 0).atZone(now.zone)
            target.toInstant().toEpochMilli() - System.currentTimeMillis()
        } else 0L
    }
}

data class UiModel(
    val data: AppData,
    val gear: GearState,
    val places: List<PlaceCount>,
    val gap: GapResult,
    val recent: WindowStats,
    val lastTrip: Trip?,
    val nowMs: Long,
) {
    /** 기어를 못 읽는 경우(UNKNOWN)는 보여 주되(시스템 UX 제한이 주행 중 화면을 막는다), 확실히 주행 중이면 가린다. */
    val driving get() = gear == GearState.D || gear == GearState.R || gear == GearState.N
}

/**
 * 프로세스 수명 동안 사는 앱 두뇌: 차량 신호 → 운행 기록 → 저장 → 화면용 상태.
 * 운전자 구분은 차량(Pleos/AAOS)의 프로필을 따른다. 안드로이드는 사용자(프로필)마다 앱 저장소를 따로 두므로
 * 이 앱은 현재 프로필의 파일 하나만 읽고 쓰면 된다(자체 로그인 없음).
 */
class AppController(context: Context, val source: VehicleSource, val sim: SimulatedVehicleSource?, val clock: Clock) {
    private val appContext = context.applicationContext
    private val store = LocalStore(File(appContext.filesDir, DATA_FILE))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "store-writer") }.asCoroutineDispatcher()
    private val zone = ZoneId.systemDefault()

    private val initial = store.load()
    private val recorder = TripRecorder(zone, initialOdoKm = initial.lastOdoKm)
    private val _data = MutableStateFlow(initial)
    private val _gear = MutableStateFlow(GearState.UNKNOWN)

    private var speaker: Speaker? = if (source.kind == SourceKind.PLEOS && PleosSpeaker.isAvailable(appContext)) PleosSpeaker(appContext) else null
    val voiceAvailable: Boolean get() = speaker != null

    val ui: StateFlow<UiModel> = combine(_data, _gear) { d, g ->
        val now = clock.nowMs()
        val trips = d.trips
        UiModel(
            data = d, gear = g,
            places = PlaceStats.topPlaces(d.visits),
            gap = GapAnalyzer.analyze(trips, d.feelings, now, zone),
            recent = GapAnalyzer.stats(trips, d.feelings, now - Rules.WINDOW_DAYS * 86_400_000L, now + 1),
            lastTrip = trips.maxByOrNull { it.endMs },
            nowMs = now,
        )
    }.stateIn(scope, SharingStarted.Eagerly, UiModel(initial, GearState.UNKNOWN, emptyList(), GapResult.Collecting(),
        GapAnalyzer.stats(emptyList(), emptyList(), 0, 1), null, clock.nowMs()))

    init {
        scope.launch { source.events.collect(::handle) }
        source.start()
    }

    private fun update(persist: Boolean = true, f: (AppData) -> AppData) {
        val next = f(_data.value)
        _data.value = next
        if (persist) scope.launch(writer) { runCatching { store.save(next) } }
    }

    private fun handle(e: VehicleEvent) {
        val now = clock.nowMs()
        if (e is VehicleEvent.GearChanged) _gear.value = e.gear
        for (a in recorder.onEvent(e, now)) when (a) {
            is RecorderAction.TripFinished -> {
                update { DataOps.addTrip(it, a.trip) }
                val s = speaker
                if (s != null && s.ready && _data.value.readAloud) s.speak(TripText.spoken(a.trip))
            }
            is RecorderAction.DestinationNoted -> update { DataOps.noteDestination(it, a.name, a.address, now, a.lat, a.lon) }
            is RecorderAction.UnrecordedDistance -> update { DataOps.addUnrecorded(it, a.km) }
        }
        if (e is VehicleEvent.GearChanged) {
            recorder.lastOdoKm?.let { km -> if (km != _data.value.lastOdoKm) update { it.copy(lastOdoKm = km) } }
        }
    }

    // ── 느낌 ─────────────────────────────────────────────
    fun chooseFeeling(f: Feeling) = update { DataOps.setFeeling(it, f, clock.nowMs()) }
    fun skipFeeling() = update { DataOps.skipFeeling(it) }

    // ── 오늘 이동 ───────────────────────────────────────────
    /** 오늘 이 길의 내비 예상(공식 NaviHelper SDK). 좌표가 없거나 실패하면 null 로 이어진다. 콜백은 메인 스레드. */
    fun requestTrip(place: PlaceCount, onResult: (TripEstimate?) -> Unit) {
        val lat = place.lat; val lon = place.lon
        if (lat == null || lon == null) { onResult(null); return }
        source.requestTrip(lat, lon, place.name) { e -> scope.launch { onResult(e) } }
    }

    /** "오늘은 이렇게 갈게요". 호출·전송은 하지 않고 기록만 쌓는다. */
    fun chooseToday(key: String, name: String, alt: Alternative) = update { DataOps.addChoice(it, key, name, alt, clock.nowMs()) }

    // ── 설정 ─────────────────────────────────────────────
    fun loadDemo() = update { DataOps.loadDemo(it, clock.nowMs()) }
    fun clearDemo() = update { DataOps.clearDemo(it) }
    fun setReadAloud(on: Boolean) = update { it.copy(readAloud = on) }

    /** 전체 삭제: 이 프로필의 기록·선택을 모두 지운다. */
    fun wipeAll() {
        val next = DataOps.wipe().copy(lastOdoKm = recorder.lastOdoKm)
        _data.value = next
        scope.launch(writer) { runCatching { store.save(next) } }
    }

    fun shutdown() { source.stop(); speaker?.release() }

    companion object { const val DATA_FILE = "everyway-data.json" }
}
