package com.example.flipclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import com.example.domain.model.AmbientSoundType
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Pro-grade offline 44.1 kHz Stereo DSP soundscape & White Noise synthesizer.
 * - Zero audio loops or seams: endless organic procedural synthesis with stereo decorrelation.
 * - Buffer-free & crash-free: generous kernel ring buffer, audio thread priority, synchronized lifecycle.
 * - Click-free: 350ms soft fade-in, sample-accurate volume slewing, transient call ducking/pause.
 * - Backward compatible from Android 5.1.1 (API 22) through Android 16 (API 36).
 */
class FlipAmbientAudioEngine {
    private val playbackLock = Any()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var isPlaying = false

    @Volatile
    private var currentType: AmbientSoundType = AmbientSoundType.OFF

    @Volatile
    private var targetVolume: Float = 0.45f

    @Volatile
    private var sleepFadeGain: Float = 1.0f

    @Volatile
    private var focusMultiplier: Float = 1.0f

    @Volatile
    private var focusMuted: Boolean = false

    private var workerThread: Thread? = null
    private var audioTrack: AudioTrack? = null

    @Volatile
    private var audioFocusRequestObj: Any? = null

    private var noisyReceiverRegistered = false
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                // Softly duck briefly when wired/BT headphones unplug so room isn't startled
                focusMultiplier = 0.35f
            }
        }
    }

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                focusMuted = false
                focusMultiplier = 1.0f
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                focusMuted = true
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                focusMuted = false
                focusMultiplier = 0.25f
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                focusMuted = false
                focusMultiplier = 0.5f
            }
        }
    }

    val currentPlayingType: AmbientSoundType
        get() = if (isPlaying) currentType else AmbientSoundType.OFF

    fun attachContext(context: Context) {
        appContext = context.applicationContext
    }

    fun setSleepFadeGain(gain: Float) {
        sleepFadeGain = gain.coerceIn(0f, 1f)
    }

    fun updatePlayback(type: AmbientSoundType, volume: Float) {
        val clampedVol = volume.coerceIn(0.05f, 1f)
        synchronized(playbackLock) {
            targetVolume = clampedVol
            if (type == AmbientSoundType.OFF) {
                stopLocked()
                return
            }
            // Live volume adjustment: sample-slewed smoothly with ZERO stream restart or glitches
            if (isPlaying && currentType == type && workerThread?.isAlive == true) {
                return
            }
            stopLocked()
            currentType = type
            isPlaying = true
            focusMuted = false
            focusMultiplier = 1.0f
            requestAudioFocusLocked()
            registerNoisyReceiverLocked()
            val genThread = thread(start = true, isDaemon = true, name = "ResolveStudioAudio") {
                runSynthesisLoop(type)
            }
            workerThread = genThread
        }
    }

    fun stop() {
        synchronized(playbackLock) {
            stopLocked()
        }
    }

    private fun stopLocked() {
        isPlaying = false
        currentType = AmbientSoundType.OFF
        val oldThread = workerThread
        workerThread = null
        oldThread?.interrupt()

        val t = audioTrack
        audioTrack = null
        if (t != null) {
            runCatching { t.pause() }
            runCatching { t.flush() }
            runCatching { t.stop() }
            runCatching { t.release() }
        }
        unregisterNoisyReceiverLocked()
        abandonAudioFocusLocked()
    }

    fun playPomodoroChime() {
        thread(start = true, isDaemon = true, name = "ResolvePomodoroChime") {
            runCatching {
                val sampleRate = 22050
                val durationSec = 0.72
                val totalSamples = (sampleRate * durationSec).toInt()
                val pcm = ShortArray(totalSamples * 2)
                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val env = exp(-t * 4.2) * (if (i < 420) i / 420.0 else 1.0)
                    val toneL = 0.62 * sin(2.0 * PI * 587.33 * t) + 0.38 * sin(2.0 * PI * 880.0 * t)
                    val toneR = 0.62 * sin(2.0 * PI * 587.33 * t + 0.08) + 0.38 * sin(2.0 * PI * 880.0 * t - 0.05)
                    pcm[i * 2] = (toneL * env * 13_500.0).toInt().coerceIn(-32767, 32767).toShort()
                    pcm[i * 2 + 1] = (toneR * env * 13_500.0).toInt().coerceIn(-32767, 32767).toShort()
                }
                val track = buildStereoAudioTrack(sampleRate, pcm.size * 2) ?: return@runCatching
                track.play()
                track.write(pcm, 0, pcm.size)
                Thread.sleep(760L)
                runCatching {
                    track.stop()
                    track.release()
                }
            }
        }
    }

    private fun runSynthesisLoop(type: AmbientSoundType) {
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) }

        val sampleRate = 44100
        val chunkFrames = 4096 // ~93ms stereo chunk — ultra-low CPU wakeup & buffer-free
        val minBytes = runCatching {
            AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
        }.getOrDefault(-1)

        if (minBytes <= 0) {
            synchronized(playbackLock) {
                if (currentType == type) isPlaying = false
            }
            return
        }

        // Triple-buffered ring (at least ~350ms) eliminates any Doze/CPU micro-stutter
        val ringBufferBytes = maxOf(minBytes * 3, (sampleRate * 4) / 3)
        val track = buildStereoAudioTrack(sampleRate, ringBufferBytes) ?: run {
            synchronized(playbackLock) {
                if (currentType == type) isPlaying = false
            }
            return
        }

        synchronized(playbackLock) {
            if (!isPlaying || currentType != type) {
                runCatching { track.release() }
                return
            }
            audioTrack = track
        }

        val playOk = runCatching { track.play() }.isSuccess
        if (!playOk) {
            synchronized(playbackLock) {
                if (audioTrack === track) audioTrack = null
                isPlaying = false
            }
            runCatching { track.release() }
            return
        }

        val pcm = ShortArray(chunkFrames * 2)
        val engineState = StereoDspState(sampleRate, Random(System.nanoTime()))

        // Soft 350ms fade-in from 0 so starting is velvety smooth
        var smoothedVol = 0.0
        var startRamp = 0.0
        val rampStep = 1.0 / (sampleRate * 0.35)

        while (isPlaying && !Thread.currentThread().isInterrupted) {
            val desiredVol = if (focusMuted) {
                0.0
            } else {
                (targetVolume * sleepFadeGain * focusMultiplier).toDouble().coerceIn(0.0, 1.0)
            }

            for (frame in 0 until chunkFrames) {
                if (startRamp < 1.0) {
                    startRamp = (startRamp + rampStep).coerceAtMost(1.0)
                }
                // Per-sample exponential slewing eliminates zipper artifacts on volume/mute changes
                smoothedVol += (desiredVol * startRamp - smoothedVol) * 0.0022

                engineState.nextStereoSample(type)
                val gain = smoothedVol * 26_500.0
                val left = (engineState.outL * gain).toInt().coerceIn(-32767, 32767).toShort()
                val right = (engineState.outR * gain).toInt().coerceIn(-32767, 32767).toShort()
                pcm[frame * 2] = left
                pcm[frame * 2 + 1] = right
            }

            val written = runCatching { track.write(pcm, 0, pcm.size) }.getOrDefault(-1)
            if (written < 0) break
        }

        runCatching { track.pause() }
        runCatching { track.flush() }
        runCatching { track.stop() }
        runCatching { track.release() }
        synchronized(playbackLock) {
            if (audioTrack === track) {
                audioTrack = null
            }
        }
    }

    private fun buildStereoAudioTrack(sampleRate: Int, bufferSizeBytes: Int): AudioTrack? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
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
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferSizeBytes)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSizeBytes,
                AudioTrack.MODE_STREAM
            )
        }
    }.getOrNull()

    private fun requestAudioFocusLocked() {
        val ctx = appContext ?: return
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(audioFocusListener)
                    .build()
                audioFocusRequestObj = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    audioFocusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        }
    }

    private fun abandonAudioFocusLocked() {
        val ctx = appContext ?: return
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = audioFocusRequestObj as? AudioFocusRequest
                if (req != null) {
                    am.abandonAudioFocusRequest(req)
                }
                audioFocusRequestObj = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(audioFocusListener)
            }
        }
    }

    private fun registerNoisyReceiverLocked() {
        val ctx = appContext ?: return
        if (noisyReceiverRegistered) return
        runCatching {
            ctx.registerReceiver(becomingNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
            noisyReceiverRegistered = true
        }
    }

    private fun unregisterNoisyReceiverLocked() {
        val ctx = appContext ?: return
        if (!noisyReceiverRegistered) return
        runCatching {
            ctx.unregisterReceiver(becomingNoisyReceiver)
        }
        noisyReceiverRegistered = false
    }

    /**
     * Stateful real-time stereo physical-modeling synthesizer for each focus soundscape.
     */
    private class StereoDspState(
        private val sampleRate: Int,
        private val rand: Random
    ) {
        var outL = 0.0
        var outR = 0.0

        // Dual independent Paul Kellet Pink Noise state for rich stereo width
        private val pinkL = PinkNoiseFilter()
        private val pinkR = PinkNoiseFilter()

        // Smooth low-pass / high-pass filter memories
        private var lpL1 = 0.0
        private var lpR1 = 0.0
        private var lpL2 = 0.0
        private var lpR2 = 0.0
        private var brownL = 0.0
        private var brownR = 0.0
        private var dcPrevInL = 0.0
        private var dcPrevOutL = 0.0
        private var dcPrevInR = 0.0
        private var dcPrevOutR = 0.0

        // Slow LFO phases
        private var lfoPhaseA = 0.0
        private var lfoPhaseB = 0.4
        private var lfoPhaseC = 1.1

        // Harmonic pad oscillators
        private var osc1 = 0.0
        private var osc2 = 0.0
        private var osc3 = 0.0
        private var osc4 = 0.0
        private var osc5 = 0.0

        // Transient events (Rain droplets, Bonfire crackles, River water blips, Distant thunder, Forest birds)
        private var dropEnvL = 0.0
        private var dropPhaseL = 0.0
        private var dropFreqL = 900.0
        private var dropEnvR = 0.0
        private var dropPhaseR = 0.0
        private var dropFreqR = 1150.0

        private var crackleEnv = 0.0
        private var crackleDecay = 0.992
        private var cracklePanL = 0.5
        private var cracklePanR = 0.5
        private var cracklePrevWhite = 0.0

        private var waterBlipEnvL = 0.0
        private var waterBlipPhaseL = 0.0
        private var waterBlipFreqL = 420.0
        private var waterBlipEnvR = 0.0
        private var waterBlipPhaseR = 0.0
        private var waterBlipFreqR = 540.0

        private var thunderProgress = -1.0 // -1 inactive, 0..1 active envelope
        private var thunderCooldownSamples = sampleRate * 7
        private var thunderLp = 0.0

        private var birdCooldownSamples = sampleRate * 4
        private var birdActiveSamples = 0
        private var birdTotalSamples = 1
        private var birdOscPhase = 0.0
        private var birdBaseFreq = 2600.0
        private var birdPanL = 0.7
        private var birdPanR = 0.3

        fun nextStereoSample(type: AmbientSoundType) {
            when (type) {
                AmbientSoundType.WHITE_NOISE -> synthesizeStudioWhite()
                AmbientSoundType.RAIN -> synthesizeSoftRain()
                AmbientSoundType.BONFIRE -> synthesizeBonfire()
                AmbientSoundType.FOREST_WIND -> synthesizeForestWind()
                AmbientSoundType.RIVER_WAVES -> synthesizeRiverWaves()
                AmbientSoundType.OCEAN_WAVES -> synthesizeOceanWaves()
                AmbientSoundType.THUNDERSTORM -> synthesizeThunderstorm()
                AmbientSoundType.BROWN_NOISE -> synthesizeBrownNoise()
                AmbientSoundType.WARM_HUM -> synthesizeWarmHum()
                AmbientSoundType.FOCUS_PAD -> synthesizeFocusPad()
                AmbientSoundType.OFF -> {
                    outL = 0.0
                    outR = 0.0
                }
            }
        }

        private fun synthesizeStudioWhite() {
            // Soft studio white noise: 2-stage warmth filter + 40% stereo Kellet pink noise -> masking without harsh hiss
            val wL = rand.nextDouble(-1.0, 1.0)
            val wR = rand.nextDouble(-1.0, 1.0)
            lpL1 = lpL1 * 0.58 + wL * 0.42
            lpL2 = lpL2 * 0.45 + lpL1 * 0.55
            lpR1 = lpR1 * 0.58 + wR * 0.42
            lpR2 = lpR2 * 0.45 + lpR1 * 0.55
            val pL = pinkL.next(wL)
            val pR = pinkR.next(wR)
            outL = (lpL2 * 0.42 + pL * 0.48).coerceIn(-1.0, 1.0)
            outR = (lpR2 * 0.42 + pR * 0.48).coerceIn(-1.0, 1.0)
        }

        private fun synthesizeSoftRain() {
            val wL = rand.nextDouble(-1.0, 1.0)
            val wR = rand.nextDouble(-1.0, 1.0)
            val pL = pinkL.next(wL)
            val pR = pinkR.next(wR)

            // Warm foliage rain bed (smooth low-pass + mild mid clarity)
            lpL1 = lpL1 * 0.84 + pL * 0.16
            lpR1 = lpR1 * 0.84 + pR * 0.16
            lfoPhaseA += (2.0 * PI * 0.075) / sampleRate
            if (lfoPhaseA > 2.0 * PI) lfoPhaseA -= 2.0 * PI
            val breeze = 0.88 + 0.12 * sin(lfoPhaseA)

            // Organic soft water droplets scattered left/right
            if (dropEnvL < 0.002 && rand.nextInt(3400) == 0) {
                dropEnvL = rand.nextDouble(0.08, 0.22)
                dropFreqL = rand.nextDouble(520.0, 1450.0)
            }
            if (dropEnvR < 0.002 && rand.nextInt(3600) == 0) {
                dropEnvR = rand.nextDouble(0.08, 0.22)
                dropFreqR = rand.nextDouble(580.0, 1580.0)
            }
            dropPhaseL += (2.0 * PI * dropFreqL) / sampleRate
            dropPhaseR += (2.0 * PI * dropFreqR) / sampleRate
            if (dropPhaseL > 2.0 * PI) dropPhaseL -= 2.0 * PI
            if (dropPhaseR > 2.0 * PI) dropPhaseR -= 2.0 * PI
            val dropL = sin(dropPhaseL) * dropEnvL
            val dropR = sin(dropPhaseR) * dropEnvR
            dropEnvL *= 0.9965
            dropEnvR *= 0.9965

            val baseL = (lpL1 * 0.92 + (pL - lpL1) * 0.28) * breeze
            val baseR = (lpR1 * 0.92 + (pR - lpR1) * 0.28) * breeze
            outL = tanh((baseL * 0.85 + dropL * 0.45 + dropR * 0.12) * 1.1)
            outR = tanh((baseR * 0.85 + dropR * 0.45 + dropL * 0.12) * 1.1)
        }

        private fun synthesizeBonfire() {
            val wL = rand.nextDouble(-1.0, 1.0)
            val wR = rand.nextDouble(-1.0, 1.0)
            val pL = pinkL.next(wL)
            val pR = pinkR.next(wR)

            // 1. Deep flickering hearth flame warmth
            lfoPhaseA += (2.0 * PI * 0.19) / sampleRate
            lfoPhaseB += (2.0 * PI * 0.47) / sampleRate
            if (lfoPhaseA > 2.0 * PI) lfoPhaseA -= 2.0 * PI
            if (lfoPhaseB > 2.0 * PI) lfoPhaseB -= 2.0 * PI
            val flameBreath = 0.72 + 0.18 * sin(lfoPhaseA) + 0.10 * sin(lfoPhaseB)

            lpL1 = lpL1 * 0.978 + pL * 0.022
            lpR1 = lpR1 * 0.978 + pR * 0.022
            lpL2 = lpL2 * 0.92 + pL * 0.08
            lpR2 = lpR2 * 0.92 + pR * 0.08

            val flameBedL = (lpL1 * 1.65 + (lpL2 - lpL1) * 0.45) * flameBreath
            val flameBedR = (lpR1 * 1.65 + (lpR2 - lpR1) * 0.45) * flameBreath

            // 2. Authentic wood crackles & ember snaps across stereo field
            if (crackleEnv < 0.005 && rand.nextInt(5200) == 0) {
                crackleEnv = rand.nextDouble(0.32, 0.85)
                crackleDecay = rand.nextDouble(0.986, 0.9945)
                val pan = rand.nextDouble(0.15, 0.85)
                cracklePanL = cos(pan * PI * 0.5)
                cracklePanR = sin(pan * PI * 0.5)
            }
            val rawSnap = wL - cracklePrevWhite * 0.82 // crisp high-pass transient pop
            cracklePrevWhite = wL
            val crackleSample = rawSnap * crackleEnv
            crackleEnv *= crackleDecay

            outL = tanh((flameBedL * 0.78 + crackleSample * cracklePanL * 0.55) * 1.15)
            outR = tanh((flameBedR * 0.78 + crackleSample * cracklePanR * 0.55) * 1.15)
        }

        private fun synthesizeForestWind() {
            val wL = rand.nextDouble(-1.0, 1.0)
            val wR = rand.nextDouble(-1.0, 1.0)
            val pL = pinkL.next(wL)
            val pR = pinkR.next(wR)

            // Slow multi-LFO forest canopy wind swells
            lfoPhaseA += (2.0 * PI * 0.055) / sampleRate
            lfoPhaseB += (2.0 * PI * 0.089) / sampleRate
            if (lfoPhaseA > 2.0 * PI) lfoPhaseA -= 2.0 * PI
            if (lfoPhaseB > 2.0 * PI) lfoPhaseB -= 2.0 * PI

            val gustL = (0.45 + 0.35 * sin(lfoPhaseA) + 0.16 * sin(lfoPhaseB)).coerceIn(0.15, 1.0)
            val gustR = (0.45 + 0.35 * sin(lfoPhaseA - 0.55) + 0.16 * sin(lfoPhaseB + 0.35)).coerceIn(0.15, 1.0)

            lpL1 = lpL1 * 0.972 + pL * 0.028
            lpR1 = lpR1 * 0.972 + pR * 0.028
            lpL2 = lpL2 * 0.86 + pL * 0.14
            lpR2 = lpR2 * 0.86 + pR * 0.14

            val leavesL = (lpL2 - lpL1) * (0.28 + 0.45 * gustL)
            val leavesR = (lpR2 - lpR1) * (0.28 + 0.45 * gustR)

            // Occasional distant calm woodland bird motif
            var birdL = 0.0
            var birdR = 0.0
            if (birdActiveSamples > 0) {
                val u = 1.0 - (birdActiveSamples.toDouble() / birdTotalSamples)
                val env = sin(PI * u).pow(2.0) * 0.045
                val glide = 1.0 + 0.14 * sin(2.0 * PI * u * 2.0)
                birdOscPhase += (2.0 * PI * birdBaseFreq * glide) / sampleRate
                if (birdOscPhase > 2.0 * PI) birdOscPhase -= 2.0 * PI
                val chirp = sin(birdOscPhase) * env
                birdL = chirp * birdPanL
                birdR = chirp * birdPanR
                birdActiveSamples--
            } else {
                birdCooldownSamples--
                if (birdCooldownSamples <= 0) {
                    birdTotalSamples = (sampleRate * rand.nextDouble(0.18, 0.34)).toInt().coerceAtLeast(1)
                    birdActiveSamples = birdTotalSamples
                    birdBaseFreq = rand.nextDouble(1850.0, 2950.0)
                    val pan = rand.nextDouble(0.2, 0.8)
                    birdPanL = cos(pan * PI * 0.5)
                    birdPanR = sin(pan * PI * 0.5)
                    birdCooldownSamples = if (rand.nextInt(3) == 0) {
                        (sampleRate * rand.nextDouble(0.35, 0.7)).toInt()
                    } else {
                        (sampleRate * rand.nextDouble(5.0, 11.5)).toInt()
                    }
                }
            }

            outL = tanh((lpL1 * 1.15 * gustL + leavesL + birdL) * 0.95)
            outR = tanh((lpR1 * 1.15 * gustR + leavesR + birdR) * 0.95)
        }

        private fun synthesizeRiverWaves() {
            val wL = rand.nextDouble(-1.0, 1.0)
            val wR = rand.nextDouble(-1.0, 1.0)
            val pL = pinkL.next(wL)
            val pR = pinkR.next(wR)

            // Dual rolling river wave LFOs (gentle shore laps + steady stream current)
            lfoPhaseA += (2.0 * PI * 0.21) / sampleRate
            lfoPhaseB += (2.0 * PI * 0.34) / sampleRate
            if (lfoPhaseA > 2.0 * PI) lfoPhaseA -= 2.0 * PI
            if (lfoPhaseB > 2.0 * PI) lfoPhaseB -= 2.0 * PI

            val waveL = 0.68 + 0.22 * sin(lfoPhaseA) + 0.10 * sin(lfoPhaseB)
            val waveR = 0.68 + 0.22 * sin(lfoPhaseA - 0.7) + 0.10 * sin(lfoPhaseB + 0.5)

            // River mid-warm wash (dynamic LP coefficient follows wave crest)
            val coefL = (0.06 + 0.12 * waveL).coerceIn(0.05, 0.25)
            val coefR = (0.06 + 0.12 * waveR).coerceIn(0.05, 0.25)
            lpL1 = lpL1 * (1.0 - coefL) + pL * coefL
            lpR1 = lpR1 * (1.0 - coefR) + pR * coefR

            // Smooth resonant water ripples / gentle stream glugs
            if (waterBlipEnvL < 0.002 && rand.nextInt(2100) == 0) {
                waterBlipEnvL = rand.nextDouble(0.05, 0.14)
                waterBlipFreqL = rand.nextDouble(280.0, 680.0)
            }
            if (waterBlipEnvR < 0.002 && rand.nextInt(2250) == 0) {
                waterBlipEnvR = rand.nextDouble(0.05, 0.14)
                waterBlipFreqR = rand.nextDouble(310.0, 740.0)
            }
            waterBlipFreqL *= 1.00018
            waterBlipFreqR *= 1.00016
            waterBlipPhaseL += (2.0 * PI * waterBlipFreqL) / sampleRate
            waterBlipPhaseR += (2.0 * PI * waterBlipFreqR) / sampleRate
            if (waterBlipPhaseL > 2.0 * PI) waterBlipPhaseL -= 2.0 * PI
            if (waterBlipPhaseR > 2.0 * PI) waterBlipPhaseR -= 2.0 * PI
            val rippleL = sin(waterBlipPhaseL) * waterBlipEnvL
            val rippleR = sin(waterBlipPhaseR) * waterBlipEnvR
            waterBlipEnvL *= 0.9985
            waterBlipEnvR *= 0.9985

            outL = tanh((lpL1 * 1.15 * waveL + rippleL + rippleR * 0.2) * 1.05)
            outR = tanh((lpR1 * 1.15 * waveR + rippleR + rippleL * 0.2) * 1.05)
        }

        private fun synthesizeOceanWaves() {
            val wL = rand.nextDouble(-1.0, 1.0)
            val wR = rand.nextDouble(-1.0, 1.0)
            val pL = pinkL.next(wL)
            val pR = pinkR.next(wR)

            // Slow 9-second coastal surf crest & long foam recede
            lfoPhaseA += (2.0 * PI * 0.11) / sampleRate
            if (lfoPhaseA > 2.0 * PI) lfoPhaseA -= 2.0 * PI
            val rawSwell = 0.5 - 0.5 * cos(lfoPhaseA)
            val surfCrest = rawSwell.pow(1.65)
            val surfCrestR = (0.5 - 0.5 * cos(lfoPhaseA - 0.25)).pow(1.65)

            val alphaL = 0.018 + 0.19 * surfCrest
            val alphaR = 0.018 + 0.19 * surfCrestR
            lpL1 = lpL1 * (1.0 - alphaL) + pL * alphaL
            lpR1 = lpR1 * (1.0 - alphaR) + pR * alphaR

            outL = tanh(lpL1 * (0.55 + 0.95 * surfCrest) * 1.1)
            outR = tanh(lpR1 * (0.55 + 0.95 * surfCrestR) * 1.1)
        }

        private fun synthesizeThunderstorm() {
            // Rich rainfall base
            synthesizeSoftRain()
            val rainOutL = outL
            val rainOutR = outR

            // Warm distant rolling thunder swell every ~14–24s
            var thunder = 0.0
            if (thunderProgress >= 0.0) {
                val p = thunderProgress
                val env = if (p < 0.18) {
                    (p / 0.18).pow(1.4)
                } else {
                    exp(-(p - 0.18) * 3.8)
                } * (0.75 + 0.25 * sin(p * 19.0))
                val subWhite = rand.nextDouble(-1.0, 1.0)
                thunderLp = thunderLp * 0.993 + subWhite * 0.007
                thunder = thunderLp * env * 2.1
                thunderProgress += 1.0 / (sampleRate * 4.2)
                if (thunderProgress >= 1.0) {
                    thunderProgress = -1.0
                    thunderCooldownSamples = (sampleRate * rand.nextDouble(12.0, 22.0)).toInt()
                }
            } else {
                thunderCooldownSamples--
                if (thunderCooldownSamples <= 0) {
                    thunderProgress = 0.0
                }
            }

            outL = tanh(rainOutL * 0.85 + thunder * 0.9)
            outR = tanh(rainOutR * 0.85 + thunder * 0.85)
        }

        private fun synthesizeBrownNoise() {
            val wL = rand.nextDouble(-1.0, 1.0)
            val wR = rand.nextDouble(-1.0, 1.0)
            brownL = ((brownL + wL * 0.030) * 0.994).coerceIn(-1.0, 1.0)
            brownR = ((brownR + wR * 0.030) * 0.994).coerceIn(-1.0, 1.0)

            // Gentle DC blocker removes sub-15Hz drift cleanly
            val cleanL = brownL - dcPrevInL + 0.996 * dcPrevOutL
            dcPrevInL = brownL
            dcPrevOutL = cleanL

            val cleanR = brownR - dcPrevInR + 0.996 * dcPrevOutR
            dcPrevInR = brownR
            dcPrevOutR = cleanR

            outL = tanh(cleanL * 0.92)
            outR = tanh(cleanR * 0.92)
        }

        private fun synthesizeWarmHum() {
            val wL = rand.nextDouble(-1.0, 1.0)
            val wR = rand.nextDouble(-1.0, 1.0)
            lpL1 = lpL1 * 0.985 + pinkL.next(wL) * 0.015
            lpR1 = lpR1 * 0.985 + pinkR.next(wR) * 0.015

            osc1 += (2.0 * PI * 110.0) / sampleRate
            osc2 += (2.0 * PI * 165.0) / sampleRate
            if (osc1 > 2.0 * PI) osc1 -= 2.0 * PI
            if (osc2 > 2.0 * PI) osc2 -= 2.0 * PI

            val droneL = (0.62 * sin(osc1) + 0.30 * sin(osc2)) * 0.42
            val droneR = (0.62 * sin(osc1 + 0.14) + 0.30 * sin(osc2 - 0.11)) * 0.42
            outL = tanh(droneL + lpL1 * 0.35)
            outR = tanh(droneR + lpR1 * 0.35)
        }

        private fun synthesizeFocusPad() {
            // Warm lush A-add9 ambient chord pad with gentle breathing filter
            osc1 += (2.0 * PI * 110.0) / sampleRate
            osc2 += (2.0 * PI * 164.81) / sampleRate
            osc3 += (2.0 * PI * 220.45) / sampleRate
            osc4 += (2.0 * PI * 246.94) / sampleRate
            osc5 += (2.0 * PI * 329.63) / sampleRate
            lfoPhaseA += (2.0 * PI * 0.11) / sampleRate
            if (osc1 > 2.0 * PI) osc1 -= 2.0 * PI
            if (osc2 > 2.0 * PI) osc2 -= 2.0 * PI
            if (osc3 > 2.0 * PI) osc3 -= 2.0 * PI
            if (osc4 > 2.0 * PI) osc4 -= 2.0 * PI
            if (osc5 > 2.0 * PI) osc5 -= 2.0 * PI
            if (lfoPhaseA > 2.0 * PI) lfoPhaseA -= 2.0 * PI

            val breath = 0.72 + 0.24 * sin(lfoPhaseA)
            val rawL = 0.38 * sin(osc1) + 0.26 * sin(osc2) + 0.18 * sin(osc3) + 0.11 * sin(osc4) + 0.07 * sin(osc5)
            val rawR = 0.38 * sin(osc1 + 0.18) + 0.26 * sin(osc2 - 0.14) + 0.18 * sin(osc3 + 0.22) + 0.11 * sin(osc4 - 0.19) + 0.07 * sin(osc5 + 0.1)

            // Soft low-pass air warmth
            lpL1 = lpL1 * 0.98 + pinkL.next(rand.nextDouble(-1.0, 1.0)) * 0.02
            lpR1 = lpR1 * 0.98 + pinkR.next(rand.nextDouble(-1.0, 1.0)) * 0.02

            outL = tanh((rawL * breath * 0.62) + lpL1 * 0.12)
            outR = tanh((rawR * breath * 0.62) + lpR1 * 0.12)
        }
    }

    private class PinkNoiseFilter {
        private var b0 = 0.0
        private var b1 = 0.0
        private var b2 = 0.0
        private var b3 = 0.0
        private var b4 = 0.0
        private var b5 = 0.0
        private var b6 = 0.0

        fun next(white: Double): Double {
            b0 = 0.99886 * b0 + white * 0.0555179
            b1 = 0.99332 * b1 + white * 0.0750759
            b2 = 0.96900 * b2 + white * 0.1538520
            b3 = 0.86650 * b3 + white * 0.3104856
            b4 = 0.55000 * b4 + white * 0.5329522
            b5 = -0.7616 * b5 - white * 0.0168980
            val out = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362) * 0.11
            b6 = white * 0.115926
            return out.coerceIn(-1.0, 1.0)
        }
    }
}
