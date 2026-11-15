package app.carwell.drive.domain

/**
 * 점수·등급·판정·진단을 암시하는 단어를 막는 문구 필터.
 * 앱이 만드는 문장은 모두 [safe] 를 거친다. 사용자 데이터(내비 목적지 이름 등)는 [protectedTerms] 로 넘겨
 * 필터가 지우지 않게 한다.
 */
object PhraseGuard {
    val BANNED = listOf(
        "위험", "치매", "점수", "등급", "순위", "진단", "판정", "경고",
        "인지 저하", "인지저하", "인지장애", "노화", "부적합", "자격 상실", "위태",
    )

    fun containsBanned(text: String, protectedTerms: Collection<String> = emptyList()): Boolean =
        BANNED.any { stripProtected(text, protectedTerms).contains(it) }

    /** 금지어가 들어 있으면 [fallback] 으로 바꾼다. */
    fun safe(text: String, fallback: String = "", protectedTerms: Collection<String> = emptyList()): String =
        if (containsBanned(text, protectedTerms)) fallback else text

    private fun stripProtected(text: String, terms: Collection<String>): String {
        var t = text
        terms.filter { it.isNotBlank() }.sortedByDescending { it.length }.forEach { t = t.replace(it, "") }
        return t
    }
}
