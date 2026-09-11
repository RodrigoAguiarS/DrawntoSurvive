package com.rodrigo.drawntosurvive.game

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.*
import kotlin.random.Random

class GameEngine(private val input: TouchController, private val onResult: (RunResult) -> Unit) {
    var player = Player(Vector2()); private set
    var weapon = WeaponStats(); private set
    var special = SpecialStats(); private set
    val enemies = mutableListOf<Enemy>();
    val projectiles = mutableListOf<Projectile>();
    val orbs = mutableListOf<ExperienceOrb>();
    val deathEffects = mutableListOf<DeathEffect>()
    val supplyCrates = mutableListOf<SupplyCrate>()
    val areaEffects = mutableListOf<AreaEffect>()
    private val commands = ConcurrentLinkedQueue<GameCommand>();
    private val levels = mutableMapOf<UpgradeType, Int>()
    private val enemyGrid = SpatialGrid(GameConfig.SPATIAL_GRID_CELL_SIZE)
    private val _ui = MutableStateFlow(GameUiState());
    val ui: StateFlow<GameUiState> = _ui
    var state = GameState.RUNNING; private set;
    var elapsed = 0f; private set;
    var kills = 0; private set
    var width = 1f;
    var height = 1f;
    private var spawnClock = 0f;
    private var supplyCrateClock = GameConfig.SUPPLY_CRATE_FIRST_SPAWN_TIME
    private var eliteClock = GameConfig.ELITE_FIRST_SPAWN_TIME
    private var miniBossClock = GameConfig.MINI_BOSS_FIRST_SPAWN_TIME
    private var uiClock = 0f;
    private var resultSent = false
    var muzzleFlashTimer = 0f; private set
    var specialAnimationTimer = 0f; private set
    var specialDirection = Vector2(0f, 1f); private set
    private var specialProjectilesPending = false
    fun resize(w: Int, h: Int) {
        width = w.toFloat(); height = h.toFloat(); if (player.position.x == 0f) {
            player.position.x = width / 2; player.position.y = height / 2
        }
    }

    fun command(c: GameCommand) = commands.add(c)
    fun requestRestart() {
        GameLog.debug("Replay clicado; state=$state"); command(GameCommand.RestartRun)
    }

    fun pause() {
        if (state == GameState.RUNNING) state = GameState.PAUSED; publish()
    }

    fun update(dtRaw: Float) {
        processCommands(); if (state != GameState.RUNNING) return;
        val dt = min(dtRaw, GameConfig.MAX_DELTA_TIME)
        elapsed += dt;
        val i = input.snapshot(); player.moveDirection = i.move; if (i.move.lengthSquared() > 0f) {
            player.facingDirection = i.move; player.aimDirection = i.move; player.lastAimDirection =
                i.move
        }
        player.position.x = (player.position.x + i.move.x * player.speed * dt).coerceIn(
            30f,
            width - 30f
        ); player.position.y =
            (player.position.y + i.move.y * player.speed * dt).coerceIn(65f, height - 30f)
        weapon.currentCooldown -= dt; special.cooldownRemaining -= dt; player.invulnerability -= dt; muzzleFlashTimer =
            (muzzleFlashTimer - dt).coerceAtLeast(0f); updateSpecial(dt)
        val firePressed =
            input.consumeFire(); if ((firePressed || input.isFireHeld()) && weapon.currentCooldown <= 0f) fire(); if (input.consumeSpecial() && special.cooldownRemaining <= 0f && specialAnimationTimer <= 0f) startSpecial()
        spawnClock -= dt; if (spawnClock <= 0f) {
            spawn(); spawnClock = GameMath.spawnInterval(elapsed)
        }
        updateTimedSpawns(dt)
        rebuildEnemyGrid()
        updateEnemies(dt)
        rebuildEnemyGrid()
        updateProjectiles(dt)
        collisions()
        updateOrbs(dt)
        updateSupplyCrates(dt)
        updateAreaEffects(dt)
        updateDeathEffects(dt)
        if (player.healthRegenPerSecond > 0) player.currentHp =
            min(player.maxHp, player.currentHp + player.healthRegenPerSecond * dt)
        removeInactiveEntities()
        if (player.currentHp <= 0) finish(false) else if (elapsed >= GameConfig.MATCH_DURATION_SECONDS) finish(
            true
        )
        uiClock -= dt
        if (uiClock <= 0f) {
            publishIfChanged()
            uiClock = GameConfig.UI_PUBLISH_INTERVAL
        }
    }

