package com.rodrigo.drawntosurvive.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

class SpriteStore private constructor(context: Context) {
    data class SpriteFrame(val bitmap: Bitmap?, val flip: Boolean)

    private class CharacterSprites(val frames: Array<Array<List<SpriteFrame>>>)

    private val assets = context.assets
    val background = load("images/cenario.png")
    val gun = load("images/Sprites/gun.png");
    val bullet = load("images/Sprites/bullet.png")
    val deathFrames = (1..7).mapNotNull { load("images/Sprites/Death FX/deathFX ($it).png") }
    val jumpEffectFrames = (1..8).mapNotNull { load("images/Sprites/Jump FX/jumpFX ($it).png") }
    private val hero = loadCharacter("Hero");
    private val monster = loadCharacter("Monster")
    private val skeleton = loadCharacter("Skeleton");
    private val base = loadCharacter("Base Character")
    private fun load(path: String): Bitmap? = try {
        assets.open(path).use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) {
        null
    }

    private fun loadCharacter(folder: String): CharacterSprites {
        val directionFolders = arrayOf("up", "up_right", "right", "down_right", "down")
        val states = arrayOf("idle", "jump")
        val sourceFrames = Array(states.size) { stateIndex ->
            Array(directionFolders.size) { directionIndex ->
                val count = if (stateIndex == IDLE_INDEX) 4 else 8
                (1..count).mapNotNull {
                    load("images/Sprites/$folder/${states[stateIndex]}_${directionFolders[directionIndex]} ($it).png")
                }
            }
        }
        val frames = Array(states.size) { stateIndex ->
            Array(Direction8.entries.size) { directionOrdinal ->
                val direction = Direction8.entries[directionOrdinal]
                sourceFrames[stateIndex][directionIndex(direction)].map {
                    SpriteFrame(it, flipsLeft(direction))
                }
            }
        }
        return CharacterSprites(frames)
    }

    fun player(direction: Direction8, moving: Boolean, time: Float) =
        frame(hero, direction, if (moving) AnimationState.JUMP else AnimationState.IDLE, time)

    fun playerJump(direction: Direction8, progress: Float) =
        jumpFrame(hero, direction, progress)

    fun enemy(type: EnemyType, direction: Direction8, state: AnimationState, time: Float) = frame(
        when (type) {
            EnemyType.SLIME -> monster; EnemyType.FAST -> base; EnemyType.SKELETON -> skeleton
        }, direction, state, time
    )

    private fun character(type: EnemyType) = when (type) {
        EnemyType.SLIME -> monster; EnemyType.FAST -> base; EnemyType.SKELETON -> skeleton
    }

    private fun directionIndex(direction: Direction8) = when (direction) {
        Direction8.NORTH -> 0
        Direction8.NORTH_EAST, Direction8.NORTH_WEST -> 1
        Direction8.EAST, Direction8.WEST -> 2
        Direction8.SOUTH_EAST, Direction8.SOUTH_WEST -> 3
        Direction8.SOUTH -> 4
    }

    private fun flipsLeft(direction: Direction8) = when (direction) {
        Direction8.WEST, Direction8.NORTH_WEST, Direction8.SOUTH_WEST -> true
        else -> false
    }

    private fun jumpFrame(
        sprites: CharacterSprites,
        direction: Direction8,
        progress: Float
    ): SpriteFrame {
        val frames = sprites.frames[JUMP_INDEX][direction.ordinal]
        val index = (progress.coerceIn(0f, .999f) * frames.size).toInt()
        return frames.getOrNull(index) ?: EMPTY_FRAME
    }

    private fun frame(
        sprites: CharacterSprites,
        direction: Direction8,
        state: AnimationState,
        time: Float
    ): SpriteFrame {
        val stateIndex = if (state == AnimationState.JUMP) JUMP_INDEX else IDLE_INDEX
        val frames = sprites.frames[stateIndex][direction.ordinal]
        val frameIndex =
            (time * (if (state == AnimationState.JUMP) 9 else 5)).toInt().coerceAtLeast(0) %
                frames.size.coerceAtLeast(1)
        return frames.getOrNull(frameIndex) ?: EMPTY_FRAME
    }

    companion object {
        private const val IDLE_INDEX = 0
        private const val JUMP_INDEX = 1
        private val EMPTY_FRAME = SpriteFrame(null, false)

        @Volatile
        private var instance: SpriteStore? = null;
        fun get(context: Context): SpriteStore = instance ?: synchronized(this) {
            instance ?: SpriteStore(context.applicationContext).also { instance = it }
        }
    }
}
