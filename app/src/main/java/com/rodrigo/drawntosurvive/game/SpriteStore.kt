package com.rodrigo.drawntosurvive.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

class SpriteStore private constructor(context: Context) {
    private val assets=context.assets
    val background=load("images/cenario.png")
    val gun=load("images/Sprites/gun.png"); val bullet=load("images/Sprites/bullet.png")
    val deathFrames=(1..7).mapNotNull{load("images/Sprites/Death FX/deathFX ($it).png")}
    val jumpEffectFrames=(1..8).mapNotNull{load("images/Sprites/Jump FX/jumpFX ($it).png")}
    private val hero=loadCharacter("Hero"); private val monster=loadCharacter("Monster")
    private val skeleton=loadCharacter("Skeleton"); private val base=loadCharacter("Base Character")
    private fun load(path:String):Bitmap?=try{assets.open(path).use{BitmapFactory.decodeStream(it)}}catch(_:Exception){null}
    private fun loadCharacter(folder:String):Map<String,List<Bitmap>> { val result=mutableMapOf<String,List<Bitmap>>();for(dir in listOf("down","down_right","right","up_right","up")){for(state in listOf("idle","jump")){val count=if(state=="idle")4 else 8;result["${state}_$dir"]=(1..count).mapNotNull{load("images/Sprites/$folder/${state}_$dir ($it).png")}}};return result }
    fun player(direction:Direction8,moving:Boolean,time:Float)=frame(hero,direction,if(moving)AnimationState.JUMP else AnimationState.IDLE,time)
    fun playerJump(direction:Direction8,progress:Float)=sequenceFrame(hero,direction,"jump",progress)
    fun enemy(type:EnemyType,direction:Direction8,state:AnimationState,time:Float)=frame(when(type){EnemyType.SLIME->monster;EnemyType.FAST->base;EnemyType.SKELETON->skeleton},direction,state,time)
    private fun character(type:EnemyType)=when(type){EnemyType.SLIME->monster;EnemyType.FAST->base;EnemyType.SKELETON->skeleton}
    private fun directionKey(direction:Direction8)=when(direction){Direction8.NORTH->"up";Direction8.NORTH_EAST,Direction8.NORTH_WEST->"up_right";Direction8.EAST,Direction8.WEST->"right";Direction8.SOUTH_EAST,Direction8.SOUTH_WEST->"down_right";Direction8.SOUTH->"down"}
    private fun flipsLeft(direction:Direction8)=direction in setOf(Direction8.WEST,Direction8.NORTH_WEST,Direction8.SOUTH_WEST)
    private fun sequenceFrame(map:Map<String,List<Bitmap>>,direction:Direction8,state:String,progress:Float):Pair<Bitmap?,Boolean>{val frames=map["${state}_${directionKey(direction)}"].orEmpty();val index=(progress.coerceIn(0f,.999f)*frames.size).toInt();return frames.getOrNull(index) to flipsLeft(direction)}
    private fun frame(map:Map<String,List<Bitmap>>,direction:Direction8,state:AnimationState,time:Float):Pair<Bitmap?,Boolean>{val assetState=if(state==AnimationState.JUMP)"jump" else "idle";val frames=map["${assetState}_${directionKey(direction)}"].orEmpty();return (frames.getOrNull((time*(if(state==AnimationState.JUMP)9 else 5)).toInt().coerceAtLeast(0)%frames.size.coerceAtLeast(1)) to flipsLeft(direction))}
    companion object {@Volatile private var instance:SpriteStore?=null;fun get(context:Context):SpriteStore=instance?:synchronized(this){instance?:SpriteStore(context.applicationContext).also{instance=it}}}
}
