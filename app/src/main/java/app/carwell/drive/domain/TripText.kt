package app.carwell.drive.domain

import java.util.Locale

/** 모든 시간 표시의 공통 형식: 60분 이상은 "1시간 6분"처럼 조합한다. */
object Dur {
    fun label(min: Int): String {
        val m = min.coerceAtLeast(0)
        return when {
            m < 60 -> "${m}분"
            m % 60 == 0 -> "${m / 60}시간"
            else -> "${m / 60}시간 ${m % 60}분"
        }
    }
}

/** 주차 직후 보여 주는 한 줄. 사실만 적고 평가하지 않는다. */
object TripText {
    fun km(km: Double): String = String.format(Locale.US, "%.1f", km)

    private fun duration(trip: Trip) = if (trip.durationMin < 1) "1분 미만" else Dur.label(trip.durationMin)

    fun summaryLine(trip: Trip): String {
        val dest = trip.destination?.takeIf { it.isNotBlank() }
        val base = "${km(trip.distanceKm)}km · ${duration(trip)}"
        val text = if (dest != null) "$base · $dest" else base
        return PhraseGuard.safe(text, fallback = base, protectedTerms = listOfNotNull(dest))
    }

    /** 방향지시등 사용 같은 긍정 신호만. 없으면 null (부정 문구는 만들지 않는다). */
    fun mannerLine(trip: Trip): String? =
        if (trip.turnSignalCount >= 1) "방향지시등을 ${trip.turnSignalCount}번 켜고 달렸어요" else null

    /** 음성(TTS)으로 읽을 때 쓰는 문장. */
    fun spoken(trip: Trip): String {
        val base = "이번 운전은 ${km(trip.distanceKm)}킬로미터, ${duration(trip)}이었어요."
        val manner = mannerLine(trip)?.let { "$it." } ?: ""
        return PhraseGuard.safe("$base $manner".trim(), fallback = base)
    }
}
