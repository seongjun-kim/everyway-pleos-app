package app.carwell.drive.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ───────────────────────── 글자 ─────────────────────────

private const val WJ = '⁠' // 붙여 쓰는 글자(word joiner): 한글 어절 중간에서 줄이 꺾이지 않게 한다.
private fun isHangul(c: Char) = c in '가'..'힣'

/** 한글 word-break: keep-all 과 같은 효과. 공백에서만 줄이 바뀐다. */
fun String.keepAll(): String {
    if (length < 2) return this
    val sb = StringBuilder(length + length / 2)
    for (i in indices) {
        sb.append(this[i])
        if (i < lastIndex && isHangul(this[i]) && isHangul(this[i + 1])) sb.append(WJ)
    }
    return sb.toString()
}

/** 모든 화면 글자는 이걸 쓴다(어절 단위 줄바꿈). */
@Composable
fun Txt(
    text: String,
    style: TextStyle = T.Body,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
) {
    val s = remember(text) { text.keepAll() }
    Text(s, modifier, color = color, style = style, maxLines = maxLines, textAlign = textAlign, overflow = TextOverflow.Ellipsis)
}

// ───────────────────────── 아이콘(그려서 쓴다: 글꼴에 없는 기호 방지) ─────────────────────────

enum class Dir(val deg: Float) { Down(0f), Up(180f), Right(-90f), Left(90f) }

@Composable
fun Chevron(dir: Dir, modifier: Modifier = Modifier, size: Dp = 24.dp, color: Color = C.Dim) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = 3.dp.toPx()
        rotate(dir.deg) {
            drawLine(color, Offset(w * .2f, h * .36f), Offset(w * .5f, h * .66f), sw, StrokeCap.Round)
            drawLine(color, Offset(w * .5f, h * .66f), Offset(w * .8f, h * .36f), sw, StrokeCap.Round)
        }
    }
}

@Composable
fun CheckMark(modifier: Modifier = Modifier, size: Dp = 24.dp, color: Color = C.OnPrimary) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = 3.5.dp.toPx()
        drawLine(color, Offset(w * .2f, h * .52f), Offset(w * .42f, h * .74f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * .42f, h * .74f), Offset(w * .8f, h * .3f), sw, StrokeCap.Round)
    }
}

// ───────────────────────── 버튼 ─────────────────────────

@Composable
private fun ButtonBase(
    text: String, onClick: () -> Unit, modifier: Modifier, bg: Color, fg: Color,
    enabled: Boolean = true, height: Dp = ButtonHeight, style: TextStyle = T.Head, shape: Shape = Shp.Row,
) {
    Box(
        modifier.heightIn(min = height).clip(shape).background(bg).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(text, style, color = fg, textAlign = TextAlign.Center, maxLines = 2) }
}

/** 화면에서 가장 두드러지는 단 하나의 행동. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    ButtonBase(text, onClick, modifier, if (enabled) C.Primary else C.Surface2, if (enabled) C.OnPrimary else C.Dim.copy(alpha = .6f), enabled)

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    ButtonBase(text, onClick, modifier, C.Surface2, if (enabled) C.Text else C.Dim.copy(alpha = .6f), enabled)

/** 배경 없는 글자 버튼. 영역은 넓게(64dp) 잡는다. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = C.Dim) =
    ButtonBase(text, onClick, modifier, Color.Transparent, color, height = 64.dp, style = T.Body)

@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) =
    ButtonBase(text, onClick, modifier, C.Danger, Color(0xFF3A0D0A))

// ───────────────────────── 카드·행·말풍선 ─────────────────────────

@Composable
fun Card(modifier: Modifier = Modifier, bg: Color = C.Surface, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(Shp.Card).background(bg).padding(Sp.L),
        verticalArrangement = Arrangement.spacedBy(Sp.S), content = content,
    )
}

enum class Tone { Neutral, Primary, Warm }

/** 값이 바로 보이는 작은 말풍선. */
@Composable
fun Pill(text: String, tone: Tone = Tone.Neutral, modifier: Modifier = Modifier) {
    val (bg, fg) = when (tone) {
        Tone.Neutral -> C.Surface2 to C.Text
        Tone.Primary -> C.Primary.copy(alpha = .18f) to C.Primary
        Tone.Warm -> C.Warm.copy(alpha = .16f) to C.Warm
    }
    Box(modifier.heightIn(min = 44.dp).clip(Shp.Pill).background(bg).padding(horizontal = 18.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(text, style = T.Sub.copy(fontWeight = FontWeight.Bold), color = fg, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

/** 큰 행: 이름(+보조) 왼쪽, 값·화살표 오른쪽. 누르면 바로 반응. */
@Composable
fun ListRow(
    title: String, onClick: () -> Unit, modifier: Modifier = Modifier, sub: String? = null,
    titleColor: Color = Color.Unspecified, chevron: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = RowHeight).clip(Shp.Card).background(C.Surface).clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Txt(title, T.Head, color = titleColor, maxLines = 2)
            if (sub != null) Txt(sub, T.Sub, maxLines = 2)
        }
        trailing?.invoke(this)
        if (chevron) Chevron(Dir.Right)
    }
}

/** 라디오 느낌의 세로 큰 행. 고른 행은 프라이머리 테두리. */
@Composable
fun RadioRow(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, sub: String? = null, tag: String? = null, onInfo: (() -> Unit)? = null) {
    val shape = Shp.Card
    Row(
        modifier.fillMaxWidth().heightIn(min = RowHeight).clip(shape)
            .background(if (selected) C.Primary.copy(alpha = .10f) else C.Surface2)
            .border(BorderStroke(2.dp, if (selected) C.Primary else Color.Transparent), shape)
            .clickable(onClick = onClick).padding(horizontal = 28.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Radio(selected)
        Column(Modifier.weight(1f)) {
            Txt(title, T.Head, maxLines = 2)
            if (sub != null) Txt(sub, T.Sub, maxLines = 2)
        }
        if (tag != null) Pill(tag, Tone.Primary)
        if (onInfo != null) Box(
            Modifier.heightIn(min = 56.dp).clip(Shp.Row).clickable(onClick = onInfo).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) { Txt("안내", T.Body, color = C.Primary, maxLines = 1) }
    }
}

@Composable
private fun Radio(selected: Boolean) {
    Box(
        Modifier.size(40.dp).border(3.dp, if (selected) C.Primary else C.Dim.copy(alpha = .6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { if (selected) Box(Modifier.size(20.dp).background(C.Primary, CircleShape)) }
}

/** 체크가 바로 보이는 원. */
@Composable
fun CheckDot(on: Boolean) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(if (on) C.Primary else Color.Transparent)
            .border(3.dp, if (on) C.Primary else C.Dim.copy(alpha = .6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { if (on) CheckMark() }
}

@Composable
fun Toggle(on: Boolean) {
    Box(
        Modifier.size(width = 84.dp, height = 48.dp).clip(Shp.Pill).background(if (on) C.Primary else C.Surface2).padding(6.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(36.dp).background(if (on) C.OnPrimary else C.Dim, CircleShape)) }
}

/** 큰 숫자 카드: 숫자가 먼저, 이름은 작게. */
@Composable
fun BigStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(Shp.Card).background(C.Surface).padding(horizontal = Sp.L, vertical = Sp.M),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Txt(value, T.Num, maxLines = 1)
        Txt(label, T.Sub, maxLines = 2)
    }
}

/** 번호 매긴 단계 한 줄. */
@Composable
fun StepRow(n: Int?, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        if (n != null) {
            Box(Modifier.size(48.dp).background(C.Primary, CircleShape), contentAlignment = Alignment.Center) {
                Text("$n", style = T.Body.copy(fontWeight = FontWeight.Bold), color = C.OnPrimary)
            }
        }
        Txt(text, T.Body, Modifier.weight(1f).padding(top = 4.dp))
    }
}

// ───────────────────────── 화면 머리글 ─────────────────────────

@Composable
fun PageTitle(title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (onBack != null) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(C.Surface2).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Chevron(Dir.Left, size = 28.dp, color = C.Text)
            }
        }
        Txt(title, T.Title, Modifier.weight(1f), maxLines = 2)
    }
}

