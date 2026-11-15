package app.carwell.drive.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.carwell.drive.data.AppController
import app.carwell.drive.data.UiModel
import app.carwell.drive.domain.*

/**
 * 오늘 이동: 가는 곳을 누르면 하단 시트가 올라와 '오늘은 어떻게 갈까요?'를 묻는다.
 * 직접 운전이 기본이고, 다른 방법은 같은 모양으로 놓고 시간·요금(거리 기반 어림)을 한 줄로만 보여 준다.
 * 방법을 고르고 '이렇게 갈게요'를 누르면 같은 시트가 다음 걸음(번호 매긴 단계)과 주 버튼 하나로 바뀐다.
 * 호출·결제는 하지 않는다. 직접 운전도 한 가지 방법이라 같은 크기·같은 모양이다.
 */
@Composable
fun TodayScreen(ui: UiModel, c: AppController) {
    var open by remember { mutableStateOf(false) }
    var selKey by remember { mutableStateOf<String?>(null) }
    var pick by remember { mutableStateOf<Alternative?>(null) }
    var done by remember { mutableStateOf<Alternative?>(null) }
    var est by remember { mutableStateOf<TripEstimate?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var guide by remember { mutableStateOf(false) }
    val place = ui.places.firstOrNull { it.key == selKey }
    val ctx = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(Sp.Xl)) {
            PageTitle("오늘 어디 가요?")
            Spacer(Modifier.height(Sp.M))
            if (ui.places.isEmpty()) {
                EmptyNote("아직 단골 길이 없어요")
            } else {
                PlaceGrid(ui.places, Modifier.weight(1f)) { p, m ->
                    val t = RouteInfo.typical(ui.data.trips, p.key)
                    ListRow(
                        p.name, onClick = {
                            selKey = p.key; pick = Alternative.SELF; done = null; msg = null; est = null; open = true
                            c.requestTrip(p) { r -> if (selKey == p.key) est = r }
                        }, modifier = m,
                        trailing = t?.let { { Txt("평소 ${RouteInfo.kmLabel(it.km)}", T.Body, color = C.Dim, maxLines = 1) } },
                    )
                }
            }
        }

        Sheet(visible = open && place != null, onDismiss = { open = false }) {
            if (place != null) {
                val t = RouteInfo.typical(ui.data.trips, place.key)
                val d = done
                if (d == null) {
                    val zone = java.time.ZoneId.systemDefault()
                    val night = Rules.isNight(ui.nowMs, zone)
                    val band = TodayFacts.bandMinutes(ui.data.trips, place.key, night)
                    val facts = TodayFacts.pills(ui.nowMs, zone, est, t)
                    val rows = MethodRows.of(est, t, place.lat, place.lon, ui.nowMs, zone, band, night)
                    Column {
                        Txt("오늘은 어떻게 갈까요?", T.Title)
                        Txt(place.name + (t?.let { " · 평소 ${RouteInfo.kmLabel(it.km)} · ${it.min}분" } ?: ""), T.Sub, maxLines = 2)
                    }
                    if (facts.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { facts.forEach { Pill(it, Tone.Warm) } }
                    FadeScroll(Modifier.weight(1f, fill = false), bg = C.Surface, spacing = 12.dp) {
                        Alternative.entries.sortedBy { if (it == Alternative.SELF) 0 else 1 }.forEach { alt ->
                            RadioRow(alt.label, selected = pick == alt, onClick = { pick = alt }, sub = rows[alt],
                                onInfo = if (alt == Alternative.DEMAND_BUS) ({ guide = true }) else null)
                        }
                    }
                    PrimaryButton(if (pick == Alternative.SELF) "직접 운전할게요" else "이렇게 갈게요", onClick = {
                        pick?.let { c.chooseToday(place.key, place.name, it); done = it }
                    }, Modifier.fillMaxWidth(), enabled = pick != null)
                } else {
                    Pill("오늘 이동에 기록했어요", Tone.Primary)
                    Txt(goText(d), T.Title)
                    val seoulNow = TodayCompare.taxiRegionIsSeoul(est, place.lat, place.lon)
                    val steps = MoveFlow.steps(d, seoulNow)
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        steps.forEachIndexed { i, s -> StepRow(if (steps.size > 1) i + 1 else null, s) }
                    }
                    msg?.let { Txt(it, T.Body, color = C.Warm) }
                    val num = MoveFlow.dialNumber(d, seoulNow)
                    if (num != null) PrimaryButton(MoveFlow.dialLabel(d, seoulNow) ?: "전화 앱 열기", {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            .onFailure { msg = "전화 앱을 열 수 없어요" }
                    }, Modifier.fillMaxWidth()) else PrimaryButton("확인", { open = false }, Modifier.fillMaxWidth())
                    QuietButton("다른 방법 보기", { done = null }, Modifier.fillMaxWidth())
                }
            }
        }

        Sheet(visible = guide, onDismiss = { guide = false }) {
            Txt("셔클이 뭐예요?", T.Title)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Txt("부르면 가까운 정류장으로 오는 수요응답 버스예요.", T.Body)
                Txt("앱이나 전화로 부를 수 있고, 지역마다 운영 여부·이름·요금이 달라요.", T.Body, color = C.Dim)
            }
            PrimaryButton("확인", { guide = false }, Modifier.fillMaxWidth())
        }
    }
}

private fun goText(alt: Alternative) = when (alt) {
    Alternative.FAMILY -> "가족이 데려다줘요"
    Alternative.TAXI -> "택시로 가요"
    Alternative.TRANSIT -> "버스·지하철로 가요"
    Alternative.DEMAND_BUS -> "셔클로 가요"
    Alternative.SELF -> "직접 운전해서 가요"
}

@Composable
internal fun EmptyNote(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
        Txt(text, T.Head, color = C.Dim)
    }
}

/** 넓으면 2열, 좁으면 1열로 놓는 단골 길 목록. 줄은 위에서 아래로 스크롤되고 더 있으면 끝에 힌트가 보인다. */
@Composable
internal fun PlaceGrid(places: List<PlaceCount>, modifier: Modifier = Modifier, row: @Composable (PlaceCount, Modifier) -> Unit) {
    BoxWithConstraints(modifier) {
        val cols = if (maxWidth >= 900.dp) 2 else 1
        FadeScroll(Modifier.fillMaxSize(), spacing = 12.dp, contentPadding = PaddingValues(bottom = 56.dp)) {
            places.chunked(cols).forEach { r ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    r.forEach { p -> Box(Modifier.weight(1f).fillMaxHeight()) { row(p, Modifier.fillMaxHeight()) } }
                    repeat(cols - r.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
