package app.carwell.drive

import app.carwell.drive.domain.*
import app.carwell.drive.domain.VehicleEvent.*
import org.junit.Assert.*
import org.junit.Test

class TripRecorderTest {
    private var n = 0
    private fun recorder(odo: Double? = null) = TripRecorder(ZONE, odo) { "id${n++}" }
    private val t0 = ms(2026, 11, 10, 10, 0)

    private fun TripRecorder.go(e: VehicleEvent, at: Long) = onEvent(e, at)

    @Test fun `P-D-P 한 번이 한 운행이고 거리·시각·방향지시등이 담긴다`() {
        val r = recorder()
        r.go(OdometerChanged(1000.0), t0); r.go(GearChanged(GearState.P), t0)
        r.go(GearChanged(GearState.D), t0 + 1 * 60_000)
        r.go(TurnSignalChanged(SignalState.LEFT), t0 + 2 * 60_000); r.go(TurnSignalChanged(SignalState.OFF), t0 + 2 * 60_000 + 3000)
        r.go(TurnSignalChanged(SignalState.RIGHT), t0 + 5 * 60_000); r.go(TurnSignalChanged(SignalState.OFF), t0 + 5 * 60_000 + 3000)
        r.go(OdometerChanged(1012.5), t0 + 20 * 60_000)
        val out = r.go(GearChanged(GearState.P), t0 + 25 * 60_000)
        val trip = (out.single() as RecorderAction.TripFinished).trip
        assertEquals(12.5, trip.distanceKm, 1e-9)
        assertEquals(t0 + 60_000, trip.startMs)
        assertEquals(t0 + 25 * 60_000, trip.endMs)
        assertEquals(2, trip.turnSignalCount)
        assertFalse(trip.night); assertFalse(trip.long)
    }

    @Test fun `켜진 채 깜빡이는 신호는 한 번으로 센다`() {
        val r = recorder()
        r.go(OdometerChanged(1.0), t0); r.go(GearChanged(GearState.P), t0); r.go(GearChanged(GearState.D), t0)
        r.go(TurnSignalChanged(SignalState.LEFT), t0); r.go(TurnSignalChanged(SignalState.LEFT), t0 + 500)
        r.go(TurnSignalChanged(SignalState.OFF), t0 + 1000)
        r.go(OdometerChanged(5.0), t0 + 60_000)
        val trip = (r.go(GearChanged(GearState.P), t0 + 120_000).single() as RecorderAction.TripFinished).trip
        assertEquals(1, trip.turnSignalCount)
    }

    @Test fun `야간 시작과 30km 이상은 파생 표시가 붙는다`() {
        val r = recorder()
        val start = ms(2026, 11, 10, 21, 30)
        r.go(OdometerChanged(100.0), start); r.go(GearChanged(GearState.P), start); r.go(GearChanged(GearState.D), start)
        r.go(OdometerChanged(135.0), start + HOUR)
        val trip = (r.go(GearChanged(GearState.P), start + HOUR).single() as RecorderAction.TripFinished).trip
        assertTrue(trip.night); assertTrue(trip.long)
    }

    @Test fun `D 를 거치지 않은 P-R-P 는 운행이 아니다`() {
        val r = recorder()
        r.go(OdometerChanged(10.0), t0); r.go(GearChanged(GearState.P), t0)
        r.go(GearChanged(GearState.R), t0 + 1000); r.go(OdometerChanged(10.5), t0 + 2000)
        assertTrue(r.go(GearChanged(GearState.P), t0 + 3000).isEmpty())
    }

    @Test fun `주차장 이동처럼 0점1km 미만이면 버린다`() {
        val r = recorder()
        r.go(OdometerChanged(10.0), t0); r.go(GearChanged(GearState.P), t0); r.go(GearChanged(GearState.D), t0)
        r.go(OdometerChanged(10.05), t0 + 1000)
        assertTrue(r.go(GearChanged(GearState.P), t0 + 5000).isEmpty())
    }