    private fun fire() {
        weapon.currentCooldown = weapon.cooldown; muzzleFlashTimer =
            GameConfig.MUZZLE_FLASH_DURATION;
        val facing = player.facingDirection.normalized(); for (a in GameMath.spreadAngles(
            weapon.projectilesPerShot,
            weapon.spreadDegrees
        )) {
            val d = GameMath.rotate(facing, a); addProjectile(
                d,
                weapon.damage,
                weapon.projectileSpeed,
                weapon.projectileRadius,
                weapon.projectileRange
            )
        }
    }

    private fun startSpecial() {
        special.cooldownRemaining = special.cooldown; specialAnimationTimer =
            GameConfig.SPECIAL_ANIMATION_DURATION; specialDirection =
            player.facingDirection.normalized(); specialProjectilesPending = true
    }

    private fun updateSpecial(dt: Float) {
        if (specialAnimationTimer <= 0f) return;
        val previousElapsed =
            GameConfig.SPECIAL_ANIMATION_DURATION - specialAnimationTimer; specialAnimationTimer =
            (specialAnimationTimer - dt).coerceAtLeast(0f);
        val elapsed =
            GameConfig.SPECIAL_ANIMATION_DURATION - specialAnimationTimer; if (specialProjectilesPending && previousElapsed < GameConfig.SPECIAL_PROJECTILE_FIRE_TIME && elapsed >= GameConfig.SPECIAL_PROJECTILE_FIRE_TIME) {
            specialProjectilesPending =
                false; for (d in GameMath.radialDirections(special.projectileCount)) addProjectile(
                d,
                special.damage,
                special.projectileSpeed,
                special.projectileRadius,
                700f
            )
        }
    }

    private fun addProjectile(
        d: Vector2,
        baseDamage: Float,
        speed: Float,
        radius: Float,
        range: Float
    ) {
        if (projectiles.size >= GameConfig.MAX_ENTITIES) return;
        val crit = Random.nextFloat() < player.criticalChance;
        val damage =
            baseDamage * (if (crit) player.criticalMultiplier else 1f); projectiles += Projectile(
            player.position + d * GameConfig.MUZZLE_OFFSET,
            d,
            speed,
            damage,
            radius,
            range,
            crit
        )
    }

    private fun updateTimedSpawns(dt: Float) {
        supplyCrateClock -= dt
        if (supplyCrateClock <= 0f) {
            spawnSupplyCrate()
            supplyCrateClock = GameConfig.SUPPLY_CRATE_INTERVAL
        }
        eliteClock -= dt
        if (eliteClock <= 0f) {
            spawnRankedEnemy(EnemyRank.ELITE)
            eliteClock = GameConfig.ELITE_SPAWN_INTERVAL
        }
        miniBossClock -= dt
        if (miniBossClock <= 0f) {
            spawnRankedEnemy(EnemyRank.MINI_BOSS)
            miniBossClock = GameConfig.MINI_BOSS_SPAWN_INTERVAL
        }
    }

    private fun spawnSupplyCrate() {
        if (supplyCrates.size >= 3) return
        val x = Random.nextFloat() * (width - 140f).coerceAtLeast(1f) + 70f
        val y = Random.nextFloat() * (height - 150f).coerceAtLeast(1f) + 75f
        val reward = SupplyReward.entries[Random.nextInt(SupplyReward.entries.size)]
        supplyCrates += SupplyCrate(Vector2(x, y), reward)
    }

