package me.kavishdevar.aln.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.kavishdevar.aln.R

@SuppressLint("InflateParams", "ClickableViewAccessibility")
class Window (context: Context) {
    private val mView: View

    @Suppress("DEPRECATION")
    @SuppressLint("NewApi")
    private val mParams: WindowManager.LayoutParams = WindowManager.LayoutParams().apply {
        height = WindowManager.LayoutParams.WRAP_CONTENT
        width = WindowManager.LayoutParams.MATCH_PARENT
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        format = PixelFormat.TRANSLUCENT
        gravity = Gravity.BOTTOM
        dimAmount = 0.3f
        flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
    }


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

        val ll = mView.findViewById<LinearLayout>(R.id.linear_layout)
        ll.setOnClickListener {
            close()
        }

        @Suppress("DEPRECATION")
        mView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        mView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val touchY = event.rawY
                val popupTop = mView.top
                if (touchY < popupTop) {
                    close()
                    true
                } else {
                    false
                }
            } else {
                false
            }
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

                    val batteryStatus = batteryNotification.getBattery()
                    val batteryLeftText = mView.findViewById<TextView>(R.id.left_battery)
                    val batteryRightText = mView.findViewById<TextView>(R.id.right_battery)
                    val batteryCaseText = mView.findViewById<TextView>(R.id.case_battery)

                    batteryLeftText.text = batteryStatus.find { it.component == BatteryComponent.LEFT }?.let {
                        "\uDBC3\uDC8E    ${it.level}%"
                    } ?: ""
                    batteryRightText.text = batteryStatus.find { it.component == BatteryComponent.RIGHT }?.let {
                        "\uDBC3\uDC8D    ${it.level}%"
                    } ?: ""
                    batteryCaseText.text = batteryStatus.find { it.component == BatteryComponent.CASE }?.let {
                        "\uDBC3\uDE6C    ${it.level}%"
                    } ?: ""

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