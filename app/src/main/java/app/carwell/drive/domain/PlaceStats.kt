package app.carwell.drive.domain

object PlaceStats {
    /** 방문 횟수 상위 [limit] 곳. 같으면 최근에 간 곳, 그다음 이름순. */
    fun topPlaces(visits: List<PlaceVisit>, limit: Int = Rules.TOP_PLACES): List<PlaceCount> =
        visits.groupBy { it.key }
            .map { (key, list) ->
                val latest = list.maxBy { it.atMs }
                PlaceCount(
                    key = key,
                    name = latest.name.trim(),
                    address = list.sortedByDescending { it.atMs }.firstNotNullOfOrNull { it.address },
                    count = list.size,
                    lastAtMs = latest.atMs,
                    lat = list.sortedByDescending { it.atMs }.firstOrNull { it.lat != null && it.lon != null }?.lat,
                    lon = list.sortedByDescending { it.atMs }.firstOrNull { it.lat != null && it.lon != null }?.lon,
                )
            }
            .sortedWith(compareByDescending<PlaceCount> { it.count }.thenByDescending { it.lastAtMs }.thenBy { it.name })
            .take(limit)

    /** 같은 목적지를 [Rules.PLACE_DEDUPE_MINUTES] 안에 다시 안내하면(재탐색 등) 새 방문으로 세지 않는다. */
    fun shouldRecordVisit(existing: List<PlaceVisit>, name: String, nowMs: Long): Boolean {
        val key = placeKey(name)
        if (key.isEmpty()) return false
        val window = Rules.PLACE_DEDUPE_MINUTES * 60_000L
        return existing.none { it.key == key && nowMs - it.atMs in 0..window }
    }
}
