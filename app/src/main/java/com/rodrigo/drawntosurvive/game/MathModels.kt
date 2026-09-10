package com.rodrigo.drawntosurvive.game

import kotlin.math.*

data class Vector2(var x: Float = 0f, var y: Float = 0f) {
    fun lengthSquared() = x * x + y * y
    fun normalized(): Vector2 {
        val length = sqrt(lengthSquared())
        return if (length > 0.0001f) Vector2(x / length, y / length) else Vector2()
    }
    operator fun plus(other: Vector2) = Vector2(x + other.x, y + other.y)
    operator fun minus(other: Vector2) = Vector2(x - other.x, y - other.y)
    operator fun times(scale: Float) = Vector2(x * scale, y * scale)
}

enum class Direction8 { NORTH, NORTH_EAST, EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, WEST, NORTH_WEST;
    companion object {
        fun from(vector: Vector2): Direction8 {
            if (vector.lengthSquared() < 0.0001f) return SOUTH
            var degrees = Math.toDegrees(atan2(vector.y.toDouble(), vector.x.toDouble())).toFloat()
            if (degrees < 0) degrees += 360f
            return when {
                degrees < 22.5f || degrees >= 337.5f -> EAST
                degrees < 67.5f -> SOUTH_EAST
                degrees < 112.5f -> SOUTH
                degrees < 157.5f -> SOUTH_WEST
                degrees < 202.5f -> WEST
                degrees < 247.5f -> NORTH_WEST
                degrees < 292.5f -> NORTH
                else -> NORTH_EAST
            }
        }
    }
}

object GameMath {
    fun circlesCollide(a: Vector2, ar: Float, b: Vector2, br: Float): Boolean {
        val dx = a.x - b.x; val dy = a.y - b.y; val sum = ar + br
        return dx * dx + dy * dy <= sum * sum
    }
    fun segmentHitsCircle(start:Vector2,end:Vector2,center:Vector2,radius:Float):Boolean {
        val segment=end-start;val lengthSquared=segment.lengthSquared()
        if(lengthSquared<0.0001f)return circlesCollide(start,0f,center,radius)
        val toCenter=center-start
        val t=((toCenter.x*segment.x+toCenter.y*segment.y)/lengthSquared).coerceIn(0f,1f)
        val closest=start+segment*t
        return circlesCollide(closest,0f,center,radius)
    }
    fun directionFromDegrees(degrees: Float): Vector2 {
        val r = Math.toRadians(degrees.toDouble())
        return Vector2(cos(r).toFloat(), sin(r).toFloat())
    }
    fun spreadAngles(count: Int, step: Float = 10f): List<Float> {
        if (count <= 1) return listOf(0f)
        val start = -step * (count - 1) / 2f
        return List(count) { start + it * step }
    }
    fun radialDirections(count: Int) = List(count.coerceAtLeast(1)) { directionFromDegrees(it * 360f / count.coerceAtLeast(1)) }
    fun xpRequired(level: Int) = GameConfig.XP_BASE_REQUIRED + (level - 1).coerceAtLeast(0) * 12
    fun coins(kills: Int, victory: Boolean) = kills / 5 + if (victory) GameConfig.VICTORY_BONUS_COINS else 0
    fun spawnInterval(elapsed: Float): Float {
        val p = (elapsed / GameConfig.MATCH_DURATION_SECONDS).coerceIn(0f, 1f)
        return GameConfig.INITIAL_SPAWN_INTERVAL + (GameConfig.MIN_SPAWN_INTERVAL - GameConfig.INITIAL_SPAWN_INTERVAL) * p
    }
    fun rotate(v: Vector2, degrees: Float): Vector2 {
        val r = Math.toRadians(degrees.toDouble()); val c = cos(r).toFloat(); val s = sin(r).toFloat()
        return Vector2(v.x * c - v.y * s, v.x * s + v.y * c)
    }
}
