package app.carwell.drive

import app.carwell.drive.domain.*
import java.time.LocalDateTime
import java.time.ZoneId

val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
const val HOUR = 3_600_000L
const val DAY = 24 * HOUR

fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0): Long =
    LocalDateTime.of(y, mo, d, h, mi).atZone(ZONE).toInstant().toEpochMilli()

fun trip(startMs: Long, km: Double = 5.0, night: Boolean? = null, long: Boolean? = null, id: String = "t$startMs") =
    Trip.create(id, startMs, startMs + 20 * 60_000L, km, 1, null, ZONE).let {
        it.copy(night = night ?: it.night, long = long ?: it.long)
    }

fun feeling(atMs: Long, f: Feeling) = FeelingEntry(atMs, f)
