package com.rodrigo.drawntosurvive.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.rodrigo.drawntosurvive.MainViewModel
import com.rodrigo.drawntosurvive.data.PlayerProgress
import com.rodrigo.drawntosurvive.game.*

private enum class Screen { INTRO, MENU, GAME }

@Composable
fun DrawnToSurviveApp(vm: MainViewModel) {
    var screen by remember { mutableStateOf(Screen.INTRO) };
    var runKey by remember { mutableIntStateOf(0) };
    val progress by vm.progress.collectAsState(); when (screen) {
        Screen.INTRO -> IntroScreen {
            screen = Screen.MENU
        }; Screen.MENU -> MainMenu(progress) {
            runKey++; screen = Screen.GAME
        }; Screen.GAME -> GameScreen(vm, runKey) { screen = Screen.MENU }
    }
}

@Composable
internal fun GameLogo(modifier: Modifier = Modifier, progress: Float = 1f) {
    val scale = when {
        progress < .7f -> .85f + (progress / .7f) * .2f; else -> 1.05f - ((progress - .7f) / .3f) * .05f
    }
    Column(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "DRAWN",
            color = Color(0xFFF5F2E9),
            fontSize = 42.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 38.sp,
            letterSpacing = 1.sp
        )
        Text(
            "TO",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 16.sp
        )
        Text(
            "SURVIVE",
            color = Color(0xFFE34A4F),
            fontSize = 45.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 43.sp,
            letterSpacing = .5.sp
        )
    }
}

@Composable
private fun MainMenu(progress: PlayerProgress, onPlay: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF194C31), Color(0xFF092A1B)))),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.fillMaxWidth(.88f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GameLogo(); Spacer(
                Modifier.height(
                    8.dp
                )
            ); Text(
                "SOBREVIVA O MÁXIMO QUE PUDER",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = .6.sp
            )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .shadow(if (pressed) 14.dp else 6.dp, RoundedCornerShape(12.dp))
                        .graphicsLayer {
                            scaleX = if (pressed) .96f else 1f; scaleY = if (pressed) .96f else 1f
                        }
                        .background(Color(0xFF55BE48), RoundedCornerShape(12.dp))
                        .border(
                            if (pressed) 3.dp else 2.dp,
                            if (pressed) Color(0xFFFFE86B) else Color(0xFF102B17),
                            RoundedCornerShape(12.dp)
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(onPress = {
                                pressed = true;
                                val released = tryAwaitRelease(); pressed =
                                false; if (released) onPlay()
                            })
                        }
                        .padding(horizontal = 54.dp, vertical = 17.dp)
                ) {
                    Text(
                        "▶  JOGAR",
                        color = Color.White,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.height(20.dp)); Text(
                "ESTATÍSTICAS",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            ); Spacer(Modifier.height(6.dp)); Text(
                "Moedas: ${progress.coins}  •  Abates: ${progress.totalKills}  •  Melhor: ${
                    formatTime(
                        progress.bestTimeSeconds.toFloat()
                    )
                }", color = Color(0xFFD5E3DC), fontSize = 12.sp, textAlign = TextAlign.Center
            )
            }
        }
    }
}

@Composable
private fun GameScreen(vm: MainViewModel, key: Int, onMenu: () -> Unit) {
    val controls = remember(key) { TouchController() };
    val engine = remember(key) { vm.newEngine(controls) };
    val view = remember(key) { GameView(vm.getApplication(), engine, controls) };
    val ui by engine.ui.collectAsState(); BackHandler {
        if (ui.gameState == GameState.RUNNING) engine.command(
            GameCommand.Pause
        ) else onMenu()
    }; Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { view },
            modifier = Modifier.fillMaxSize()
        ); GameHud(ui) { engine.command(GameCommand.Pause) }; when (ui.gameState) {
        GameState.LEVEL_UP -> UpgradeOverlay(ui.upgradeChoices) {
            engine.command(
                GameCommand.SelectUpgrade(
                    it
                )
            )
        }; GameState.PAUSED -> PauseOverlay(
            { engine.command(GameCommand.Resume) },
            onMenu
        ); GameState.GAME_OVER, GameState.VICTORY -> ResultOverlay(
            ui,
            { engine.requestRestart() },
            onMenu
        ); else -> {}
    }
    }
}

