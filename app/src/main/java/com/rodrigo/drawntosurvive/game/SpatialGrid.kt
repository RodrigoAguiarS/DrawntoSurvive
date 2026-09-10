package com.rodrigo.drawntosurvive.game

import kotlin.math.ceil
import kotlin.math.floor

internal class SpatialGrid(private val cellSize: Float) {
    @PublishedApi
    internal var columns = 1
    private var rows = 1
    private var originX = 0f
    private var originY = 0f
    @PublishedApi
    internal var heads = IntArray(1) { -1 }
    @PublishedApi
    internal var next = IntArray(0)

    fun rebuild(enemies: List<Enemy>, width: Float, height: Float, margin: Float) {
        originX = -margin
        originY = -margin
        columns = ceil((width + margin * 2f) / cellSize).toInt().coerceAtLeast(1)
        rows = ceil((height + margin * 2f) / cellSize).toInt().coerceAtLeast(1)
        val cellCount = columns * rows
        if (heads.size != cellCount) heads = IntArray(cellCount)
        heads.fill(-1)
        if (next.size < enemies.size) next = IntArray(enemies.size)
        next.fill(-1, 0, enemies.size)

        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (!enemy.active) continue
            val cell = cellIndex(enemy.position.x, enemy.position.y)
            next[index] = heads[cell]
            heads[cell] = index
        }
    }

    inline fun forEachNearby(x: Float, y: Float, radius: Float, action: (Int) -> Unit) {
        forEachInBounds(x - radius, y - radius, x + radius, y + radius, action)
    }

    inline fun forEachInBounds(
        minX: Float,
        minY: Float,
        maxX: Float,
        maxY: Float,
        action: (Int) -> Unit
    ) {
        val firstColumn = column(minX)
        val lastColumn = column(maxX)
        val firstRow = row(minY)
        val lastRow = row(maxY)
        for (row in firstRow..lastRow) {
            for (column in firstColumn..lastColumn) {
                var index = heads[row * columns + column]
                while (index >= 0) {
                    action(index)
                    index = next[index]
                }
            }
        }
    }

    private fun cellIndex(x: Float, y: Float) = row(y) * columns + column(x)

    @PublishedApi
    internal fun column(x: Float) =
        floor((x - originX) / cellSize).toInt().coerceIn(0, columns - 1)

    @PublishedApi
    internal fun row(y: Float) =
        floor((y - originY) / cellSize).toInt().coerceIn(0, rows - 1)
}