// ───────────────────────── 스크롤 힌트(끝 페이드 + 옅은 화살표, 스크롤바 없음) ─────────────────────────

/** 더 볼 쪽에만 배경색으로 옅어지는 페이드와 옅은 화살표를 얹는다. 끝까지 가면 그쪽은 사라진다. */
@Composable
fun BoxScope.ScrollHints(canBack: Boolean, canForward: Boolean, bg: Color) {
    if (canBack) {
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(72.dp).background(Brush.verticalGradient(listOf(bg, Color.Transparent))))
        Chevron(Dir.Up, Modifier.align(Alignment.TopCenter).padding(top = 6.dp), color = C.Dim.copy(alpha = .7f))
    }
    if (canForward) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(96.dp).background(Brush.verticalGradient(listOf(Color.Transparent, bg))))
        Chevron(Dir.Down, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), color = C.Dim.copy(alpha = .7f))
    }
}

/** 세로 스크롤 열 + 힌트. 스크롤바는 그리지 않는다. */
@Composable
fun FadeScroll(
    modifier: Modifier = Modifier, bg: Color = C.Bg, spacing: Dp = Sp.S,
    contentPadding: PaddingValues = PaddingValues(0.dp), content: @Composable ColumnScope.() -> Unit,
) {
    val st = rememberScrollState()
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(st).padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(spacing), content = content,
        )
        ScrollHints(st.canScrollBackward, st.canScrollForward, bg)
    }
}

// ───────────────────────── 하단 시트 ─────────────────────────

/** 이 시트를 담은 Box(화면 영역) 전체를 덮는다. 바깥을 누르거나 뒤로 가기를 누르면 닫힌다. */
@Composable
fun Sheet(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(enabled = visible, onBack = onDismiss)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxH = maxHeight * 0.94f
        AnimatedVisibility(visible, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = .62f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible, modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(240)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160)),
        ) {
            Column(
                Modifier.widthIn(max = 1080.dp).fillMaxWidth().heightIn(max = maxH).clip(Shp.Sheet).background(C.Surface)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(start = Sp.L, end = Sp.L, top = 14.dp, bottom = Sp.L),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(width = 56.dp, height = 6.dp).background(C.Line, Shp.Pill))
                Spacer(Modifier.height(Sp.M))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Sp.S), content = content)
            }
        }
    }
}

/** 오늘 이동에서 본인이 정해 둔 방법을 조용히 꺼내 보이는 카드. 권유 문구 없이 사실(이유)과 정해 둔 방법만. */
@Composable
fun HintCard(reason: String, text: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(Shp.Card).background(C.Surface2).padding(horizontal = Sp.L, vertical = Sp.M),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Sp.M),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(reason, Tone.Warm)
            Txt(text, T.Body, maxLines = 2)
        }
        SecondaryButton(action, onAction, Modifier.width(220.dp))
    }
}