@Composable
private fun GameHud(ui: GameUiState, onPause: () -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .safeDrawingPadding()
            .padding(horizontal = 6.dp, vertical = 5.dp)
    ) {
        val compact = maxWidth < 700.dp
        Row(
            Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerStatusPanel(
                ui,
                Modifier.width(if (compact) 190.dp else 230.dp)
            )
            MatchTimer(
                ui.elapsedTime,
                Modifier.width(if (compact) 64.dp else 74.dp)
            )
            KillCounter(ui.kills)
            PauseButton(onPause)
        }
    }
}

@Composable
private fun PlayerStatusPanel(ui: GameUiState, modifier: Modifier = Modifier) {
    val healthProgress = if (ui.maxHp > 0f) (ui.hp / ui.maxHp).coerceIn(0f, 1f) else 0f
    val xpProgress =
        (ui.experience.toFloat() / ui.experienceRequired.coerceAtLeast(1)).coerceIn(0f, 1f)
    val critical = healthProgress <= .25f
    HudPanel(modifier.border(1.dp, if (critical) HudDanger else HudBorder, HudShape)) {
        StatusBar(
            "♥ VIDA",
            "${ui.hp.toInt().coerceAtLeast(0)}/${ui.maxHp.toInt().coerceAtLeast(0)}",
            healthProgress,
            if (critical) HudCritical else HudHealth
        )
        Spacer(Modifier.height(4.dp))
        StatusBar(
            "NV ${ui.level}",
            "${ui.experience}/${ui.experienceRequired} XP",
            xpProgress,
            HudExperience,
            small = true
        )
    }
}

@Composable
private fun StatusBar(
    label: String,
    value: String,
    progress: Float,
    color: Color,
    small: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = color,
            fontSize = if (small) 8.sp else 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = .3.sp,
            maxLines = 1
        )
        Text(
            value,
            color = HudText,
            fontSize = if (small) 8.sp else 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
    Spacer(Modifier.height(2.dp))
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(if (small) 4.dp else 5.dp)
            .clip(RoundedCornerShape(6.dp)),
        color = color,
        trackColor = HudTrack
    )
}

