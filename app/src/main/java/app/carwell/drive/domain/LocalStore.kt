package app.carwell.drive.domain

import kotlinx.serialization.json.Json
import java.io.File

/**
 * 저장소 선택 이유(한 줄): 운행·방문 수백 건 규모의 단일 사용자 데이터라 Room/DataStore 의 스키마·마이그레이션이
 * 필요 없고, JSON 한 파일을 임시 파일→이름 변경(원자적)으로 쓰면 가장 단순하고 JVM 테스트도 쉽다.
 */
class LocalStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    fun load(): AppData = try {
        if (file.exists()) json.decodeFromString(AppData.serializer(), file.readText()) else AppData()
    } catch (e: Exception) {
        // 파일이 깨졌으면 덮어쓰기 전에 보관해 두고 빈 상태로 시작
        runCatching { file.renameTo(File(file.parentFile, file.name + ".corrupt")) }
        AppData()
    }

    @Synchronized
    fun save(data: AppData) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(AppData.serializer(), data))
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "저장 실패" }
        }
    }

    fun delete() { file.delete(); File(file.parentFile, file.name + ".tmp").delete() }
}