    private fun spawnRankedEnemy(rank: EnemyRank) {
        if (enemies.size >= GameConfig.MAX_ENEMIES) return
        val side = Random.nextInt(4)
        val m = GameConfig.SPAWN_MARGIN
        val p = when (side) {
            0 -> Vector2(Random.nextFloat() * width, -m)
            1 -> Vector2(width + m, Random.nextFloat() * height)
            2 -> Vector2(Random.nextFloat() * width, height + m)
            else -> Vector2(-m, Random.nextFloat() * height)
        }
        val type = if (rank == EnemyRank.MINI_BOSS) EnemyType.SKELETON else EnemyType.entries.random()
        enemies += Enemy(
            p,
            type,
            hp = type.hp * enemyHpMultiplier(rank),
            jumpTimer = randomJumpInterval(),
            rank = rank
        )
    }

    private fun enemyScale(rank: EnemyRank) = when (rank) {
        EnemyRank.NORMAL -> 1f
        EnemyRank.ELITE -> GameConfig.ELITE_SCALE
        EnemyRank.MINI_BOSS -> GameConfig.MINI_BOSS_SCALE
    }

    private fun enemyRadius(enemy: Enemy) = enemy.type.radius * enemyScale(enemy.rank)

    private fun enemyHpMultiplier(rank: EnemyRank) = when (rank) {
        EnemyRank.NORMAL -> 1f
        EnemyRank.ELITE -> GameConfig.ELITE_HP_MULTIPLIER
        EnemyRank.MINI_BOSS -> GameConfig.MINI_BOSS_HP_MULTIPLIER
    }

    private fun enemyDamageMultiplier(rank: EnemyRank) = when (rank) {
        EnemyRank.NORMAL -> 1f
        EnemyRank.ELITE -> GameConfig.ELITE_DAMAGE_MULTIPLIER
        EnemyRank.MINI_BOSS -> GameConfig.MINI_BOSS_DAMAGE_MULTIPLIER
    }

    private fun enemyXpMultiplier(rank: EnemyRank) = when (rank) {
        EnemyRank.NORMAL -> 1
        EnemyRank.ELITE -> GameConfig.ELITE_XP_MULTIPLIER
        EnemyRank.MINI_BOSS -> GameConfig.MINI_BOSS_XP_MULTIPLIER
    }

    private fun deathExplosionChance(): Float {
        val level = levels[UpgradeType.DEATH_EXPLOSION] ?: 0
        return GameConfig.DEATH_EXPLOSION_BASE_CHANCE * level
    }

    private fun spawn() {
        if (enemies.size >= GameConfig.MAX_ENEMIES) return;
        val count = if (elapsed > 360) 2 else 1; repeat(count) {
            val side = Random.nextInt(4);
            val m = GameConfig.SPAWN_MARGIN;
            val p = when (side) {
                0 -> Vector2(Random.nextFloat() * width, -m); 1 -> Vector2(
                    width + m,
                    Random.nextFloat() * height
                ); 2 -> Vector2(Random.nextFloat() * width, height + m); else -> Vector2(
                    -m,
                    Random.nextFloat() * height
                )
            };
            val r = Random.nextFloat();
            val type = when {
                elapsed < 60 -> EnemyType.SLIME; elapsed < 180 -> if (r < .7f) EnemyType.SLIME else EnemyType.FAST; elapsed < 360 -> if (r < .48f) EnemyType.SLIME else if (r < .75f) EnemyType.FAST else EnemyType.SKELETON; elapsed < 480 -> if (r < .4f) EnemyType.SKELETON else if (r < .7f) EnemyType.FAST else EnemyType.SLIME; else -> if (r < .55f) EnemyType.SKELETON else if (r < .8f) EnemyType.FAST else EnemyType.SLIME
            }; enemies += Enemy(p, type, jumpTimer = randomJumpInterval())
        }
    }

    private fun randomJumpInterval() =
        GameConfig.MIN_JUMP_INTERVAL + Random.nextFloat() * (GameConfig.MAX_JUMP_INTERVAL - GameConfig.MIN_JUMP_INTERVAL)

