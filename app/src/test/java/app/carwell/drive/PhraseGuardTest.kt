package app.carwell.drive

import app.carwell.drive.domain.PhraseGuard
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PhraseGuardTest {
    @Test fun `금지어를 잡아낸다`() {
        listOf("위험해요", "치매 의심", "운전 점수 80", "3등급", "순위 1위", "진단 결과", "판정: 주의", "경고").forEach {
            assertTrue(it, PhraseGuard.containsBanned(it))
        }
        assertFalse(PhraseGuard.containsBanned("야간 운행이 늘었어요"))
    }

    @Test fun `금지어가 있으면 대체 문구로 바꾼다`() {
        assertEquals("", PhraseGuard.safe("위험해요"))
        assertEquals("기록", PhraseGuard.safe("점수", fallback = "기록"))
        assertEquals("편안", PhraseGuard.safe("편안"))
    }

    @Test fun `사용자 데이터로 들어온 이름은 보호한다`() {
        val text = "· 치매안심센터: 택시"
        assertTrue(PhraseGuard.containsBanned(text))
        assertFalse(PhraseGuard.containsBanned(text, listOf("치매안심센터")))
    }

    /** 앱 소스(Kotlin 문자열 리터럴)와 strings.xml 에 금지어가 없어야 한다. PhraseGuard 정의 자체만 예외. */
    @Test fun `앱 소스의 한국어 문구에 금지어가 없다`() {
        val root = File("src/main")
        assertTrue("src/main 을 찾지 못함: ${root.absolutePath}", root.exists())
        val literal = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
        val offenders = mutableListOf<String>()
        root.walkTopDown().filter { it.isFile }.forEach { f ->
            if (f.name == "PhraseGuard.kt") return@forEach
            when (f.extension) {
                "kt" -> f.readLines().forEach { raw ->
                    val line = raw.substringBefore("//").trim()
                    if (line.startsWith("*") || line.startsWith("/*")) return@forEach
                    literal.findAll(line).forEach { m ->
                        if (PhraseGuard.containsBanned(m.groupValues[1])) offenders += "${f.name}: ${m.groupValues[1]}"
                    }
                }
                "xml" -> {
                    val text = f.readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
                    if (PhraseGuard.containsBanned(text)) offenders += f.name
                }
            }
        }
        assertTrue("금지어 발견: $offenders", offenders.isEmpty())
    }
}
