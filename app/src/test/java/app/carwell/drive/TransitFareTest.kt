package app.carwell.drive

import app.carwell.drive.domain.*
import app.carwell.drive.domain.TaxiFare.Region
import org.junit.Assert.*
import org.junit.Test

class TransitFareTest {
    @Test fun `서울 지하철은 10km까지 1550원이고 10~50km는 5km마다 100원`() {
        assertEquals(1550, TransitFare.subwayWon(5.0))
        assertEquals(1550, TransitFare.subwayWon(10.0))
        assertEquals(1650, TransitFare.subwayWon(10.1))
        assertEquals(1850, TransitFare.subwayWon(24.0))   // (24-10)/5 = 2.8 → 3단계
        assertEquals(1550 + 800, TransitFare.subwayWon(50.0))
        assertEquals(1550 + 800 + 100, TransitFare.subwayWon(51.0))
    }

    @Test fun `경기 일반 시내버스는 1650원에서 시작해 상한 2350원`() {
        assertEquals(1650, TransitFare.gyeonggiBusWon(8.0))
        assertEquals(1850, TransitFare.gyeonggiBusWon(20.0))
        assertEquals(2350, TransitFare.gyeonggiBusWon(80.0))
    }

    @Test fun `대표 요금은 지역에 따라 갈린다`() {
        assertEquals(1850, TransitFare.transitWon(24.0, Region.SEOUL))
        assertEquals(1950, TransitFare.transitWon(24.0, Region.GYEONGGI))   // 1650 + 3×100
        assertEquals(1500, TransitFare.drtWon(Region.SEOUL))
        assertEquals(1650, TransitFare.drtWon(Region.GYEONGGI))
    }

    @Test fun `시간은 자동차 예상의 배수에 대기를 더한 어림`() {
        assertEquals(83, TransitFare.transitMinutes(46))    // 46×1.6+10 = 83.6
        assertEquals(74, TransitFare.drtMinutes(46))        // 46×1.4+10 = 74.4
        assertEquals("약 45분", TransitFare.minutesLabel(45))
        assertEquals("약 1시간 23분", TransitFare.minutesLabel(83))
        assertEquals("약 2시간", TransitFare.minutesLabel(120))
    }
}
