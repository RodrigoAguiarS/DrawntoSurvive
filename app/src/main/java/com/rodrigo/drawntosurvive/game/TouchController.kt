package com.rodrigo.drawntosurvive.game

import android.view.MotionEvent
import kotlin.math.sqrt

class VirtualJoystick(private val deadZone: Float = 0.16f) {
    var pointerId = -1;
    var center = Vector2();
    var knob = Vector2();
    var radius = 80f
    fun claim(id: Int, x: Float, y: Float) {
        pointerId = id; center = Vector2(x, y); knob = Vector2(x, y)
    }

    fun move(x: Float, y: Float): Vector2 {
        var dx = x - center.x;
        var dy = y - center.y;
        val length = sqrt(dx * dx + dy * dy)
        if (length > radius) {
            dx *= radius / length; dy *= radius / length
        }
        knob = Vector2(center.x + dx, center.y + dy)
        return if (length / radius >= deadZone) Vector2(dx, dy).normalized() else Vector2()
    }

    fun release() {
        pointerId = -1; knob = center.copy()
    }
}

class TouchController {
    val moveJoystick = VirtualJoystick()
    private var firePointerId = -1;
    private var specialPointerId = -1
    @Volatile
    private var move = Vector2()
    @Volatile
    private var fireRequested = false
    @Volatile
    var specialRequested = false; private set
    var width = 1;
    var height = 1;
    var density = 1f
    val defaultMoveCenter get() = Vector2(95f * density, height - 105f * density)
    val fireCenter get() = Vector2(width - 100f * density, height - 105f * density)
    val specialCenter get() = Vector2(width - 100f * density, height - 225f * density)
    fun configure(w: Int, h: Int, d: Float) {
        width = w; height = h; density = d; moveJoystick.radius =
            68f * d; if (moveJoystick.pointerId < 0) {
            moveJoystick.center = defaultMoveCenter; moveJoystick.knob = defaultMoveCenter
        }
    }

    fun snapshot() = InputSnapshot(move.copy())
    fun consumeFire(): Boolean {
        val value = fireRequested; fireRequested = false; return value
    }

    fun isFireHeld(): Boolean = firePointerId >= 0
    fun consumeSpecial(): Boolean {
        val value = specialRequested; specialRequested = false; return value
    }

    internal fun requestSpecial() {
        specialRequested = true
    }

    fun onTouch(event: MotionEvent): Boolean {
        val actionIndex = event.actionIndex;
        val action = event.actionMasked
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> claim(
                event.getPointerId(
                    actionIndex
                ), event.getX(actionIndex), event.getY(actionIndex)
            )

            MotionEvent.ACTION_MOVE -> for (i in 0 until event.pointerCount) update(
                event.getPointerId(
                    i
                ), event.getX(i), event.getY(i)
            )

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> release(
                event.getPointerId(
                    actionIndex
                )
            )

            MotionEvent.ACTION_CANCEL -> cancel()
        }; return true
    }

    private fun claim(id: Int, x: Float, y: Float) {
        val sc = specialCenter;
        val specialRadius = 48f * density
        if (isInside(x, y, sc, specialRadius) && specialPointerId < 0) {
            specialPointerId = id; specialRequested = true; return
        }
        val fc = fireCenter;
        val fireRadius = 58f * density
        if (isInside(x, y, fc, fireRadius) && firePointerId < 0) {
            firePointerId = id; fireRequested = true; return
        }
        if (x < width / 2f && moveJoystick.pointerId < 0) {
            moveJoystick.claim(id, x, y); move = Vector2()
        }
    }

    private fun isInside(x: Float, y: Float, center: Vector2, radius: Float): Boolean {
        val dx = x - center.x;
        val dy = y - center.y; return dx * dx + dy * dy <= radius * radius
    }

    private fun update(id: Int, x: Float, y: Float) {
        if (id == moveJoystick.pointerId) move =
            moveJoystick.move(x, y); if (id == firePointerId && !isInside(
                x,
                y,
                fireCenter,
                72f * density
            )
        ) firePointerId = -1
    }

    private fun release(id: Int) {
        if (id == moveJoystick.pointerId) {
            moveJoystick.release(); move = Vector2()
        }; if (id == firePointerId) firePointerId =
            -1; if (id == specialPointerId) specialPointerId = -1
    }

    fun reset() {
        moveJoystick.release(); moveJoystick.center = defaultMoveCenter; moveJoystick.knob =
            defaultMoveCenter; move = Vector2(); firePointerId = -1; specialPointerId =
            -1; fireRequested = false; specialRequested = false
    }

    private fun cancel() = reset()
}
