package com.example.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.example.audio.AudioService
import com.example.data.ScoreStorage
import com.example.model.BackgroundLeaf
import com.example.model.Collectible
import com.example.model.CollectibleType
import com.example.model.DistantBird
import com.example.model.FloatingText
import com.example.model.GameState
import com.example.model.MonkeyAnimState
import com.example.model.Obstacle
import com.example.model.ObstacleType
import com.example.model.Particle
import com.example.ui.theme.BananaYellow
import com.example.ui.theme.BrightJungleGreen
import com.example.ui.theme.CoinGold
import com.example.ui.theme.DarkSoil
import com.example.ui.theme.DarkUI
import com.example.ui.theme.DeepJungleGreen
import com.example.ui.theme.EarthBrown
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.LeafGreen
import com.example.ui.theme.MossGreen
import com.example.ui.theme.SkyLightGreen
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Core 2D Game Engine for Jungle Monkey Run.
 * Manages world simulation, physics, procedural generation, camera, collision,
 * signature monkey animations (including collision hand-lick), and parallax rendering.
 */
class JungleMonkeyGameEngine(
    val audioService: AudioService,
    val scoreStorage: ScoreStorage
) {
    var gameState: GameState = GameState.HOME
    var monkeyAnimState: MonkeyAnimState = MonkeyAnimState.IDLE

    // Screen dimensions
    var screenWidth: Float = 1080f
    var screenHeight: Float = 1920f
    var baseGroundY: Float = 1450f

    // Player Monkey Physics
    var monkeyScreenX: Float = 220f
    var monkeyY: Float = 1450f
    var monkeyVy: Float = 0f
    val monkeyWidth: Float = 110f
    val monkeyHeight: Float = 130f
    val gravity: Float = 2400f
    val jumpVelocity: Float = 980f
    var isGrounded: Boolean = true

    // World & Movement
    var worldX: Float = 0f
    var baseSpeed: Float = 330f
    var currentSpeed: Float = 330f
    val maxSpeed: Float = 750f

    // Score & Progress
    var currentScore: Int = 0
    var distanceMeters: Float = 0f
    var coinsCollectedInRun: Int = 0
    var bananasCollectedInRun: Int = 0
    var comboCount: Int = 0
    var comboTimer: Float = 0f
    var isNewBestScore: Boolean = false
    var lastMilestone: Int = 0

    // Animation Timers & Counters
    var runCycle: Float = 0f
    var blinkTimer: Float = 0f
    var isBlinking: Boolean = false
    var freezeTimer: Float = 0f
    var cameraShake: Float = 0f
    var collisionSequenceTimer: Float = 0f
    var lickPhase: Float = 0f

    // Countdown
    var countdownTimer: Float = 3f

    // Obstacles & Collectibles
    val obstacles = mutableListOf<Obstacle>()
    val collectibles = mutableListOf<Collectible>()
    var nextSpawnWorldX: Float = 700f

    // Particles & Visuals
    val particles = mutableListOf<Particle>()
    val floatingTexts = mutableListOf<FloatingText>()
    val leaves = mutableListOf<BackgroundLeaf>()
    val birds = mutableListOf<DistantBird>()

    init {
        initAtmosphere()
    }

    private fun initAtmosphere() {
        leaves.clear()
        for (i in 0 until 18) {
            leaves.add(
                BackgroundLeaf(
                    x = Random.nextFloat() * 1200f,
                    y = Random.nextFloat() * 1800f,
                    vx = -(30f + Random.nextFloat() * 40f),
                    vy = 25f + Random.nextFloat() * 35f,
                    size = 12f + Random.nextFloat() * 16f,
                    rotation = Random.nextFloat() * 360f,
                    rotationSpeed = (Random.nextFloat() - 0.5f) * 60f,
                    color = if (Random.nextBoolean()) LeafGreen else MossGreen
                )
            )
        }

        birds.clear()
        for (i in 0 until 3) {
            birds.add(
                DistantBird(
                    x = Random.nextFloat() * 1000f,
                    y = 150f + Random.nextFloat() * 350f,
                    speed = 70f + Random.nextFloat() * 50f,
                    wingPhase = Random.nextFloat() * 10f
                )
            )
        }
    }

    fun resize(width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return
        screenWidth = width
        screenHeight = height
        baseGroundY = height * 0.76f
        monkeyScreenX = width * 0.22f
        if (isGrounded) {
            monkeyY = getGroundY(worldX + monkeyScreenX)
        }
    }

    /**
     * Smooth undulating terrain calculation.
     * Gentle slopes and small dips that naturally vary as distance increases.
     */
    fun getGroundY(wX: Float): Float {
        val distProgress = min(1f, distanceMeters / 600f)
        val elevationScale = 12f + 28f * distProgress
        val wave1 = sin(wX * 0.0022f) * elevationScale
        val wave2 = sin(wX * 0.0008f) * (elevationScale * 0.5f)
        return baseGroundY + wave1 + wave2
    }

    fun startNewGame() {
        worldX = 0f
        distanceMeters = 0f
        currentScore = 0
        coinsCollectedInRun = 0
        bananasCollectedInRun = 0
        comboCount = 0
        comboTimer = 0f
        currentSpeed = baseSpeed
        isNewBestScore = false
        lastMilestone = 0
        monkeyVy = 0f
        isGrounded = true
        freezeTimer = 0f
        cameraShake = 0f
        collisionSequenceTimer = 0f
        lickPhase = 0f
        obstacles.clear()
        collectibles.clear()
        particles.clear()
        floatingTexts.clear()

        nextSpawnWorldX = 650f
        monkeyY = getGroundY(monkeyScreenX)
        monkeyAnimState = MonkeyAnimState.IDLE

        countdownTimer = 3.2f
        gameState = GameState.READY
        audioService.startMusic()
    }

    fun onJumpPressed() {
        when (gameState) {
            GameState.RUNNING -> {
                if (isGrounded) {
                    monkeyVy = -jumpVelocity
                    isGrounded = false
                    monkeyAnimState = MonkeyAnimState.JUMPING
                    audioService.playJump()

                    // Spawn jump dust particles
                    spawnDustPuff(monkeyScreenX + monkeyWidth * 0.3f, monkeyY)
                }
            }
            GameState.READY -> {
                // If in countdown, allow player to be eager
            }
            else -> {}
        }
    }

    fun update(dt: Float) {
        val safeDt = min(dt, 0.05f)

        // Update atmospheric background leaves & birds regardless of state
        updateAtmosphere(safeDt)

        when (gameState) {
            GameState.READY -> {
                countdownTimer -= safeDt
                if (countdownTimer <= 0f) {
                    gameState = GameState.RUNNING
                    monkeyAnimState = MonkeyAnimState.RUNNING
                }
            }
            GameState.RUNNING -> {
                updateRunning(safeDt)
            }
            GameState.COLLISION -> {
                updateCollisionSequence(safeDt)
            }
            GameState.PAUSED, GameState.HOME, GameState.TUTORIAL, GameState.GAME_OVER, GameState.SETTINGS -> {
                // Only idle animations
                runCycle += safeDt * 2f
            }
        }
    }

    private fun updateAtmosphere(dt: Float) {
        for (leaf in leaves) {
            leaf.x += leaf.vx * dt
            leaf.y += leaf.vy * dt
            leaf.rotation += leaf.rotationSpeed * dt
            if (leaf.y > screenHeight + 30f || leaf.x < -40f) {
                leaf.y = -20f
                leaf.x = Random.nextFloat() * (screenWidth + 200f)
            }
        }

        for (bird in birds) {
            bird.x += bird.speed * dt
            bird.wingPhase += dt * 8f
            if (bird.x > screenWidth + 80f) {
                bird.x = -80f
                bird.y = 120f + Random.nextFloat() * 300f
            }
        }
    }

    private fun updateRunning(dt: Float) {
        // Speed progression
        currentSpeed = min(maxSpeed, baseSpeed + (distanceMeters * 0.065f))

        // Advance world
        val moveDelta = currentSpeed * dt
        worldX += moveDelta
        distanceMeters += moveDelta * 0.05f

        // Score based on distance
        currentScore = distanceMeters.toInt() + (coinsCollectedInRun * 10) + (bananasCollectedInRun * 25)

        // Milestone feedback (every 500m)
        val currentHundred = (distanceMeters / 500).toInt() * 500
        if (currentHundred > 0 && currentHundred > lastMilestone) {
            lastMilestone = currentHundred
            addFloatingText("${lastMilestone}m Milestone!", monkeyScreenX + 60f, monkeyY - 140f, CoinGold)
            audioService.playGoldenBanana()
        }

        // Combo timer
        if (comboTimer > 0f) {
            comboTimer -= dt
            if (comboTimer <= 0f) {
                comboCount = 0
            }
        }

        // Running animation
        runCycle += dt * (currentSpeed / 180f) * 14f

        // Eye blinking
        blinkTimer += dt
        if (blinkTimer > 3.5f) {
            isBlinking = true
            if (blinkTimer > 3.65f) {
                isBlinking = false
                blinkTimer = 0f
            }
        }

        // Monkey physics
        val groundAtMonkey = getGroundY(worldX + monkeyScreenX)

        if (!isGrounded) {
            monkeyVy += gravity * dt
            monkeyY += monkeyVy * dt

            if (monkeyVy > 0f) {
                monkeyAnimState = MonkeyAnimState.FALLING
            }

            if (monkeyY >= groundAtMonkey) {
                // Landing!
                monkeyY = groundAtMonkey
                monkeyVy = 0f
                isGrounded = true
                monkeyAnimState = MonkeyAnimState.RUNNING
                audioService.playLand()
                spawnDustPuff(monkeyScreenX + monkeyWidth * 0.5f, monkeyY)
            }
        } else {
            monkeyY = groundAtMonkey
            monkeyAnimState = MonkeyAnimState.RUNNING

            // Running dust particles
            if (Random.nextFloat() < 0.25f) {
                particles.add(
                    Particle(
                        x = monkeyScreenX + monkeyWidth * 0.2f,
                        y = monkeyY - 4f,
                        vx = -(currentSpeed * 0.35f + Random.nextFloat() * 50f),
                        vy = -(20f + Random.nextFloat() * 40f),
                        size = 6f + Random.nextFloat() * 6f,
                        color = EarthBrown.copy(alpha = 0.6f),
                        life = 0.4f,
                        maxLife = 0.4f
                    )
                )
            }
        }

        // Camera shake decay
        if (cameraShake > 0f) {
            cameraShake = max(0f, cameraShake - dt * 25f)
        }

        // Procedural Spawning
        spawnObjectsIfNeeded()

        // Update Obstacles & Check Collisions
        val monkeyBounds = Rect(
            offset = Offset(monkeyScreenX + 22f, monkeyY - monkeyHeight + 18f),
            size = Size(monkeyWidth - 44f, monkeyHeight - 24f)
        )

        val iteratorObstacle = obstacles.iterator()
        while (iteratorObstacle.hasNext()) {
            val obs = iteratorObstacle.next()
            val screenObsX = obs.worldX - worldX
            val obsGroundY = getGroundY(obs.worldX)

            // Remove if far offscreen left
            if (screenObsX < -200f) {
                iteratorObstacle.remove()
                continue
            }

            // Check Collision
            if (!obs.hit && screenObsX < screenWidth + 100f) {
                val obsBounds = Rect(
                    offset = Offset(screenObsX + 10f, obsGroundY - obs.height + 6f),
                    size = Size(obs.width - 20f, obs.height - 8f)
                )

                if (monkeyBounds.overlaps(obsBounds)) {
                    if (obs.type == ObstacleType.MUD_PATCH) {
                        // Mud patch slows the monkey temporarily instead of fatal hit
                        currentSpeed = max(180f, currentSpeed * 0.65f)
                        addFloatingText("MUD SLOW!", monkeyScreenX + 40f, monkeyY - 100f, EarthBrown)
                        spawnMudSplash(monkeyScreenX + monkeyWidth * 0.5f, monkeyY)
                        obs.hit = true
                    } else {
                        // Fatal hit! Trigger signature collision & hand-licking sequence
                        triggerCollision(obs)
                        break
                    }
                }
            }
        }

        // Update Collectibles
        val iteratorCollectible = collectibles.iterator()
        while (iteratorCollectible.hasNext()) {
            val col = iteratorCollectible.next()
            val screenColX = col.worldX - worldX

            // Remove if far offscreen left
            if (screenColX < -150f) {
                iteratorCollectible.remove()
                continue
            }

            col.bounceOffset = sin(worldX * 0.015f + col.worldX) * 6f

            if (!col.collected && screenColX < screenWidth + 100f) {
                val colBounds = Rect(
                    offset = Offset(screenColX - col.size * 0.5f, col.worldY + col.bounceOffset - col.size * 0.5f),
                    size = Size(col.size, col.size)
                )

                if (monkeyBounds.overlaps(colBounds)) {
                    col.collected = true
                    handleCollection(col, screenColX)
                }
            }
        }

        // Update Particles
        updateParticles(dt)
        updateFloatingTexts(dt)
    }

    private fun handleCollection(col: Collectible, screenX: Float) {
        comboCount++
        comboTimer = 2.5f

        val (points, text, color) = when (col.type) {
            CollectibleType.COIN -> {
                coinsCollectedInRun++
                audioService.playCoin()
                Triple(10, "+10", CoinGold)
            }
            CollectibleType.BANANA -> {
                bananasCollectedInRun++
                audioService.playBanana()
                Triple(25, "+25", BananaYellow)
            }
            CollectibleType.GOLDEN_BANANA -> {
                bananasCollectedInRun += 3
                audioService.playGoldenBanana()
                Triple(100, "+100 GOLDEN!", CoinGold)
            }
        }

        currentScore += points
        addFloatingText(
            if (comboCount > 2) "$text (x$comboCount)" else text,
            screenX,
            col.worldY - 30f,
            color
        )

        // Sparkle burst particles
        for (i in 0 until 10) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val speed = 80f + Random.nextFloat() * 120f
            particles.add(
                Particle(
                    x = screenX,
                    y = col.worldY,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    size = 5f + Random.nextFloat() * 6f,
                    color = color,
                    life = 0.5f,
                    maxLife = 0.5f
                )
            )
        }
    }

    /**
     * Special signature collision sequence requested in Section 7 of prompt:
     * RUN -> COLLISION -> IMPACT (freeze ~200ms, camera shake) -> MONKEY STOPS -> MONKEY LOOKS AT HAND -> MONKEY LICKS HAND -> GAME OVER UI
     */
    private fun triggerCollision(obs: Obstacle) {
        obs.hit = true
        gameState = GameState.COLLISION
        audioService.stopMusic()
        audioService.playHit()

        freezeTimer = 0.22f
        cameraShake = 16f
        collisionSequenceTimer = 0f
        monkeyAnimState = MonkeyAnimState.COLLIDED

        // Impact particles
        val impactX = monkeyScreenX + monkeyWidth * 0.7f
        val impactY = monkeyY - monkeyHeight * 0.4f
        for (i in 0 until 16) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val spd = 120f + Random.nextFloat() * 180f
            particles.add(
                Particle(
                    x = impactX,
                    y = impactY,
                    vx = cos(angle) * spd,
                    vy = sin(angle) * spd - 60f,
                    size = 7f + Random.nextFloat() * 7f,
                    color = Color.White,
                    life = 0.4f,
                    maxLife = 0.4f
                )
            )
        }
    }

    private fun updateCollisionSequence(dt: Float) {
        // Handle initial freeze frame
        if (freezeTimer > 0f) {
            freezeTimer -= dt
            return
        }

        collisionSequenceTimer += dt

        // Camera shake decay
        if (cameraShake > 0f) {
            cameraShake = max(0f, cameraShake - dt * 25f)
        }

        // Signature Animation Stages:
        // 0.0s - 0.45s: Monkey stumbles and stops on ground (LOOKING_AT_HAND)
        // 0.45s - 1.6s: Monkey raises hand, head tilts, inspects bruised hand
        // 1.6s - 3.2s: Monkey funny hand-licking animation (cute lick sound and animated tongue)
        // > 3.2s: Transition to GAME_OVER screen
        when {
            collisionSequenceTimer < 0.5f -> {
                monkeyAnimState = MonkeyAnimState.STOPPED
                monkeyY = getGroundY(worldX + monkeyScreenX)
            }
            collisionSequenceTimer < 1.4f -> {
                monkeyAnimState = MonkeyAnimState.LOOKING_AT_HAND
            }
            collisionSequenceTimer < 3.0f -> {
                if (monkeyAnimState != MonkeyAnimState.LICKING_HAND) {
                    monkeyAnimState = MonkeyAnimState.LICKING_HAND
                    audioService.playHandLick()
                }
                lickPhase += dt * 8f
            }
            else -> {
                // Finish sequence, show GAME OVER UI
                finishGameOver()
            }
        }

        updateParticles(dt)
        updateFloatingTexts(dt)
    }

    private fun finishGameOver() {
        gameState = GameState.GAME_OVER
        audioService.playGameOver()

        // Update score & storage
        isNewBestScore = scoreStorage.updateScoreIfBest(currentScore)
        scoreStorage.addCollectibles(coinsCollectedInRun, bananasCollectedInRun)

        // Celebration confetti if new best!
        if (isNewBestScore) {
            for (i in 0 until 50) {
                particles.add(
                    Particle(
                        x = Random.nextFloat() * screenWidth,
                        y = -20f,
                        vx = (Random.nextFloat() - 0.5f) * 140f,
                        vy = 120f + Random.nextFloat() * 200f,
                        size = 10f + Random.nextFloat() * 12f,
                        color = listOf(CoinGold, BananaYellow, BrightJungleGreen, Color.White).random(),
                        life = 2.5f,
                        maxLife = 2.5f,
                        rotationSpeed = (Random.nextFloat() - 0.5f) * 300f
                    )
                )
            }
        }
    }

    private fun spawnObjectsIfNeeded() {
        // Ensure fair spacing: jump distance = currentSpeed * (2 * jumpVelocity / gravity)
        val airTime = (2f * jumpVelocity) / gravity
        val minJumpDist = currentSpeed * airTime
        val safeGap = max(minJumpDist * 1.35f, 440f)

        while (nextSpawnWorldX < worldX + screenWidth + 600f) {
            val spawnX = nextSpawnWorldX
            val groundY = getGroundY(spawnX)

            // Select obstacle type with weighted fairness
            val obstacleType = when (Random.nextInt(100)) {
                in 0..22 -> ObstacleType.FALLEN_LOG
                in 23..42 -> ObstacleType.ROCK
                in 43..60 -> ObstacleType.SMALL_BUSH
                in 61..75 -> ObstacleType.TREE_ROOT
                in 76..87 -> ObstacleType.THORNY_PLANT
                in 88..94 -> ObstacleType.MUD_PATCH
                else -> ObstacleType.BROKEN_BRANCH
            }

            val (w, h) = when (obstacleType) {
                ObstacleType.FALLEN_LOG -> Pair(110f, 65f)
                ObstacleType.ROCK -> Pair(85f, 75f)
                ObstacleType.SMALL_BUSH -> Pair(95f, 70f)
                ObstacleType.TREE_ROOT -> Pair(100f, 60f)
                ObstacleType.THORNY_PLANT -> Pair(80f, 85f)
                ObstacleType.MUD_PATCH -> Pair(130f, 25f)
                ObstacleType.BROKEN_BRANCH -> Pair(90f, 55f)
                ObstacleType.HANGING_VINE -> Pair(40f, 150f)
            }

            obstacles.add(
                Obstacle(
                    worldX = spawnX,
                    width = w,
                    height = h,
                    type = obstacleType
                )
            )

            // Spawn collectibles in arc or patterns
            spawnCollectiblesNear(spawnX, groundY, obstacleType)

            // Increment next spawn position with random safe spacing
            val extraVariance = Random.nextFloat() * 260f
            nextSpawnWorldX = spawnX + safeGap + extraVariance
        }
    }

    private fun spawnCollectiblesNear(obstacleX: Float, groundY: Float, type: ObstacleType) {
        val pattern = Random.nextInt(4)
        when (pattern) {
            0 -> {
                // Arc of 4 coins directly over the obstacle
                val numCoins = 4
                val arcRadius = 140f
                for (i in 0 until numCoins) {
                    val angle = PI.toFloat() * (0.2f + 0.6f * (i.toFloat() / (numCoins - 1)))
                    val colX = obstacleX + (i - numCoins / 2) * 45f
                    val colY = groundY - 70f - sin(angle) * arcRadius
                    collectibles.add(
                        Collectible(
                            worldX = colX,
                            worldY = colY,
                            size = 32f,
                            type = CollectibleType.COIN
                        )
                    )
                }
            }
            1 -> {
                // Banana reward above the obstacle
                collectibles.add(
                    Collectible(
                        worldX = obstacleX,
                        worldY = groundY - 170f,
                        size = 38f,
                        type = CollectibleType.BANANA
                    )
                )
            }
            2 -> {
                // Trail of 3 coins before the obstacle
                for (i in 0 until 3) {
                    collectibles.add(
                        Collectible(
                            worldX = obstacleX - 160f + (i * 45f),
                            worldY = groundY - 50f,
                            size = 32f,
                            type = CollectibleType.COIN
                        )
                    )
                }
            }
            3 -> {
                // Rare golden banana high up! (12% chance)
                if (Random.nextFloat() < 0.15f) {
                    collectibles.add(
                        Collectible(
                            worldX = obstacleX,
                            worldY = groundY - 240f,
                            size = 46f,
                            type = CollectibleType.GOLDEN_BANANA
                        )
                    )
                } else {
                    // Double banana
                    collectibles.add(
                        Collectible(
                            worldX = obstacleX - 35f,
                            worldY = groundY - 160f,
                            size = 36f,
                            type = CollectibleType.BANANA
                        )
                    )
                    collectibles.add(
                        Collectible(
                            worldX = obstacleX + 35f,
                            worldY = groundY - 160f,
                            size = 36f,
                            type = CollectibleType.BANANA
                        )
                    )
                }
            }
        }
    }

    private fun spawnDustPuff(x: Float, y: Float) {
        for (i in 0 until 8) {
            particles.add(
                Particle(
                    x = x + (Random.nextFloat() - 0.5f) * 20f,
                    y = y - 4f,
                    vx = (Random.nextFloat() - 0.5f) * 70f - 40f,
                    vy = -(Random.nextFloat() * 45f + 15f),
                    size = 8f + Random.nextFloat() * 8f,
                    color = EarthBrown.copy(alpha = 0.55f),
                    life = 0.45f,
                    maxLife = 0.45f
                )
            )
        }
    }

    private fun spawnMudSplash(x: Float, y: Float) {
        for (i in 0 until 12) {
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = (Random.nextFloat() - 0.5f) * 120f,
                    vy = -(Random.nextFloat() * 80f + 20f),
                    size = 9f + Random.nextFloat() * 7f,
                    color = DarkSoil.copy(alpha = 0.8f),
                    life = 0.5f,
                    maxLife = 0.5f
                )
            )
        }
    }

    private fun addFloatingText(text: String, x: Float, y: Float, color: Color) {
        floatingTexts.add(FloatingText(text, x, y, color))
    }

    private fun updateParticles(dt: Float) {
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.life -= dt
            p.alpha = max(0f, p.life / p.maxLife)
            if (p.life <= 0f) {
                iter.remove()
            }
        }
    }

    private fun updateFloatingTexts(dt: Float) {
        val iter = floatingTexts.iterator()
        while (iter.hasNext()) {
            val ft = iter.next()
            ft.y -= 45f * dt
            ft.life -= dt
            if (ft.life <= 0f) {
                iter.remove()
            }
        }
    }

    // ==========================================
    // RENDERING PIPELINE (Compose Canvas)
    // ==========================================

    fun render(drawScope: DrawScope) {
        with(drawScope) {
            // Apply camera shake if active
            val shakeOffsetX = if (cameraShake > 0f) (Random.nextFloat() - 0.5f) * cameraShake else 0f
            val shakeOffsetY = if (cameraShake > 0f) (Random.nextFloat() - 0.5f) * cameraShake else 0f

            translate(left = shakeOffsetX, top = shakeOffsetY) {
                // 1. Sky & Tropical Atmosphere
                renderSky()

                // 2. Distant Mountains & Mist (0.08x scroll)
                renderDistantMountains()

                // 3. Birds in Distance
                renderBirds()

                // 4. Midground Jungle Trees & Canopy (0.35x scroll)
                renderMidgroundJungle()

                // 5. Falling Ambient Leaves
                renderLeaves()

                // 6. Rolling Jungle Ground (1.0x scroll)
                renderGround()

                // 7. Obstacles (1.0x scroll)
                renderObstacles()

                // 8. Collectibles (1.0x scroll)
                renderCollectibles()

                // 9. Main Character: Monkey (Custom 2D Vector Animations)
                renderMonkey()

                // 10. Particles
                renderParticles()

                // 11. Floating Score Texts
                renderFloatingTexts()
            }
        }
    }

    private fun DrawScope.renderSky() {
        // Atmospheric gradient from jungle morning green-tinted sky to rich lime
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF8CE397),
                    Color(0xFFBBE9AC),
                    Color(0xFFE2F7C8),
                    Color(0xFF78BE55)
                )
            ),
            size = size
        )

        // Sun glow & rays in top-right
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x80FFF4B8),
                    Color(0x20FFF4B8),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.82f, size.height * 0.16f),
                radius = 240f
            ),
            radius = 240f,
            center = Offset(size.width * 0.82f, size.height * 0.16f)
        )
    }

    private fun DrawScope.renderDistantMountains() {
        val parallaxX = (worldX * 0.08f) % size.width
        val path = Path()
        val mountainBaseY = size.height * 0.58f

        path.moveTo(0f, size.height)
        path.lineTo(0f, mountainBaseY)

        val step = 140f
        var curX = -parallaxX - 200f
        while (curX < size.width + 300f) {
            val peakH = 110f + sin(curX * 0.012f) * 60f
            path.quadraticTo(
                curX + step * 0.5f, mountainBaseY - peakH,
                curX + step, mountainBaseY
            )
            curX += step
        }
        path.lineTo(size.width, size.height)
        path.close()

        drawPath(path, color = Color(0x351F6B3A))
    }

    private fun DrawScope.renderBirds() {
        for (bird in birds) {
            val flap = sin(bird.wingPhase) * 10f
            val birdPath = Path().apply {
                moveTo(bird.x - 14f, bird.y - flap)
                quadraticTo(bird.x - 7f, bird.y - 2f, bird.x, bird.y)
                quadraticTo(bird.x + 7f, bird.y - 2f, bird.x + 14f, bird.y - flap)
            }
            drawPath(birdPath, color = DeepJungleGreen.copy(alpha = 0.6f), style = Stroke(width = 3.5f))
        }
    }

    private fun DrawScope.renderMidgroundJungle() {
        val parallaxX = (worldX * 0.35f) % 600f
        val startX = -parallaxX - 300f
        val trunkBaseY = baseGroundY - 40f

        var x = startX
        while (x < size.width + 400f) {
            // Giant Jungle Tree Trunk
            drawRect(
                color = EarthBrown.copy(alpha = 0.55f),
                topLeft = Offset(x + 40f, trunkBaseY - 380f),
                size = Size(45f, 380f)
            )

            // Foliage Canopy Circles
            drawCircle(
                color = ForestGreen.copy(alpha = 0.65f),
                radius = 110f,
                center = Offset(x + 62f, trunkBaseY - 380f)
            )
            drawCircle(
                color = BrightJungleGreen.copy(alpha = 0.55f),
                radius = 85f,
                center = Offset(x + 20f, trunkBaseY - 340f)
            )
            drawCircle(
                color = LeafGreen.copy(alpha = 0.6f),
                radius = 90f,
                center = Offset(x + 110f, trunkBaseY - 350f)
            )

            // Hanging Moss / Vines
            val vinePath = Path().apply {
                moveTo(x + 20f, trunkBaseY - 310f)
                quadraticTo(x + 26f, trunkBaseY - 220f, x + 22f, trunkBaseY - 140f)
                moveTo(x + 85f, trunkBaseY - 320f)
                quadraticTo(x + 92f, trunkBaseY - 250f, x + 88f, trunkBaseY - 170f)
            }
            drawPath(vinePath, color = MossGreen.copy(alpha = 0.7f), style = Stroke(width = 4f))

            x += 340f
        }
    }

    private fun DrawScope.renderLeaves() {
        for (leaf in leaves) {
            rotate(leaf.rotation, pivot = Offset(leaf.x, leaf.y)) {
                drawOval(
                    color = leaf.color.copy(alpha = 0.65f),
                    topLeft = Offset(leaf.x - leaf.size * 0.5f, leaf.y - leaf.size * 0.25f),
                    size = Size(leaf.size, leaf.size * 0.5f)
                )
            }
        }
    }

    private fun DrawScope.renderGround() {
        // Multi-segment smooth terrain path following getGroundY
        val path = Path()
        path.moveTo(0f, size.height)

        val step = 16f
        var curScreenX = 0f
        val firstGroundY = getGroundY(worldX)
        path.lineTo(0f, firstGroundY)

        while (curScreenX <= size.width + step) {
            val gY = getGroundY(worldX + curScreenX)
            path.lineTo(curScreenX, gY)
            curScreenX += step
        }

        path.lineTo(size.width, size.height)
        path.close()

        // Rich Soil fill
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(
                    EarthBrown,
                    DarkSoil,
                    Color(0xFF22150D)
                ),
                startY = baseGroundY - 60f,
                endY = size.height
            )
        )

        // Lush Green Grass layer along top edge of ground
        val grassPath = Path()
        grassPath.moveTo(0f, getGroundY(worldX))
        curScreenX = 0f
        while (curScreenX <= size.width + step) {
            val gY = getGroundY(worldX + curScreenX)
            grassPath.lineTo(curScreenX, gY)
            curScreenX += step
        }
        drawPath(
            path = grassPath,
            color = LeafGreen,
            style = Stroke(width = 18f)
        )

        // Bright top highlight
        drawPath(
            path = grassPath,
            color = BrightJungleGreen,
            style = Stroke(width = 6f)
        )

        // Grass tufts & pebbles along the ground
        val tuftSpacing = 75f
        var tuftScreenX = 10f - (worldX % tuftSpacing)
        while (tuftScreenX < size.width + 40f) {
            val tuftY = getGroundY(worldX + tuftScreenX)
            // Small grass tuft
            drawLine(
                color = BrightJungleGreen,
                start = Offset(tuftScreenX, tuftY),
                end = Offset(tuftScreenX - 6f, tuftY - 14f),
                strokeWidth = 3f
            )
            drawLine(
                color = LeafGreen,
                start = Offset(tuftScreenX, tuftY),
                end = Offset(tuftScreenX + 6f, tuftY - 16f),
                strokeWidth = 3f
            )
            tuftScreenX += tuftSpacing
        }
    }

    private fun DrawScope.renderObstacles() {
        for (obs in obstacles) {
            val screenX = obs.worldX - worldX
            if (screenX < -200f || screenX > size.width + 100f) continue

            val groundY = getGroundY(obs.worldX)

            when (obs.type) {
                ObstacleType.FALLEN_LOG -> {
                    // Fallen mossy log with bark rings
                    val logY = groundY - obs.height
                    drawRoundRect(
                        color = EarthBrown,
                        topLeft = Offset(screenX, logY),
                        size = Size(obs.width, obs.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
                    )
                    // Moss patch on top of log
                    drawRoundRect(
                        color = MossGreen,
                        topLeft = Offset(screenX + 10f, logY - 2f),
                        size = Size(obs.width - 20f, 14f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                    )
                    // Log end cut / ring
                    drawOval(
                        color = Color(0xFF8B5A2B),
                        topLeft = Offset(screenX + obs.width - 24f, logY + 8f),
                        size = Size(20f, obs.height - 16f)
                    )
                }
                ObstacleType.ROCK -> {
                    // Sturdy jungle rock with moss
                    val rockPath = Path().apply {
                        moveTo(screenX, groundY)
                        lineTo(screenX + 15f, groundY - obs.height + 12f)
                        lineTo(screenX + obs.width * 0.5f, groundY - obs.height)
                        lineTo(screenX + obs.width - 12f, groundY - obs.height + 18f)
                        lineTo(screenX + obs.width, groundY)
                        close()
                    }
                    drawPath(rockPath, color = Color(0xFF5A665A))
                    // Moss highlights
                    drawCircle(
                        color = LeafGreen,
                        radius = 16f,
                        center = Offset(screenX + obs.width * 0.45f, groundY - obs.height + 18f)
                    )
                }
                ObstacleType.SMALL_BUSH -> {
                    // Dense tropical bush
                    val bushCenterY = groundY - obs.height * 0.5f
                    drawCircle(
                        color = DeepJungleGreen,
                        radius = obs.width * 0.42f,
                        center = Offset(screenX + obs.width * 0.5f, bushCenterY)
                    )
                    drawCircle(
                        color = LeafGreen,
                        radius = obs.width * 0.35f,
                        center = Offset(screenX + obs.width * 0.35f, bushCenterY - 6f)
                    )
                    drawCircle(
                        color = BrightJungleGreen,
                        radius = obs.width * 0.30f,
                        center = Offset(screenX + obs.width * 0.65f, bushCenterY - 8f)
                    )
                }
                ObstacleType.TREE_ROOT -> {
                    // Gnarled root arching from the ground
                    val rootPath = Path().apply {
                        moveTo(screenX, groundY)
                        quadraticTo(
                            screenX + obs.width * 0.5f, groundY - obs.height * 1.5f,
                            screenX + obs.width, groundY
                        )
                    }
                    drawPath(rootPath, color = DarkSoil, style = Stroke(width = 24f))
                    drawPath(rootPath, color = EarthBrown, style = Stroke(width = 16f))
                }
                ObstacleType.THORNY_PLANT -> {
                    // Spiky carnivorous plant
                    val plantY = groundY - obs.height
                    drawOval(
                        color = Color(0xFF9C27B0),
                        topLeft = Offset(screenX + 12f, plantY),
                        size = Size(obs.width - 24f, obs.height * 0.6f)
                    )
                    // Spikes
                    val spikePath = Path().apply {
                        moveTo(screenX + 8f, plantY + 20f)
                        lineTo(screenX - 4f, plantY + 12f)
                        lineTo(screenX + 14f, plantY + 28f)
                        moveTo(screenX + obs.width - 8f, plantY + 20f)
                        lineTo(screenX + obs.width + 4f, plantY + 12f)
                        lineTo(screenX + obs.width - 14f, plantY + 28f)
                    }
                    drawPath(spikePath, color = BananaYellow, style = Fill)
                    // Stem
                    drawLine(
                        color = ForestGreen,
                        start = Offset(screenX + obs.width * 0.5f, plantY + obs.height * 0.5f),
                        end = Offset(screenX + obs.width * 0.5f, groundY),
                        strokeWidth = 10f
                    )
                }
                ObstacleType.MUD_PATCH -> {
                    // Dark slippery mud patch
                    drawOval(
                        color = DarkSoil.copy(alpha = 0.9f),
                        topLeft = Offset(screenX, groundY - 10f),
                        size = Size(obs.width, 22f)
                    )
                    // Mud bubbles
                    drawCircle(
                        color = Color(0xFF4A321E),
                        radius = 5f,
                        center = Offset(screenX + obs.width * 0.35f, groundY - 2f)
                    )
                }
                ObstacleType.BROKEN_BRANCH, ObstacleType.HANGING_VINE -> {
                    // Spiky branch or vine
                    val branchY = groundY - obs.height
                    drawLine(
                        color = EarthBrown,
                        start = Offset(screenX, groundY),
                        end = Offset(screenX + obs.width, branchY),
                        strokeWidth = 14f
                    )
                    drawLine(
                        color = LeafGreen,
                        start = Offset(screenX + obs.width * 0.6f, branchY + 18f),
                        end = Offset(screenX + obs.width * 0.75f, branchY + 6f),
                        strokeWidth = 5f
                    )
                }
            }
        }
    }

    private fun DrawScope.renderCollectibles() {
        for (col in collectibles) {
            if (col.collected) continue
            val screenX = col.worldX - worldX
            if (screenX < -100f || screenX > size.width + 100f) continue

            val cy = col.worldY + col.bounceOffset

            when (col.type) {
                CollectibleType.COIN -> {
                    // Shiny spinning gold coin
                    val spinScale = abs(cos(worldX * 0.035f + col.worldX))
                    val coinW = max(6f, col.size * spinScale)

                    // Outer golden ring
                    drawOval(
                        color = CoinGold,
                        topLeft = Offset(screenX - coinW * 0.5f, cy - col.size * 0.5f),
                        size = Size(coinW, col.size)
                    )
                    // Inner lighter shine
                    if (coinW > 12f) {
                        drawOval(
                            color = Color(0xFFFFE082),
                            topLeft = Offset(screenX - (coinW - 8f) * 0.5f, cy - (col.size - 8f) * 0.5f),
                            size = Size(coinW - 8f, col.size - 8f)
                        )
                    }
                }
                CollectibleType.BANANA -> {
                    // Bright crescent banana
                    val bPath = Path().apply {
                        moveTo(screenX - 16f, cy + 12f)
                        quadraticTo(screenX, cy + 18f, screenX + 16f, cy - 8f)
                        quadraticTo(screenX, cy + 8f, screenX - 16f, cy + 12f)
                    }
                    drawPath(bPath, color = BananaYellow, style = Fill)
                    drawPath(bPath, color = Color(0xFFE6B800), style = Stroke(width = 2.5f))
                    // Banana stem tip
                    drawCircle(color = ForestGreen, radius = 2.5f, center = Offset(screenX - 16f, cy + 12f))
                }
                CollectibleType.GOLDEN_BANANA -> {
                    // Glowing rare golden banana with aura
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x99FFD700),
                                Color.Transparent
                            ),
                            center = Offset(screenX, cy),
                            radius = col.size * 1.3f
                        ),
                        radius = col.size * 1.3f,
                        center = Offset(screenX, cy)
                    )
                    val gbPath = Path().apply {
                        moveTo(screenX - 22f, cy + 16f)
                        quadraticTo(screenX, cy + 24f, screenX + 22f, cy - 10f)
                        quadraticTo(screenX, cy + 10f, screenX - 22f, cy + 16f)
                    }
                    drawPath(gbPath, color = Color(0xFFFFEA00), style = Fill)
                    drawPath(gbPath, color = Color.White, style = Stroke(width = 3.5f))
                }
            }
        }
    }

    /**
     * Main Character: Athletic, Expressive Jungle Monkey
     * Renders responsive 2D vector monkey with running cycle, jumping arcs,
     * blinking eyes, waving tail, and the signature hand-licking sequence!
     */
    private fun DrawScope.renderMonkey() {
        val mx = monkeyScreenX
        val my = monkeyY

        val bodyColor = EarthBrown
        val chestColor = Color(0xFFD7A15C)
        val faceColor = Color(0xFFE8BC7A)
        val earInnerColor = Color(0xFFFFAB91)

        when (monkeyAnimState) {
            MonkeyAnimState.IDLE, MonkeyAnimState.RUNNING -> {
                // Running bounce
                val bobY = if (isGrounded) abs(sin(runCycle)) * 6f else 0f
                val bodyY = my - monkeyHeight + bobY

                // Tail (animated wavy tail behind body)
                val tailWave = sin(runCycle * 0.8f) * 18f
                val tailPath = Path().apply {
                    moveTo(mx + 20f, bodyY + 68f)
                    quadraticTo(
                        mx - 25f + tailWave, bodyY + 45f,
                        mx - 15f + tailWave * 1.5f, bodyY + 15f
                    )
                }
                drawPath(tailPath, color = bodyColor, style = Stroke(width = 9f))

                // Left Leg (back leg)
                val backLegAngle = sin(runCycle + PI.toFloat()) * 30f
                rotate(backLegAngle, pivot = Offset(mx + 38f, bodyY + 75f)) {
                    drawRoundRect(
                        color = Color(0xFF5A3A20),
                        topLeft = Offset(mx + 32f, bodyY + 75f),
                        size = Size(14f, 48f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(7f, 7f)
                    )
                }

                // Left Arm (back arm)
                val backArmAngle = sin(runCycle) * 32f
                rotate(backArmAngle, pivot = Offset(mx + 45f, bodyY + 42f)) {
                    drawRoundRect(
                        color = Color(0xFF5A3A20),
                        topLeft = Offset(mx + 40f, bodyY + 40f),
                        size = Size(12f, 40f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                    )
                }

                // Main Body / Torso
                drawRoundRect(
                    color = bodyColor,
                    topLeft = Offset(mx + 26f, bodyY + 28f),
                    size = Size(46f, 58f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(22f, 22f)
                )
                // Lighter Chest Patch
                drawOval(
                    color = chestColor,
                    topLeft = Offset(mx + 40f, bodyY + 36f),
                    size = Size(26f, 42f)
                )

                // Head
                drawCircle(
                    color = bodyColor,
                    radius = 24f,
                    center = Offset(mx + 54f, bodyY + 18f)
                )
                // Monkey Face
                drawOval(
                    color = faceColor,
                    topLeft = Offset(mx + 44f, bodyY + 6f),
                    size = Size(28f, 26f)
                )
                // Ears
                drawCircle(color = bodyColor, radius = 10f, center = Offset(mx + 34f, bodyY + 15f))
                drawCircle(color = earInnerColor, radius = 6f, center = Offset(mx + 34f, bodyY + 15f))

                // Expressive Eyes
                if (isBlinking) {
                    // Closed eye line
                    drawLine(
                        color = DarkUI,
                        start = Offset(mx + 54f, bodyY + 14f),
                        end = Offset(mx + 66f, bodyY + 14f),
                        strokeWidth = 2.5f
                    )
                } else {
                    // Open eyes
                    drawCircle(color = Color.White, radius = 6f, center = Offset(mx + 58f, bodyY + 14f))
                    drawCircle(color = DarkUI, radius = 3.5f, center = Offset(mx + 60f, bodyY + 14f))
                    drawCircle(color = Color.White, radius = 1.2f, center = Offset(mx + 61f, bodyY + 13f))
                }

                // Cute Smile & Snout
                drawOval(
                    color = chestColor,
                    topLeft = Offset(mx + 54f, bodyY + 19f),
                    size = Size(18f, 13f)
                )
                drawCircle(color = DarkUI, radius = 2f, center = Offset(mx + 63f, bodyY + 23f))

                // Right Leg (front leg)
                val frontLegAngle = sin(runCycle) * 32f
                rotate(frontLegAngle, pivot = Offset(mx + 48f, bodyY + 75f)) {
                    drawRoundRect(
                        color = bodyColor,
                        topLeft = Offset(mx + 42f, bodyY + 75f),
                        size = Size(15f, 50f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(7f, 7f)
                    )
                }

                // Right Arm (front arm)
                val frontArmAngle = sin(runCycle + PI.toFloat()) * 32f
                rotate(frontArmAngle, pivot = Offset(mx + 46f, bodyY + 42f)) {
                    drawRoundRect(
                        color = bodyColor,
                        topLeft = Offset(mx + 42f, bodyY + 40f),
                        size = Size(13f, 42f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                    )
                }
            }

            MonkeyAnimState.JUMPING, MonkeyAnimState.FALLING -> {
                // Tucked agile jumping pose
                val bodyY = my - monkeyHeight

                // Tail curled dynamically
                val tailPath = Path().apply {
                    moveTo(mx + 20f, bodyY + 65f)
                    quadraticTo(mx - 30f, bodyY + 75f, mx - 20f, bodyY + 30f)
                }
                drawPath(tailPath, color = bodyColor, style = Stroke(width = 9f))

                // Tucked legs
                drawRoundRect(
                    color = bodyColor,
                    topLeft = Offset(mx + 30f, bodyY + 68f),
                    size = Size(20f, 32f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                )

                // Torso & Chest
                drawRoundRect(
                    color = bodyColor,
                    topLeft = Offset(mx + 28f, bodyY + 26f),
                    size = Size(46f, 54f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(22f, 22f)
                )
                drawOval(
                    color = chestColor,
                    topLeft = Offset(mx + 42f, bodyY + 34f),
                    size = Size(26f, 38f)
                )

                // Head tilted forward
                drawCircle(color = bodyColor, radius = 24f, center = Offset(mx + 56f, bodyY + 16f))
                drawOval(color = faceColor, topLeft = Offset(mx + 46f, bodyY + 4f), size = Size(28f, 26f))
                drawCircle(color = bodyColor, radius = 10f, center = Offset(mx + 36f, bodyY + 13f))
                drawCircle(color = earInnerColor, radius = 6f, center = Offset(mx + 36f, bodyY + 13f))

                // Wide confident eyes
                drawCircle(color = Color.White, radius = 6.5f, center = Offset(mx + 60f, bodyY + 13f))
                drawCircle(color = DarkUI, radius = 3.5f, center = Offset(mx + 62f, bodyY + 13f))

                // Arms reaching forward/up
                drawRoundRect(
                    color = bodyColor,
                    topLeft = Offset(mx + 52f, bodyY + 24f),
                    size = Size(36f, 13f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                )
            }

            MonkeyAnimState.COLLIDED, MonkeyAnimState.STOPPED -> {
                // Collision stumble & sit on ground
                val sitY = my - 95f

                // Stunned/dizzy body sitting
                drawRoundRect(
                    color = bodyColor,
                    topLeft = Offset(mx + 20f, sitY + 20f),
                    size = Size(52f, 52f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f, 24f)
                )
                drawOval(
                    color = chestColor,
                    topLeft = Offset(mx + 32f, sitY + 28f),
                    size = Size(32f, 36f)
                )

                // Drooping tail
                drawLine(
                    color = bodyColor,
                    start = Offset(mx + 20f, sitY + 60f),
                    end = Offset(mx - 15f, my),
                    strokeWidth = 9f
                )

                // Head tilted
                drawCircle(color = bodyColor, radius = 23f, center = Offset(mx + 46f, sitY + 8f))
                drawOval(color = faceColor, topLeft = Offset(mx + 36f, sitY - 4f), size = Size(26f, 24f))

                // Dizzy eyes (X)
                drawLine(
                    color = DarkUI,
                    start = Offset(mx + 45f, sitY + 4f),
                    end = Offset(mx + 53f, sitY + 12f),
                    strokeWidth = 2.5f
                )
                drawLine(
                    color = DarkUI,
                    start = Offset(mx + 53f, sitY + 4f),
                    end = Offset(mx + 45f, sitY + 12f),
                    strokeWidth = 2.5f
                )
            }

            MonkeyAnimState.LOOKING_AT_HAND, MonkeyAnimState.LICKING_HAND -> {
                // Signature Hand-Lick Humorous Animation!
                val sitY = my - 95f

                // Body sitting comfortably on ground
                drawRoundRect(
                    color = bodyColor,
                    topLeft = Offset(mx + 20f, sitY + 20f),
                    size = Size(52f, 52f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f, 24f)
                )
                drawOval(
                    color = chestColor,
                    topLeft = Offset(mx + 30f, sitY + 28f),
                    size = Size(32f, 36f)
                )

                // Head looking down toward paw
                val headX = mx + 46f
                val headY = sitY + 8f
                drawCircle(color = bodyColor, radius = 24f, center = Offset(headX, headY))
                drawOval(color = faceColor, topLeft = Offset(headX - 12f, headY - 10f), size = Size(28f, 26f))
                drawCircle(color = bodyColor, radius = 10f, center = Offset(headX - 22f, headY + 2f))
                drawCircle(color = earInnerColor, radius = 6f, center = Offset(headX - 22f, headY + 2f))

                // Inquisitive eyes looking down at hand
                drawCircle(color = Color.White, radius = 6f, center = Offset(headX + 4f, headY - 1f))
                drawCircle(color = DarkUI, radius = 3.5f, center = Offset(headX + 6f, headY + 2f))

                // Left Arm resting
                drawRoundRect(
                    color = bodyColor,
                    topLeft = Offset(mx + 18f, sitY + 48f),
                    size = Size(18f, 32f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                )

                // Right Arm raised to face holding paw!
                val pawX = headX + 16f
                val pawY = headY + 16f

                val armPath = Path().apply {
                    moveTo(mx + 50f, sitY + 40f)
                    quadraticTo(mx + 68f, sitY + 36f, pawX, pawY)
                }
                drawPath(armPath, color = bodyColor, style = Stroke(width = 14f))

                // Monkey Paw
                drawCircle(color = chestColor, radius = 9f, center = Offset(pawX, pawY))

                if (monkeyAnimState == MonkeyAnimState.LICKING_HAND) {
                    // Tongue licking the hand with funny squishy animation
                    val lickExt = 8f + sin(lickPhase) * 6f
                    val tonguePath = Path().apply {
                        moveTo(headX + 10f, headY + 12f)
                        quadraticTo(pawX - 2f, pawY - 4f, pawX + lickExt - 6f, pawY)
                        quadraticTo(pawX - 2f, pawY + 5f, headX + 10f, headY + 16f)
                    }
                    drawPath(tonguePath, color = Color(0xFFFF5252), style = Fill)

                    // Small saliva sparkle droplets
                    if (sin(lickPhase) > 0.5f) {
                        drawCircle(color = Color(0xAA80D8FF), radius = 3f, center = Offset(pawX + 8f, pawY - 8f))
                    }
                }
            }
            else -> {}
        }
    }

    private fun DrawScope.renderParticles() {
        for (p in particles) {
            drawCircle(
                color = p.color.copy(alpha = p.alpha),
                radius = p.size * 0.5f,
                center = Offset(p.x, p.y)
            )
        }
    }

    private fun DrawScope.renderFloatingTexts() {
        // Floating texts are also rendered in UI overlay or via canvas where suitable
    }
}
