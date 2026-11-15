package app.carwell.drive

import app.carwell.drive.domain.*
import app.carwell.drive.domain.TaxiFare.Region
import org.junit.Assert.*
import org.junit.Test

class TaxiFareTest {
    private val noon = ms(2026, 11, 15, 12, 0)
    private fun est(km: Double, min: Int, heavy: Int, h: Int, o: Region?, d: Region?) =
        TaxiFare.estimate(km, min, heavy, ms(2026, 11, 15, h, 0), ZONE, o, d)!!

    @Test fun `기본거리까지는 기본요금`() {
        assertEquals(4800, est(1.0, 5, 0, 12, Region.SEOUL, Region.SEOUL).won)
    }

    @Test fun `서울 낮 12km는 131m마다 100원`() {
        // 4,800 + ceil((12-1.6)*1000/131)=80 단계 × 100
        val r = est(12.0, 25, 0, 12, Region.SEOUL, Region.SEOUL)
        assertEquals(12800, r.won); assertFalse(r.night); assertFalse(r.outside)
    }

    @Test fun `서울 심야는 시간대별 20과 40퍼센트`() {
        assertEquals(15360, est(12.0, 25, 0, 22, Region.SEOUL, Region.SEOUL).won)   // 20%
        assertEquals(17920, est(12.0, 25, 0, 23, Region.SEOUL, Region.SEOUL).won)   // 40%
        assertEquals(17920, est(12.0, 25, 0, 0, Region.SEOUL, Region.SEOUL).won)
        assertEquals(15360, est(12.0, 25, 0, 3, Region.SEOUL, Region.SEOUL).won)    // 02~04시 20%
        assertEquals(12800, est(12.0, 25, 0, 4, Region.SEOUL, Region.SEOUL).won)
    }

    @Test fun `시계외 할증은 출발과 도착 지역이 다를 때 20퍼센트이고 심야와 합산된다`() {
        assertEquals(15360, est(12.0, 25, 0, 12, Region.SEOUL, Region.GYEONGGI).won)       // 낮 20%
        assertEquals(20480, est(12.0, 25, 0, 23, Region.SEOUL, Region.GYEONGGI).won)       // 40%+20% = 60%
        assertTrue(est(12.0, 25, 0, 12, Region.SEOUL, Region.GYEONGGI).outside)
    }

    @Test fun `경기는 23시부터 30퍼센트`() {
        assertEquals(12800, est(12.0, 25, 0, 22, Region.GYEONGGI, Region.GYEONGGI).won)
        assertEquals(16640, est(12.0, 25, 0, 23, Region.GYEONGGI, Region.GYEONGGI).won)
    }

    @Test fun `정체 구간은 거리 대신 시간으로 요금이 붙는다`() {
        val calm = est(12.0, 25, 0, 12, Region.SEOUL, Region.SEOUL).won
        val jam = est(12.0, 60, 50, 12, Region.SEOUL, Region.SEOUL).won
        assertTrue("jam=$jam calm=$calm", jam > calm)
    }

    @Test fun `수도권 밖이거나 도착 지역을 모르면 요금을 내지 않는다`() {
        assertNull(TaxiFare.estimate(12.0, 25, 0, noon, ZONE, Region.SEOUL, null))
        assertNull(TaxiFare.regionOf(35.1796, 129.0756))   // 부산
    }

    @Test fun `좌표로 서울과 경기를 어림한다`() {
        assertEquals(Region.SEOUL, TaxiFare.regionOf(37.5665, 126.9780))
        assertEquals(Region.GYEONGGI, TaxiFare.regionOf(37.4127, 127.0947))
    }

    @Test fun `오늘 이동 비교 문구는 짧고 사실만`() {
        val e = TripEstimate.of(22442, 2813, listOf(Segment(Congestion.HEAVY, 0, 40), Segment(Congestion.FAST, 40, 100)))
        assertEquals(47, e.minutes); assertEquals(40, e.slowPct)
        assertEquals("오늘 47분 · 주행비 약 4,200원 · 평소보다 3분 더 · 서행·정체 많음", TodayCompare.selfLine(e, RouteInfo.Typical(24.0, 44, 4)))
        val calm = TripEstimate.of(10000, 1200, listOf(Segment(Congestion.FAST, 0, 100)))
        assertEquals("오늘 20분 · 주행비 약 1,900원", TodayCompare.selfLine(calm, RouteInfo.Typical(10.0, 20, 3)))
        assertFalse(PhraseGuard.containsBanned(TodayCompare.selfLine(e, RouteInfo.Typical(24.0, 44, 4))))
    }

    @Test fun `휘발유 주행비는 km당 약 185원`() {
        assertEquals(4500, FuelCost.won(24.0))           // 2.4L × 1,855 = 4,452 → 4,500
        assertEquals(1900, FuelCost.won(10.0))           // 1,855 → 1,900
        assertEquals(100, FuelCost.won(0.1))
    }

    @Test fun `전기차는 내비의 소비 전력으로 계산한다`() {
        val ev = TripEstimate.of(22442, 2813, emptyList(), energyWh = 2871.0, socAtDest = 96)
        assertTrue(ev.isEv)
        assertEquals(900, FuelCost.won(ev.km, ev))       // 2.871kWh × 325.6 = 935 → 900
        val noEnergy = TripEstimate.of(10000, 1200, emptyList(), socAtDest = 80)
        assertEquals(400, FuelCost.won(noEnergy.km, noEnergy))   // 10km × 128Wh × 325.6 = 417 → 400
        val ice = TripEstimate.of(22442, 2813, emptyList())
        assertFalse(ice.isEv)
        assertEquals(4200, FuelCost.won(ice.km, ice))
    }
}