    private fun updateEnemies(dt: Float) {
        val count = enemies.size
        for (i in 0 until count) {
            val e = enemies[i]; e.separationX = 0f; e.separationY = 0f; if (!e.active) continue
            if (e.animationState == AnimationState.JUMP) {
                e.animationTime += dt; if (e.animationTime >= GameConfig.ENEMY_JUMP_DURATION) {
                    e.animationState = AnimationState.WALK; e.animationTime = 0f; e.jumpTimer =
                        randomJumpInterval()
                }
            } else {
                e.animationTime += dt; e.jumpTimer -= dt; if (e.type.hasJump && e.jumpTimer <= 0f) {
                    e.animationState = AnimationState.JUMP; e.animationTime = 0f
                }
            }
        }
        for (i in 0 until count) {
            val a = enemies[i]
            if (!a.active) continue
            val searchRadius =
                (enemyRadius(a) + MAX_ENEMY_RADIUS) * GameConfig.ENEMY_SEPARATION_RADIUS_MULTIPLIER
            enemyGrid.forEachNearby(a.position.x, a.position.y, searchRadius) { j ->
                if (j <= i) return@forEachNearby
                val b = enemies[j]
                if (!b.active) return@forEachNearby
                var dx = a.position.x - b.position.x
                var dy = a.position.y - b.position.y
                val desired =
                    (enemyRadius(a) + enemyRadius(b)) * GameConfig.ENEMY_SEPARATION_RADIUS_MULTIPLIER
                val desiredSq = desired * desired
                var distanceSq = dx * dx + dy * dy
                if (distanceSq >= desiredSq) return@forEachNearby
                if (distanceSq < 0.0001f) {
                    val angle = ((i * 31 + j * 17) % 360) * PI.toFloat() / 180f
                    dx = cos(angle)
                    dy = sin(angle)
                    distanceSq = 1f
                }
                val distance = sqrt(distanceSq)
                val strength = (desired - distance) / desired
                val nx = dx / distance
                val ny = dy / distance
                a.separationX += nx * strength
                a.separationY += ny * strength
                b.separationX -= nx * strength
                b.separationY -= ny * strength
            }
        }
        for (e in enemies) if (e.active) {
            val dx = player.position.x - e.position.x;
            val dy = player.position.y - e.position.y;
            val len = sqrt(dx * dx + dy * dy);
            val chaseX = if (len > 0.0001f) dx / len else 0f;
            val chaseY = if (len > 0.0001f) dy / len else 0f;
            val separationLenSq =
                e.separationX * e.separationX + e.separationY * e.separationY; if (separationLenSq > 1f) {
                val inverse =
                    1f / sqrt(separationLenSq); e.separationX *= inverse; e.separationY *= inverse
            }; e.position.x += (chaseX * e.type.speed + e.separationX * GameConfig.ENEMY_SEPARATION_FORCE) * dt; e.position.y += (chaseY * e.type.speed + e.separationY * GameConfig.ENEMY_SEPARATION_FORCE) * dt; if (!e.position.x.isFinite() || !e.position.y.isFinite()) {
                e.position.x = width / 2f; e.position.y = height / 2f
            }
        }
    }

    private fun updateProjectiles(dt: Float) {
        for (p in projectiles) if (p.active) {
            p.previousPosition.x = p.position.x
            p.previousPosition.y = p.position.y
            val dist = p.speed * dt
            p.position.x += p.direction.x * dist
            p.position.y += p.direction.y * dist
            p.distanceTraveled += dist
            if (p.distanceTraveled >= p.maxDistance) p.active = false
        }
    }

