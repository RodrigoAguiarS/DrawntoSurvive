package com.rodrigo.drawntosurvive.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rodrigo.drawntosurvive.MainViewModel
import com.rodrigo.drawntosurvive.data.PlayerProgress
import com.rodrigo.drawntosurvive.game.*

private enum class Screen{MENU,GAME}
@Composable fun DrawnToSurviveApp(vm:MainViewModel){var screen by remember{mutableStateOf(Screen.MENU)};var runKey by remember{mutableIntStateOf(0)};val progress by vm.progress.collectAsState();when(screen){Screen.MENU->MainMenu(progress){runKey++;screen=Screen.GAME};Screen.GAME->GameScreen(vm,runKey){screen=Screen.MENU}}}
@Composable private fun MainMenu(progress:PlayerProgress,onPlay:()->Unit){Box(Modifier.fillMaxSize().background(Color(0xFF17201D)),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("DRAWN TO SURVIVE",style=MaterialTheme.typography.headlineLarge,color=Color(0xFFFFB74D));Spacer(Modifier.height(18.dp));Text("Twin-stick arena survivor",color=Color.White);Spacer(Modifier.height(28.dp));Button(onClick=onPlay){Text("JOGAR")};Spacer(Modifier.height(30.dp));Text("Moedas: ${progress.coins}",color=Color.White);Text("Abates totais: ${progress.totalKills}",color=Color.LightGray);Text("Melhor tempo: ${formatTime(progress.bestTimeSeconds.toFloat())}",color=Color.LightGray)}}}
@Composable private fun GameScreen(vm:MainViewModel,key:Int,onMenu:()->Unit){val controls=remember(key){TouchController()};val engine=remember(key){vm.newEngine(controls)};val view=remember(key){GameView(vm.getApplication(),engine,controls)};val ui by engine.ui.collectAsState();BackHandler{if(ui.gameState==GameState.RUNNING)engine.command(GameCommand.Pause)else onMenu()};Box(Modifier.fillMaxSize()){AndroidView(factory={view},modifier=Modifier.fillMaxSize());GameHud(ui){engine.command(GameCommand.Pause)};when(ui.gameState){GameState.LEVEL_UP->UpgradeOverlay(ui.upgradeChoices){engine.command(GameCommand.SelectUpgrade(it))};GameState.PAUSED->PauseOverlay({engine.command(GameCommand.Resume)},onMenu);GameState.GAME_OVER,GameState.VICTORY->ResultOverlay(ui,{engine.requestRestart()},onMenu);else->{}}}}
@Composable private fun GameHud(ui:GameUiState,onPause:()->Unit){Column(Modifier.fillMaxWidth().padding(12.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text("HP ${ui.hp.toInt()}/${ui.maxHp.toInt()}",color=Color.White);Spacer(Modifier.width(8.dp));LinearProgressIndicator({(ui.hp/ui.maxHp).coerceIn(0f,1f)},Modifier.weight(1f),color=Color.Red);Spacer(Modifier.width(12.dp));Button(onClick=onPause,colors=ButtonDefaults.buttonColors(containerColor=Color(0xDD26332F)),contentPadding=PaddingValues(horizontal=18.dp,vertical=10.dp)){Text("Ⅱ  PAUSAR",color=Color.White)}};Row{Text("LV ${ui.level}",color=Color.White);Spacer(Modifier.width(10.dp));LinearProgressIndicator({ui.experience.toFloat()/ui.experienceRequired.coerceAtLeast(1)},Modifier.weight(1f),color=Color(0xFF43E5A9));Spacer(Modifier.width(10.dp));Text(formatTime(ui.elapsedTime),color=Color.White)};Text("Abates: ${ui.kills}",color=Color.White)}}
@Composable private fun UpgradeOverlay(choices:List<UpgradeUiModel>,select:(String)->Unit){Overlay{Text("LEVEL UP!",style=MaterialTheme.typography.headlineMedium,color=Color(0xFFFFB74D));Text("Escolha uma melhoria",color=Color.White);choices.forEach{Card(onClick={select(it.id)},Modifier.fillMaxWidth().padding(vertical=5.dp)){Column(Modifier.padding(15.dp)){Text(it.name);Text(it.description);Text("Nível ${it.level}",style=MaterialTheme.typography.labelSmall)}}}}}
@Composable private fun PauseOverlay(resume:()->Unit,menu:()->Unit){Overlay{Text("PAUSADO",style=MaterialTheme.typography.headlineMedium,color=Color.White);Button(onClick=resume){Text("CONTINUAR")};OutlinedButton(onClick=menu){Text("VOLTAR AO MENU")}}}
@Composable private fun ResultOverlay(ui:GameUiState,replay:()->Unit,menu:()->Unit){Overlay{Text(if(ui.gameState==GameState.VICTORY)"VOCÊ SOBREVIVEU!" else "GAME OVER",style=MaterialTheme.typography.headlineMedium,color=if(ui.gameState==GameState.VICTORY)Color(0xFF43E5A9)else Color(0xFFFF6B6B));Text("Tempo: ${formatTime(ui.elapsedTime)}",color=Color.White);Text("Level: ${ui.level}   Abates: ${ui.kills}",color=Color.White);Text("Moedas ganhas: ${ui.coinsEarned}",color=Color(0xFFFFB74D));Button(onClick=replay){Text("JOGAR NOVAMENTE")};OutlinedButton(onClick=menu){Text("MENU")}}}
@Composable private fun Overlay(content:@Composable ColumnScope.()->Unit){Box(Modifier.fillMaxSize().background(Color(0xCC101614)),contentAlignment=Alignment.Center){Column(Modifier.fillMaxWidth(.88f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp),content=content)}}
private fun formatTime(seconds:Float):String{val s=seconds.toInt().coerceAtLeast(0);return "%02d:%02d".format(s/60,s%60)}
