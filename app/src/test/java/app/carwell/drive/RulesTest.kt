package app.carwell.drive

import app.carwell.drive.domain.Rules
import org.junit.Assert.*
import org.junit.Test

class RulesTest {
    @Test fun `임계값은 한 곳에 고정돼 있다`() {
        assertEquals(20, Rules.NIGHT_START_HOUR)
        assertEquals(5, Rules.NIGHT_END_HOUR)
        assertEquals(30.0, Rules.LONG_TRIP_KM, 0.0)
        assertEquals(0.1, Rules.MIN_TRIP_KM, 0.0)
        assertEquals(12, Rules.TOP_PLACES)
        assertEquals(30, Rules.PLACE_DEDUPE_MINUTES)
        assertEquals(28, Rules.WINDOW_DAYS)
        assertEquals(8, Rules.MIN_TRIPS_PER_WINDOW)
        assertEquals(6, Rules.MIN_FEELINGS_RECENT)
        assertEquals(4, Rules.MIN_FEELINGS_BASELINE)
        assertEquals(0.15, Rules.NIGHT_RATIO_RISE, 0.0)
        assertEquals(0.15, Rules.LONG_RATIO_RISE, 0.0)
        assertEquals(0.30, Rules.WEEKLY_TRIPS_RISE_RATIO, 0.0)
        assertEquals(1.0, Rules.WEEKLY_TRIPS_RISE_MIN_ABS, 0.0)
        assertEquals(0.60, Rules.EASY_DOMINANT_SHARE, 0.0)
        assertEquals(0.20, Rules.TENSE_LOW_SHARE, 0.0)
        assertEquals(0.20, Rules.TENSE_RISE, 0.0)
        assertEquals(0.30, Rules.TENSE_MIN_SHARE, 0.0)
    }

    @Test fun `야간 경계는 20시 이상 또는 5시 미만`() {
        assertFalse(Rules.isNight(ms(2026, 11, 3, 19, 59), ZONE))
        assertTrue(Rules.isNight(ms(2026, 11, 3, 20, 0), ZONE))
        assertTrue(Rules.isNight(ms(2026, 11, 3, 4, 59), ZONE))
        assertFalse(Rules.isNight(ms(2026, 11, 3, 5, 0), ZONE))
    }

    @Test fun `장거리 경계는 30km 이상`() {
        assertFalse(Rules.isLong(29.9))
        assertTrue(Rules.isLong(30.0))
    }
}