    private fun collisions() {
        for (p in projectiles) {
            if (!p.active) continue
            val padding = p.radius + GameConfig.PROJECTILE_COLLISION_PADDING + MAX_ENEMY_RADIUS
            val minX = min(p.previousPosition.x, p.position.x) - padding
            val minY = min(p.previousPosition.y, p.position.y) - padding
            val maxX = max(p.previousPosition.x, p.position.x) + padding
            val maxY = max(p.previousPosition.y, p.position.y) + padding
            var hitIndex = -1
            enemyGrid.forEachInBounds(minX, minY, maxX, maxY) { index ->
                if (hitIndex >= 0 && index >= hitIndex) return@forEachInBounds
                val enemy = enemies[index]
                if (!enemy.active) return@forEachInBounds
                val hitRadius = enemyRadius(enemy) + p.radius + GameConfig.PROJECTILE_COLLISION_PADDING
                if (GameMath.segmentHitsCircle(
                        p.previousPosition.x,
                        p.previousPosition.y,
                        p.position.x,
                        p.position.y,
                        enemy.position.x,
                        enemy.position.y,
                        hitRadius
                    )
                ) {
                    hitIndex = index
                }
            }
            if (hitIndex >= 0) hitEnemy(p, enemies[hitIndex])
        }
        if (player.invulnerability <= 0f) {
            val searchRadius = GameConfig.PLAYER_COLLISION_RADIUS + MAX_ENEMY_RADIUS
            var hitIndex = -1
            enemyGrid.forEachNearby(player.position.x, player.position.y, searchRadius) { index ->
                if (hitIndex >= 0 && index >= hitIndex) return@forEachNearby
                val enemy = enemies[index]
                if (enemy.active && GameMath.circlesCollide(
                        player.position,
                        GameConfig.PLAYER_COLLISION_RADIUS,
                        enemy.position,
                        enemyRadius(enemy)
                    )
                ) {
                    hitIndex = index
                }
            }
            if (hitIndex >= 0) {
                val enemy = enemies[hitIndex]
                player.currentHp -= enemy.type.damage * enemyDamageMultiplier(enemy.rank)
                player.invulnerability = GameConfig.INVULNERABILITY_TIME
            }
        }
    }

    private fun hitEnemy(projectile: Projectile, enemy: Enemy) {
        enemy.hp -= projectile.damage
        projectile.active = false
        enemy.position.x =
            (enemy.position.x + projectile.direction.x * GameConfig.PROJECTILE_KNOCKBACK).coerceIn(
                -GameConfig.SPAWN_MARGIN,
                width + GameConfig.SPAWN_MARGIN
            )
        enemy.position.y =
            (enemy.position.y + projectile.direction.y * GameConfig.PROJECTILE_KNOCKBACK).coerceIn(
                -GameConfig.SPAWN_MARGIN,
                height + GameConfig.SPAWN_MARGIN
            )
        if (enemy.hp <= 0f) {
            killEnemy(enemy)
        }
    }

    private fun killEnemy(enemy: Enemy) {
        if (!enemy.active) return
        enemy.active = false
        kills++
        deathEffects += DeathEffect(enemy.position.copy())
        orbs += ExperienceOrb(enemy.position.copy(), enemy.type.xp * enemyXpMultiplier(enemy.rank))
        if (Random.nextFloat() < deathExplosionChance()) {
            applyAreaDamage(
                enemy.position.x,
                enemy.position.y,
                GameConfig.DEATH_EXPLOSION_RADIUS,
                GameConfig.DEATH_EXPLOSION_DAMAGE,
                AreaEffectType.DEATH_EXPLOSION
            )
        }
        if (enemy.rank != EnemyRank.NORMAL && Random.nextFloat() < .75f) {
            supplyCrates += SupplyCrate(enemy.position.copy(), SupplyReward.entries.random())
        }
    }

    private fun applyAreaDamage(x: Float, y: Float, radius: Float, damage: Float, type: AreaEffectType) {
        areaEffects += AreaEffect(Vector2(x, y), radius, type)
        val radiusSq = radius * radius
        enemyGrid.forEachNearby(x, y, radius + MAX_ENEMY_RADIUS) { index ->
            val enemy = enemies[index]
            if (!enemy.active) return@forEachNearby
            val dx = enemy.position.x - x
            val dy = enemy.position.y - y
            if (dx * dx + dy * dy <= radiusSq) {
                enemy.hp -= damage
                if (enemy.hp <= 0f) killEnemy(enemy)
            }
        }
    }

    private fun rebuildEnemyGrid() {
        enemyGrid.rebuild(enemies, width, height, GameConfig.SPAWN_MARGIN)
    }

    private fun removeInactiveEntities() {
        removeIf(enemies) { !it.active }
        removeIf(projectiles) { !it.active }
        removeIf(orbs) { !it.active }
        removeIf(supplyCrates) { !it.active }
        removeIf(areaEffects) { it.elapsed >= it.duration }
        removeIf(deathEffects) { it.elapsed >= it.duration }
    }

