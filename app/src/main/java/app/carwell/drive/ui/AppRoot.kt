package app.carwell.drive.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.carwell.drive.DriveApp
import app.carwell.drive.data.AppController
import app.carwell.drive.data.SimulatedVehicleSource
import app.carwell.drive.data.UiModel
import app.carwell.drive.domain.GearState
import app.carwell.drive.domain.SourceKind

/** 화면 위치. 레일에는 3곳(홈·오늘 이동·더 보기)만 두고, 기록·설정은 '더 보기' 안에 둔다. */
private enum class Dest { HOME, TODAY, MORE, HISTORY, SETTINGS }

private enum class Tab(val label: String, val home: Dest) {
    HOME("홈", Dest.HOME), TODAY("오늘 이동", Dest.TODAY), MORE("더 보기", Dest.MORE),
}

private fun Dest.tab(): Tab = when (this) {
    Dest.HOME -> Tab.HOME
    Dest.TODAY -> Tab.TODAY
    else -> Tab.MORE
}

@Composable
fun AppRoot(c: AppController) {
    val ui by c.ui.collectAsStateWithLifecycle()
    DriveTheme {
        Column(Modifier.fillMaxSize().background(C.Bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (ui.data.hasDemo) DemoBanner()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    ui.driving -> DrivingScreen()
                    else -> MainContent(ui, c)
                }
            }
            c.sim?.let { SimPanel(it, c) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                val label = if (c.source.kind == SourceKind.PLEOS) "차량 신호 · Pleos" else "차량 신호 · 시뮬레이터"
                val gearNote = if (ui.gear == GearState.UNKNOWN) " · 기어 신호 없음" else ""
                Txt(label + gearNote, T.Caption.copy(color = C.Dim.copy(alpha = .7f)), maxLines = 1)
            }
        }
    }
}

/** 얇고 조용한 한 줄. */
@Composable
private fun DemoBanner() {
    Row(
        Modifier.fillMaxWidth().background(C.Warm.copy(alpha = .12f)).heightIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(8.dp).background(C.Warm, CircleShape))
        Spacer(Modifier.width(10.dp))
        Txt("시연용 가상 데이터", T.Caption.copy(color = C.Warm), maxLines = 1)
    }
}

@Composable
private fun MainContent(ui: UiModel, c: AppController) {
    var dest by remember { mutableStateOf(Dest.HOME) }
    BackHandler(enabled = dest == Dest.HISTORY || dest == Dest.SETTINGS) { dest = Dest.MORE }
    Row(Modifier.fillMaxSize()) {
        Rail(selected = dest.tab(), onTab = { dest = it.home })
        Box(Modifier.width(1.dp).fillMaxHeight().background(C.Line))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (dest) {
                Dest.HOME -> HomeScreen(ui, c, onToday = { dest = Dest.TODAY })
                Dest.TODAY -> TodayScreen(ui, c)
                Dest.MORE -> MoreScreen(onHistory = { dest = Dest.HISTORY }, onSettings = { dest = Dest.SETTINGS })
                Dest.HISTORY -> HistoryScreen(ui, onBack = { dest = Dest.MORE })
                Dest.SETTINGS -> SettingsScreen(ui, c, onBack = { dest = Dest.MORE })
            }
        }
    }
}

/** 글자만 있는 슬림한 레일. 선택된 곳은 프라이머리 글자 + 옅은 바탕. */
@Composable
private fun Rail(selected: Tab, onTab: (Tab) -> Unit) {
    Column(
        Modifier.width(208.dp).fillMaxHeight().padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Tab.entries.forEach { t ->
            val on = t == selected
            Box(
                Modifier.fillMaxWidth().height(80.dp).clip(Shp.Row)
                    .background(if (on) C.Primary.copy(alpha = .16f) else Color.Transparent)
                    .clickable { onTab(t) },
                contentAlignment = Alignment.Center,
            ) {
                Txt(t.label, T.Head.copy(fontSize = 30.sp, color = if (on) C.Primary else C.Dim), maxLines = 1)
            }
        }
    }
}

/** 차량 신호가 없는 기기에서만 나오는 시연용 조작 줄. 주행 중(D) 화면이 비어도 항상 보인다. */
@Composable
private fun SimPanel(sim: SimulatedVehicleSource, c: AppController) {
    var dest by remember { mutableStateOf("병원") }
    var night by remember { mutableStateOf(false) }
    val ui by c.ui.collectAsStateWithLifecycle()
    val simClock = DriveApp.simClockRef
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF0A1016)).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        val h = Modifier.weight(1f)
        @Composable fun mini(text: String, on: Boolean = false, click: () -> Unit) {
            val shape = Shp.Row
            Box(
                h.heightIn(min = MinTouch).clip(shape).background(if (on) C.Primary else C.Surface2).clickable(onClick = click).padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Txt(text, T.Caption.copy(color = if (on) C.OnPrimary else C.Text, fontWeight = FontWeight.Bold), maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        mini("기어 P", ui.gear == GearState.P) { sim.setGear(GearState.P) }
        mini("기어 D", ui.gear == GearState.D) { sim.setGear(GearState.D) }
        mini("+1km") { sim.drive(1.0) }
        mini("+10km") { sim.drive(10.0) }
        mini("+35km") { sim.drive(35.0) }
        mini("방향지시등") { sim.blinkTurnSignal() }
        mini(if (night) "야간 22시 켬" else "야간 22시", night) { night = !night; simClock?.setNight(night) }
        OutlinedTextField(
            value = dest, onValueChange = { dest = it }, singleLine = true,
            textStyle = TextStyle(fontSize = 22.sp, color = C.Text),
            modifier = Modifier.weight(1.6f).heightIn(min = MinTouch),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Primary, unfocusedBorderColor = C.Dim),
        )
        mini("안내 시작") { sim.startGuidance(dest) }
    }
}
