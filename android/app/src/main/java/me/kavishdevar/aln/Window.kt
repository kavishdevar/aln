package me.kavishdevar.aln

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.TextView
import android.widget.VideoView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.Exception

class Window @SuppressLint("InflateParams") constructor(
    context: Context
) {
    private val mView: View
    private val mParams: WindowManager.LayoutParams = WindowManager.LayoutParams(
        (context.resources.displayMetrics.widthPixels * 0.95).toInt(),
        WindowManager.LayoutParams.WRAP_CONTENT,  // Display it on top of other application windows
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,  // Don't let it grab the input focus
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,  // Make the underlying application window visible
        PixelFormat.TRANSLUCENT
    )
    private val mWindowManager: WindowManager
    init {
        val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        mView = layoutInflater.inflate(R.layout.popup_window, null)
        mParams.x = 0
        mParams.y = 0

        mParams.gravity = Gravity.BOTTOM
        mView.setOnClickListener(View.OnClickListener {
            close()
        })

        mView.findViewById<ImageButton>(R.id.close_button)
            .setOnClickListener {
                close()
            }

        mWindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    @SuppressLint("InlinedApi", "SetTextI18n")
    fun open(name: String = "AirPods Pro", batteryNotification: AirPodsNotifications.BatteryNotification) {
        try {
            if (mView.windowToken == null) {
                if (mView.parent == null) {
                    // Add the view initially off-screen
                    mWindowManager.addView(mView, mParams)
                    mView.findViewById<TextView>(R.id.name).text = name
                    val vid = mView.findViewById<VideoView>(R.id.video)
                    vid.setVideoPath("android.resource://me.kavishdevar.aln/" + R.raw.connected)
                    vid.resolveAdjustedSize(vid.width, vid.height)
                    vid.start()
                    vid.setOnCompletionListener {
                        vid.start()
                    }
                    
//                    receive battery broadcast and set to R.id.battery
                    val batteryText = mView.findViewById<TextView>(R.id.battery)
                    val batteryList = batteryNotification.getBattery()
                    batteryText.text = "Why are the battery levels zero :( " + batteryList[0].level.toString() + "%" + " " + batteryList[0].status + " " + batteryList[1].level.toString() + "%" + " " + batteryList[1].status + " " + batteryList[2].level.toString() + "%" + " " + batteryList[2].status

                    // Slide-up animation
                    val displayMetrics = mView.context.resources.displayMetrics
                    val screenHeight = displayMetrics.heightPixels

                    mView.translationY = screenHeight.toFloat()  // Start below the screen
                    ObjectAnimator.ofFloat(mView, "translationY", 0f).apply {
                        duration = 500  // Animation duration in milliseconds
                        interpolator = DecelerateInterpolator()  // Smooth deceleration
                        start()
                    }

                    CoroutineScope(MainScope().coroutineContext).launch {
                        delay(12000)
                        close()
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("PopupService", e.toString())
        }
    }

    fun close() {
        try {
            ObjectAnimator.ofFloat(mView, "translationY", mView.height.toFloat()).apply {
                duration = 500  // Animation duration in milliseconds
                interpolator = AccelerateInterpolator()  // Smooth acceleration
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        try {
                            mWindowManager.removeView(mView)
                        } catch (e: Exception) {
                            Log.d("PopupService", e.toString())
                        }
                    }
                })
                start()
            }
        } catch (e: Exception) {
            Log.d("PopupService", e.toString())
        }
    }
}