    private inline fun <T> removeIf(items: MutableList<T>, predicate: (T) -> Boolean) {
        var writeIndex = 0
        for (readIndex in items.indices) {
            val item = items[readIndex]
            if (!predicate(item)) {
                if (writeIndex != readIndex) items[writeIndex] = item
                writeIndex++
            }
        }
        if (writeIndex < items.size) items.subList(writeIndex, items.size).clear()
    }

    private fun updateDeathEffects(dt: Float) {
        for (effect in deathEffects) effect.elapsed += dt
    }

    private fun updateAreaEffects(dt: Float) {
        for (effect in areaEffects) effect.elapsed += dt
    }

    private fun updateOrbs(dt: Float) {
        for (o in orbs) if (o.active) {
            val delta = player.position - o.position;
            val d2 = delta.lengthSquared(); if (d2 <= player.pickupRadius * player.pickupRadius) {
                o.active = false; gainXp(o.amount)
            } else if (d2 <= player.magnetRadius * player.magnetRadius) {
                val d =
                    delta.normalized(); o.position.x += d.x * 330f * dt; o.position.y += d.y * 330f * dt
            }
        }
    }

    private fun updateSupplyCrates(dt: Float) {
        for (crate in supplyCrates) if (crate.active) {
            crate.lifeTime += dt
            if (GameMath.circlesCollide(
                    player.position,
                    GameConfig.PLAYER_COLLISION_RADIUS,
                    crate.position,
                    GameConfig.SUPPLY_CRATE_RADIUS
                )
            ) {
                collectSupplyCrate(crate)
            }
        }
    }

    private fun collectSupplyCrate(crate: SupplyCrate) {
        crate.active = false
        when (crate.reward) {
            SupplyReward.HEAL -> player.currentHp =
                min(player.maxHp, player.currentHp + GameConfig.SUPPLY_CRATE_HEAL)

            SupplyReward.XP -> gainXp(GameConfig.SUPPLY_CRATE_XP)
            SupplyReward.SPECIAL_COOLDOWN -> special.cooldownRemaining = 0f
            SupplyReward.EXPLOSION -> applyAreaDamage(
                crate.position.x,
                crate.position.y,
                GameConfig.SUPPLY_CRATE_REWARD_EXPLOSION_RADIUS,
                GameConfig.SUPPLY_CRATE_REWARD_EXPLOSION_DAMAGE,
                AreaEffectType.SUPPLY_EXPLOSION
            )
        }
    }

    private fun gainXp(amount: Int) {
        player.experience += amount; if (player.experience >= player.experienceRequired) beginLevelUp()
    }

    private fun beginLevelUp() {
        player.experience -= player.experienceRequired; player.level++; player.experienceRequired =
            GameMath.xpRequired(player.level); state = GameState.LEVEL_UP;
        val available =
            UpgradeCatalog.all.filter { (levels[it.type] ?: 0) < it.maxLevel }.shuffled()
                .take(3); _ui.value = snapshot(available.map {
            UpgradeUiModel(
                it.type.name,
                it.name,
                it.description,
                (levels[it.type] ?: 0) + 1
            )
        })
    }

    private fun processCommands() {
        var changed = false; while (true) {
            when (val c = commands.poll() ?: break) {
                is GameCommand.Pause -> if (state == GameState.RUNNING) {
                    state = GameState.PAUSED; changed = true
                }; is GameCommand.Resume -> if (state == GameState.PAUSED) {
                state = GameState.RUNNING; changed = true
            }; is GameCommand.RestartRun -> if (state == GameState.GAME_OVER || state == GameState.VICTORY) {
                restartRun(); changed = true
            }; is GameCommand.SelectUpgrade -> if (state == GameState.LEVEL_UP) {
                val type = runCatching { UpgradeType.valueOf(c.upgradeId) }.getOrNull()
                    ?: continue; applyUpgrade(type); if (player.experience >= player.experienceRequired) beginLevelUp() else state =
                    GameState.RUNNING; changed = true
            }
            }
        }; if (changed) publish()
    }

