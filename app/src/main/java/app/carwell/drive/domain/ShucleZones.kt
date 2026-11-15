package app.carwell.drive.domain

import kotlin.math.*

/**
 * 셔클(수요응답 버스)은 지자체가 정한 운영 지역 안에서만 달린다. 이 앱은 출발지와 목적지가 같은 운영 지역 안일 때만
 * 셔클을 보여 준다. 지역은 언론 보도·현대차그룹 자료에 이름이 나온 곳의 대표 지점과 반경으로 어림한 것이고,
 * 실제 운영 구역·운영 여부는 지역과 시기마다 달라 앱 업데이트 때 갱신한다(근거: docs 의 "셔클 운영 지역").
 */
object ShucleZones {
    data class Zone(val name: String, val lat: Double, val lon: Double, val radiusKm: Double)

    val ZONES = listOf(
        Zone("판교", 37.3947, 127.1112, 3.5),
        Zone("광교", 37.2890, 127.0540, 3.5),
        Zone("김포 고촌·풍무", 37.6070, 126.7520, 4.5),
        Zone("검단", 37.5920, 126.6880, 4.5),
        Zone("덕은·향동", 37.6130, 126.8730, 3.5),
        Zone("은평뉴타운", 37.6400, 126.9300, 3.0),
        Zone("파주 운정", 37.7160, 126.7440, 5.0),
        Zone("세종", 36.5000, 127.2600, 10.0),
        Zone("영암 삼호", 34.7750, 126.4300, 6.0),
        Zone("서산", 36.7850, 126.4500, 8.0),
        Zone("보령", 36.3330, 126.6130, 8.0),
    )

    fun distanceKm(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(bLat - aLat); val dLon = Math.toRadians(bLon - aLon)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    fun zoneOf(lat: Double, lon: Double): Zone? =
        ZONES.filter { distanceKm(lat, lon, it.lat, it.lon) <= it.radiusKm }.minByOrNull { distanceKm(lat, lon, it.lat, it.lon) }

    /** 출발지와 목적지가 같은 운영 지역 안이면 그 지역. 좌표를 모르면 null(보여 주지 않음). */
    fun sameZone(aLat: Double?, aLon: Double?, bLat: Double?, bLon: Double?): Zone? {
        if (aLat == null || aLon == null || bLat == null || bLon == null) return null
        val a = zoneOf(aLat, aLon) ?: return null
        return a.takeIf { it == zoneOf(bLat, bLon) }
    }
}
