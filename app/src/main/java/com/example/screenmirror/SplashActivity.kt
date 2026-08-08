package com.example.screenmirror

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val icon = findViewById<ImageView>(R.id.splash_icon)
        val title = findViewById<TextView>(R.id.splash_title)
        val subtitle = findViewById<TextView>(R.id.splash_subtitle)
        val progress = findViewById<ProgressBar>(R.id.splash_progress)

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

        progress.postDelayed({
            progress.alpha = 1f
        }, 900)

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }, 2200)
    }
}