    private fun restartRun() {
        val previous = state; GameLog.debug("restartRun iniciado; state anterior=$previous")
        input.reset(); enemies.clear(); projectiles.clear(); orbs.clear(); deathEffects.clear(); supplyCrates.clear(); areaEffects.clear(); levels.clear(); commands.clear()
        player = Player(Vector2(width / 2f, height / 2f)); weapon = WeaponStats(); special =
            SpecialStats(); elapsed = 0f; kills = 0; spawnClock = 0f; supplyCrateClock =
            GameConfig.SUPPLY_CRATE_FIRST_SPAWN_TIME; eliteClock = GameConfig.ELITE_FIRST_SPAWN_TIME; miniBossClock =
            GameConfig.MINI_BOSS_FIRST_SPAWN_TIME; uiClock =
            0f; muzzleFlashTimer = 0f; specialAnimationTimer = 0f; specialDirection =
            Vector2(0f, 1f); specialProjectilesPending = false; resultSent = false; state =
            GameState.RUNNING
        GameLog.debug("GameEngine resetado; state novo=$state")
    }

    private fun applyUpgrade(t: UpgradeType) {
        levels[t] = (levels[t] ?: 0) + 1; when (t) {
            UpgradeType.MOVE_SPEED -> player.speed *= 1.1f; UpgradeType.MAX_HP -> {
                player.maxHp += 20; player.currentHp = min(player.maxHp, player.currentHp + 20)
            }; UpgradeType.REGEN -> player.healthRegenPerSecond += 1
            UpgradeType.DAMAGE -> weapon.damage *= 1.1f; UpgradeType.FIRE_RATE -> weapon.cooldown =
                max(
                    GameConfig.MIN_WEAPON_COOLDOWN,
                    weapon.cooldown * .9f
                ); UpgradeType.PROJECTILE_SPEED -> weapon.projectileSpeed *= 1.2f
            UpgradeType.PROJECTILE_SIZE -> weapon.projectileRadius *= 1.1f; UpgradeType.MULTISHOT -> weapon.projectilesPerShot++; UpgradeType.CRITICAL -> player.criticalChance =
                min(1f, player.criticalChance + .05f)

            UpgradeType.PICKUP_RANGE -> player.magnetRadius *= 1.2f; UpgradeType.SPECIAL_DAMAGE -> special.damage *= 1.2f; UpgradeType.SPECIAL_COOLDOWN -> special.cooldown =
                max(GameConfig.MIN_SPECIAL_COOLDOWN, special.cooldown * .9f)

            UpgradeType.SPECIAL_PROJECTILES -> special.projectileCount += 2; UpgradeType.SPECIAL_SIZE -> special.projectileRadius *= 1.15f
            UpgradeType.DEATH_EXPLOSION -> {}
        }
    }

    private fun finish(victory: Boolean) {
        if (resultSent) return;
        val previous = state; state =
            if (victory) GameState.VICTORY else GameState.GAME_OVER; resultSent =
            true; GameLog.debug("finishRun chamado; state anterior=$previous; state novo=$state");
        val result = RunResult(
            elapsed,
            player.level,
            kills,
            GameMath.coins(kills, victory),
            victory
        ); publish(); onResult(result)
    }

    private fun snapshot(choices: List<UpgradeUiModel> = if (state == GameState.LEVEL_UP) _ui.value.upgradeChoices else emptyList()) =
        GameUiState(
            player.currentHp,
            player.maxHp,
            player.level,
            player.experience,
            player.experienceRequired,
            elapsed,
            kills,
            state,
            choices,
            if (resultSent) GameMath.coins(kills, state == GameState.VICTORY) else 0,
            collectedUpgradeSummary()
        )

    private fun collectedUpgradeSummary(): List<UpgradeSummaryUiModel> =
        UpgradeCatalog.all.mapNotNull { upgrade ->
            val level = levels[upgrade.type] ?: return@mapNotNull null
            UpgradeSummaryUiModel(upgrade.name, level)
        }

    private fun publishIfChanged() {
        val next = snapshot()
        if (next != _ui.value) _ui.value = next
    }

    private fun publish() {
        _ui.value = snapshot()
    }

    private companion object {
        val MAX_ENEMY_RADIUS = EnemyType.entries.maxOf { it.radius } * GameConfig.MINI_BOSS_SCALE
    }
}
