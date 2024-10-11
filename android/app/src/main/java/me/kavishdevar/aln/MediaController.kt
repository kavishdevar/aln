package me.kavishdevar.aln

import android.media.AudioManager
import android.view.KeyEvent

class MediaController (private val audioManager: AudioManager){
    fun sendPause() {
        if (audioManager.isMusicActive) {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
        }
    }

    fun sendPlay() {
        if (!audioManager.isMusicActive) {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
        }
    }
    var initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    fun startSpeaking() {
        if (!audioManager.isMusicActive) {
            // reduce volume to 10% of initial volume
            initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (initialVolume * 0.1).toInt(), 0)
        }
    }

    fun stopSpeaking() {
        if (!audioManager.isMusicActive) {
            // restore initial volume
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, initialVolume, 0)
        }

    }
}