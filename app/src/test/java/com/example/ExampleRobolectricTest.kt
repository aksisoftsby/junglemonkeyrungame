package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ScoreStorage
import com.example.model.CollectibleType
import com.example.model.GameState
import com.example.model.MonkeyAnimState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Jungle Monkey Run", appName)
    }

    @Test
    fun `score storage persists best score and tutorial flag`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = ScoreStorage(context)

        storage.bestScore = 500
        assertEquals(500, storage.bestScore)

        val updated = storage.updateScoreIfBest(800)
        assertTrue(updated)
        assertEquals(800, storage.bestScore)

        val notUpdated = storage.updateScoreIfBest(600)
        assertFalse(notUpdated)
        assertEquals(800, storage.bestScore)

        storage.tutorialCompleted = true
        assertTrue(storage.tutorialCompleted)

        storage.addCollectibles(coins = 5, bananas = 2)
        assertEquals(5, storage.totalCoins)
        assertEquals(2, storage.totalBananas)
    }

    @Test
    fun `collectible points match game design specifications`() {
        assertEquals(10, CollectibleType.COIN.points)
        assertEquals(25, CollectibleType.BANANA.points)
        assertEquals(100, CollectibleType.GOLDEN_BANANA.points)
    }

    @Test
    fun `game states and animation transitions cover full lifecycle`() {
        val states = GameState.values()
        assertTrue(states.contains(GameState.HOME))
        assertTrue(states.contains(GameState.READY))
        assertTrue(states.contains(GameState.RUNNING))
        assertTrue(states.contains(GameState.COLLISION))
        assertTrue(states.contains(GameState.GAME_OVER))

        val anims = MonkeyAnimState.values()
        assertTrue(anims.contains(MonkeyAnimState.RUNNING))
        assertTrue(anims.contains(MonkeyAnimState.JUMPING))
        assertTrue(anims.contains(MonkeyAnimState.LICKING_HAND))
    }
}
