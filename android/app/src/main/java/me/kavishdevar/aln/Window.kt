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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.Exception

@SuppressLint("InflateParams")
class Window (context: Context) {
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

        val ll = mView.findViewById<LinearLayout>(R.id.linear_layout)
        ll.setOnClickListener {
            close()
        }
        ll.setViewTreeLifecycleOwner(mView.findViewTreeLifecycleOwner())
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
//                    composeView.setContent {
//                        Row (
//                            modifier = Modifier
//                                .fillMaxWidth(),
//                            horizontalArrangement = Arrangement.Center,
//                            verticalAlignment = Alignment.CenterVertically
//                        ) {
//                            Row (
//                                modifier = Modifier
//                                    .fillMaxWidth(0.5f),
//                                horizontalArrangement = Arrangement.SpaceBetween
//                            ){
//                                val left = batteryStatus.find { it.component == BatteryComponent.LEFT }
//                                val right = batteryStatus.find { it.component == BatteryComponent.RIGHT }
//                                if ((right?.status == BatteryStatus.CHARGING && left?.status == BatteryStatus.CHARGING) || (left?.status == BatteryStatus.NOT_CHARGING && right?.status == BatteryStatus.NOT_CHARGING))
//                                {
//                                    BatteryIndicator(right.level.let { left.level.coerceAtMost(it) }, left.status == BatteryStatus.CHARGING)
//                                }
//                                else {
//                                    Row {
//                                        if (left?.status != BatteryStatus.DISCONNECTED) {
//                                            Text(text = "\uDBC6\uDCE5", fontFamily = FontFamily(Font(R.font.sf_pro)))
//                                            BatteryIndicator(left?.level ?: 0, left?.status == BatteryStatus.CHARGING)
//                                            Spacer(modifier = Modifier.width(16.dp))
//                                        }
//                                        if (right?.status != BatteryStatus.DISCONNECTED) {
//                                            Text(text = "\uDBC6\uDCE8", fontFamily = FontFamily(Font(R.font.sf_pro)))
//                                            BatteryIndicator(right?.level ?: 0, right?.status == BatteryStatus.CHARGING)
//                                        }
//                                    }
//                                }
//                            }
//                            Row (
//                                modifier = Modifier
//                                    .fillMaxWidth(),
//                                horizontalArrangement = Arrangement.Center
//                            ) {
//                                val case =
//                                    batteryStatus.find { it.component == BatteryComponent.CASE }
//                                BatteryIndicator(case?.level ?: 0)
//                            }
//                        }
//                    }

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