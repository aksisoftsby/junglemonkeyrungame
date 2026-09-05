package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Procedural Audio Service for Jungle Monkey Run.
 * Generates all sound effects and background jungle music procedurally via AudioTrack.
 * 100% offline, zero latency, zero external asset dependencies.
 */
class AudioService {

    var soundEnabled: Boolean = true
    var musicEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) {
                stopMusic()
            } else if (isMusicPlayingDesired) {
                startMusic()
            }
        }

    private val audioScope = CoroutineScope(Dispatchers.Default)
    private var musicJob: Job? = null
    private var isMusicPlayingDesired: Boolean = false

    private val sampleRate = 22050

    private fun playPcm(samples: ShortArray) {
        if (!soundEnabled) return
        audioScope.launch {
            try {
                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(samples.size * 2, minBufferSize)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(samples, 0, samples.size)
                track.play()
                // Wait for playback to finish before release
                val durationMs = (samples.size * 1000L) / sampleRate
                delay(durationMs + 50)
                track.stop()
                track.release()
            } catch (_: Exception) {
                // Ignore audio errors gracefully
            }
        }
    }

    fun playJump() {
        // Upward pitch sweep 280Hz -> 650Hz
        val numSamples = (sampleRate * 0.16).toInt()
        val samples = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val freq = 280.0 + 370.0 * progress
            val envelope = (1.0 - progress) * 0.9
            phase += 2.0 * PI * freq / sampleRate
            samples[i] = (sin(phase) * envelope * Short.MAX_VALUE * 0.6).toInt().toShort()
        }
        playPcm(samples)
    }

    fun playLand() {
        // Low soft thud (120Hz -> 50Hz)
        val numSamples = (sampleRate * 0.08).toInt()
        val samples = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val freq = 120.0 - 70.0 * progress
            val envelope = (1.0 - progress) * (1.0 - progress)
            phase += 2.0 * PI * freq / sampleRate
            samples[i] = (sin(phase) * envelope * Short.MAX_VALUE * 0.5).toInt().toShort()
        }
        playPcm(samples)
    }

    fun playCoin() {
        // Bright bell chime (987Hz B5 + 1318Hz E6)
        val numSamples = (sampleRate * 0.18).toInt()
        val samples = ShortArray(numSamples)
        var phase1 = 0.0
        var phase2 = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val envelope = (1.0 - progress)
            phase1 += 2.0 * PI * 987.0 / sampleRate
            phase2 += 2.0 * PI * 1318.0 / sampleRate
            val v = (sin(phase1) * 0.5 + sin(phase2) * 0.5) * envelope
            samples[i] = (v * Short.MAX_VALUE * 0.55).toInt().toShort()
        }
        playPcm(samples)
    }

    fun playBanana() {
        // Playful double arpeggio (784Hz G5 -> 1046Hz C6)
        val numSamples = (sampleRate * 0.22).toInt()
        val samples = ShortArray(numSamples)
        var phase = 0.0
        val mid = numSamples / 2
        for (i in 0 until numSamples) {
            val isSecond = i >= mid
            val freq = if (!isSecond) 784.0 else 1046.0
            val subProgress = if (!isSecond) i.toDouble() / mid else (i - mid).toDouble() / mid
            val envelope = (1.0 - subProgress) * 0.85
            phase += 2.0 * PI * freq / sampleRate
            samples[i] = (sin(phase) * envelope * Short.MAX_VALUE * 0.6).toInt().toShort()
        }
        playPcm(samples)
    }

    fun playGoldenBanana() {
        // Celebratory bright fanfare arpeggio (C6, E6, G6, C7)
        val numSamples = (sampleRate * 0.40).toInt()
        val samples = ShortArray(numSamples)
        var phase = 0.0
        val notes = doubleArrayOf(1046.5, 1318.5, 1567.98, 2093.0)
        val seg = numSamples / notes.size
        for (i in 0 until numSamples) {
            val noteIdx = minOf(i / seg, notes.size - 1)
            val subProgress = (i % seg).toDouble() / seg
            val envelope = (1.0 - subProgress) * 0.9
            phase += 2.0 * PI * notes[noteIdx] / sampleRate
            samples[i] = (sin(phase) * envelope * Short.MAX_VALUE * 0.65).toInt().toShort()
        }
        playPcm(samples)
    }

    fun playHit() {
        // Dull wood impact bonk with noise crunch
        val numSamples = (sampleRate * 0.25).toInt()
        val samples = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val freq = 220.0 - 150.0 * progress
            val envelope = (1.0 - progress) * (1.0 - progress)
            phase += 2.0 * PI * freq / sampleRate
            val noise = (Math.random() * 2.0 - 1.0) * 0.3 * (1.0 - progress)
            val v = (sin(phase) * 0.7 + noise) * envelope
            samples[i] = (v * Short.MAX_VALUE * 0.7).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playPcm(samples)
    }

    fun playHandLick() {
        // Funny cute squeaky/squishy licking sound (frequency modulation sweep)
        val numSamples = (sampleRate * 0.28).toInt()
        val samples = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            // Pitch rises then dips like a cartoon lick
            val freq = 550.0 + sin(progress * PI) * 350.0
            val envelope = sin(progress * PI) * 0.8
            phase += 2.0 * PI * freq / sampleRate
            samples[i] = (sin(phase) * envelope * Short.MAX_VALUE * 0.55).toInt().toShort()
        }
        playPcm(samples)
    }

    fun playGameOver() {
        // Playful sad stumble chords
        val numSamples = (sampleRate * 0.55).toInt()
        val samples = ShortArray(numSamples)
        val notes = doubleArrayOf(440.0, 392.0, 349.2, 329.6)
        val seg = numSamples / notes.size
        var phase = 0.0
        for (i in 0 until numSamples) {
            val noteIdx = minOf(i / seg, notes.size - 1)
            val subProgress = (i % seg).toDouble() / seg
            val envelope = (1.0 - subProgress) * 0.85
            phase += 2.0 * PI * notes[noteIdx] / sampleRate
            samples[i] = (sin(phase) * envelope * Short.MAX_VALUE * 0.6).toInt().toShort()
        }
        playPcm(samples)
    }

    fun playButtonClick() {
        // Crisp woodblock click
        val numSamples = (sampleRate * 0.05).toInt()
        val samples = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val envelope = (1.0 - progress) * (1.0 - progress)
            phase += 2.0 * PI * 850.0 / sampleRate
            samples[i] = (sin(phase) * envelope * Short.MAX_VALUE * 0.5).toInt().toShort()
        }
        playPcm(samples)
    }

    fun startMusic() {
        isMusicPlayingDesired = true
        if (!musicEnabled || musicJob?.isActive == true) return

        musicJob = audioScope.launch {
            try {
                // Loop upbeat jungle rhythmic marimba pattern
                // Notes in pentatonic scale: C4(261.6), D4(293.7), E4(329.6), G4(392.0), A4(440.0), C5(523.2)
                val melody = intArrayOf(
                    392, 440, 523, 440, 392, 329, 392, 0,
                    440, 523, 587, 523, 440, 392, 329, 294,
                    329, 392, 440, 523, 392, 440, 329, 0,
                    294, 329, 392, 329, 294, 261, 294, 0
                )
                val noteDurationMs = 150L
                val noteSamples = ((sampleRate * noteDurationMs) / 1000).toInt()

                var melodyIndex = 0

                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBufferSize, noteSamples * 4))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()

                val buffer = ShortArray(noteSamples)

                while (isActive && musicEnabled && isMusicPlayingDesired) {
                    val freq = melody[melodyIndex].toDouble()
                    melodyIndex = (melodyIndex + 1) % melody.size

                    var phase = 0.0
                    for (i in 0 until noteSamples) {
                        if (freq == 0.0) {
                            buffer[i] = 0
                        } else {
                            val progress = i.toDouble() / noteSamples
                            // Warm marimba envelope
                            val envelope = (1.0 - progress) * (1.0 - progress * 0.7)
                            phase += 2.0 * PI * freq / sampleRate
                            val tone = (sin(phase) + 0.35 * sin(phase * 2.0) + 0.15 * sin(phase * 3.0))
                            buffer[i] = (tone * envelope * Short.MAX_VALUE * 0.22).toInt().toShort()
                        }
                    }

                    track.write(buffer, 0, buffer.size)
                }

                track.stop()
                track.release()
            } catch (_: Exception) {
                // Ignore gracefully
            }
        }
    }

    fun stopMusic() {
        isMusicPlayingDesired = false
        musicJob?.cancel()
        musicJob = null
    }

    fun pauseMusic() {
        musicJob?.cancel()
        musicJob = null
    }

    fun resumeMusic() {
        if (isMusicPlayingDesired && musicEnabled) {
            startMusic()
        }
    }
}
