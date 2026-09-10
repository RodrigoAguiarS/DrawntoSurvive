package com.rodrigo.drawntosurvive.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withTranslation
import com.rodrigo.drawntosurvive.game.*
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin

internal enum class IntroState { STUDIO, PLAYER_ENTER, ENEMIES_ENTER, SHOOT, TITLE, WAIT_FOR_TAP }

@Composable
internal fun IntroScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val sprites = remember(context) { SpriteStore.get(context) }
    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var previous = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now ->
                elapsed += (now - previous) / 1_000_000_000f
                previous = now
            }
        }
    }
    val state = introStateAt(elapsed)
    Box(Modifier
        .fillMaxSize()
        .background(Color.Black)
        .pointerInput(state) {
            detectTapGestures {
                if (state == IntroState.WAIT_FOR_TAP) onFinished() else elapsed = INTRO_END
            }
        }) {
        if (state == IntroState.STUDIO) StudioSplash(elapsed) else IntroScene(sprites, elapsed)
        if (state == IntroState.TITLE || state == IntroState.WAIT_FOR_TAP) {
            val titleProgress = ((elapsed - TITLE_START) / .55f).coerceIn(0f, 1f)
            GameLogo(Modifier
                .align(Alignment.Center)
                .alpha(titleProgress), titleProgress)
        }
        if (state == IntroState.WAIT_FOR_TAP) {
            val pulse = .45f + .55f * ((sin(elapsed * PI.toFloat() * 1.6f) + 1f) / 2f)
            androidx.compose.material3.Text(
                "TOQUE PARA CONTINUAR",
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
                    .alpha(pulse),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.4.sp
            )
        }
    }
}

