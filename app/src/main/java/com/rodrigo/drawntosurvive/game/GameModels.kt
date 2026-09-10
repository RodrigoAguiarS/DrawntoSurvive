package com.rodrigo.drawntosurvive.game

import kotlin.math.min

enum class GameState { MENU, RUNNING, LEVEL_UP, PAUSED, GAME_OVER, VICTORY }
enum class AnimationState { IDLE, WALK, JUMP, DEATH }
enum class EnemyType(
    val hp: Float,
    val speed: Float,
    val damage: Float,
    val xp: Int,
    val radius: Float,
    val hasJump: Boolean = true
) {
    SLIME(35f, 88f, 10f, 7, 23f), FAST(24f, 145f, 7f, 7, 20f), SKELETON(85f, 62f, 17f, 15, 25f)
}

data class Player(
    val position: Vector2,
    var currentHp: Float = GameConfig.PLAYER_INITIAL_HP,
    var maxHp: Float = GameConfig.PLAYER_INITIAL_HP,
    var speed: Float = GameConfig.PLAYER_SPEED,
    var level: Int = 1,
    var experience: Int = 0,
    var experienceRequired: Int = GameConfig.XP_BASE_REQUIRED,
    var pickupRadius: Float = GameConfig.PICKUP_RADIUS,
    var magnetRadius: Float = GameConfig.MAGNET_RADIUS,
    var moveDirection: Vector2 = Vector2(),
    var aimDirection: Vector2 = Vector2(1f, 0f),
    var lastAimDirection: Vector2 = Vector2(1f, 0f),
    var facingDirection: Vector2 = Vector2(0f, 1f),
    var criticalChance: Float = GameConfig.CRITICAL_CHANCE,
    var criticalMultiplier: Float = GameConfig.CRITICAL_MULTIPLIER,
    var healthRegenPerSecond: Float = 0f,
    var invulnerability: Float = 0f
)

data class Enemy(
    val position: Vector2,
    val type: EnemyType,
    var hp: Float = type.hp,
    var active: Boolean = true,
    var animationState: AnimationState = AnimationState.WALK,
    var animationTime: Float = 0f,
    var jumpTimer: Float = GameConfig.MIN_JUMP_INTERVAL,
    var separationX: Float = 0f,
    var separationY: Float = 0f
)

data class Projectile(
    val position: Vector2,
    val direction: Vector2,
    val speed: Float,
    val damage: Float,
    val radius: Float,
    val maxDistance: Float,
    val critical: Boolean,
    var distanceTraveled: Float = 0f,
    var active: Boolean = true,
    var previousPosition: Vector2 = position.copy()
)

data class DeathEffect(
    val position: Vector2,
    var elapsed: Float = 0f,
    val duration: Float = GameConfig.DEATH_EFFECT_DURATION
)

data class ExperienceOrb(
    val position: Vector2,
    val amount: Int,
    val radius: Float = 9f,
    var active: Boolean = true
)

data class WeaponStats(
    var damage: Float = GameConfig.WEAPON_DAMAGE,
    var cooldown: Float = GameConfig.WEAPON_COOLDOWN,
    var currentCooldown: Float = 0f,
    var projectileSpeed: Float = GameConfig.PROJECTILE_SPEED,
    var projectileRange: Float = GameConfig.PROJECTILE_RANGE,
    var projectileRadius: Float = GameConfig.PROJECTILE_RADIUS,
    var projectilesPerShot: Int = 1,
    var spreadDegrees: Float = 10f
)

data class SpecialStats(
    var damage: Float = GameConfig.SPECIAL_DAMAGE,
    var cooldown: Float = GameConfig.SPECIAL_COOLDOWN,
    var cooldownRemaining: Float = 0f,
    var projectileCount: Int = GameConfig.SPECIAL_PROJECTILE_COUNT,
    var projectileSpeed: Float = GameConfig.SPECIAL_PROJECTILE_SPEED,
    var projectileRadius: Float = GameConfig.SPECIAL_PROJECTILE_RADIUS
)

enum class UpgradeType { MOVE_SPEED, MAX_HP, REGEN, DAMAGE, FIRE_RATE, PROJECTILE_SPEED, PROJECTILE_SIZE, MULTISHOT, CRITICAL, PICKUP_RANGE, SPECIAL_DAMAGE, SPECIAL_COOLDOWN, SPECIAL_PROJECTILES, SPECIAL_SIZE }
data class Upgrade(
    val type: UpgradeType,
    val name: String,
    val description: String,
    val maxLevel: Int = 5
)

data class UpgradeUiModel(val id: String, val name: String, val description: String, val level: Int)
data class GameUiState(
    val hp: Float = 100f, val maxHp: Float = 100f, val level: Int = 1,
    val experience: Int = 0, val experienceRequired: Int = 20, val elapsedTime: Float = 0f,
    val kills: Int = 0, val gameState: GameState = GameState.RUNNING,
    val upgradeChoices: List<UpgradeUiModel> = emptyList(), val coinsEarned: Int = 0
)

data class RunResult(
    val elapsedTime: Float,
    val level: Int,
    val kills: Int,
    val coins: Int,
    val victory: Boolean
)

sealed interface GameCommand {
    data class SelectUpgrade(val upgradeId: String) : GameCommand;
    data object Pause : GameCommand;
    data object Resume : GameCommand;
    data object RestartRun : GameCommand
}

data class InputSnapshot(val move: Vector2 = Vector2())

object UpgradeCatalog {
    val all = listOf(
        Upgrade(UpgradeType.MOVE_SPEED, "Passos Rápidos", "+10% velocidade"),
        Upgrade(UpgradeType.MAX_HP, "Coração Forte", "+20 HP máximo e atual"),
        Upgrade(UpgradeType.REGEN, "Regeneração", "+1 HP por segundo"),
        Upgrade(UpgradeType.DAMAGE, "Munição Pesada", "+10% de dano"),
        Upgrade(UpgradeType.FIRE_RATE, "Gatilho Rápido", "-10% de recarga"),
        Upgrade(UpgradeType.PROJECTILE_SPEED, "Tiro Veloz", "+20% velocidade do tiro"),
        Upgrade(UpgradeType.PROJECTILE_SIZE, "Tiro Largo", "+10% tamanho do tiro"),
        Upgrade(UpgradeType.MULTISHOT, "Multishot", "+1 projétil"),
        Upgrade(UpgradeType.CRITICAL, "Precisão", "+5% crítico"),
        Upgrade(UpgradeType.PICKUP_RANGE, "Ímã", "+20% alcance de XP"),
        Upgrade(UpgradeType.SPECIAL_DAMAGE, "Especial Potente", "+20% dano especial"),
        Upgrade(UpgradeType.SPECIAL_COOLDOWN, "Especial Rápido", "-10% recarga especial"),
        Upgrade(UpgradeType.SPECIAL_PROJECTILES, "Rajada Maior", "+2 projéteis especiais"),
        Upgrade(UpgradeType.SPECIAL_SIZE, "Explosão Larga", "+15% tamanho especial")
    )
}
