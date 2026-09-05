package com.example.model

import androidx.compose.ui.graphics.Color

enum class GameState {
    HOME,
    TUTORIAL,
    READY,
    RUNNING,
    PAUSED,
    COLLISION,
    GAME_OVER,
    SETTINGS
}

enum class MonkeyAnimState {
    IDLE,
    RUNNING,
    JUMPING,
    FALLING,
    LANDING,
    COLLIDED,
    STOPPED,
    LOOKING_AT_HAND,
    LICKING_HAND
}

enum class ObstacleType {
    FALLEN_LOG,
    TREE_ROOT,
    ROCK,
    SMALL_BUSH,
    THORNY_PLANT,
    BROKEN_BRANCH,
    MUD_PATCH,
    HANGING_VINE
}

enum class CollectibleType(val points: Int) {
    COIN(10),
    BANANA(25),
    GOLDEN_BANANA(100)
}

data class Obstacle(
    var worldX: Float,
    var width: Float,
    var height: Float,
    val type: ObstacleType,
    var hit: Boolean = false,
    var passed: Boolean = false
)

data class Collectible(
    var worldX: Float,
    var worldY: Float,
    var size: Float,
    val type: CollectibleType,
    var collected: Boolean = false,
    var bounceOffset: Float = 0f
)

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var size: Float,
    val color: Color,
    var alpha: Float = 1f,
    var life: Float = 1f,
    val maxLife: Float = 1f,
    val rotation: Float = 0f,
    var rotationSpeed: Float = 0f
)

data class FloatingText(
    val text: String,
    var x: Float,
    var y: Float,
    val color: Color,
    var life: Float = 1f,
    val maxLife: Float = 1f
)

data class BackgroundLeaf(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var size: Float,
    var rotation: Float,
    var rotationSpeed: Float,
    val color: Color
)

data class DistantBird(
    var x: Float,
    var y: Float,
    var speed: Float,
    var wingPhase: Float = 0f
)
