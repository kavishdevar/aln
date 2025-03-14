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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log.e
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.AnticipateOvershootInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.VideoView
import androidx.core.content.ContextCompat.getString
import me.kavishdevar.aln.R
import me.kavishdevar.aln.services.ServiceManager

enum class IslandType {
    CONNECTED,
    TAKING_OVER,
    MOVED_TO_REMOTE,
//    CALL_GESTURE
}

class IslandWindow(context: Context) {
    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    @SuppressLint("InflateParams")
    private val islandView: View = LayoutInflater.from(context).inflate(R.layout.island_window, null)
    private var isClosing = false

    val isVisible: Boolean
        get() = islandView.parent != null && islandView.visibility == View.VISIBLE

    @SuppressLint("SetTextI18n")
    fun show(name: String, batteryPercentage: Int, context: Context, type: IslandType = IslandType.CONNECTED) {
        if (ServiceManager.getService()?.islandOpen == true) return
        else ServiceManager.getService()?.islandOpen = true

        val displayMetrics = Resources.getSystem().displayMetrics
        val width = (displayMetrics.widthPixels * 0.95).toInt()

        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }

        islandView.visibility = View.VISIBLE
        islandView.findViewById<TextView>(R.id.island_battery_text).text = "$batteryPercentage%"
        islandView.findViewById<TextView>(R.id.island_device_name).text = name

        islandView.setOnClickListener {
            ServiceManager.getService()?.startMainActivity()
            close()
        }

        when (type) {
            IslandType.CONNECTED -> {
                islandView.findViewById<TextView>(R.id.island_connected_text).text = getString(context, R.string.island_connected_text)
            }
            IslandType.TAKING_OVER -> {
                islandView.findViewById<TextView>(R.id.island_connected_text).text = getString(context, R.string.island_taking_over_text)
            }
            IslandType.MOVED_TO_REMOTE -> {
                islandView.findViewById<TextView>(R.id.island_connected_text).text = getString(context, R.string.island_moved_to_remote_text)
            }
//            IslandType.CALL_GESTURE -> {
//                islandView.findViewById<TextView>(R.id.island_connected_text).text = "Incoming Call from $name"
//                islandView.findViewById<TextView>(R.id.island_device_name).text = "Use Head Gestures to answer."
//            }
        }

        val batteryProgressBar = islandView.findViewById<ProgressBar>(R.id.island_battery_progress)
        batteryProgressBar.progress = batteryPercentage
        batteryProgressBar.isIndeterminate = false

        val videoView = islandView.findViewById<VideoView>(R.id.island_video_view)
        val videoUri = Uri.parse("android.resource://me.kavishdevar.aln/${R.raw.island}")
        videoView.setVideoURI(videoUri)
        videoView.setOnPreparedListener { mediaPlayer ->
            mediaPlayer.isLooping = true
            videoView.start()
        }

        windowManager.addView(islandView, params)

        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.5f, 1f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.5f, 1f)
        val translationY = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -200f, 0f)
        ObjectAnimator.ofPropertyValuesHolder(islandView, scaleX, scaleY, translationY).apply {
            duration = 700
            interpolator = AnticipateOvershootInterpolator()
            start()
        }
        Handler(Looper.getMainLooper()).postDelayed({
            close()
        }, 4500)
    }

    fun close() {
        try {
            if (isClosing) return
            isClosing = true

            ServiceManager.getService()?.islandOpen = false

            val videoView = islandView.findViewById<VideoView>(R.id.island_video_view)
            videoView.stopPlayback()
            val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.5f)
            val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.5f)
            val translationY = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, -200f)
            ObjectAnimator.ofPropertyValuesHolder(islandView, scaleX, scaleY, translationY).apply {
                duration = 700
                interpolator = AnticipateOvershootInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        islandView.visibility = View.GONE
                        try {
                            windowManager.removeView(islandView)
                        } catch (e: Exception) {
                            e("IslandWindow", "Error removing view: $e")
                        }
                        isClosing = false
                    }
                })
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
