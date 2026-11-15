package app.carwell.drive

import app.carwell.drive.domain.*
import org.junit.Assert.*
import org.junit.Test

class CoordsTest {
    private val now = ms(2026, 11, 15, 10)

    @Test fun `내비 목적지의 좌표가 방문과 단골 길에 이어진다`() {
        val r = TripRecorder(ZONE, initialOdoKm = 100.0)
        val noted = r.onEvent(VehicleEvent.DestinationSet("종합병원", "서울시", 37.58, 127.13), now).single()
        assertEquals(RecorderAction.DestinationNoted("종합병원", "서울시", 37.58, 127.13), noted)
        var d = DataOps.noteDestination(AppData(), "종합병원", "서울시", now, 37.58, 127.13)
        d = DataOps.noteDestination(d, "종합병원", null, now + 2 * HOUR)   // 좌표 없는 재안내가 있어도
        val p = PlaceStats.topPlaces(d.visits).single()
        assertEquals(37.58, p.lat!!, 0.0001)                                  // 좌표는 남는다
        assertEquals(127.13, p.lon!!, 0.0001)
    }

    @Test fun `좌표가 없으면 단골 길에도 없다`() {
        val d = DataOps.noteDestination(AppData(), "마트", null, now)
        assertNull(PlaceStats.topPlaces(d.visits).single().lat)
    }

    @Test fun `가상 이력의 모든 단골 길에 좌표가 있다`() {
        val d = DemoData.generate(ms(2026, 11, 14, 15, 30), ZONE)
        assertTrue(PlaceStats.topPlaces(d.visits).all { it.lat != null && it.lon != null })
    }
}
