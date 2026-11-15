package app.carwell.drive.domain

import java.util.Locale

/** 주차 직후 보여 주는 한 줄. 사실만 적고 평가하지 않는다. */
object TripText {
    fun km(km: Double): String = String.format(Locale.US, "%.1f", km)

    private fun duration(trip: Trip) = if (trip.durationMin < 1) "1분 미만" else "${trip.durationMin}분"

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
