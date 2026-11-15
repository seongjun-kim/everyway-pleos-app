package app.carwell.drive

import app.carwell.drive.domain.ShucleZones
import org.junit.Assert.*
import org.junit.Test

class ShucleZonesTest {
    @Test fun 같은_운영_지역_안이면_보여_준다() {
        assertEquals("판교", ShucleZones.sameZone(37.4127, 127.0947, 37.3935, 127.1090)?.name)
    }

    @Test fun 운영_지역_밖이거나_서로_다른_지역이면_숨긴다() {
        assertNull(ShucleZones.sameZone(37.4127, 127.0947, 37.5759, 126.9769))   // 도착이 서울 도심
        assertNull(ShucleZones.sameZone(37.3947, 127.1112, 37.2890, 127.0540))   // 판교 → 광교
        assertNull(ShucleZones.sameZone(37.5759, 126.9769, 37.5700, 126.9800))   // 둘 다 운영 지역 밖
    }

    @Test fun 좌표를_모르면_숨긴다() {
        assertNull(ShucleZones.sameZone(null, null, 37.3935, 127.1090))
    }
}
