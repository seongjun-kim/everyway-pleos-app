package app.carwell.drive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.carwell.drive.data.AppController
import app.carwell.drive.data.UiModel
import app.carwell.drive.domain.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val DATE_FMT = DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm", Locale.KOREAN)
private fun fmt(ms: Long) = DATE_FMT.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

// ───────────────────────── 홈 ─────────────────────────
/** 왼쪽은 '지금 할 일'(이번 운전 → 느낌 → 다음 이동), 오른쪽은 '요즘 기록'(간극 카드 → 큰 숫자). */
@Composable
fun HomeScreen(ui: UiModel, c: AppController, onToday: () -> Unit = {}) {
    val pendingFeeling = ui.data.pendingFeelingTripId != null
    Row(Modifier.fillMaxSize().padding(Sp.Xl), horizontalArrangement = Arrangement.spacedBy(Sp.L)) {
        FadeScroll(Modifier.weight(1f).fillMaxHeight(), spacing = Sp.M, contentPadding = PaddingValues(bottom = 24.dp)) {
            val trip = ui.lastTrip
            Card {
                if (trip == null) {
                    Txt("아직 운행 기록이 없어요", T.Title)
                } else {
                    val recent = ui.nowMs - trip.endMs < 6 * 3_600_000L
                    Txt(if (recent) "이번 운전" else "지난 운전 · ${fmt(trip.startMs)}", T.Sub)
                    Txt("${TripText.km(trip.distanceKm)}km", T.Display, maxLines = 1)
                    TripText.summaryLine(trip).substringAfter(" · ", "").takeIf { it.isNotBlank() }?.let { Txt(it, T.Head) }
                    TripText.mannerLine(trip)?.let { Pill(it, Tone.Primary) }
                }
            }
            if (pendingFeeling) {
                Card {
                    Txt("오늘 운전은 어땠어요?", T.Head)
                    Row(horizontalArrangement = Arrangement.spacedBy(Sp.S)) {
                        Feeling.entries.forEach { f ->
                            SecondaryButton(f.label, { c.chooseFeeling(f) }, Modifier.weight(1f).height(120.dp))
                        }
                    }
                    QuietButton("건너뛰기", { c.skipFeeling() }, Modifier.fillMaxWidth())
                }
            }
            // 느낌을 묻는 중에는 그쪽이 주인공이라 이 버튼은 조용하게.
            if (pendingFeeling) SecondaryButton("다음에 어디 가세요?", onToday, Modifier.fillMaxWidth())
            else PrimaryButton("다음에 어디 가세요?", onToday, Modifier.fillMaxWidth())
            if (ui.data.unrecordedKm >= Rules.MIN_TRIP_KM) {
                Txt("앱이 꺼진 동안 ${TripText.km(ui.data.unrecordedKm)}km 움직였어요 (기록 안 됨)", T.Sub)
            }
        }
        FadeScroll(Modifier.weight(1f).fillMaxHeight(), spacing = Sp.M, contentPadding = PaddingValues(bottom = 24.dp)) {
            when (val g = ui.gap) {
                is GapResult.Card -> Card {
                    Txt(g.title, T.Title.copy(fontSize = 40.sp, color = C.Warm))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { g.lines.forEach { Txt(it, T.Body) } }
                }
                is GapResult.Collecting -> Card { Txt(g.message, T.Head) }
                GapResult.Quiet -> Card { Txt("느낌과 기록이 비슷해요", T.Head) }
            }
            val other = MoveFlow.otherCountThisMonth(ui.data.choices, ui.nowMs, ZoneId.systemDefault())
            if (other > 0) BigStat("${other}번", "이번 달 다른 방법으로 간 날", Modifier.fillMaxWidth())
            if (ui.recent.trips > 0) {
                BigStat("${ui.recent.trips}번", "최근 4주 운행", Modifier.fillMaxWidth())
                // 횟수가 적을 때의 비율은 오해를 부르므로, 비교에 쓰는 최소 횟수 이상일 때만 보여 준다.
                if (ui.recent.trips >= Rules.MIN_TRIPS_PER_WINDOW) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Sp.M)) {
                        BigStat("${(ui.recent.nightRatio * 100).roundToInt()}%", "야간", Modifier.weight(1f))
                        BigStat("${(ui.recent.longRatio * 100).roundToInt()}%", "장거리", Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ───────────────────────── 더 보기 ─────────────────────────
@Composable
fun MoreScreen(onHistory: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(Sp.Xl), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageTitle("더 보기")
        Spacer(Modifier.height(Sp.S))
        Column(Modifier.widthIn(max = 1040.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ListRow("기록", onHistory, Modifier.heightIn(min = 112.dp))
            ListRow("설정", onSettings, Modifier.heightIn(min = 112.dp))
        }
    }
}

// ───────────────────────── 기록 ─────────────────────────
@Composable
fun HistoryScreen(ui: UiModel, onBack: () -> Unit) {
    val trips = remember(ui.data.trips) { ui.data.trips.sortedByDescending { it.startMs }.take(60) }
    val days = remember(ui.data.choices) { MoveFlow.byDay(ui.data.choices, ZoneId.systemDefault()).take(6) }
    Column(Modifier.fillMaxSize().padding(Sp.Xl)) {
        PageTitle("기록", onBack = onBack)
        Spacer(Modifier.height(Sp.M))
        if (trips.isEmpty() && days.isEmpty()) {
            EmptyNote("아직 기록이 없어요")
        } else Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Sp.L)) {
            if (days.isNotEmpty()) {
                Column(
                    Modifier.weight(0.42f).fillMaxHeight().clip(Shp.Card).background(C.Surface).padding(Sp.M),
                    verticalArrangement = Arrangement.spacedBy(Sp.S),
                ) {
                    Txt("오늘 이동", T.Head)
                    FadeScroll(Modifier.weight(1f), bg = C.Surface, spacing = Sp.M, contentPadding = PaddingValues(bottom = 40.dp)) {
                        days.forEach { (day, list) ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Txt(day, T.Sub)
                                list.forEach {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                                        Txt(it.name, T.Body, Modifier.weight(1f))
                                        Txt(it.alt.label, T.Body, color = C.Primary, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 220.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (trips.isEmpty()) {
                Box(Modifier.weight(0.58f).fillMaxHeight()) { EmptyNote("아직 운행 기록이 없어요") }
            } else {
                val ls = rememberLazyListState()
                Box(Modifier.weight(0.58f).fillMaxHeight()) {
                    LazyColumn(Modifier.fillMaxSize(), state = ls, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 56.dp)) {
                        items(trips, key = { it.id }) { t -> TripRow(t) }
                    }
                    ScrollHints(ls.canScrollBackward, ls.canScrollForward, C.Bg)
                }
            }
        }
    }
}

@Composable
private fun TripRow(t: Trip) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = RowHeight).clip(Shp.Card).background(C.Surface).padding(horizontal = 28.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Txt(t.destination?.takeIf { it.isNotBlank() } ?: fmt(t.startMs), T.Head, maxLines = 2)
            if (!t.destination.isNullOrBlank()) Txt(fmt(t.startMs), T.Sub, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt("${TripText.km(t.distanceKm)}km · " + if (t.durationMin < 1) "1분 미만" else "${t.durationMin}분", T.Body, maxLines = 1)
            if (t.night || t.long) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (t.night) Pill("야간")
                if (t.long) Pill("장거리")
            }
        }
    }
}

// ───────────────────────── 설정 ─────────────────────────
@Composable
fun SettingsScreen(ui: UiModel, c: AppController, onBack: () -> Unit) {
    var confirmDemo by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(Sp.Xl)) {
            PageTitle("설정", onBack = onBack)
            Spacer(Modifier.height(Sp.M))
            FadeScroll(Modifier.widthIn(max = 1040.dp).weight(1f), spacing = 12.dp, contentPadding = PaddingValues(bottom = 56.dp)) {
                ListRow("시연용 가상 이력 불러오기", { confirmDemo = true }, chevron = false)
                if (ui.data.hasDemo) ListRow("가상 이력 지우기", { c.clearDemo() }, chevron = false)
                if (c.voiceAvailable) ListRow("음성 읽기", { c.setReadAloud(!ui.data.readAloud) }, chevron = false,
                    trailing = { Toggle(ui.data.readAloud) })
                ListRow("전체 삭제", { confirmWipe = true }, chevron = false, titleColor = C.Danger)
                Column(Modifier.padding(top = Sp.S, start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Txt("이 앱은 의료기기가 아니며, 기록을 보여 줄 뿐 운전 능력을 판단하지 않아요.", T.Caption)
                    Txt("기록은 이 차 안에만, 차량 프로필별로 저장돼요. 서버로 보내지 않아요.", T.Caption)
                }
            }
        }
        ConfirmSheet(
            confirmDemo, "가상 이력으로 바꿀까요?", "지금 기록은 사라지고, 8주치 가상 기록이 들어와요.", "불러오기",
            onYes = { confirmDemo = false; c.loadDemo() }, onNo = { confirmDemo = false },
        )
        ConfirmSheet(
            confirmWipe, "전체 삭제할까요?", "이 프로필의 기록이 모두 지워져요. 되돌릴 수 없어요.", "삭제",
            onYes = { confirmWipe = false; c.wipeAll() }, onNo = { confirmWipe = false }, danger = true,
        )
    }
}

@Composable
private fun ConfirmSheet(visible: Boolean, title: String, body: String, yes: String, onYes: () -> Unit, onNo: () -> Unit, danger: Boolean = false) {
    Sheet(visible, onNo) {
        Txt(title, T.Title)
        Txt(body, T.Body, color = C.Dim)
        Spacer(Modifier.height(Sp.Xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Sp.S)) {
            SecondaryButton("취소", onNo, Modifier.weight(1f))
            if (danger) DangerButton(yes, onYes, Modifier.weight(1f)) else PrimaryButton(yes, onYes, Modifier.weight(1f))
        }
    }
}

// ───────────────────────── 주행 중 ─────────────────────────
@Composable
fun DrivingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Txt("주행 중에는 화면이 없어요", T.Title, color = C.Dim)
    }
}
