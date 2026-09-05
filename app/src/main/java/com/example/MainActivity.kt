package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.audio.AudioService
import com.example.data.ScoreStorage
import com.example.game.JungleMonkeyGameEngine
import com.example.model.GameState
import com.example.ui.screens.GameScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TutorialDialog
import com.example.ui.theme.DarkUI
import com.example.ui.theme.JungleMonkeyTheme

enum class ScreenView {
    HOME,
    GAME,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private lateinit var audioService: AudioService
    private lateinit var scoreStorage: ScoreStorage
    private lateinit var gameEngine: JungleMonkeyGameEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        audioService = AudioService()
        scoreStorage = ScoreStorage(applicationContext)

        // Apply persisted audio preferences
        audioService.soundEnabled = scoreStorage.soundEnabled
        audioService.musicEnabled = scoreStorage.musicEnabled

        gameEngine = JungleMonkeyGameEngine(audioService, scoreStorage)

        setContent {
            JungleMonkeyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkUI
                ) {
                    JungleMonkeyApp(
                        engine = gameEngine,
                        audioService = audioService,
                        scoreStorage = scoreStorage
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::audioService.isInitialized) {
            audioService.pauseMusic()
        }
        if (::gameEngine.isInitialized && gameEngine.gameState == GameState.RUNNING) {
            gameEngine.gameState = GameState.PAUSED
        }
    }

    override fun onResume() {
        super.onResume()
        if (::audioService.isInitialized && ::gameEngine.isInitialized) {
            if (gameEngine.gameState == GameState.RUNNING) {
                audioService.resumeMusic()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::audioService.isInitialized) {
            audioService.stopMusic()
        }
    }
}

@Composable
fun JungleMonkeyApp(
    engine: JungleMonkeyGameEngine,
    audioService: AudioService,
    scoreStorage: ScoreStorage
) {
    var currentView by remember { mutableStateOf(ScreenView.HOME) }
    var showTutorial by remember { mutableStateOf(!scoreStorage.tutorialCompleted) }
    var soundEnabled by remember { mutableStateOf(scoreStorage.soundEnabled) }
    var musicEnabled by remember { mutableStateOf(scoreStorage.musicEnabled) }
    var bestScoreState by remember { mutableIntStateOf(scoreStorage.bestScore) }
    var totalCoinsState by remember { mutableIntStateOf(scoreStorage.totalCoins) }
    var totalBananasState by remember { mutableIntStateOf(scoreStorage.totalBananas) }

    // Lifecycle observer for background/foreground handling
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                audioService.pauseMusic()
                if (engine.gameState == GameState.RUNNING) {
                    engine.gameState = GameState.PAUSED
                }
            } else if (event == Lifecycle.Event.ON_RESUME) {
                if (engine.gameState == GameState.RUNNING) {
                    audioService.resumeMusic()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    when (currentView) {
        ScreenView.HOME -> {
            HomeScreen(
                bestScore = bestScoreState,
                totalCoins = totalCoinsState,
                totalBananas = totalBananasState,
                onPlayClicked = {
                    audioService.playButtonClick()
                    if (!scoreStorage.tutorialCompleted) {
                        showTutorial = true
                    } else {
                        engine.startNewGame()
                        currentView = ScreenView.GAME
                    }
                },
                onHowToPlayClicked = {
                    audioService.playButtonClick()
                    showTutorial = true
                },
                onSettingsClicked = {
                    audioService.playButtonClick()
                    currentView = ScreenView.SETTINGS
                }
            )
        }

        ScreenView.GAME -> {
            BackHandler {
                if (engine.gameState == GameState.RUNNING) {
                    engine.gameState = GameState.PAUSED
                    audioService.pauseMusic()
                } else {
                    audioService.stopMusic()
                    bestScoreState = scoreStorage.bestScore
                    totalCoinsState = scoreStorage.totalCoins
                    totalBananasState = scoreStorage.totalBananas
                    currentView = ScreenView.HOME
                }
            }

            GameScreen(
                engine = engine,
                onBackToHome = {
                    audioService.stopMusic()
                    bestScoreState = scoreStorage.bestScore
                    totalCoinsState = scoreStorage.totalCoins
                    totalBananasState = scoreStorage.totalBananas
                    currentView = ScreenView.HOME
                }
            )
        }

        ScreenView.SETTINGS -> {
            BackHandler {
                currentView = ScreenView.HOME
            }

            SettingsScreen(
                soundEnabled = soundEnabled,
                musicEnabled = musicEnabled,
                onSoundToggled = { enabled ->
                    soundEnabled = enabled
                    scoreStorage.soundEnabled = enabled
                    audioService.soundEnabled = enabled
                },
                onMusicToggled = { enabled ->
                    musicEnabled = enabled
                    scoreStorage.musicEnabled = enabled
                    audioService.musicEnabled = enabled
                },
                onShowTutorialAgain = {
                    audioService.playButtonClick()
                    showTutorial = true
                },
                onBack = {
                    audioService.playButtonClick()
                    currentView = ScreenView.HOME
                }
            )
        }
    }

    // First-time or requested Tutorial Dialog
    if (showTutorial) {
        TutorialDialog(
            onDismiss = {
                audioService.playButtonClick()
                showTutorial = false
                scoreStorage.tutorialCompleted = true
                if (currentView == ScreenView.HOME && engine.gameState != GameState.RUNNING) {
                    engine.startNewGame()
                    currentView = ScreenView.GAME
                }
            }
        )
    }
}
