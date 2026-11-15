package app.carwell.drive

import app.carwell.drive.domain.Dur
import org.junit.Test
import org.junit.Assert.assertEquals

class DurTest {
    @Test fun combinesHoursAndMinutes() {
        assertEquals("45분", Dur.label(45))
        assertEquals("59분", Dur.label(59))
        assertEquals("1시간", Dur.label(60))
        assertEquals("1시간 6분", Dur.label(66))
        assertEquals("1시간 8분", Dur.label(68))
        assertEquals("2시간 30분", Dur.label(150))
    }
}
