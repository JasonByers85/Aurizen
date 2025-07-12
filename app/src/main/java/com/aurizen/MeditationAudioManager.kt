package com.aurizen

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import java.io.IOException
import kotlin.math.*

class MeditationAudioManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var binauralAudioTrack: AudioTrack? = null
    private var currentSound: BackgroundSound = BackgroundSound.NONE
    private var currentTone: BinauralTone = BinauralTone.NONE
    private var binauralJob: Job? = null
    private val TAG = "MeditationAudioManager"

    // Volume controls
    private var backgroundVolume: Float = 0.3f
    private var binauralVolume: Float = 0.1f
    
    // TTS Volume control - handled via TTS parameters instead of system volume
    private var ttsVolumeLevel: Float = 0.8f

    // Audio parameters for binaural generation
    private val sampleRate = 44100
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_STEREO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    fun playBackgroundSound(sound: BackgroundSound) {
        Log.d(TAG, "playBackgroundSound called with: ${sound.displayName}")

        if (sound == BackgroundSound.NONE) {
            stopBackgroundSound()
            return
        }

        if (currentSound == sound && mediaPlayer?.isPlaying == true) {
            Log.d(TAG, "Already playing this sound")
            return
        }

        try {
            stopBackgroundSound()
            currentSound = sound

            val resourceId = getResourceId(sound.fileName)
            if (resourceId == 0) {
                Log.w(TAG, "Audio file not found for ${sound.fileName}, generating fallback")
                playFallbackSound()
                return
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )

                setDataSource(context, Uri.parse("android.resource://${context.packageName}/$resourceId"))
                isLooping = true
                setVolume(backgroundVolume, backgroundVolume) // Apply current volume

                prepareAsync()
                setOnPreparedListener { mp ->
                    mp.start()
                    Log.d(TAG, "Started playing background sound: ${sound.displayName}")
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    playFallbackSound()
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing background sound", e)
            playFallbackSound()
        }
    }

    private fun getResourceId(fileName: String): Int {
        return if (fileName.isNotEmpty()) {
            context.resources.getIdentifier(fileName, "raw", context.packageName)
        } else {
            0
        }
    }

    private fun playFallbackSound() {
        Log.d(TAG, "Playing fallback silence")
        // Just continue silently - meditation can work without background sounds
    }

    fun pauseBackgroundSound() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                Log.d(TAG, "Paused background sound")
            }
        }
    }

    fun resumeBackgroundSound() {
        mediaPlayer?.let { player ->
            if (!player.isPlaying) {
                player.start()
                Log.d(TAG, "Resumed background sound")
            }
        }
    }

    fun stopBackgroundSound() {
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
                Log.d(TAG, "Stopped and released background sound")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping background sound", e)
            }
        }
        mediaPlayer = null
        currentSound = BackgroundSound.NONE
    }

    fun playBinauralTone(tone: BinauralTone) {
        Log.d(TAG, "playBinauralTone called with: ${tone.displayName} (${tone.frequency}Hz)")

        if (tone == BinauralTone.NONE) {
            stopBinauralTone()
            return
        }

        if (currentTone == tone && binauralAudioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
            Log.d(TAG, "Already playing this binaural tone")
            return
        }

        try {
            stopBinauralTone()
            currentTone = tone

            // Generate binaural beats using AudioTrack for precise control
            generateBinauralBeats(tone.frequency)

        } catch (e: Exception) {
            Log.e(TAG, "Error playing binaural tone", e)
        }
    }

    private fun generateBinauralBeats(beatFrequency: Float) {
        try {
            Log.d(TAG, "Generating binaural beats at ${beatFrequency}Hz")

            binauralAudioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize * 4) // Larger buffer for smooth playback
                .build()

            binauralAudioTrack?.play()

            // Generate audio in a coroutine
            binauralJob = CoroutineScope(Dispatchers.IO).launch {
                val baseFreq = 200f // Base carrier frequency
                val leftFreq = baseFreq
                val rightFreq = baseFreq + beatFrequency

                val chunkSize = 4096 // Samples per chunk
                val audioBuffer = ShortArray(chunkSize * 2) // Stereo

                var phase = 0.0

                while (isActive && binauralAudioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    // Generate audio chunk
                    for (i in 0 until chunkSize) {
                        val time = phase / sampleRate

                        // Apply binaural volume to the generated samples
                        val amplitude = binauralVolume * 0.1 * Short.MAX_VALUE

                        // Left channel (base frequency)
                        val leftSample = (sin(2 * PI * leftFreq * time) * amplitude).toInt().toShort()
                        // Right channel (base + beat frequency)
                        val rightSample = (sin(2 * PI * rightFreq * time) * amplitude).toInt().toShort()

                        audioBuffer[i * 2] = leftSample      // Left
                        audioBuffer[i * 2 + 1] = rightSample // Right

                        phase++
                    }

                    // Write to audio track
                    val written = binauralAudioTrack?.write(audioBuffer, 0, audioBuffer.size) ?: -1
                    if (written < 0) {
                        Log.e(TAG, "Error writing to AudioTrack: $written")
                        break
                    }

                    // Small delay to prevent overwhelming the audio system
                    delay(10)
                }
            }

            Log.d(TAG, "Binaural tone generation started: ${beatFrequency}Hz")

        } catch (e: Exception) {
            Log.e(TAG, "Error generating binaural beats", e)
            stopBinauralTone()
        }
    }

    fun pauseBinauralTone() {
        binauralAudioTrack?.let { track ->
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause()
                Log.d(TAG, "Paused binaural tone")
            }
        }
    }

    fun resumeBinauralTone() {
        binauralAudioTrack?.let { track ->
            if (track.playState == AudioTrack.PLAYSTATE_PAUSED) {
                track.play()
                Log.d(TAG, "Resumed binaural tone")
            }
        }
    }

    fun stopBinauralTone() {
        binauralJob?.cancel()
        binauralJob = null

        binauralAudioTrack?.let { track ->
            try {
                if (track.playState != AudioTrack.PLAYSTATE_STOPPED) {
                    track.stop()
                }
                track.release()
                Log.d(TAG, "Stopped and released binaural tone")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping binaural tone", e)
            }
        }
        binauralAudioTrack = null
        currentTone = BinauralTone.NONE
    }

    fun setVolume(volume: Float) {
        backgroundVolume = volume
        mediaPlayer?.setVolume(volume, volume)
        Log.d(TAG, "Set background volume to: $volume")
    }

    fun setBinauralVolume(volume: Float) {
        binauralVolume = volume
        Log.d(TAG, "Set binaural volume to: $volume")
        // Note: For active binaural tones, volume change will apply to new audio generation cycles
    }
    
    // TTS Volume Control - returns the volume level for TTS engine to use
    fun setTtsVolume(volume: Float) {
        ttsVolumeLevel = volume
        Log.d(TAG, "Set TTS volume level to: $volume")
    }
    
    fun getTtsVolume(): Float = ttsVolumeLevel

    fun release() {
        Log.d(TAG, "Releasing MeditationAudioManager")
        stopBackgroundSound()
        stopBinauralTone()
    }

    // Test function to verify binaural tone generation
    fun testBinauralTone() {
        Log.d(TAG, "Testing binaural tone generation...")
        playBinauralTone(BinauralTone.ANXIETY_RELIEF)

        // Auto-stop after 5 seconds for testing
        CoroutineScope(Dispatchers.IO).launch {
            delay(5000)
            stopBinauralTone()
            Log.d(TAG, "Test binaural tone stopped")
        }
    }
}