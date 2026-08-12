package com.example.screenmirror

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.example.screenmirror.splash.ParticleView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Premium Splash Screen with MotionLayout, Particle Effects and Interactive Animations
 */
class SplashActivity : AppCompatActivity() {

    private val TAG = "SplashActivity"
    private lateinit var motionLayout: MotionLayout
    private lateinit var particleView: ParticleView
    private lateinit var lottieAnimation: LottieAnimationView
    private lateinit var progressView: View
    private var isTransitioning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")
        
        try {
            // Enable edge-to-edge immersive experience
            WindowCompat.setDecorFitsSystemWindows(window, false)
            
            setContentView(R.layout.activity_splash)
            Log.d(TAG, "setContentView successful")
            
            initViews()
            setupMotionLayout()
            startAnimationSequence()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            // Fallback - go directly to MainActivity
            navigateToMain()
        }
    }

    private fun initViews() {
        try {
            motionLayout = findViewById(R.id.splash_motion_layout)
            particleView = findViewById(R.id.particle_view)
            lottieAnimation = findViewById(R.id.splash_lottie)
            progressView = findViewById(R.id.progress_shimmer)
            
            // Configure Lottie animation
            lottieAnimation.apply {
                setAnimation("spinner.json")
                repeatCount = LottieDrawable.INFINITE
                playAnimation()
            }
            
            // Start particle system
            particleView.startParticles()
            Log.d(TAG, "Views initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing views", e)
            throw e
        }
    }

    private fun setupMotionLayout() {
        motionLayout.setTransitionListener(object : MotionLayout.TransitionListener {
            override fun onTransitionStarted(motionLayout: MotionLayout?, startId: Int, endId: Int) {
                HapticHelper.lightTap(this@SplashActivity)
            }

            override fun onTransitionChange(motionLayout: MotionLayout?, startId: Int, endId: Int, progress: Float) {
                particleView.setIntensity(0.3f + (progress * 0.7f))
                
                if (progress > 0.5f) {
                    animateProgressShimmer()
                }
            }

            override fun onTransitionCompleted(motionLayout: MotionLayout?, currentId: Int) {
                if (currentId == R.id.end && !isTransitioning) {
                    navigateToMain()
                }
            }

            override fun onTransitionTrigger(motionLayout: MotionLayout?, triggerId: Int, positive: Boolean, progress: Float) {}
        })
        
        setupTouchInteraction()
    }

    private fun setupTouchInteraction() {
        motionLayout.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    particleView.createTouchBurst(event.x, event.y)
                    HapticHelper.lightTap(this)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 1) {
                        particleView.createTouchTrail(event.x, event.y)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun startAnimationSequence() {
        lifecycleScope.launch {
            try {
                delay(100)
                motionLayout.transitionToState(R.id.middle)
                
                delay(800)
                motionLayout.transitionToState(R.id.end)
                
                delay(700)
                
                if (!isFinishing && !isDestroyed) {
                    particleView.createCelebrationBurst()
                    delay(300)
                    navigateToMain()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in animation sequence", e)
                navigateToMain()
            }
        }
    }

    private fun animateProgressShimmer() {
        try {
            progressView.animate()
                .alpha(0.8f)
                .setDuration(300)
                .withEndAction {
                    progressView.animate()
                        .alpha(0.3f)
                        .setDuration(300)
                        .start()
                }
                .start()
        } catch (e: Exception) {
            Log.e(TAG, "Error animating shimmer", e)
        }
    }

    private fun navigateToMain() {
        if (isTransitioning) return
        isTransitioning = true
        
        try {
            particleView.stopParticles()
            HapticHelper.mediumTap(this)
            
            motionLayout.animate()
                .alpha(0f)
                .setDuration(400)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    startActivity(Intent(this, MainActivity::class.java))
                    if (android.os.Build.VERSION.SDK_INT >= 34) {
                        overrideActivityTransition(
                            android.app.Activity.OVERRIDE_TRANSITION_OPEN,
                            R.anim.splash_fade_in,
                            R.anim.splash_fade_out
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        overridePendingTransition(R.anim.splash_fade_in, R.anim.splash_fade_out)
                    }
                    finish()
                }
                .start()
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to main", e)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            particleView.cleanup()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up", e)
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Disable back button during splash
    }
}
