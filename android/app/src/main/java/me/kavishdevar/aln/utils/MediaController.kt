/*
 * AirPods like Normal (ALN) - Bringing Apple-only features to Linux and Android for seamless AirPods functionality!
 *
 * Copyright (C) 2024 Kavish Devar
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package me.kavishdevar.aln.utils

import android.content.SharedPreferences
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import me.kavishdevar.aln.services.ServiceManager

object MediaController {
    private var initialVolume: Int? = null
    private lateinit var audioManager: AudioManager
    var iPausedTheMedia = false
    var userPlayedTheMedia = false
    private lateinit var sharedPreferences: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    var pausedForCrossDevice = false

    private var relativeVolume: Boolean = false
    private var conversationalAwarenessVolume: Int = 1/12
    private var conversationalAwarenessPauseMusic: Boolean = false

    fun initialize(audioManager: AudioManager, sharedPreferences: SharedPreferences) {
        if (this::audioManager.isInitialized) {
            return
        }
        this.audioManager = audioManager
        this.sharedPreferences = sharedPreferences
        Log.d("MediaController", "Initializing MediaController")
        relativeVolume = sharedPreferences.getBoolean("relative_conversational_awareness_volume", false)
        conversationalAwarenessVolume = sharedPreferences.getInt("conversational_awareness_volume", 100/12)
        conversationalAwarenessPauseMusic = sharedPreferences.getBoolean("conversational_awareness_pause_music", false)

        sharedPreferences.registerOnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "relative_conversational_awareness_volume" -> {
                    relativeVolume = sharedPreferences.getBoolean("relative_conversational_awareness_volume", false)
                }
                "conversational_awareness_volume" -> {
                    conversationalAwarenessVolume = sharedPreferences.getInt("conversational_awareness_volume", 100/12)
                }
                "conversational_awareness_pause_music" -> {
                    conversationalAwarenessPauseMusic = sharedPreferences.getBoolean("conversational_awareness_pause_music", false)
                }
            }
        }

        audioManager.registerAudioPlaybackCallback(cb, null)
    }

    val cb = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            super.onPlaybackConfigChanged(configs)
            Log.d("MediaController", "Playback config changed, iPausedTheMedia: $iPausedTheMedia")
            if (configs != null && !iPausedTheMedia) {
                Log.d("MediaController", "Seems like the user changed the state of media themselves, now I won't `play` until the ear detection pauses it.")
                handler.postDelayed({
                    iPausedTheMedia = !audioManager.isMusicActive
                    userPlayedTheMedia = audioManager.isMusicActive
                }, 7) // i have no idea why android sends an event a hundred times after the user does something.
            }
            Log.d("MediaController", "Ear detection status: ${ServiceManager.getService()?.earDetectionNotification?.status}, music active: ${audioManager.isMusicActive} and cross device available: ${CrossDevice.isAvailable}")
            if (!pausedForCrossDevice && CrossDevice.isAvailable && ServiceManager.getService()?.earDetectionNotification?.status?.contains(0x00) == true && audioManager.isMusicActive) {
                if (ServiceManager.getService()?.isConnectedLocally == false) {
                    sendPause(true)
                    pausedForCrossDevice = true
                }
                ServiceManager.getService()?.takeOver()
            }
        }
    }

    @Synchronized
    fun sendPause(force: Boolean = false) {
        Log.d("MediaController", "Sending pause with iPausedTheMedia: $iPausedTheMedia, userPlayedTheMedia: $userPlayedTheMedia")
        if ((audioManager.isMusicActive && !userPlayedTheMedia) || force) {
            iPausedTheMedia = true
            userPlayedTheMedia = false
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_MEDIA_PAUSE
                )
            )
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(
                    KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_MEDIA_PAUSE
                )
            )
        }
    }

    @Synchronized
    fun sendPlay() {
        Log.d("MediaController", "Sending play with iPausedTheMedia: $iPausedTheMedia")
        if (iPausedTheMedia) {
            Log.d("MediaController", "Sending play and setting userPlayedTheMedia to false")
            userPlayedTheMedia = false
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_MEDIA_PLAY
                )
            )
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(
                    KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_MEDIA_PLAY
                )
            )
        }
    }

    @Synchronized
    fun startSpeaking() {
        Log.d("MediaController", "Starting speaking")
        if (initialVolume == null) {
            initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            Log.d("MediaController", "Initial Volume Set: $initialVolume")
            val targetVolume = if (relativeVolume) initialVolume!! * conversationalAwarenessVolume * 1/100 else if ( initialVolume!! > audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * conversationalAwarenessVolume * 1/100) audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * conversationalAwarenessVolume * 1/100 else initialVolume!!
            smoothVolumeTransition(initialVolume!!, targetVolume.toInt())
            if (conversationalAwarenessPauseMusic) {
                sendPause(force = true)
            }
        }
        Log.d("MediaController", "Initial Volume: $initialVolume")
    }

    @Synchronized
    fun stopSpeaking() {
        Log.d("MediaController", "Stopping speaking, initialVolume: $initialVolume")
        if (initialVolume != null) {
            smoothVolumeTransition(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC), initialVolume!!)
            if (conversationalAwarenessPauseMusic) {
                sendPlay()
            }
            initialVolume = null
        }
    }

    private fun smoothVolumeTransition(fromVolume: Int, toVolume: Int) {
        val step = if (fromVolume < toVolume) 1 else -1
        val delay = 50L
        var currentVolume = fromVolume

        handler.post(object : Runnable {
            override fun run() {
                if (currentVolume != toVolume) {
                    currentVolume += step
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0)
                    handler.postDelayed(this, delay)
                }
            }
        })
    }
}
