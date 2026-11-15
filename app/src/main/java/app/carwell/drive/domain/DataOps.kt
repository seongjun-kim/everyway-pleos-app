package app.carwell.drive.domain

/** AppData 를 바꾸는 순수 함수 모음 (저장·UI 와 분리해 테스트한다). */
object DataOps {
    fun addTrip(d: AppData, trip: Trip): AppData =
        d.copy(trips = d.trips + trip, pendingFeelingTripId = trip.id)

    fun noteDestination(d: AppData, name: String, address: String?, nowMs: Long, lat: Double? = null, lon: Double? = null): AppData =
        if (!PlaceStats.shouldRecordVisit(d.visits, name, nowMs)) d
        else d.copy(visits = d.visits + PlaceVisit(name.trim(), address, nowMs, lat = lat, lon = lon))

    fun setFeeling(d: AppData, feeling: Feeling, nowMs: Long): AppData {
        val id = d.pendingFeelingTripId
        return d.copy(
            feelings = d.feelings + FeelingEntry(nowMs, feeling, id),
            pendingFeelingTripId = null,
        )
    }

    fun skipFeeling(d: AppData) = d.copy(pendingFeelingTripId = null)

    fun addUnrecorded(d: AppData, km: Double) = d.copy(unrecordedKm = d.unrecordedKm + km)

    /** 오늘 이렇게 간다고 누른 기록을 쌓는다(같은 날 여러 번 가능). */
    fun addChoice(d: AppData, key: String, name: String, alt: Alternative, nowMs: Long): AppData =
        d.copy(choices = d.choices + MoveChoice(nowMs, key, name.trim(), alt))

    /** 시연용 가상 이력으로 기록을 통째로 바꾼다. 설정은 유지. */
    fun loadDemo(d: AppData, nowMs: Long): AppData {
        val demo = DemoData.generate(nowMs)
        return d.copy(
            trips = demo.trips, feelings = demo.feelings, visits = demo.visits, choices = demo.choices,
            pendingFeelingTripId = null, unrecordedKm = 0.0,
        )
    }

    fun clearDemo(d: AppData): AppData {
        val trips = d.trips.filterNot { it.demo }
        val visits = d.visits.filterNot { it.demo }
        return d.copy(
            trips = trips,
            feelings = d.feelings.filterNot { it.demo },
            choices = d.choices.filterNot { it.demo },
            visits = visits,
            pendingFeelingTripId = d.pendingFeelingTripId?.takeIf { id -> trips.any { it.id == id } },
        )
    }

    /** 전체 삭제: 기록·선택을 모두 지운다. */
    fun wipe(): AppData = AppData()
}
