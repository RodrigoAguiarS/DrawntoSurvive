package com.rodrigo.drawntosurvive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigo.drawntosurvive.data.*
import com.rodrigo.drawntosurvive.game.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app:Application):AndroidViewModel(app){
    private val repository=ProgressRepository(app);val progress=repository.progress.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),PlayerProgress())
    var engine:GameEngine?=null;private set
    fun newEngine(input:TouchController):GameEngine=GameEngine(input){viewModelScope.launch{repository.record(it)}}.also{engine=it}
}
