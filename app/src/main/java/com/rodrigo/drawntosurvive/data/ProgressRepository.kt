package com.rodrigo.drawntosurvive.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.max

private val Context.progressDataStore by preferencesDataStore("player_progress")
data class PlayerProgress(val coins:Int=0,val totalKills:Int=0,val bestTimeSeconds:Int=0)
class ProgressRepository(private val context:Context){
    private object Keys{val coins=intPreferencesKey("coins");val kills=intPreferencesKey("total_kills");val best=intPreferencesKey("best_time_seconds")}
    val progress:Flow<PlayerProgress> = context.progressDataStore.data.map{PlayerProgress(it[Keys.coins]?:0,it[Keys.kills]?:0,it[Keys.best]?:0)}
    suspend fun record(result: com.rodrigo.drawntosurvive.game.RunResult){context.progressDataStore.edit{it[Keys.coins]=(it[Keys.coins]?:0)+result.coins;it[Keys.kills]=(it[Keys.kills]?:0)+result.kills;it[Keys.best]=max(it[Keys.best]?:0,result.elapsedTime.toInt())}}
}
