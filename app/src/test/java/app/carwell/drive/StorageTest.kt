package app.carwell.drive

import app.carwell.drive.domain.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageTest {
    @get:Rule val tmp = TemporaryFolder()


    @Test fun `저장하고 다시 읽으면 같다`() {
        val store = LocalStore(File(tmp.root, "d.json"))
        val d = DemoData.generate(ms(2026, 11, 14, 12), ZONE).let {
            AppData(it.trips, it.feelings, it.visits, "x", 12345.6, 7.5, false, it.choices)
        }
        store.save(d)
        assertEquals(d, LocalStore(File(tmp.root, "d.json")).load())
    }

    @Test fun `파일이 없으면 빈 상태, 깨졌으면 보관하고 빈 상태`() {
        val f = File(tmp.root, "d.json")
        assertEquals(AppData(), LocalStore(f).load())
        f.writeText("{깨진 json")
        assertEquals(AppData(), LocalStore(f).load())
        assertTrue(File(tmp.root, "d.json.corrupt").exists())
    }

    @Test fun `나중 버전이 필드를 추가해도 읽는다`() {
        val f = File(tmp.root, "d.json")
        f.writeText("""{"trips":[],"newField":123}""")
        assertEquals(AppData(), LocalStore(f).load())
    }

    @Test fun `전체 삭제는 기록·선택을 모두 비운다`() {
        val demo = DataOps.loadDemo(AppData(), ms(2026, 11, 14, 12))
        assertTrue(demo.trips.isNotEmpty() && demo.hasDemo)
        val wiped = DataOps.wipe()
        assertEquals(AppData(), wiped)
        assertFalse(wiped.hasDemo)
    }

    @Test fun `가상 이력 지우기는 가상 항목만 지운다`() {
        val now = ms(2026, 11, 14, 12)
        var d = DataOps.loadDemo(AppData(), now)
        val real = trip(now - HOUR, 8.0).copy(id = "real")
        d = DataOps.addTrip(d, real)
        d = DataOps.noteDestination(d, "약국", null, now)
        val cleared = DataOps.clearDemo(d)
        assertEquals(listOf("real"), cleared.trips.map { it.id })
        assertEquals(listOf("약국"), cleared.visits.map { it.name })
        assertFalse(cleared.hasDemo)
        assertTrue(cleared.choices.isEmpty())
        assertEquals("real", cleared.pendingFeelingTripId)
    }

    @Test fun `느낌 한 번 누르기 흐름`() {
        val now = ms(2026, 11, 14, 12)
        var d = DataOps.addTrip(AppData(), trip(now - HOUR).copy(id = "a"))
        assertEquals("a", d.pendingFeelingTripId)
        d = DataOps.setFeeling(d, Feeling.TENSE, now)
        assertNull(d.pendingFeelingTripId)
        assertEquals(Feeling.TENSE, d.feelings.single().feeling)
        assertEquals("a", d.feelings.single().tripId)
        d = DataOps.skipFeeling(DataOps.addTrip(d, trip(now).copy(id = "b")))
        assertNull(d.pendingFeelingTripId)
        assertEquals(1, d.feelings.size) // 건너뛰면 기록하지 않음
    }

    @Test fun `같은 목적지를 연속 안내해도 방문은 한 번`() {
        val now = ms(2026, 11, 14, 12)
        var d = DataOps.noteDestination(AppData(), "병원", "주소", now)
        d = DataOps.noteDestination(d, "병원", null, now + 5 * 60_000)
        assertEquals(1, d.visits.size)
        d = DataOps.noteDestination(d, "병원", null, now + 2 * HOUR)
        assertEquals(2, d.visits.size)
    }
}