    @Test fun `후진 후 전진은 한 운행으로 묶인다`() {
        val r = recorder()
        r.go(OdometerChanged(10.0), t0); r.go(GearChanged(GearState.P), t0)
        r.go(GearChanged(GearState.R), t0 + 1000); r.go(GearChanged(GearState.D), t0 + 5000)
        r.go(GearChanged(GearState.N), t0 + 6000); r.go(GearChanged(GearState.D), t0 + 7000)
        r.go(OdometerChanged(14.0), t0 + 600_000)
        val out = r.go(GearChanged(GearState.P), t0 + 610_000)
        assertEquals(1, out.filterIsInstance<RecorderAction.TripFinished>().size)
        assertEquals(4.0, (out.single() as RecorderAction.TripFinished).trip.distanceKm, 1e-9)
    }

    @Test fun `앱이 이미 주행 중일 때 시작되면 그 운행은 기록하지 않는다`() {
        val r = recorder()
        r.go(GearChanged(GearState.D), t0); r.go(OdometerChanged(50.0), t0)
        r.go(OdometerChanged(60.0), t0 + 10 * 60_000)
        assertTrue(r.go(GearChanged(GearState.P), t0 + 11 * 60_000).isEmpty())
        // 다음 운행부터는 정상 기록
        r.go(GearChanged(GearState.D), t0 + 20 * 60_000); r.go(OdometerChanged(70.0), t0 + 30 * 60_000)
        assertEquals(1, r.go(GearChanged(GearState.P), t0 + 31 * 60_000).size)
    }

    @Test fun `목적지는 운행에 붙고 같은 운행 뒤 초기화된다`() {
        val r = recorder()
        r.go(OdometerChanged(1.0), t0); r.go(GearChanged(GearState.P), t0)
        val noted = r.go(DestinationSet(" 서울병원 ", "서울시"), t0 + 1000)
        assertEquals(RecorderAction.DestinationNoted("서울병원", "서울시"), noted.single())
        r.go(GearChanged(GearState.D), t0 + 2000); r.go(OdometerChanged(9.0), t0 + 600_000)
        val trip = (r.go(GearChanged(GearState.P), t0 + 610_000).single() as RecorderAction.TripFinished).trip
        assertEquals("서울병원", trip.destination)
        r.go(GearChanged(GearState.D), t0 + 700_000); r.go(OdometerChanged(12.0), t0 + 900_000)
        val trip2 = (r.go(GearChanged(GearState.P), t0 + 910_000).single() as RecorderAction.TripFinished).trip
        assertNull(trip2.destination)
    }

    @Test fun `오래전에 정한 목적지는 붙이지 않는다`() {
        val r = recorder()
        r.go(OdometerChanged(1.0), t0); r.go(GearChanged(GearState.P), t0)
        r.go(DestinationSet("시장", null), t0)
        val late = t0 + (Rules.DESTINATION_STALE_HOURS + 1) * HOUR
        r.go(GearChanged(GearState.D), late); r.go(OdometerChanged(6.0), late + 60_000)
        val trip = (r.go(GearChanged(GearState.P), late + 120_000).single() as RecorderAction.TripFinished).trip
        assertNull(trip.destination)
    }

    @Test fun `빈 목적지 이름은 무시한다`() {
        assertTrue(recorder().go(DestinationSet("  ", null), t0).isEmpty())
    }

    @Test fun `앱이 꺼진 동안 올라간 주행거리는 첫 읽기에서 따로 알린다`() {
        val r = recorder(odo = 1000.0)
        r.go(GearChanged(GearState.P), t0)
        val out = r.go(OdometerChanged(1012.0), t0)
        assertEquals(12.0, (out.single() as RecorderAction.UnrecordedDistance).km, 1e-9)
        assertTrue(r.go(OdometerChanged(1013.0), t0 + 1000).isEmpty()) // 두 번째부터는 알리지 않음
        assertEquals(1013.0, r.lastOdoKm!!, 0.0)
    }

    @Test fun `계기 값이 줄어도 마지막 값은 유지한다`() {
        val r = recorder(odo = 500.0)
        r.go(OdometerChanged(400.0), t0)
        assertEquals(500.0, r.lastOdoKm!!, 0.0)
    }
}
