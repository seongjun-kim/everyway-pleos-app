package app.carwell.drive

import app.carwell.drive.domain.Alternative
import app.carwell.drive.domain.RouteInfo
import app.carwell.drive.domain.Trip
import app.carwell.drive.domain.placeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class RouteInfoTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private fun trip(id: String, dest: String?, km: Double, min: Int) =
        Trip.create(id, 1_000_000L, 1_000_000L + min * 60_000L, km, 0, dest, zone)

    @Test fun 서울_요금은_기본거리까지_기본요금() {
        assertEquals(4800, RouteInfo.seoulTaxiFare(1.0))
        assertEquals(4800, RouteInfo.seoulTaxiFare(1.6))
    }

    @Test fun 서울_요금은_131미터마다_100원() {
        // (12.0-1.6)km = 10,400m / 131m = 79.4 → 80단계 → 4,800 + 8,000
        assertEquals(12800, RouteInfo.seoulTaxiFare(12.0))
    }

    @Test fun 요금_문구는_천원_단위로_어림() {
        assertEquals("1만 3천원", RouteInfo.wonLabel(12800))
        assertEquals("5천원", RouteInfo.wonLabel(4800))
        assertEquals("2만원", RouteInfo.wonLabel(20100))
    }

    @Test fun 평소_길은_같은_목적지_운행의_중앙값() {
        val trips = listOf(trip("1", "병원", 10.0, 20), trip("2", "병원", 14.0, 30), trip("3", "병원", 12.0, 25), trip("4", "시장", 3.0, 8))
        val t = RouteInfo.typical(trips, placeKey("병원"))!!
        assertEquals(12.0, t.km, 0.001)
        assertEquals(25, t.min)
        assertEquals(3, t.samples)
        assertNull(RouteInfo.typical(trips, placeKey("교회")))
    }

    @Test fun 택시_한_줄에는_거리_시간_요금_전화가_들어간다() {
        val t = RouteInfo.Typical(12.0, 25, 3)
        val line = RouteInfo.infoLine(Alternative.TAXI, t)!!
        assertTrue(line, line.contains("평소 12km·25분") && line.contains("약 1만 3천원") && line.contains("02-120 → 6번"))
    }

    @Test fun 운행_기록이_없어도_전화_안내는_나온다() {
        val line = RouteInfo.infoLine(Alternative.TAXI, null)!!
        assertTrue(line.contains("02-120 → 6번") && !line.contains("평소"))
        assertNull(RouteInfo.infoLine(Alternative.TRANSIT, null))
        assertNull(RouteInfo.infoLine(Alternative.SELF, null))
    }

    @Test fun 장거리는_요금을_빼고_보여_준다() {
        val line = RouteInfo.infoLine(Alternative.TAXI, RouteInfo.Typical(48.0, 82, 5))!!
        assertTrue(line, line.contains("평소 48km·1시간 22분") && !line.contains("요금표") && line.contains("02-120 → 6번"))
    }
}
