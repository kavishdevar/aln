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

import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent

object MediaController {
    private var initialVolume: Int? = null  // Nullable to track the unset state
    private lateinit var audioManager: AudioManager  // Declare AudioManager

    // Initialize the singleton with the AudioManager instance
    fun initialize(audioManager: AudioManager) {
        this.audioManager = audioManager
    }

    @Synchronized
    fun sendPause() {
        if (audioManager.isMusicActive) {
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
        if (!audioManager.isMusicActive) {
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
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * 1 / 12,  // Set to a lower volume when speaking starts
                0
            )
        }
        Log.d("MediaController", "Initial Volume: $initialVolume")
    }

    @Synchronized
    fun stopSpeaking() {
        Log.d("MediaController", "Stopping speaking, initialVolume: $initialVolume")
        initialVolume?.let { volume ->
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
            initialVolume = null  // Reset to null after restoring the volume
        }
    }
}