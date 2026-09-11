package com.rodrigo.drawntosurvive.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.*
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.sin

@SuppressLint("ViewConstructor")
class GameView(context: Context, val engine: GameEngine, val controls: TouchController) :
    SurfaceView(context), SurfaceHolder.Callback {
    private var loop: GameLoop? = null;
    private val sprites = SpriteStore.get(context);
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG);
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG);
    private val dst = RectF();
    private val backgroundSrc = Rect();
    private val backgroundDst = RectF();
    private val muzzleFlashPath = Path();
    private val playerVisualPosition = Vector2()
    private val density = resources.displayMetrics.density

    init {
        holder.addCallback(this); isFocusable = true; setZOrderOnTop(false)
    }

    override fun surfaceCreated(h: SurfaceHolder) {
        GameLog.debug("surfaceCreated"); controls.configure(width, height, density); engine.resize(
            width,
            height
        ); if (loop?.isAlive != true) {
            GameLog.debug("GameLoop.start chamado"); loop =
                GameLoop(engine::update, this::drawFrame).also { it.start() }
        }
    }

    override fun surfaceChanged(h: SurfaceHolder, format: Int, w: Int, height: Int) {
        controls.configure(w, height, density); engine.resize(w, height)
    }

    override fun surfaceDestroyed(h: SurfaceHolder) {
        GameLog.debug("surfaceDestroyed");
        val old = loop; old?.shutdown(); if (old != Thread.currentThread()) try {
            old?.join(1000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }; if (loop === old) loop = null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent) = controls.onTouch(event)
    fun pauseGame() = engine.pause()
    private fun drawFrame() {
        if (!holder.surface.isValid) return;
        val c = try {
            holder.lockHardwareCanvas()
        } catch (_: Exception) {
            try {
                holder.lockCanvas()
            } catch (_: Exception) {
                null
            }
        } ?: return; try {
            render(c)
        } finally {
            try {
                holder.unlockCanvasAndPost(c)
            } catch (_: Exception) {
            }
        }
    }

    private fun render(c: Canvas) {
        drawBackground(c)
        drawAreaEffects(c)
        drawSupplyCrates(c)
        for (o in engine.orbs) if (o.active && isVisible(o.position.x, o.position.y, o.radius)) {
            paint.color = Color.rgb(62, 230, 170); c.drawCircle(
                o.position.x,
                o.position.y,
                o.radius,
                paint
            )
        }
        for (p in engine.projectiles) if (p.active && isVisible(p.position.x, p.position.y, p.radius)) {
            drawProjectile(c, p)
        }
        for (effect in engine.deathEffects) {
            if (!isVisible(effect.position.x, effect.position.y, 55f * density)) continue
            val frames = sprites.deathFrames; if (frames.isNotEmpty()) {
                val progress = (effect.elapsed / effect.duration).coerceIn(0f, .999f);
                val frame = frames[(progress * frames.size).toInt()]; drawSprite(
                    c,
                    frame,
                    effect.position,
                    110f * density,
                    false
                )
            }
        }
        for (e in engine.enemies) if (e.active && isVisible(e.position.x, e.position.y, 41f * density)) {
            drawEnemyAura(c, e)
            val directionX = engine.player.position.x - e.position.x
            val directionY = engine.player.position.y - e.position.y
            val direction = Direction8.from(directionX, directionY); val (frame, flip) = sprites.enemy(
                e.type,
                direction,
                e.animationState,
                e.animationTime
            ); drawSprite(c, frame, e.position, 82f * density * enemyScale(e), flip)
            drawEnemyHealth(c, e)
        }
        val p = engine.player;
        val specialAnimating = engine.specialAnimationTimer > 0f;
        val specialProgress =
            if (specialAnimating) ((GameConfig.SPECIAL_ANIMATION_DURATION - engine.specialAnimationTimer) / GameConfig.SPECIAL_ANIMATION_DURATION).coerceIn(
                0f,
                .999f
            ) else 0f; val (pd, pflip) = if (specialAnimating) sprites.playerJump(
            Direction8.from(
                engine.specialDirection
            ), specialProgress
        ) else sprites.player(
            Direction8.from(p.facingDirection),
            p.moveDirection.lengthSquared() > 0f,
            engine.elapsed
        ); if (p.invulnerability <= 0f || ((p.invulnerability * 14).toInt() % 2 == 0)) drawSprite(
            c,
            pd,
            playerVisualPosition(),
            94f * density,
            pflip
        )
        if (specialAnimating) drawSpecialEffect(c); drawWeapon(c); if (engine.muzzleFlashTimer > 0f) drawMuzzleFlash(
            c
        ); drawControls(c)
    }

    private fun drawSupplyCrates(c: Canvas) {
        for (crate in engine.supplyCrates) if (crate.active && isVisible(
                crate.position.x,
                crate.position.y,
                GameConfig.SUPPLY_CRATE_RADIUS * density
            )
        ) {
            val pulse = .85f + sin((engine.elapsed + crate.lifeTime) * 4f).coerceIn(-1f, 1f) * .15f
            val radius = GameConfig.SUPPLY_CRATE_RADIUS * density * pulse
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(70, 255, 196, 92)
            c.drawCircle(crate.position.x, crate.position.y, radius * 1.5f, paint)
            paint.color = supplyColor(crate.reward)
            c.drawCircle(crate.position.x, crate.position.y, radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * density
            paint.color = Color.argb(210, 255, 255, 255)
            c.drawCircle(crate.position.x, crate.position.y, radius, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 13f * density
            paint.typeface = Typeface.DEFAULT_BOLD
            c.drawText(supplyLabel(crate.reward), crate.position.x, crate.position.y + 5f * density, paint)
            paint.textAlign = Paint.Align.LEFT
        }
    }

    private fun supplyColor(reward: SupplyReward) = when (reward) {
        SupplyReward.HEAL -> Color.rgb(76, 220, 115)
        SupplyReward.XP -> Color.rgb(67, 229, 169)
        SupplyReward.SPECIAL_COOLDOWN -> Color.rgb(255, 145, 40)
        SupplyReward.EXPLOSION -> Color.rgb(255, 86, 86)
    }

    private fun supplyLabel(reward: SupplyReward) = when (reward) {
        SupplyReward.HEAL -> "+"
        SupplyReward.XP -> "XP"
        SupplyReward.SPECIAL_COOLDOWN -> "S"
        SupplyReward.EXPLOSION -> "!"
    }

    private fun drawProjectile(c: Canvas, projectile: Projectile) {
        val haloRadius = projectile.radius * if (projectile.critical) 1.35f else 1.15f
        paint.style = Paint.Style.FILL
        paint.color = if (projectile.critical) Color.argb(215, 255, 235, 70) else Color.argb(
            185,
            255,
            174,
            0
        )
        c.drawCircle(projectile.position.x, projectile.position.y, haloRadius, paint)
        val bullet = sprites.bullet
        if (bullet == null) {
            paint.color = if (projectile.critical) Color.YELLOW else Color.rgb(255, 111, 30)
            c.drawCircle(projectile.position.x, projectile.position.y, projectile.radius, paint)
            return
        }
        val angle = Math.toDegrees(
            atan2(projectile.direction.y.toDouble(), projectile.direction.x.toDouble())
        ).toFloat()
        val width = projectile.radius * 3.2f
        val height = projectile.radius * 1.8f
        dst.set(-width / 2f, -height / 2f, width / 2f, height / 2f)
        c.save()
        c.translate(projectile.position.x, projectile.position.y)
        c.rotate(angle)
        bitmapPaint.alpha = 255
        bitmapPaint.colorFilter = null
        c.drawBitmap(bullet, null, dst, bitmapPaint)
        c.restore()
    }

    private fun drawAreaEffects(c: Canvas) {
        for (effect in engine.areaEffects) {
            val progress = (effect.elapsed / effect.duration).coerceIn(0f, 1f)
            val radius = effect.radius * progress
            val alpha = ((1f - progress) * 155).toInt().coerceIn(0, 155)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (5f + 5f * (1f - progress)) * density
            paint.color = when (effect.type) {
                AreaEffectType.DEATH_EXPLOSION -> Color.argb(alpha, 255, 92, 92)
                AreaEffectType.SUPPLY_EXPLOSION -> Color.argb(alpha, 255, 196, 92)
            }
            c.drawCircle(effect.position.x, effect.position.y, radius, paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawEnemyAura(c: Canvas, enemy: Enemy) {
        if (enemy.rank == EnemyRank.NORMAL) return
        val scale = enemyScale(enemy)
        val radius = enemy.type.radius * scale * density * 1.7f
        paint.style = Paint.Style.FILL
        paint.color = when (enemy.rank) {
            EnemyRank.ELITE -> Color.argb(80, 255, 196, 92)
            EnemyRank.MINI_BOSS -> Color.argb(100, 180, 92, 255)
            EnemyRank.NORMAL -> Color.TRANSPARENT
        }
        c.drawCircle(enemy.position.x, enemy.position.y, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = when (enemy.rank) {
            EnemyRank.ELITE -> Color.argb(190, 255, 196, 92)
            EnemyRank.MINI_BOSS -> Color.argb(210, 215, 160, 255)
            EnemyRank.NORMAL -> Color.TRANSPARENT
        }
        c.drawCircle(enemy.position.x, enemy.position.y, radius, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawEnemyHealth(c: Canvas, enemy: Enemy) {
        if (enemy.rank == EnemyRank.NORMAL) return
        val maxHp = enemy.type.hp * when (enemy.rank) {
            EnemyRank.NORMAL -> 1f
            EnemyRank.ELITE -> GameConfig.ELITE_HP_MULTIPLIER
            EnemyRank.MINI_BOSS -> GameConfig.MINI_BOSS_HP_MULTIPLIER
        }
        val progress = (enemy.hp / maxHp).coerceIn(0f, 1f)
        val width = 50f * density * enemyScale(enemy)
        val height = 5f * density
        val left = enemy.position.x - width / 2f
        val top = enemy.position.y - 48f * density * enemyScale(enemy)
        paint.color = Color.argb(170, 20, 24, 22)
        c.drawRect(left, top, left + width, top + height, paint)
        paint.color = if (enemy.rank == EnemyRank.MINI_BOSS) Color.rgb(190, 115, 255) else Color.rgb(255, 196, 92)
        c.drawRect(left, top, left + width * progress, top + height, paint)
    }

    private fun enemyScale(enemy: Enemy) = when (enemy.rank) {
        EnemyRank.NORMAL -> 1f
        EnemyRank.ELITE -> GameConfig.ELITE_SCALE
        EnemyRank.MINI_BOSS -> GameConfig.MINI_BOSS_SCALE
    }

    private fun isVisible(x: Float, y: Float, margin: Float) =
        x + margin >= 0f && x - margin <= width && y + margin >= 0f && y - margin <= height

    private fun drawBackground(c: Canvas) {
        paint.alpha = 255
        paint.colorFilter = null
        paint.style = Paint.Style.FILL
        val b = sprites.background; if (b == null) {
            c.drawColor(Color.rgb(23, 30, 28)); return
        };
        val canvasRatio = c.width.toFloat() / c.height;
        val bitmapRatio = b.width.toFloat() / b.height; if (bitmapRatio > canvasRatio) {
            val sourceWidth = (b.height * canvasRatio).toInt();
            val left = (b.width - sourceWidth) / 2; backgroundSrc.set(
                left,
                0,
                left + sourceWidth,
                b.height
            )
        } else {
            val sourceHeight = (b.width / canvasRatio).toInt();
            val top = (b.height - sourceHeight) / 2; backgroundSrc.set(
                0,
                top,
                b.width,
                top + sourceHeight
            )
        }; backgroundDst.set(0f, 0f, c.width.toFloat(), c.height.toFloat()); c.drawBitmap(
            b,
            backgroundSrc,
            backgroundDst,
            paint
        )
    }

    private fun drawSprite(c: Canvas, b: Bitmap?, pos: Vector2, size: Float, flip: Boolean) {
        bitmapPaint.alpha = 255
        bitmapPaint.colorFilter = null
        if (b == null) {
            paint.color = Color.MAGENTA; c.drawCircle(pos.x, pos.y, size * .3f, paint); return
        }; dst.set(
            pos.x - size / 2,
            pos.y - size / 2,
            pos.x + size / 2,
            pos.y + size / 2
        ); if (flip) {
            c.save(); c.scale(-1f, 1f, pos.x, pos.y); c.drawBitmap(b, null, dst, bitmapPaint); c.restore()
        } else c.drawBitmap(b, null, dst, bitmapPaint)
    }

    private fun playerVisualPosition(): Vector2 {
        val p = engine.player.position
        playerVisualPosition.x = p.x
        playerVisualPosition.y = p.y
        if (engine.specialAnimationTimer <= 0f) return playerVisualPosition
        val progress =
            ((GameConfig.SPECIAL_ANIMATION_DURATION - engine.specialAnimationTimer) / GameConfig.SPECIAL_ANIMATION_DURATION).coerceIn(
                0f,
                1f
            )
        playerVisualPosition.y -=
            sin(progress * Math.PI).toFloat() * GameConfig.SPECIAL_JUMP_HEIGHT * density
        return playerVisualPosition
    }

    private fun drawWeapon(c: Canvas) {
        val p = engine.player;
        val d = p.facingDirection.normalized();
        val angle = Math.toDegrees(atan2(d.y.toDouble(), d.x.toDouble())).toFloat();
        val wp = playerVisualPosition() + d * GameConfig.WEAPON_OFFSET; c.save(); c.translate(
            wp.x,
            wp.y
        ); c.rotate(angle);
        bitmapPaint.alpha = 255
        bitmapPaint.colorFilter = null
        val b = sprites.gun; if (b != null) c.drawBitmap(
            b,
            null,
            RectF(-12f * density, -22f * density, 58f * density, 22f * density),
            bitmapPaint
        ) else {
            paint.color = Color.DKGRAY; c.drawRect(0f, -5f, 55f, 5f, paint)
        }; c.restore()
    }

    private fun drawSpecialEffect(c: Canvas) {
        val frames = sprites.jumpEffectFrames; if (frames.isEmpty()) return;
        val elapsed = GameConfig.SPECIAL_ANIMATION_DURATION - engine.specialAnimationTimer;
        val progress =
            (elapsed / GameConfig.SPECIAL_ANIMATION_DURATION).coerceIn(0f, .999f); drawSprite(
            c,
            frames[(progress * frames.size).toInt()],
            engine.player.position,
            128f * density,
            false
        )
    }

    private fun drawMuzzleFlash(c: Canvas) {
        val d = engine.player.facingDirection.normalized();
        val pos = engine.player.position + d * GameConfig.MUZZLE_FLASH_OFFSET;
        val angle = Math.toDegrees(atan2(d.y.toDouble(), d.x.toDouble())).toFloat();
        val life = (engine.muzzleFlashTimer / GameConfig.MUZZLE_FLASH_DURATION).coerceIn(0f, 1f);
        val scale = .75f + .25f * life
        muzzleFlashPath.reset(); muzzleFlashPath.moveTo(
            0f,
            0f
        ); muzzleFlashPath.lineTo(
            22f * density * scale,
            -8f * density * scale
        ); muzzleFlashPath.lineTo(
            16f * density * scale,
            0f
        ); muzzleFlashPath.lineTo(
            28f * density * scale,
            0f
        ); muzzleFlashPath.lineTo(
            15f * density * scale,
            8f * density * scale
        ); muzzleFlashPath.close()
        c.save(); c.translate(pos.x, pos.y); c.rotate(angle); paint.color =
            Color.rgb(255, 174, 0); c.drawPath(muzzleFlashPath, paint); paint.color =
            Color.rgb(255, 244, 92); c.drawCircle(
            5f * density,
            0f,
            7f * density * scale,
            paint
        ); c.restore()
    }

    private fun drawControls(c: Canvas) {
        drawStick(c, controls.moveJoystick); drawActionButton(
            c,
            controls.fireCenter,
            62f * density,
            if (controls.isFireHeld()) Color.rgb(255, 95, 65) else Color.rgb(205, 55, 45),
            "ATIRAR"
        );
        val s = controls.specialCenter;
        val specialReady = engine.special.cooldownRemaining <= 0f; drawActionButton(
            c,
            s,
            45f * density,
            if (specialReady) Color.rgb(255, 145, 40) else Color.rgb(80, 80, 80),
            if (specialReady) "SPECIAL" else ceil(engine.special.cooldownRemaining).toInt()
                .toString()
        )
    }

    private fun drawActionButton(
        c: Canvas,
        center: Vector2,
        radius: Float,
        color: Int,
        text: String
    ) {
        paint.color = color; c.drawCircle(center.x, center.y, radius, paint); paint.style =
            Paint.Style.STROKE; paint.strokeWidth = 3f * density; paint.color =
            Color.argb(190, 255, 255, 255); c.drawCircle(
            center.x,
            center.y,
            radius,
            paint
        ); paint.style = Paint.Style.FILL; paint.color = Color.WHITE; paint.textAlign =
            Paint.Align.CENTER; paint.textSize = 14f * density; paint.typeface =
            Typeface.DEFAULT_BOLD; c.drawText(
            text,
            center.x,
            center.y + 5f * density,
            paint
        ); paint.textAlign = Paint.Align.LEFT
    }

    private fun drawStick(c: Canvas, j: VirtualJoystick) {
        paint.style = Paint.Style.FILL; paint.color = Color.argb(75, 255, 255, 255); c.drawCircle(
            j.center.x,
            j.center.y,
            j.radius,
            paint
        ); paint.color = Color.argb(150, 255, 183, 77); c.drawCircle(
            j.knob.x,
            j.knob.y,
            j.radius * .42f,
            paint
        ); paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f * density; paint.color =
            Color.argb(160, 255, 255, 255); c.drawCircle(
            j.center.x,
            j.center.y,
            j.radius,
            paint
        ); paint.style = Paint.Style.FILL
    }
}
