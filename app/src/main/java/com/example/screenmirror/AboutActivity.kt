package com.example.screenmirror

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

class AboutActivity : AppCompatActivity() {

    companion object {
        private const val INSTAGRAM_URL = "https://www.instagram.com/ferit22901/"
        private const val GITHUB_PROFILE = "https://github.com/Memati8383/"
        private const val SOURCE_CODE = "https://github.com/Memati8383/ScreenShareApp"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.title = ""

        toolbar.setNavigationOnClickListener {
            HapticHelper.lightTap(this)
            finish()
        }

        setupSocialButtons()
        animateElements()
    }

    private fun setupSocialButtons() {
        findViewById<LinearLayout>(R.id.btnInstagram).setOnClickListener {
            HapticHelper.lightTap(this)
            openUrl(INSTAGRAM_URL)
        }

        findViewById<LinearLayout>(R.id.btnGithub).setOnClickListener {
            HapticHelper.lightTap(this)
            openUrl(GITHUB_PROFILE)
        }

        findViewById<LinearLayout>(R.id.btnSource).setOnClickListener {
            HapticHelper.lightTap(this)
            openUrl(SOURCE_CODE)
        }
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun animateElements() {
        val icon = findViewById<ImageView>(R.id.appIcon)
        val nameCard = findViewById<LinearLayout>(R.id.nameCard)
        val featureCard = findViewById<LinearLayout>(R.id.featureCard)
        val devCard = findViewById<LinearLayout>(R.id.devCard)

        icon.alpha = 0f
        icon.translationY = 50f
        icon.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(600)
            .setInterpolator(OvershootInterpolator())
            .start()

        nameCard.alpha = 0f
        nameCard.translationY = 30f
        nameCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setStartDelay(200)
            .setInterpolator(OvershootInterpolator())
            .start()

        featureCard.alpha = 0f
        featureCard.translationY = 30f
        featureCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setStartDelay(400)
            .setInterpolator(OvershootInterpolator())
            .start()

        devCard.alpha = 0f
        devCard.translationY = 30f
        devCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setStartDelay(600)
            .setInterpolator(OvershootInterpolator())
            .start()
    }
}