@Composable
private fun StudioSplash(elapsed: Float) {
    val alpha = when {
        elapsed < .22f -> elapsed / .22f; elapsed > .58f -> (INTRO_STUDIO_END - elapsed) / .22f; else -> 1f
    }.coerceIn(0f, 1f)
    Column(
        Modifier
            .fillMaxSize()
            .alpha(alpha),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        androidx.compose.material3.Text(
            "RODRIGO GAMES",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.Text(
            "APRESENTA",
            color = Color(0xFFB8C5C0),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp
        )
    }
}

@Composable
private fun IntroScene(sprites: SpriteStore, elapsed: Float) {
    Canvas(Modifier.fillMaxSize()) {
        drawIntoCanvas { composeCanvas ->
            val c = composeCanvas.nativeCanvas;
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            drawCover(c, sprites.background, paint)
            val cx = size.width * .5f;
            val cy = size.height * .54f;
            val unit = density
            val playerProgress = ((elapsed - INTRO_STUDIO_END) / .55f).coerceIn(0f, 1f)
            if (elapsed >= ENEMIES_START) drawIntroEnemies(c, sprites, paint, cx, cy, unit, elapsed)
            val (playerBitmap, playerFlip) = sprites.player(Direction8.EAST, false, elapsed)
            drawBitmapCentered(
                c,
                playerBitmap,
                cx,
                cy,
                92f * unit * (.78f + .22f * playerProgress),
                paint,
                playerFlip,
                playerProgress
            )
            drawIntroWeapon(c, sprites.gun, paint, cx, cy, unit, elapsed)
        }
    }
}

private fun drawIntroEnemies(
    c: AndroidCanvas,
    sprites: SpriteStore,
    paint: Paint,
    cx: Float,
    cy: Float,
    d: Float,
    elapsed: Float
) {
    val progress = ((elapsed - ENEMIES_START) / .75f).coerceIn(0f, 1f)
    val targets = arrayOf(
        -2.8f to -1.25f,
        -.4f to -1.7f,
        2.7f to -1.15f,
        -2.9f to 1.1f,
        .15f to 1.65f,
        2.75f to 1.05f
    )
    val types = arrayOf(
        EnemyType.SLIME,
        EnemyType.SKELETON,
        EnemyType.FAST,
        EnemyType.FAST,
        EnemyType.SLIME,
        EnemyType.SKELETON
    )
    targets.forEachIndexed { index, target ->
        val tx = cx + target.first * 75f * d;
        val ty = cy + target.second * 64f * d
        val edgeX = if (target.first < 0) -80f * d else c.width + 80f * d
        val edgeY =
            if (kotlin.math.abs(target.first) < 1f) if (target.second < 0) -70f * d else c.height + 70f * d else ty
        val x = edgeX + (tx - edgeX) * progress;
        val y = edgeY + (ty - edgeY) * progress
        val (bitmap, flip) = sprites.enemy(
            types[index],
            Direction8.from(Vector2(cx - x, cy - y)),
            AnimationState.WALK,
            elapsed + index
        )
        drawBitmapCentered(c, bitmap, x, y, 72f * d, paint, flip, progress)
    }
}


private fun drawIntroWeapon(
    c: AndroidCanvas,
    gun: Bitmap?,
    paint: Paint,
    cx: Float,
    cy: Float,
    d: Float,
    elapsed: Float
) {
    val recoil =
        if (elapsed in SHOOT_START..SHOOT_END) 5f * d * (1f - ((elapsed - SHOOT_START) / (SHOOT_END - SHOOT_START)).coerceIn(
            0f,
            1f
        )) else 0f
    val wx = cx + 30f * d - recoil;
    val wy = cy + 26f * d
    c.withTranslation(wx, wy) {
        if (gun != null) {
            drawBitmap(
                gun,
                null,
                RectF(-12f * d, -22f * d, 58f * d, 22f * d),
                paint
            )
        }
    }
    if (elapsed in SHOOT_START..SHOOT_END) {
        val muzzleX = wx + 58f * d; if (((elapsed - SHOOT_START) % .24f) < .09f) drawFlash(
            c,
            muzzleX,
            wy,
            d,
            paint
        )
        repeat(3) { i ->
            val p = ((elapsed - SHOOT_START) * 2.8f - i * .24f).coerceIn(
                0f,
                1f
            ); if (p > 0f && p < 1f) {
            val x = muzzleX + p * 190f * d;
            val y = wy + (i - 1) * 8f * d; paint.color =
                AndroidColor.rgb(255, 174, 0); c.drawCircle(x, y, 6f * d, paint); paint.color =
                AndroidColor.rgb(255, 242, 74); c.drawCircle(x, y, 4.3f * d, paint)
        }
        }
    }
}

private fun drawFlash(c: AndroidCanvas, x: Float, y: Float, d: Float, paint: Paint) {
    val p = Path().apply {
        moveTo(x, y); lineTo(x + 22f * d, y - 9f * d); lineTo(
        x + 16f * d,
        y
    ); lineTo(x + 28f * d, y); lineTo(x + 15f * d, y + 8f * d); close()
    }; paint.color = AndroidColor.rgb(255, 174, 0); c.drawPath(p, paint); paint.color =
        AndroidColor.rgb(255, 244, 92); c.drawCircle(x + 5f * d, y, 7f * d, paint)
}

private fun drawCover(c: AndroidCanvas, bitmap: Bitmap?, paint: Paint) {
    if (bitmap == null) {
        c.drawColor(AndroidColor.rgb(28, 75, 46)); return
    };
    val canvasRatio = c.width.toFloat() / c.height;
    val bitmapRatio = bitmap.width.toFloat() / bitmap.height
    val src = if (bitmapRatio > canvasRatio) {
        val w = (bitmap.height * canvasRatio).toInt(); Rect(
            (bitmap.width - w) / 2,
            0,
            (bitmap.width + w) / 2,
            bitmap.height
        )
    } else {
        val h = (bitmap.width / canvasRatio).toInt(); Rect(
            0,
            (bitmap.height - h) / 2,
            bitmap.width,
            (bitmap.height + h) / 2
        )
    }
    c.drawBitmap(bitmap, src, Rect(0, 0, c.width, c.height), paint)
}

private fun drawBitmapCentered(
    c: AndroidCanvas,
    bitmap: Bitmap?,
    x: Float,
    y: Float,
    size: Float,
    paint: Paint,
    flip: Boolean = false,
    alpha: Float = 1f
) {
    if (bitmap == null) return; paint.alpha = (alpha * 255).toInt();
    val dst = RectF(x - size / 2, y - size / 2, x + size / 2, y + size / 2); if (flip) {
        c.save(); c.scale(-1f, 1f, x, y); c.drawBitmap(bitmap, null, dst, paint); c.restore()
    } else c.drawBitmap(bitmap, null, dst, paint); paint.alpha = 255
}

internal fun introStateAt(time: Float) = when {
    time < INTRO_STUDIO_END -> IntroState.STUDIO; time < ENEMIES_START -> IntroState.PLAYER_ENTER; time < SHOOT_START -> IntroState.ENEMIES_ENTER; time < TITLE_START -> IntroState.SHOOT; time < INTRO_END -> IntroState.TITLE; else -> IntroState.WAIT_FOR_TAP
}

private const val INTRO_STUDIO_END = .8f
private const val ENEMIES_START = 1.5f
private const val SHOOT_START = 2.5f
private const val SHOOT_END = 3.4f
private const val TITLE_START = 3.4f
private const val INTRO_END = 4.3f