@Composable
private fun MatchTimer(elapsed: Float, modifier: Modifier = Modifier) {
    HudPanel(modifier, horizontal = 6.dp, vertical = 5.dp) {
        Text(
            "TEMPO",
            Modifier.align(Alignment.CenterHorizontally),
            color = HudMuted,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = .7.sp,
            maxLines = 1
        )
        Text(
            formatTime(elapsed),
            Modifier.align(Alignment.CenterHorizontally),
            color = HudGold,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

@Composable
private fun KillCounter(kills: Int, modifier: Modifier = Modifier) {
    HudPanel(modifier.widthIn(min = 48.dp), horizontal = 6.dp, vertical = 5.dp) {
        Text(
            "ABATES",
            Modifier.align(Alignment.CenterHorizontally),
            color = HudMuted,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = .4.sp,
            maxLines = 1
        )
        Text(
            kills.coerceAtLeast(0).toString(),
            Modifier.align(Alignment.CenterHorizontally),
            color = HudText,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

@Composable
private fun PauseButton(onPause: () -> Unit) {
    Button(
        onClick = onPause,
        modifier = Modifier
            .size(44.dp)
            .semantics { contentDescription = "Pausar partida" },
        shape = HudShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xE63A4742),
            contentColor = HudText
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text("Ⅱ", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
private fun HudPanel(
    modifier: Modifier = Modifier,
    horizontal: androidx.compose.ui.unit.Dp = 8.dp,
    vertical: androidx.compose.ui.unit.Dp = 6.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .background(HudSurface, HudShape)
            .border(1.dp, HudBorder, HudShape)
            .padding(horizontal, vertical), content = content
    )
}

private val HudShape = RoundedCornerShape(8.dp)
private val HudSurface = Color(0xD916211E)
private val HudBorder = Color(0x665E756C)
private val HudTrack = Color(0xB3293531)
private val HudText = Color(0xFFF4F7F5)
private val HudMuted = Color(0xFFB8C5C0)
private val HudHealth = Color(0xFFEF5350)
private val HudCritical = Color(0xFFFF3D4D)
private val HudDanger = Color(0xCCFF3D4D)
private val HudExperience = Color(0xFF43E5A9)
private val HudGold = Color(0xFFFFC45C)
@Composable
private fun UpgradeOverlay(choices: List<UpgradeUiModel>, select: (String) -> Unit) {
    Overlay {
        HudPanel(
            Modifier
                .fillMaxWidth()
                .border(1.dp, HudGold.copy(alpha = .55f), HudShape),
            horizontal = 14.dp,
            vertical = 10.dp
        ) {
            Text(
                "LEVEL UP!",
                Modifier.align(Alignment.CenterHorizontally),
                color = HudGold,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Escolha uma melhoria",
                Modifier.align(Alignment.CenterHorizontally),
                color = HudMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .4.sp,
                maxLines = 1
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            choices.forEach {
                UpgradeChoiceCard(
                    choice = it,
                    onClick = { select(it.id) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun UpgradeChoiceCard(
    choice: UpgradeUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 118.dp),
        shape = HudShape,
        colors = CardDefaults.cardColors(containerColor = HudSurface),
        border = BorderStroke(1.dp, HudBorder)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 9.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    choice.name.uppercase(),
                    modifier = Modifier.weight(1f),
                    color = HudText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = .3.sp,
                    maxLines = 2
                )
                Spacer(Modifier.width(6.dp))
                UpgradeLevelChip(choice.level)
            }
            Spacer(Modifier.height(7.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(HudBorder)
            )
            Spacer(Modifier.height(7.dp))
            Text(
                choice.description,
                color = HudMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 13.sp
            )
        }
    }
}

@Composable
private fun UpgradeLevelChip(level: Int) {
    Box(
        Modifier
            .background(HudExperience.copy(alpha = .18f), RoundedCornerShape(7.dp))
            .border(1.dp, HudExperience.copy(alpha = .55f), RoundedCornerShape(7.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "NV $level",
            color = HudExperience,
            fontSize = 8.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

@Composable
private fun PauseOverlay(resume: () -> Unit, menu: () -> Unit) {
    Overlay {
        Text(
            "PAUSADO",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        ); Button(onClick = resume) { Text("CONTINUAR") }; OutlinedButton(onClick = menu) { Text("VOLTAR AO MENU") }
    }
}

@Composable
private fun ResultOverlay(ui: GameUiState, replay: () -> Unit, menu: () -> Unit) {
    val victory = ui.gameState == GameState.VICTORY
    val title = if (victory) "VOCÊ SOBREVIVEU!" else "VOCÊ CAIU..."
    val titleColor = if (victory) HudExperience else HudCritical
    val storyLine1 = if (victory) {
        "Os rabiscos recuaram... por enquanto."
    } else {
        "Os rabiscos tomaram conta da página."
    }
    val storyLine2 = if (victory) {
        "Seu traço manteve a página viva até o amanhecer."
    } else {
        "Mas cada tentativa deixa um novo traço de esperança."
    }

    Overlay {
        HudPanel(
            Modifier
                .fillMaxWidth()
                .border(1.dp, titleColor.copy(alpha = .62f), HudShape),
            horizontal = 14.dp,
            vertical = 12.dp
        ) {
            Text(
                title,
                Modifier.align(Alignment.CenterHorizontally),
                color = titleColor,
                fontSize = 23.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                storyLine1,
                Modifier.align(Alignment.CenterHorizontally),
                color = HudText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                storyLine2,
                Modifier.align(Alignment.CenterHorizontally),
                color = HudMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }

        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ResultStatCard("TEMPO", formatTime(ui.elapsedTime), HudExperience, Modifier.weight(1f))
                ResultStatCard("LEVEL", ui.level.toString(), HudGold, Modifier.weight(1f))
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ResultStatCard("ABATES", ui.kills.toString(), HudText, Modifier.weight(1f))
                ResultStatCard("MOEDAS", ui.coinsEarned.toString(), HudGold, Modifier.weight(1f))
            }
        }

        UpgradeSummaryPanel(ui.collectedUpgrades)

        if (victory) {
            HudPanel(
                Modifier.fillMaxWidth(),
                horizontal = 14.dp,
                vertical = 10.dp
            ) {
                Text(
                    "CRÉDITOS",
                    Modifier.align(Alignment.CenterHorizontally),
                    color = HudGold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = .8.sp
                )
                Spacer(Modifier.height(6.dp))
                CreditLine("Criado por", "Rodrigo")
                CreditLine("Desenvolvido com", "Kotlin + Android")
                CreditLine("", "Obrigado por jogar!")
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = replay) { Text(if (victory) "JOGAR NOVAMENTE" else "TENTAR NOVAMENTE") }
            OutlinedButton(onClick = menu) { Text("MENU") }
        }
    }
}

@Composable
private fun ResultStatCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    HudPanel(modifier, horizontal = 10.dp, vertical = 8.dp) {
        Text(
            label,
            Modifier.align(Alignment.CenterHorizontally),
            color = HudMuted,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = .5.sp,
            maxLines = 1
        )
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            Modifier.align(Alignment.CenterHorizontally),
            color = accent,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

@Composable
private fun UpgradeSummaryPanel(upgrades: List<UpgradeSummaryUiModel>) {
    HudPanel(
        Modifier.fillMaxWidth(),
        horizontal = 14.dp,
        vertical = 10.dp
    ) {
        Text(
            "MELHORIAS DA RUN",
            Modifier.align(Alignment.CenterHorizontally),
            color = HudGold,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = .8.sp
        )
        Spacer(Modifier.height(6.dp))
        if (upgrades.isEmpty()) {
            Text(
                "Nenhuma melhoria coletada",
                Modifier.align(Alignment.CenterHorizontally),
                color = HudMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        } else {
            val visibleUpgrades = upgrades.sortedByDescending { it.level }.take(8)
            visibleUpgrades.forEach { UpgradeSummaryRow(it) }
            val hiddenCount = upgrades.size - visibleUpgrades.size
            if (hiddenCount > 0) {
                Spacer(Modifier.height(3.dp))
                Text(
                    "+$hiddenCount melhorias adicionais",
                    Modifier.align(Alignment.CenterHorizontally),
                    color = HudMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun UpgradeSummaryRow(upgrade: UpgradeSummaryUiModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            upgrade.name,
            modifier = Modifier.weight(1f),
            color = HudText,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .background(HudExperience.copy(alpha = .16f), RoundedCornerShape(7.dp))
                .border(1.dp, HudExperience.copy(alpha = .48f), RoundedCornerShape(7.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "NV ${upgrade.level}",
                color = HudExperience,
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CreditLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (label.isBlank()) Arrangement.Center else Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (label.isNotBlank()) {
            Text(
                label,
                color = HudMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            value,
            color = HudText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = if (label.isBlank()) TextAlign.Center else TextAlign.End
        )
    }
}

@Composable
private fun Overlay(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC101614)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth(.88f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

private fun formatTime(seconds: Float): String {
    val s = seconds.toInt().coerceAtLeast(0); return "%02d:%02d".format(s / 60, s % 60)
}
