package com.example.screenmirror

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val icon = findViewById<ImageView>(R.id.splash_icon)
        val title = findViewById<TextView>(R.id.splash_title)
        val subtitle = findViewById<TextView>(R.id.splash_subtitle)
        val lottie = findViewById<LottieAnimationView>(R.id.splash_lottie)

        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in).apply {
            duration = 600
        }

        icon.postDelayed({
            icon.startAnimation(fadeIn)
            icon.alpha = 1f
        }, 200)

        title.postDelayed({
            title.startAnimation(fadeIn)
            title.alpha = 1f
        }, 500)

        subtitle.postDelayed({
            subtitle.startAnimation(fadeIn)
            subtitle.alpha = 1f
        }, 700)

        lottie.setAnimation("spinner.json")
        lottie.repeatCount = LottieDrawable.INFINITE
        lottie.playAnimation()
        lottie.postDelayed({
            lottie.alpha = 1f
        }, 900)

        lifecycleScope.launch {
            delay(2200)
            if (!isFinishing && !isDestroyed) {
                HapticHelper.lightTap(this@SplashActivity)
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_OPEN, R.anim.fade_in, R.anim.fade_out)
                } else {
                    @Suppress("DEPRECATION")
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                }
                finish()
            }
        }
    }
}
