package app.carwell.drive.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 디자인 토큰. 색은 중립 다크 + 프라이머리(청록) 1색 + 노랑 1색(주의·비어 있음)만 쓴다.
 * 빨강(Danger)은 되돌릴 수 없는 '전체 삭제'에만 쓴다.
 */
object C {
    val Bg = Color(0xFF0D1319)
    val Surface = Color(0xFF17212B)
    val Surface2 = Color(0xFF212D39)
    val Line = Color(0xFF2A3745)
    val Primary = Color(0xFF4FD1C5)
    val OnPrimary = Color(0xFF06201D)
    val Text = Color(0xFFF2F6FA)
    val Dim = Color(0xFFA3B2C2)
    val Warm = Color(0xFFFFC857)
    val Danger = Color(0xFFFF8A80)
}

/**
 * 타이포. 이 단말은 160dpi(1dp = 1px)라서 휴대폰 기준보다 한 단계씩 크게 잡는다.
 * 가장 작은 글씨(Caption)도 22sp.
 */
object T {
    val Display = TextStyle(fontSize = 88.sp, lineHeight = 96.sp, fontWeight = FontWeight.Bold, color = C.Text)
    val Num = TextStyle(fontSize = 68.sp, lineHeight = 76.sp, fontWeight = FontWeight.Bold, color = C.Text)
    val Title = TextStyle(fontSize = 44.sp, lineHeight = 56.sp, fontWeight = FontWeight.Bold, color = C.Text)
    val Head = TextStyle(fontSize = 32.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, color = C.Text)
    val Body = TextStyle(fontSize = 28.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium, color = C.Text)
    val Sub = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, color = C.Dim)
    val Caption = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, color = C.Dim)
}

/** 8dp 그리드. */
object Sp {
    val Xs = 8.dp
    val S = 16.dp
    val M = 24.dp
    val L = 32.dp
    val Xl = 40.dp
}

object Shp {
    val Card = RoundedCornerShape(24.dp)
    val Row = RoundedCornerShape(20.dp)
    val Pill = RoundedCornerShape(50)
    val Sheet = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
}

/** 차량(장갑·흔들림)을 고려한 최소 터치 높이. 주 버튼·행은 이보다 크게. */
val MinTouch = 56.dp
val RowHeight = 96.dp
val ButtonHeight = 80.dp

@Composable
fun DriveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Primary, onPrimary = C.OnPrimary, background = C.Bg, onBackground = C.Text,
            surface = C.Surface, onSurface = C.Text,
        ),
        content = content,
    )
}
