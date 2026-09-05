package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.JungleMonkeyGameEngine
import com.example.model.GameState
import com.example.ui.components.GameOverOverlay
import com.example.ui.components.PauseOverlay
import com.example.ui.components.ScoreHud
import com.example.ui.theme.BrightJungleGreen
import com.example.ui.theme.CoinGold

@Composable
fun GameScreen(
    engine: JungleMonkeyGameEngine,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    var frameTick by remember { mutableIntStateOf(0) }
    var currentScore by remember { mutableIntStateOf(0) }
    var bestScore by remember { mutableIntStateOf(engine.scoreStorage.bestScore) }
    var currentCoins by remember { mutableIntStateOf(0) }
    var currentBananas by remember { mutableIntStateOf(0) }
    var currentGameState by remember { mutableStateOf(engine.gameState) }

    // High-performance game loop at 60 FPS
    LaunchedEffect(Unit) {
        var lastTime = 0L
        while (true) {
            withFrameNanos { timeNanos ->
                if (lastTime == 0L) {
                    lastTime = timeNanos
                }
                val dt = (timeNanos - lastTime) / 1_000_000_000f
                lastTime = timeNanos

                // Update physics & simulation
                engine.update(dt)

                // Sync Compose states
                frameTick++
                currentScore = engine.currentScore
                bestScore = engine.scoreStorage.bestScore
                currentCoins = engine.coinsCollectedInRun
                currentBananas = engine.bananasCollectedInRun
                currentGameState = engine.gameState
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                engine.resize(size.width.toFloat(), size.height.toFloat())
            }
            .pointerInput(Unit) {
                // Tap anywhere to jump
                detectTapGestures(
                    onTap = {
                        engine.onJumpPressed()
                    }
                )
            }
            .pointerInput(Unit) {
                // Swipe-up to jump
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -15f) {
                        engine.onJumpPressed()
                    }
                }
            }
            .testTag("gameplay_canvas_box")
    ) {
        // 2D Game World Canvas Rendering
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Reference frameTick to ensure recomposition every frame
            val _tick = frameTick
            engine.render(this)
        }

        // Countdown Overlay (3, 2, 1, GO!)
        if (currentGameState == GameState.READY) {
            val countdownInt = engine.countdownTimer.toInt() + 1
            val countdownText = if (countdownInt in 1..3) "$countdownInt" else "GO!"

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = countdownText,
                    color = CoinGold,
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 4.sp
                )
            }
        }

        // Gameplay HUD
        if (currentGameState == GameState.RUNNING || currentGameState == GameState.READY || currentGameState == GameState.COLLISION) {
            ScoreHud(
                score = currentScore,
                bestScore = bestScore,
                coins = currentCoins,
                bananas = currentBananas,
                floatingTexts = engine.floatingTexts,
                onPauseClicked = {
                    if (engine.gameState == GameState.RUNNING) {
                        engine.gameState = GameState.PAUSED
                        engine.audioService.pauseMusic()
                        engine.audioService.playButtonClick()
                    }
                }
            )
        }

        // Pause Menu Dialog
        if (currentGameState == GameState.PAUSED) {
            PauseOverlay(
                score = currentScore,
                bestScore = bestScore,
                onResume = {
                    engine.gameState = GameState.RUNNING
                    engine.audioService.resumeMusic()
                    engine.audioService.playButtonClick()
                },
                onRestart = {
                    engine.audioService.playButtonClick()
                    engine.startNewGame()
                },
                onHome = {
                    engine.audioService.playButtonClick()
                    engine.audioService.stopMusic()
                    onBackToHome()
                }
            )
        }

        // Game Over Screen (Shown after hand-licking sequence finishes)
        if (currentGameState == GameState.GAME_OVER) {
            GameOverOverlay(
                score = currentScore,
                bestScore = bestScore,
                isNewBest = engine.isNewBestScore,
                coins = currentCoins,
                bananas = currentBananas,
                onRunAgain = {
                    engine.audioService.playButtonClick()
                    engine.startNewGame()
                },
                onHome = {
                    engine.audioService.playButtonClick()
                    engine.audioService.stopMusic()
                    onBackToHome()
                }
            )
        }
    }
}
